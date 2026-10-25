'use strict';
const {
  app,
  BrowserWindow,
  WebContentsView,
  ipcMain,
  shell,
  Notification,
  nativeTheme,
  Menu,
} = require('electron');
const path = require('path');
const fs = require('fs');
const crypto = require('crypto');

// La app se llamaba Claudeck: se sigue usando esa carpeta de datos para no perder cuentas ni sesiones.
app.setPath('userData', path.join(app.getPath('appData'), 'Claudeck'));

const RAIL_W = 76; // ancho de la barra lateral (debe coincidir con styles.css)
const TOPBAR_H = 80; // marco propio + barra superior (debe coincidir con styles.css)
const DRAWER_W = 300; // ancho del cajón de ajustes (debe coincidir con --drawer-w)
// Cada servicio es una "mitad" de la app: sitio propio y cuentas propias.
const SERVICES = {
  claude: {
    label: 'Claude',
    startUrl: 'https://claude.ai/',
    hosts: ['claude.ai'],
    trusted: (u) => /(^|\.)(claude|anthropic)\.(ai|com)$/i.test(u.hostname),
  },
  replit: {
    label: 'Replit',
    startUrl: 'https://replit.com/',
    hosts: ['replit.com'],
    // Incluye los accesos de inicio de sesión (Google, GitHub, Apple) para que el login no se corte.
    trusted: (u) =>
      /(^|\.)replit\.(com|dev)$/i.test(u.hostname) ||
      /(^|\.)repl\.co$/i.test(u.hostname) ||
      u.hostname === 'accounts.google.com' ||
      u.hostname === 'appleid.apple.com' ||
      (u.hostname === 'github.com' && /^\/(login|sessions?)(\/|$)/.test(u.pathname)),
  },
};
const ALLOWED_PERMISSIONS = new Set(['media', 'clipboard-sanitized-write', 'fullscreen', 'notifications']);

// Que el sitio vea un Chromium normal (sin la palabra "Electron" en el user agent).
if (app.userAgentFallback) {
  app.userAgentFallback = app.userAgentFallback
    .replace(/\s*Electron\/\S+/i, '')
    .replace(/\s*claudeck\/\S+/i, '');
}

let win = null;
let modalOpen = false;
let drawerOpen = false;
let drawerOpenedAt = 0;
let programmaticFocus = false;
const views = new Map(); // id -> WebContentsView
const configuredSessions = new WeakSet();

// ---------------------------------------------------------------- Estado

// `state` es lo que ve el renderer: las cuentas y la cuenta activa del servicio en uso.
// `buckets` guarda las de cada servicio por separado (Claude conserva las claves originales del archivo).
let state = { accounts: [], selectedId: null, tutorialShown: false, service: 'claude' };
const buckets = {
  claude: { accounts: [], selectedId: null },
  replit: { accounts: [], selectedId: null },
};
const stateFile = () => path.join(app.getPath('userData'), 'accounts.json');

function sanitizeAccounts(list, svc) {
  if (!Array.isArray(list)) return [];
  return list
    .filter((a) => a && typeof a.id === 'string' && /^[a-f0-9]{6,32}$/.test(a.id))
    .map((a) => ({
      id: a.id,
      name: cleanText(a.name, 24) || 'Cuenta',
      purpose: cleanText(a.purpose, 60),
      color: cleanColor(a.color),
      limitUntil: Number(a.limitUntil) > 0 ? Number(a.limitUntil) : 0,
      limitStart: Number(a.limitStart) > 0 ? Number(a.limitStart) : 0,
      lastUrl: cleanUrl(a.lastUrl, svc) || '',
    }));
}

function loadState() {
  try {
    const raw = JSON.parse(fs.readFileSync(stateFile(), 'utf8'));
    buckets.claude.accounts = sanitizeAccounts(raw.accounts, 'claude');
    buckets.claude.selectedId = typeof raw.selectedId === 'string' ? raw.selectedId : null;
    if (raw.replit && typeof raw.replit === 'object') {
      buckets.replit.accounts = sanitizeAccounts(raw.replit.accounts, 'replit');
      buckets.replit.selectedId = typeof raw.replit.selectedId === 'string' ? raw.replit.selectedId : null;
    }
    state.tutorialShown = raw.tutorialShown === true;
    state.service = SERVICES[raw.service] ? raw.service : 'claude';
  } catch (_) {
    /* primera vez: estado vacío */
  }
  for (const b of Object.values(buckets)) {
    if (!b.accounts.some((a) => a.id === b.selectedId)) {
      b.selectedId = b.accounts.length ? b.accounts[0].id : null;
    }
  }
  state.accounts = buckets[state.service].accounts;
  state.selectedId = buckets[state.service].selectedId;
}

// Vuelca lo que se está usando (state) a su servicio.
function syncBucket() {
  buckets[state.service] = { accounts: state.accounts, selectedId: state.selectedId };
}

function saveState() {
  try {
    syncBucket();
    const out = {
      accounts: buckets.claude.accounts,
      selectedId: buckets.claude.selectedId,
      replit: buckets.replit,
      service: state.service,
      tutorialShown: state.tutorialShown,
    };
    const tmp = stateFile() + '.tmp';
    fs.writeFileSync(tmp, JSON.stringify(out, null, 2));
    fs.renameSync(tmp, stateFile());
  } catch (e) {
    console.error('No se pudo guardar el estado', e);
  }
}

function cleanText(v, max) {
  return typeof v === 'string' ? v.trim().slice(0, max) : '';
}
function cleanColor(v) {
  return typeof v === 'string' && /^#[0-9a-f]{6}$/i.test(v) ? v : '#E07A5F';
}
function cleanUrl(raw, svc) {
  if (typeof raw !== 'string' || raw.length > 4096) return '';
  const hosts = (SERVICES[svc] || SERVICES.claude).hosts;
  try {
    const url = new URL(raw);
    const host = url.hostname.toLowerCase();
    if (
      url.protocol !== 'https:' ||
      !hosts.some((h) => host === h || host.endsWith('.' + h)) ||
      url.username ||
      url.password ||
      (url.port && url.port !== '443')
    ) return '';
    url.search = '';
    url.hash = '';
    return url.href;
  } catch (_) {
    return '';
  }
}
// Busca en las cuentas de ambos servicios (las vistas de la otra mitad siguen vivas, ocultas).
const find = (id) => state.accounts.find((a) => a.id === id) || otherAccounts().find((a) => a.id === id);
function otherAccounts() {
  return Object.keys(SERVICES)
    .filter((k) => k !== state.service)
    .flatMap((k) => buckets[k].accounts);
}
function serviceOf(id) {
  return state.accounts.some((a) => a.id === id) ? state.service : Object.keys(SERVICES).find((k) => k !== state.service && buckets[k].accounts.some((a) => a.id === id)) || 'claude';
}

function broadcast() {
  if (win && !win.isDestroyed()) win.webContents.send('state', state);
}

function rememberPage(id, rawUrl) {
  const acc = find(id);
  const url = cleanUrl(rawUrl, serviceOf(id));
  if (!acc || !url || acc.lastUrl === url) return;
  acc.lastUrl = url;
  saveState();
}

// ---------------------------------------------------------------- Vistas (una por cuenta)

function partitionOf(acc) {
  // "persist:" guarda cookies y datos en disco: la sesión sobrevive al cerrar la app.
  return 'persist:acc_' + acc.id;
}

function setupSession(ses) {
  if (configuredSessions.has(ses)) return;
  configuredSessions.add(ses);
  ses.setPermissionRequestHandler((_wc, permission, callback) => {
    callback(ALLOWED_PERMISSIONS.has(permission));
  });
}

function openLink(url, wc) {
  let u;
  try {
    u = new URL(url);
  } catch (_) {
    return;
  }
  if (u.protocol === 'mailto:') {
    shell.openExternal(url);
  } else if (u.protocol === 'https:' || u.protocol === 'http:') {
    if (SERVICES[state.service].trusted(u)) wc.loadURL(url);
    else shell.openExternal(url);
  }
}

function createView(acc) {
  const view = new WebContentsView({
    webPreferences: {
      partition: partitionOf(acc),
      contextIsolation: true,
      sandbox: true,
      nodeIntegration: false,
    },
  });
  const wc = view.webContents;
  setupSession(wc.session);
  wc.on('did-navigate', (_event, url) => rememberPage(acc.id, url));
  wc.on('did-navigate-in-page', (_event, url, isMainFrame) => {
    if (isMainFrame) rememberPage(acc.id, url);
  });
  wc.setWindowOpenHandler(({ url }) => {
    openLink(url, wc);
    return { action: 'deny' };
  });
  wc.on('before-input-event', handleShortcut);
  // Tocar la página con el cajón abierto lo cierra (las vistas se dibujan sobre el HTML y no dejan pasar el clic).
  wc.on('focus', () => {
    if (drawerOpen && !programmaticFocus && Date.now() - drawerOpenedAt > 400) closeDrawerFromMain();
  });
  const svc = SERVICES[serviceOf(acc.id)];
  wc.loadURL(cleanUrl(acc.lastUrl, serviceOf(acc.id)) || svc.startUrl);
  view.setVisible(false);
  win.contentView.addChildView(view);
  views.set(acc.id, view);
  layout();
  return view;
}

function destroyView(id) {
  const view = views.get(id);
  if (!view) return;
  views.delete(id);
  try {
    win.contentView.removeChildView(view);
    view.webContents.close();
  } catch (_) {
    /* ya cerrada */
  }
}

function layout() {
  if (!win || win.isDestroyed()) return;
  const [w, h] = win.getContentSize();
  const left = RAIL_W + (drawerOpen ? DRAWER_W : 0); // con el cajón abierto la página se corre a la derecha
  const bounds = {
    x: left,
    y: TOPBAR_H,
    width: Math.max(0, w - left),
    height: Math.max(0, h - TOPBAR_H),
  };
  for (const v of views.values()) v.setBounds(bounds);
}

function showCurrent() {
  for (const [id, v] of views) v.setVisible(!modalOpen && id === state.selectedId);
  const cur = views.get(state.selectedId);
  if (cur && !modalOpen) {
    programmaticFocus = true;
    cur.webContents.focus();
    setTimeout(() => {
      programmaticFocus = false;
    }, 150);
  }
  const acc = find(state.selectedId);
  if (win) win.setTitle(acc ? `${acc.name} — AlternIAs` : 'AlternIAs');
}

function closeDrawerFromMain() {
  drawerOpen = false;
  layout();
  if (win && !win.isDestroyed()) win.webContents.send('action', { type: 'drawer-closed' });
}

function selectAccount(id) {
  const acc = find(id);
  if (!acc) return;
  state.selectedId = id;
  if (!views.has(id)) createView(acc);
  saveState();
  layout();
  showCurrent();
  broadcast();
}

// ---------------------------------------------------------------- Atajos

function handleShortcut(event, input) {
  if (input.type !== 'keyDown' || !(input.control || input.meta)) return;
  const key = String(input.key || '');
  if (/^[1-9]$/.test(key)) {
    const acc = state.accounts[Number(key) - 1];
    if (acc) {
      event.preventDefault();
      selectAccount(acc.id);
    }
  } else if (key === 'Tab') {
    event.preventDefault();
    const n = state.accounts.length;
    if (!n) return;
    const i = state.accounts.findIndex((a) => a.id === state.selectedId);
    selectAccount(state.accounts[(i + (input.shift ? -1 : 1) + n) % n].id);
  } else if (key.toLowerCase() === 'r') {
    event.preventDefault();
    const cur = views.get(state.selectedId);
    if (cur) cur.webContents.reload();
  } else if (key.toLowerCase() === 'n') {
    event.preventDefault();
    if (win) win.webContents.send('action', { type: 'add' });
  }
}

// ---------------------------------------------------------------- Límites de uso

function checkLimits() {
  const now = Date.now();
  let changed = false;
  for (const a of [...state.accounts, ...otherAccounts()]) {
    if (a.limitUntil > 0 && a.limitUntil <= now) {
      a.limitUntil = 0;
      a.limitStart = 0;
      changed = true;
      if (Notification.isSupported()) {
        new Notification({
          title: 'AlternIAs',
          body: `«${a.name}» ya puede usarse de nuevo`,
        }).show();
      }
    }
  }
  if (changed) {
    saveState();
    broadcast();
  }
}

// ---------------------------------------------------------------- IPC

async function wipeSession(acc) {
  const ses = require('electron').session.fromPartition(partitionOf(acc));
  await ses.clearStorageData();
  await ses.clearCache();
}

function registerIpc() {
  ipcMain.handle('state:get', () => state);
  ipcMain.handle('app:version', () => app.getVersion());
  ipcMain.handle('tutorial:complete', () => {
    state.tutorialShown = true;
    saveState();
  });
  ipcMain.handle('window:minimize', () => {
    if (win && !win.isDestroyed()) win.minimize();
  });
  ipcMain.handle('window:toggle-maximize', () => {
    if (!win || win.isDestroyed()) return;
    if (win.isMaximized()) win.unmaximize();
    else win.maximize();
  });
  ipcMain.handle('window:close', () => {
    if (win && !win.isDestroyed()) win.close();
  });

  ipcMain.handle('account:add', (_e, data) => {
    const acc = {
      id: crypto.randomBytes(6).toString('hex'),
      name: cleanText(data && data.name, 24) || 'Cuenta',
      purpose: cleanText(data && data.purpose, 60),
      color: cleanColor(data && data.color),
      limitUntil: 0,
      limitStart: 0,
    };
    state.accounts.push(acc);
    selectAccount(acc.id);
    return acc.id;
  });

  ipcMain.handle('account:update', (_e, id, patch) => {
    const acc = find(id);
    if (!acc || !patch) return;
    if (patch.name !== undefined) acc.name = cleanText(patch.name, 24) || acc.name;
    if (patch.purpose !== undefined) acc.purpose = cleanText(patch.purpose, 60);
    if (patch.color !== undefined) acc.color = cleanColor(patch.color);
    saveState();
    showCurrent();
    broadcast();
  });

  ipcMain.handle('account:select', (_e, id) => selectAccount(id));

  // Cambia de mitad de la app (Claude ↔ Replit): cada una con sus cuentas y su cuenta activa.
  ipcMain.handle('service:set', (_e, svc) => {
    if (!SERVICES[svc] || svc === state.service) return;
    syncBucket();
    state.service = svc;
    applyServiceTheme();
    state.accounts = buckets[svc].accounts;
    state.selectedId = buckets[svc].selectedId;
    if (state.selectedId && find(state.selectedId)) {
      selectAccount(state.selectedId);
    } else {
      state.selectedId = state.accounts.length ? state.accounts[0].id : null;
      if (state.selectedId) {
        selectAccount(state.selectedId);
      } else {
        saveState();
        showCurrent();
        broadcast();
      }
    }
  });

  ipcMain.handle('account:reload', (_e, id) => {
    const v = views.get(id);
    if (v) v.webContents.reload();
    else selectAccount(id);
  });

  ipcMain.handle('account:limit', (_e, id, until) => {
    const acc = find(id);
    if (!acc) return;
    const n = Number(until);
    acc.limitUntil = Number.isFinite(n) && n > Date.now() ? n : 0;
    acc.limitStart = acc.limitUntil > 0 ? Date.now() : 0;
    saveState();
    broadcast();
  });

  ipcMain.handle('account:wipe', async (_e, id) => {
    const acc = find(id);
    if (!acc) return;
    destroyView(id);
    await wipeSession(acc);
    if (state.selectedId === id) selectAccount(id);
  });

  ipcMain.handle('account:remove', async (_e, id) => {
    const acc = find(id);
    if (!acc) return;
    destroyView(id);
    await wipeSession(acc);
    state.accounts = state.accounts.filter((a) => a.id !== id);
    if (state.selectedId === id) {
      state.selectedId = state.accounts.length ? state.accounts[0].id : null;
      if (state.selectedId) {
        selectAccount(state.selectedId);
        return;
      }
    }
    saveState();
    showCurrent();
    broadcast();
  });

  ipcMain.handle('ui:modal', (_e, open) => {
    modalOpen = !!open;
    showCurrent();
  });

  ipcMain.handle('ui:drawer', (_e, open) => {
    drawerOpen = !!open;
    if (drawerOpen) drawerOpenedAt = Date.now();
    layout();
  });

  ipcMain.handle('app:downloads', () => shell.openPath(app.getPath('downloads')));

  ipcMain.handle('account:menu', (_e, id) => {
    const acc = find(id);
    if (!acc || !win) return;
    const act = (type) => () => win.webContents.send('action', { type, id });
    const menu = Menu.buildFromTemplate([
      { label: 'Editar nombre, propósito o color', click: act('edit') },
      {
        label: 'Recargar',
        click: () => {
          const v = views.get(id);
          if (v) v.webContents.reload();
          else selectAccount(id);
        },
      },
      { type: 'separator' },
      { label: 'Marcar límite alcanzado…', click: act('limit') },
      {
        label: 'Quitar marca de límite',
        enabled: acc.limitUntil > Date.now(),
        click: () => {
          acc.limitUntil = 0;
          acc.limitStart = 0;
          saveState();
          broadcast();
        },
      },
      { type: 'separator' },
      { label: 'Cerrar sesión y borrar datos…', click: act('confirm-wipe') },
      { label: 'Eliminar cuenta…', click: act('confirm-remove') },
    ]);
    menu.popup({ window: win });
  });
}

// ---------------------------------------------------------------- Ventana

// Claude va oscuro y Replit claro, sin importar el tema de Windows: nativeTheme lo aplica a la
// interfaz y a las páginas (prefers-color-scheme).
function applyServiceTheme() {
  const light = state.service === 'replit';
  nativeTheme.themeSource = light ? 'light' : 'dark';
  if (win && !win.isDestroyed()) win.setBackgroundColor(light ? '#fbf8f4' : '#1c1a18');
}

function createWindow() {
  const dark = state.service !== 'replit';
  win = new BrowserWindow({
    width: 1280,
    height: 820,
    minWidth: 900,
    minHeight: 600,
    title: 'AlternIAs',
    backgroundColor: dark ? '#1c1a18' : '#f6f2ec',
    frame: false,
    resizable: true,
    autoHideMenuBar: true,
    show: false,
    webPreferences: {
      preload: path.join(__dirname, 'preload.js'),
      contextIsolation: true,
      sandbox: true,
      nodeIntegration: false,
    },
  });
  win.removeMenu();
  win.on('page-title-updated', (e) => e.preventDefault());
  const sendWindowState = () => {
    if (win && !win.isDestroyed()) {
      win.webContents.send('window-state', { maximized: win.isMaximized() });
    }
  };
  win.webContents.on('before-input-event', handleShortcut);
  win.once('ready-to-show', () => win.show());
  for (const ev of ['resize', 'maximize', 'unmaximize', 'enter-full-screen', 'leave-full-screen', 'restore']) {
    win.on(ev, layout);
  }
  for (const ev of ['maximize', 'unmaximize', 'restore']) win.on(ev, sendWindowState);
  win.on('closed', () => {
    win = null;
    views.clear();
  });
  win.webContents.setWindowOpenHandler(() => ({ action: 'deny' }));
  win.webContents.on('will-navigate', (e) => e.preventDefault());
  win.loadFile(path.join(__dirname, 'renderer', 'index.html'));
  win.webContents.once('did-finish-load', () => {
    sendWindowState();
    if (state.selectedId) selectAccount(state.selectedId);
    else broadcast();
  });
}

if (!app.requestSingleInstanceLock()) {
  app.quit();
} else {
  app.on('second-instance', () => {
    if (win) {
      if (win.isMinimized()) win.restore();
      win.focus();
    }
  });

  app.whenReady().then(() => {
    Menu.setApplicationMenu(null);
    loadState();
    applyServiceTheme();
    app.on('before-quit', () => {
      for (const [id, view] of views) {
        if (!view.webContents.isDestroyed()) rememberPage(id, view.webContents.getURL());
      }
    });
    registerIpc();
    createWindow();
    setInterval(checkLimits, 30000);
    app.on('activate', () => {
      if (BrowserWindow.getAllWindows().length === 0) createWindow();
    });
  });

  app.on('window-all-closed', () => {
    app.quit();
  });
}
