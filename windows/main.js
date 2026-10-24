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

const RAIL_W = 76; // ancho de la barra lateral (debe coincidir con styles.css)
const TOPBAR_H = 44; // alto de la barra superior (idem)
const START_URL = 'https://claude.ai/';
const TRUSTED_HOST = /(^|\.)(claude|anthropic)\.(ai|com)$/i;
const ALLOWED_PERMISSIONS = new Set(['media', 'clipboard-sanitized-write', 'fullscreen', 'notifications']);

// Que el sitio vea un Chromium normal (sin la palabra "Electron" en el user agent).
if (app.userAgentFallback) {
  app.userAgentFallback = app.userAgentFallback
    .replace(/\s*Electron\/\S+/i, '')
    .replace(/\s*claudeck\/\S+/i, '');
}

let win = null;
let modalOpen = false;
const views = new Map(); // id -> WebContentsView
const configuredSessions = new WeakSet();

// ---------------------------------------------------------------- Estado

let state = { accounts: [], selectedId: null };
const stateFile = () => path.join(app.getPath('userData'), 'accounts.json');

function loadState() {
  try {
    const raw = JSON.parse(fs.readFileSync(stateFile(), 'utf8'));
    if (Array.isArray(raw.accounts)) {
      state.accounts = raw.accounts
        .filter((a) => a && typeof a.id === 'string' && /^[a-f0-9]{6,32}$/.test(a.id))
        .map((a) => ({
          id: a.id,
          name: cleanText(a.name, 24) || 'Cuenta',
          purpose: cleanText(a.purpose, 60),
          color: cleanColor(a.color),
          limitUntil: Number(a.limitUntil) > 0 ? Number(a.limitUntil) : 0,
        }));
    }
    state.selectedId = typeof raw.selectedId === 'string' ? raw.selectedId : null;
  } catch (_) {
    /* primera vez: estado vacío */
  }
  if (!state.accounts.some((a) => a.id === state.selectedId)) {
    state.selectedId = state.accounts.length ? state.accounts[0].id : null;
  }
}

function saveState() {
  try {
    const tmp = stateFile() + '.tmp';
    fs.writeFileSync(tmp, JSON.stringify(state, null, 2));
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
const find = (id) => state.accounts.find((a) => a.id === id);

function broadcast() {
  if (win && !win.isDestroyed()) win.webContents.send('state', state);
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
    if (TRUSTED_HOST.test(u.hostname)) wc.loadURL(url);
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
  wc.setWindowOpenHandler(({ url }) => {
    openLink(url, wc);
    return { action: 'deny' };
  });
  wc.on('before-input-event', handleShortcut);
  wc.loadURL(START_URL);
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
  const bounds = {
    x: RAIL_W,
    y: TOPBAR_H,
    width: Math.max(0, w - RAIL_W),
    height: Math.max(0, h - TOPBAR_H),
  };
  for (const v of views.values()) v.setBounds(bounds);
}

function showCurrent() {
  for (const [id, v] of views) v.setVisible(!modalOpen && id === state.selectedId);
  const cur = views.get(state.selectedId);
  if (cur && !modalOpen) cur.webContents.focus();
  const acc = find(state.selectedId);
  if (win) win.setTitle(acc ? `${acc.name} — Claudeck` : 'Claudeck');
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
  for (const a of state.accounts) {
    if (a.limitUntil > 0 && a.limitUntil <= now) {
      a.limitUntil = 0;
      changed = true;
      if (Notification.isSupported()) {
        new Notification({
          title: 'Claudeck',
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

  ipcMain.handle('account:add', (_e, data) => {
    const acc = {
      id: crypto.randomBytes(6).toString('hex'),
      name: cleanText(data && data.name, 24) || 'Cuenta',
      purpose: cleanText(data && data.purpose, 60),
      color: cleanColor(data && data.color),
      limitUntil: 0,
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

function createWindow() {
  const dark = nativeTheme.shouldUseDarkColors;
  win = new BrowserWindow({
    width: 1280,
    height: 820,
    minWidth: 900,
    minHeight: 600,
    title: 'Claudeck',
    backgroundColor: dark ? '#1c1a18' : '#f6f2ec',
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
  win.webContents.on('before-input-event', handleShortcut);
  win.once('ready-to-show', () => win.show());
  for (const ev of ['resize', 'maximize', 'unmaximize', 'enter-full-screen', 'leave-full-screen', 'restore']) {
    win.on(ev, layout);
  }
  win.on('closed', () => {
    win = null;
    views.clear();
  });
  win.webContents.setWindowOpenHandler(() => ({ action: 'deny' }));
  win.webContents.on('will-navigate', (e) => e.preventDefault());
  win.loadFile(path.join(__dirname, 'renderer', 'index.html'));
  win.webContents.once('did-finish-load', () => {
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
