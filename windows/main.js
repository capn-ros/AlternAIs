'use strict';
const {
  app,
  BrowserWindow,
  WebContentsView,
  ipcMain,
  shell,
  Notification,
  nativeTheme,
  clipboard,
  Menu,
  Tray,
  nativeImage,
  dialog,
} = require('electron');
const path = require('path');
const fileops = require('./fileops');
const fs = require('fs');
const crypto = require('crypto');

// La app se llamaba Claudeck: se sigue usando esa carpeta de datos para no perder cuentas ni sesiones.
app.setPath('userData', path.join(app.getPath('appData'), 'Claudeck'));

const RAIL_W = 76; // ancho de la barra lateral (debe coincidir con styles.css)
const TOPBAR_H = 80; // marco propio + barra superior (debe coincidir con styles.css)
const RAIL_H = 72; // alto de la barra de cuentas cuando va abajo (debe coincidir con --rail-h)
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
let state = {
  accounts: [],
  selectedId: null,
  tutorialShown: false,
  service: 'claude',
  // Tema (oscuro/claro) de cada servicio: Claude arranca oscuro y Replit claro.
  themes: { claude: 'dark', replit: 'light' },
  // Opciones de la app: posición de la barra de cuentas, inicio con Windows y qué pasa al cerrar.
  settings: { railPos: 'left', launchAtLogin: false, closeToTray: false },
  // Mensajes guardados para escribir en el chat (compartidos por Claude y Replit).
  snippets: [],
  // Navegador interno: última página y dónde guarda las descargas (default = Descargas, ask = preguntar, folder = carpeta fija).
  browser: { lastUrl: '', dlMode: 'default', dlDir: '' },
};
const DEFAULT_SNIPPETS = [
  'Seguí donde te quedaste.',
  'Antes de hacer nada, explicame lo siguiente: ',
  'Resumí lo anterior en pocos puntos.',
  'Explicámelo de forma simple, con un ejemplo.',
];
function cleanSnippets(list) {
  if (!Array.isArray(list)) return [];
  return list.filter((t) => typeof t === 'string' && t.trim()).map((t) => t.slice(0, 4000)).slice(0, 50);
}
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
      email: cleanEmail(a.email),
      autoReply: cleanText(a.autoReply, 4000),
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
    state.snippets = Array.isArray(raw.snippets) ? cleanSnippets(raw.snippets) : DEFAULT_SNIPPETS.slice();
    state.service = SERVICES[raw.service] ? raw.service : 'claude';
    const th = raw.themes && typeof raw.themes === 'object' ? raw.themes : {};
    state.themes = {
      claude: th.claude === 'light' ? 'light' : 'dark',
      replit: th.replit === 'dark' ? 'dark' : 'light',
    };
    const br = raw.browser && typeof raw.browser === 'object' ? raw.browser : {};
    state.browser = {
      lastUrl: typeof br.lastUrl === 'string' && /^https?:\/\//i.test(br.lastUrl) ? br.lastUrl.slice(0, 2000) : '',
      dlMode: ['default', 'ask', 'folder'].includes(br.dlMode) ? br.dlMode : 'default',
      dlDir: typeof br.dlDir === 'string' && path.isAbsolute(br.dlDir) ? br.dlDir : '',
    };
    const st = raw.settings && typeof raw.settings === 'object' ? raw.settings : {};
    state.settings = {
      railPos: ['left', 'right', 'bottom'].includes(st.railPos) ? st.railPos : 'left',
      launchAtLogin: st.launchAtLogin === true,
      closeToTray: st.closeToTray === true,
    };
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
      themes: state.themes,
      tutorialShown: state.tutorialShown,
      snippets: state.snippets,
      settings: state.settings,
      browser: state.browser,
    };
    const tmp = stateFile() + '.tmp';
    fs.writeFileSync(tmp, JSON.stringify(out, null, 2));
    fs.renameSync(tmp, stateFile());
  } catch (e) {
    console.error('No se pudo guardar el estado', e);
  }
}

function cleanEmail(v) {
  const t = cleanText(v, 80);
  return /^[^\s@]+@[^\s@]+$/.test(t) ? t : '';
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
  state.loading = loadingIds.has(state.selectedId);
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

// Páginas que todavía cargan (la primera vez o al recargar): mientras tanto la vista queda oculta y el
// renderer muestra el logo animado en bucle. Se queda al menos ~0,7 s para que no parpadee.
const loadingIds = new Set();
function trackLoading(id, wc) {
  loadingIds.add(id);
  const since = Date.now();
  let done = false;
  const finish = () => {
    if (done) return;
    done = true;
    clearTimeout(safety);
    wc.removeListener('did-stop-loading', finish);
    wc.removeListener('did-fail-load', onFail);
    setTimeout(() => {
      loadingIds.delete(id);
      if (views.has(id)) showCurrent();
      broadcast();
    }, Math.max(0, 700 - (Date.now() - since)));
  };
  const onFail = (_e, _code, _desc, _url, isMainFrame) => {
    if (isMainFrame) finish();
  };
  const safety = setTimeout(finish, 25000);
  wc.on('did-stop-loading', finish);
  wc.on('did-fail-load', onFail);
}

function reloadView(id) {
  const v = views.get(id);
  if (!v) return false;
  trackLoading(id, v.webContents);
  v.webContents.reload();
  showCurrent();
  broadcast();
  return true;
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
  const svc = SERVICES[serviceOf(acc.id)];
  trackLoading(acc.id, wc);
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
  loadingIds.delete(id);
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
  // La barra de cuentas puede ir a la izquierda, a la derecha o abajo; con el cajón abierto la
  // página deja ese lugar libre (del lado de la barra).
  const drawer = 0; // el cajón se dibuja encima de la página: ya no la corre
  const pos = state.settings.railPos;
  let x = 0;
  let width = w;
  let height = h - TOPBAR_H;
  if (pos === 'left') {
    x = RAIL_W + drawer;
    width = w - x;
  } else if (pos === 'right') {
    width = w - RAIL_W - drawer;
  } else {
    x = drawer;
    width = w - drawer;
    height = h - TOPBAR_H - RAIL_H;
  }
  const bounds = { x, y: TOPBAR_H, width: Math.max(0, width), height: Math.max(0, height) };
  for (const v of views.values()) v.setBounds(bounds);
}

function showCurrent() {
  for (const [id, v] of views) v.setVisible(!modalOpen && !drawerOpen && id === state.selectedId && !loadingIds.has(id));
  const cur = views.get(state.selectedId);
  if (cur && !modalOpen && !drawerOpen) {
    programmaticFocus = true;
    cur.webContents.focus();
    setTimeout(() => {
      programmaticFocus = false;
    }, 150);
  }
  const acc = find(state.selectedId);
  if (win) win.setTitle(acc ? `${acc.name} — AlternAIs` : 'AlternAIs');
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
  if (drawerOpen) closeDrawerFromMain(); // elegir una cuenta cierra el cajón
  if (!views.has(id)) createView(acc);
  saveState();
  layout();
  showCurrent();
  broadcast();
}

// ---------------------------------------------------------------- Atajos

// ---------------------------------------------------------------- Navegador interno (universal)

// Una sola vista web para toda la app, con su propia sesión (partición «browser»): no pertenece a ninguna cuenta.
const BROWSER_HOME = 'https://github.com/';
let browserView = null;
let browserShown = false;
let browserRect = null;
let browserLoaded = false;

function normalizeBrowserInput(raw) {
  const t = typeof raw === 'string' ? raw.trim().slice(0, 2000) : '';
  if (!t) return null;
  if (/^https?:\/\//i.test(t)) return t;
  if (/^[a-z][a-z0-9+.-]*:\/\//i.test(t)) return null; // otros protocolos: no
  if (!/\s/.test(t) && t.includes('.')) return 'https://' + t;
  return 'https://duckduckgo.com/?q=' + encodeURIComponent(t);
}

function browserState() {
  if (!browserView || browserView.webContents.isDestroyed()) {
    return { url: '', title: '', canGoBack: false, canGoForward: false, loading: false };
  }
  const wc = browserView.webContents;
  const h = wc.navigationHistory;
  return {
    url: wc.getURL(),
    title: wc.getTitle(),
    canGoBack: h ? h.canGoBack() : wc.canGoBack(),
    canGoForward: h ? h.canGoForward() : wc.canGoForward(),
    loading: wc.isLoading(),
  };
}

function sendBrowserState() {
  if (win && !win.isDestroyed()) win.webContents.send('browser-state', browserState());
}

function applyBrowserVisibility() {
  if (!browserView) return;
  if (browserRect) browserView.setBounds(browserRect);
  browserView.setVisible(browserShown && !modalOpen && !!browserRect);
}

function ensureBrowser() {
  if (browserView) return browserView;
  browserView = new WebContentsView({
    webPreferences: { partition: 'persist:browser', contextIsolation: true, sandbox: true, nodeIntegration: false },
  });
  const wc = browserView.webContents;
  setupSession(wc.session);
  // Que los sitios lo vean como un Chrome común y no como una app Electron.
  wc.setUserAgent(wc.getUserAgent().replace(/\s(Electron|AlternAIs|claudeck)\/\S+/gi, ''));
  wc.setWindowOpenHandler(({ url }) => {
    if (/^https?:\/\//i.test(url)) wc.loadURL(url);
    return { action: 'deny' };
  });
  wc.on('will-navigate', (e, url) => {
    if (!/^https?:\/\//i.test(url)) e.preventDefault();
  });
  wc.on('before-input-event', (event, input) => {
    if (input.type !== 'keyDown') return;
    const k = String(input.key || '').toLowerCase();
    if (input.control && k === 'r') {
      event.preventDefault();
      wc.reload();
    } else if (input.control && k === 'l') {
      event.preventDefault();
      if (win) win.webContents.send('action', { type: 'browser-url' });
    } else if (input.control && k === 'b') {
      event.preventDefault();
      if (win) win.webContents.send('action', { type: 'browser' });
    } else if (input.alt && k === 'arrowleft') {
      event.preventDefault();
      wc.navigationHistory ? wc.navigationHistory.canGoBack() && wc.navigationHistory.goBack() : wc.goBack();
    } else if (input.alt && k === 'arrowright') {
      event.preventDefault();
      wc.navigationHistory ? wc.navigationHistory.canGoForward() && wc.navigationHistory.goForward() : wc.goForward();
    } else {
      handleShortcut(event, input);
    }
  });
  for (const ev of ['did-navigate', 'did-navigate-in-page', 'page-title-updated', 'did-start-loading', 'did-stop-loading', 'did-fail-load']) {
    wc.on(ev, sendBrowserState);
  }
  // Recuerda la última página para retomarla la próxima vez.
  let rememberTimer = null;
  const remember = () => {
    const u = wc.getURL();
    if (!/^https?:\/\//i.test(u) || u === state.browser.lastUrl) return;
    state.browser.lastUrl = u;
    clearTimeout(rememberTimer);
    rememberTimer = setTimeout(saveState, 1500);
  };
  wc.on('did-navigate', remember);
  wc.on('did-navigate-in-page', remember);
  // Descargas: Descargas, carpeta fija, o dejar que Electron pregunte (ask).
  wc.session.on('will-download', (_e, item) => {
    const { dlMode, dlDir } = state.browser;
    if (dlMode === 'ask') return; // diálogo nativo «Guardar como»
    let dir = app.getPath('downloads');
    if (dlMode === 'folder' && dlDir && fs.existsSync(dlDir)) dir = dlDir;
    const name = path.basename(item.getFilename() || 'descarga');
    const ext = path.extname(name);
    const base = ext ? name.slice(0, -ext.length) : name;
    let target = path.join(dir, name);
    for (let i = 2; fs.existsSync(target); i++) target = path.join(dir, `${base} (${i})${ext}`);
    item.setSavePath(target);
    item.once('done', (_ev, st) => {
      if (win && !win.isDestroyed()) win.webContents.send('browser-download', { ok: st === 'completed', name: path.basename(target), path: target });
    });
  });
  browserView.setVisible(false);
  win.contentView.addChildView(browserView);
  return browserView;
}

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
    if (state.selectedId) reloadView(state.selectedId);
  } else if (key.toLowerCase() === 'b') {
    event.preventDefault();
    if (win) win.webContents.send('action', { type: 'browser' });
  } else if (key.toLowerCase() === 'e') {
    event.preventDefault();
    if (win) win.webContents.send('action', { type: 'files' });
  } else if (key.toLowerCase() === 'm') {
    event.preventDefault();
    if (win && state.selectedId) win.webContents.send('action', { type: 'snippets' });
  } else if (key.toLowerCase() === 'n') {
    event.preventDefault();
    if (win) win.webContents.send('action', { type: 'add' });
  }
}

// ---------------------------------------------------------------- Límites de uso

// Respuesta automática: al terminar el descanso escribe el mensaje elegido en el chat de la cuenta y lo envía.
// Trabaja en segundo plano con la vista de esa cuenta (sin cambiar la que estás mirando).
let autoChain = Promise.resolve();
function runAutoReply(acc, text) {
  autoChain = autoChain.then(() => doAutoReply(acc, text)).catch(() => {});
}

// Avisos de la respuesta automática. Tocarlos abre la ventana; con `replyId` abre además el selector de respuesta rápida.
function notifyAuto(title, body, replyId) {
  if (!Notification.isSupported()) return;
  const n = new Notification({ title, body });
  n.on('click', () => {
    showWindow();
    if (replyId && win && !win.isDestroyed()) win.webContents.send('action', { type: 'quick-reply', id: replyId });
  });
  n.show();
}

async function doAutoReply(acc, text) {
  const sleep = (ms) => new Promise((r) => setTimeout(r, ms));
  let view = views.get(acc.id);
  if (view && !view.webContents.isDestroyed()) {
    view.webContents.reload(); // página fresca: sin el aviso de límite
  } else {
    view = createView(acc);
  }
  const wc = view.webContents;
  await sleep(1500);
  for (let i = 0; i < 20 && wc.isLoading(); i++) await sleep(1000);
  await sleep(2500); // que la página termine de armarse
  const js = `(function (t) {
    function isEd(e) { return e && (e.isContentEditable || e.tagName === 'TEXTAREA'); }
    var c = [].slice.call(document.querySelectorAll('div[contenteditable="true"],textarea')).filter(function (x) { return x.offsetParent !== null; });
    var el = c.length ? c[c.length - 1] : null;
    if (!isEd(el)) return 'none';
    el.focus();
    if (el.isContentEditable) {
      var s = window.getSelection(); s.selectAllChildren(el); s.collapseToEnd();
      if (!document.execCommand('insertText', false, t)) return 'none';
    } else {
      var set = Object.getOwnPropertyDescriptor(HTMLTextAreaElement.prototype, 'value').set;
      set.call(el, (el.value || '') + t);
      el.dispatchEvent(new Event('input', { bubbles: true }));
    }
    setTimeout(function () {
      var b = [].slice.call(document.querySelectorAll('button')).filter(function (x) {
        var l = (x.getAttribute('aria-label') || '') + ' ' + (x.getAttribute('data-testid') || '');
        return /send|enviar|submit/i.test(l) && !x.disabled && x.offsetParent !== null;
      });
      if (b.length) { b[b.length - 1].click(); return; }
      ['keydown', 'keypress', 'keyup'].forEach(function (n) {
        el.dispatchEvent(new KeyboardEvent(n, { key: 'Enter', code: 'Enter', keyCode: 13, which: 13, bubbles: true, cancelable: true }));
      });
    }, 600);
    return 'ok';
  })(${JSON.stringify(text)})`;
  for (let i = 0; i < 25; i++) {
    let r = 'none';
    try {
      r = await wc.executeJavaScript(js);
    } catch (_) {
      r = 'none';
    }
    if (r === 'ok') {
      notifyAuto(`Mensaje enviado a «${acc.name}»`, `Terminó el descanso. Se envió: «${text.length > 90 ? text.slice(0, 90) + '…' : text}»`);
      return;
    }
    await sleep(2000);
  }
  clipboard.writeText(text);
  notifyAuto(`No se pudo enviar el mensaje a «${acc.name}»`, 'Copié el mensaje al portapapeles. Tocá para responder desde acá o pegalo en el chat.', acc.id);
}

function checkLimits() {
  updateTrayMenu();
  const now = Date.now();
  let changed = false;
  for (const a of [...state.accounts, ...otherAccounts()]) {
    if (a.limitUntil > 0 && a.limitUntil <= now) {
      a.limitUntil = 0;
      a.limitStart = 0;
      changed = true;
      const hadAuto = !!a.autoReply;
      if (a.autoReply) {
        const text = a.autoReply;
        a.autoReply = ''; // se limpia antes de enviar para que nunca se mande dos veces
        runAutoReply(a, text);
      }
      // Con mensaje automático el aviso llega cuando se envía (o falla); sin él, se ofrece responder desde la notificación.
      if (!hadAuto) notifyAuto(`«${a.name}» ya puede usarse`, 'Terminó el descanso. Tocá para responder con un mensaje guardado.', a.id);
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
  ipcMain.handle('state:get', () => {
    state.loading = loadingIds.has(state.selectedId);
    return state;
  });
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
      email: cleanEmail(data && data.email),
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
    if (patch.email !== undefined) acc.email = cleanEmail(patch.email);
    saveState();
    showCurrent();
    broadcast();
  });

  ipcMain.handle('account:select', (_e, id) => selectAccount(id));

  // Alterna entre vista oscura y clara del servicio activo (interfaz y páginas).
  ipcMain.handle('theme:toggle', () => {
    state.themes[state.service] = state.themes[state.service] === 'dark' ? 'light' : 'dark';
    applyServiceTheme();
    saveState();
    broadcast();
  });

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
    if (!reloadView(id)) selectAccount(id);
  });

  ipcMain.handle('account:limit', (_e, id, until, auto) => {
    const acc = find(id);
    if (!acc) return;
    const n = Number(until);
    acc.limitUntil = Number.isFinite(n) && n > Date.now() ? n : 0;
    acc.limitStart = acc.limitUntil > 0 ? Date.now() : 0;
    // Respuesta automática: un mensaje que se escribe y se envía solo cuando termina el descanso.
    acc.autoReply = acc.limitUntil > 0 ? cleanText(auto, 4000) : '';
    saveState();
    broadcast();
  });

  // Respuesta rápida: escribe y envía un mensaje ahora en el chat de la cuenta (en segundo plano).
  ipcMain.handle('account:reply', (_e, id, text) => {
    const acc = find(id);
    const t = cleanText(text, 4000);
    if (!acc || !t) return { ok: false };
    runAutoReply(acc, t);
    return { ok: true };
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
    applyBrowserVisibility();
  });

  // Con el cajón abierto la página se reemplaza por una captura (las vistas se dibujan sobre el HTML y
  // no se pueden mezclar con él), así el cajón se ve encima con transparencia y desenfoque.
  ipcMain.handle('ui:drawer', async (_e, open) => {
    if (!open) {
      drawerOpen = false;
      showCurrent();
      return null;
    }
    let snap = null;
    const view = views.get(state.selectedId);
    if (view && !modalOpen) {
      try {
        const img = await view.webContents.capturePage();
        if (!img.isEmpty()) {
          const b = view.getBounds();
          snap = { url: 'data:image/jpeg;base64,' + img.toJPEG(82).toString('base64'), x: b.x, y: b.y, width: b.width, height: b.height };
        }
      } catch (_) {
        snap = null;
      }
    }
    drawerOpen = true;
    showCurrent();
    return snap;
  });

  // ---- Navegador interno ----
  ipcMain.handle('browser:open', () => {
    const v = ensureBrowser();
    if (!browserLoaded) {
      browserLoaded = true;
      v.webContents.loadURL(state.browser.lastUrl || BROWSER_HOME);
    }
    return browserState();
  });
  ipcMain.handle('browser:show', (_e, rect) => {
    if (!browserView || !rect) return;
    const n = (x) => Math.max(0, Math.round(Number(x) || 0));
    browserRect = { x: n(rect.x), y: n(rect.y), width: n(rect.width), height: n(rect.height) };
    browserShown = true;
    applyBrowserVisibility();
    if (!modalOpen) browserView.webContents.focus();
  });
  ipcMain.handle('browser:hide', () => {
    browserShown = false;
    applyBrowserVisibility();
  });
  ipcMain.handle('browser:go', (_e, text) => {
    const url = normalizeBrowserInput(text);
    if (!url || !browserView) return { ok: false, message: 'Solo se pueden abrir direcciones http y https' };
    browserView.webContents.loadURL(url);
    browserView.webContents.focus();
    return { ok: true };
  });
  ipcMain.handle('browser:nav', (_e, action) => {
    if (!browserView) return;
    const wc = browserView.webContents;
    const h = wc.navigationHistory;
    if (action === 'back') (h ? h.canGoBack() && h.goBack() : wc.canGoBack() && wc.goBack());
    else if (action === 'forward') (h ? h.canGoForward() && h.goForward() : wc.canGoForward() && wc.goForward());
    else if (action === 'reload') wc.reload();
    else if (action === 'stop') wc.stop();
  });
  ipcMain.handle('browser:dl-get', () => ({ mode: state.browser.dlMode, dir: state.browser.dlDir || app.getPath('downloads') }));
  ipcMain.handle('browser:dl-set', (_e, mode) => {
    if (['default', 'ask'].includes(mode)) {
      state.browser.dlMode = mode;
      saveState();
    }
    return { mode: state.browser.dlMode, dir: state.browser.dlDir || app.getPath('downloads') };
  });
  ipcMain.handle('browser:dl-pick', async () => {
    const r = await dialog.showOpenDialog(win, {
      title: 'Carpeta donde guarda el navegador',
      defaultPath: state.browser.dlDir || app.getPath('downloads'),
      properties: ['openDirectory', 'createDirectory'],
    });
    if (!r.canceled && r.filePaths[0]) {
      state.browser.dlDir = r.filePaths[0];
      state.browser.dlMode = 'folder';
      saveState();
    }
    return { mode: state.browser.dlMode, dir: state.browser.dlDir || app.getPath('downloads') };
  });
  ipcMain.handle('browser:external', () => {
    const u = browserView ? browserView.webContents.getURL() : '';
    if (/^https?:\/\//i.test(u)) shell.openExternal(u);
  });

  // ---- Explorador de archivos interno ----
  const okPath = (p) => typeof p === 'string' && p.length > 0 && p.length < 4096 && !p.includes('\u0000') && path.isAbsolute(p);
  const fsReply = async (fn) => {
    try {
      return { ok: true, ...(await fn()) };
    } catch (e) {
      return { ok: false, message: e && e.userMessage ? e.userMessage : e && e.code === 'EACCES' ? 'Sin permiso para esa operación' : e && e.code === 'EPERM' ? 'Sin permiso (o el archivo está en uso)' : e && e.code === 'EBUSY' ? 'El archivo está en uso' : (e && e.message) || 'No se pudo' };
    }
  };

  ipcMain.handle('fs:roots', () => {
    const items = [];
    const add = (label, name) => {
      try {
        items.push({ label, path: app.getPath(name) });
      } catch (_) {
        /* no existe en este sistema */
      }
    };
    add('Descargas', 'downloads');
    add('Escritorio', 'desktop');
    add('Documentos', 'documents');
    add('Imágenes', 'pictures');
    add('Inicio', 'home');
    for (const d of fileops.drives()) items.push({ label: d, path: d });
    return items;
  });

  ipcMain.handle('fs:list', (_e, dir) =>
    fsReply(async () => {
      if (!okPath(dir)) throw new Error('Ruta no válida');
      const resolved = path.resolve(dir);
      const entries = await fileops.list(resolved);
      const parent = path.dirname(resolved);
      return { dir: resolved, parent: parent === resolved ? null : parent, entries };
    })
  );

  const manyPaths = (paths) => Array.isArray(paths) && paths.length > 0 && paths.length < 5000 && paths.every(okPath);

  ipcMain.handle('fs:copy', (_e, paths, dest) =>
    fsReply(async () => {
      if (!manyPaths(paths) || !okPath(dest)) throw new Error('Ruta no válida');
      for (const p of paths) await fileops.copyOne(p, dest);
      return { message: `Copiados: ${paths.length}` };
    })
  );

  ipcMain.handle('fs:move', (_e, paths, dest) =>
    fsReply(async () => {
      if (!manyPaths(paths) || !okPath(dest)) throw new Error('Ruta no válida');
      for (const p of paths) await fileops.moveOne(p, dest);
      return { message: `Movidos: ${paths.length}` };
    })
  );

  // Borrar manda los elementos a la Papelera de reciclaje (se pueden recuperar).
  ipcMain.handle('fs:delete', (_e, paths) =>
    fsReply(async () => {
      if (!manyPaths(paths)) throw new Error('Ruta no válida');
      let n = 0;
      for (const p of paths) {
        await shell.trashItem(p);
        n++;
      }
      return { message: `Enviados a la Papelera: ${n}` };
    })
  );

  ipcMain.handle('fs:rename', (_e, p, name) =>
    fsReply(async () => {
      if (!okPath(p)) throw new Error('Ruta no válida');
      const target = await fileops.renameOne(p, name);
      return { message: `Ahora se llama «${path.basename(target)}»` };
    })
  );

  ipcMain.handle('fs:mkdir', (_e, dir, name) =>
    fsReply(async () => {
      if (!okPath(dir)) throw new Error('Ruta no válida');
      const target = await fileops.mkdirOne(dir, name);
      return { message: `Carpeta «${path.basename(target)}» creada` };
    })
  );

  ipcMain.handle('fs:unzip', (_e, zip, dest) =>
    fsReply(async () => {
      if (!okPath(zip) || !okPath(dest)) throw new Error('Ruta no válida');
      if (!/\.zip$/i.test(zip)) throw new Error('No es un archivo .zip');
      await fileops.unzip(zip, dest);
      return { message: `Extraído en «${path.basename(dest)}»` };
    })
  );

  ipcMain.handle('fs:open', async (_e, p) => {
    if (!okPath(p)) return { ok: false, message: 'Ruta no válida' };
    const err = await shell.openPath(p);
    return err ? { ok: false, message: err } : { ok: true };
  });

  ipcMain.handle('fs:reveal', (_e, p) => {
    if (okPath(p)) shell.showItemInFolder(p);
    return { ok: true };
  });

  // Mensajes guardados: la lista la edita el renderer; escribir en el chat lo hace el proceso principal.
  ipcMain.handle('snippets:set', (_e, list) => {
    state.snippets = cleanSnippets(list);
    saveState();
    broadcast();
  });

  // Escribe el texto en la caja de chat de la cuenta activa (no lo envía). Si no encuentra dónde, lo copia.
  ipcMain.handle('snippet:insert', async (_e, text) => {
    const view = views.get(state.selectedId);
    if (!view || typeof text !== 'string' || !text) return false;
    const wc = view.webContents;
    await new Promise((r) => setTimeout(r, 120));
    showCurrent();
    wc.focus();
    let ok = false;
    try {
      ok = await wc.executeJavaScript(`(function () {
        function editable(e) { return e && (e.isContentEditable || e.tagName === 'TEXTAREA' || (e.tagName === 'INPUT' && /^(text|search|)$/.test(e.type))); }
        var el = document.activeElement;
        if (!editable(el)) {
          var c = [].slice.call(document.querySelectorAll('div[contenteditable="true"],textarea')).filter(function (x) { return x.offsetParent !== null; });
          el = c.length ? c[c.length - 1] : null;
          if (!el) return false;
          el.focus();
          if (el.isContentEditable) { var s = window.getSelection(); s.selectAllChildren(el); s.collapseToEnd(); }
        }
        return true;
      })()`);
    } catch (_) {
      ok = false;
    }
    if (ok) {
      wc.insertText(text);
      return true;
    }
    clipboard.writeText(text);
    return false;
  });

  ipcMain.handle('settings:set', (_e, patch) => {
    if (!patch || typeof patch !== 'object') return;
    const st = state.settings;
    if (['left', 'right', 'bottom'].includes(patch.railPos)) st.railPos = patch.railPos;
    if (typeof patch.launchAtLogin === 'boolean') st.launchAtLogin = patch.launchAtLogin;
    if (typeof patch.closeToTray === 'boolean') st.closeToTray = patch.closeToTray;
    applySettings();
    saveState();
    layout();
    broadcast();
  });

  // Exportar / importar las cuentas (sin sesiones) como .json, para llevarlas a Android u otra PC.
  // El archivo lleva nombre, propósito, color y marcas de límite; NO cookies ni contraseñas.
  ipcMain.handle('data:export', async () => {
    if (!win) return { ok: false };
    const r = await dialog.showSaveDialog(win, {
      title: 'Exportar cuentas',
      defaultPath: path.join(app.getPath('documents'), 'AlternAIs-cuentas.json'),
      filters: [{ name: 'Archivo JSON', extensions: ['json'] }],
    });
    if (r.canceled || !r.filePath) return { ok: false, canceled: true };
    syncBucket();
    const services = {};
    let count = 0;
    for (const key of Object.keys(SERVICES)) {
      services[key] = {
        accounts: buckets[key].accounts.map((a) => {
          count += 1;
          return {
            name: a.name,
            purpose: a.purpose,
            email: a.email || '',
            color: a.color,
            limitUntil: a.limitUntil,
            limitStart: a.limitStart,
            lastUrl: a.lastUrl || '',
            selected: a.id === buckets[key].selectedId,
          };
        }),
      };
    }
    try {
      fs.writeFileSync(r.filePath, JSON.stringify({ app: 'AlternAIs', version: 1, exportedAt: Date.now(), services }, null, 2));
    } catch (_) {
      return { ok: false };
    }
    return { ok: true, count };
  });

  ipcMain.handle('data:import', async () => {
    if (!win) return { ok: false };
    const r = await dialog.showOpenDialog(win, {
      title: 'Importar cuentas',
      properties: ['openFile'],
      filters: [{ name: 'Archivo JSON', extensions: ['json'] }],
    });
    if (r.canceled || !r.filePaths.length) return { ok: false, canceled: true };
    let services;
    try {
      const raw = fs.readFileSync(r.filePaths[0], 'utf8');
      if (raw.length > 1000000) return { ok: false };
      services = JSON.parse(raw).services;
      if (!services || typeof services !== 'object') return { ok: false };
    } catch (_) {
      return { ok: false };
    }
    syncBucket();
    const now = Date.now();
    let added = 0;
    let skipped = 0;
    for (const key of Object.keys(SERVICES)) {
      const list = services[key] && Array.isArray(services[key].accounts) ? services[key].accounts.slice(0, 50) : [];
      const bucket = buckets[key];
      for (const a of list) {
        const name = cleanText(a && a.name, 24);
        if (!name || bucket.accounts.some((x) => x.name.toLowerCase() === name.toLowerCase())) {
          skipped += 1;
          continue;
        }
        const active = Number(a.limitUntil) > now;
        const imported = {
          id: crypto.randomBytes(6).toString('hex'),
          name,
          purpose: cleanText(a.purpose, 60),
          email: cleanEmail(a.email),
          color: cleanColor(a.color),
          limitUntil: active ? Number(a.limitUntil) : 0,
          limitStart: active && Number(a.limitStart) > 0 ? Number(a.limitStart) : 0,
          lastUrl: cleanUrl(a.lastUrl, key) || '',
        };
        // Si la mitad no tenía cuenta activa, queda activa la marcada en el archivo (o la primera).
        const hadNone = !bucket.accounts.length;
        bucket.accounts.push(imported);
        if (!bucket.selectedId || (hadNone && a.selected === true)) bucket.selectedId = imported.id;
        added += 1;
      }
    }
    state.accounts = buckets[state.service].accounts;
    state.selectedId = buckets[state.service].selectedId;
    if (state.selectedId) selectAccount(state.selectedId);
    else {
      saveState();
      broadcast();
    }
    return { ok: true, added, skipped };
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
          if (!reloadView(id)) selectAccount(id);
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

// ---------------------------------------------------------------- Bandeja del sistema e inicio

let tray = null;
let quitting = false;

function showWindow() {
  if (!win || win.isDestroyed()) {
    createWindow();
    return;
  }
  if (win.isMinimized()) win.restore();
  win.show();
  win.focus();
}

function ensureTray() {
  if (tray) return;
  try {
    const img = nativeImage.createFromPath(path.join(__dirname, 'renderer', 'tray.png')).resize({ width: 32, height: 32 });
    tray = new Tray(img);
    tray.setToolTip('AlternAIs');
    updateTrayMenu();
    tray.on('click', showWindow);
  } catch (e) {
    console.error('No se pudo crear el ícono de la bandeja', e);
    tray = null;
  }
}

// "2 h 10 min", "3 d 4 h", "12 min" (mínimo 1 min).
function formatRemaining(ms) {
  const totalMin = Math.max(1, Math.floor(ms / 60000));
  const days = Math.floor(totalMin / 1440);
  const hours = Math.floor((totalMin % 1440) / 60);
  const mins = totalMin % 60;
  if (days > 0) return `${days} d ${hours} h`;
  if (hours > 0) return `${hours} h ${mins} min`;
  return `${mins} min`;
}

// Menú de la bandeja: cada cuenta con su cuenta regresiva (o «Disponible»). Se rearma cada 30 s.
function updateTrayMenu() {
  if (!tray) return;
  try {
    syncBucket();
    const now = Date.now();
    const items = [{ label: 'Abrir AlternAIs', click: showWindow }, { type: 'separator' }];
    let free = 0;
    let total = 0;
    for (const key of Object.keys(SERVICES)) {
      const list = buckets[key].accounts;
      if (!list.length) continue;
      items.push({ label: SERVICES[key].label, enabled: false });
      for (const a of list) {
        total++;
        const resting = a.limitUntil > now;
        if (!resting) free++;
        items.push({
          label: `${resting ? '⏳' : '✅'}  ${a.name} — ${resting ? formatRemaining(a.limitUntil - now) : 'Disponible'}`,
          click: () => {
            showWindow();
            if (key === state.service) selectAccount(a.id);
          },
        });
      }
      items.push({ type: 'separator' });
    }
    items.push({ label: 'Salir', click: () => { quitting = true; app.quit(); } });
    tray.setContextMenu(Menu.buildFromTemplate(items));
    tray.setToolTip(total ? `AlternAIs — ${free} de ${total} disponibles` : 'AlternAIs');
  } catch (e) {
    console.error('No se pudo armar el menú de la bandeja', e);
  }
}

function removeTray() {
  if (tray) {
    tray.destroy();
    tray = null;
  }
}

// Aplica las opciones que dependen del sistema: bandeja e inicio con Windows.
function applySettings() {
  const st = state.settings;
  if (st.closeToTray) ensureTray();
  else removeTray();
  if (app.isPackaged) {
    try {
      app.setLoginItemSettings({ openAtLogin: st.launchAtLogin, args: ['--hidden'] });
    } catch (e) {
      console.error('No se pudo cambiar el inicio con Windows', e);
    }
  }
}

// Cada servicio tiene su tema (Claude oscuro y Replit claro por defecto; el logo del cajón lo alterna),
// sin importar el tema de Windows: nativeTheme lo aplica a la interfaz y a las páginas (prefers-color-scheme).
// Fondo de la ventana: el de la web del servicio (Claude #FFFFFF / #191919, Replit crema / #1E1E1F).
function windowBg() {
  const light = state.themes[state.service] === 'light';
  if (state.service === 'replit') return light ? '#fbf8f4' : '#1e1e1f';
  return light ? '#ffffff' : '#191919';
}

function applyServiceTheme() {
  const light = state.themes[state.service] === 'light';
  nativeTheme.themeSource = light ? 'light' : 'dark';
  if (win && !win.isDestroyed()) win.setBackgroundColor(windowBg());
}

function createWindow() {
  const dark = state.themes[state.service] === 'dark';
  win = new BrowserWindow({
    width: 1280,
    height: 820,
    minWidth: 900,
    minHeight: 600,
    title: 'AlternAIs',
    backgroundColor: windowBg(),
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
  // Si Windows la abrió al iniciar sesión y se cierra a la bandeja, arranca escondida.
  const startHidden = process.argv.includes('--hidden') && state.settings.closeToTray;
  win.once('ready-to-show', () => {
    if (!startHidden) win.show();
  });
  win.on('close', (e) => {
    if (!quitting && state.settings.closeToTray) {
      e.preventDefault();
      ensureTray();
      win.hide();
    }
  });
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
    // La animación del logo ocupa la ventana ~1,7 s: la cuenta (una vista web encima) entra después.
    setTimeout(() => {
      if (!win || win.isDestroyed()) return;
      if (state.selectedId) selectAccount(state.selectedId);
      else broadcast();
    }, 1720);
  });
}

if (!app.requestSingleInstanceLock()) {
  app.quit();
} else {
  app.on('second-instance', () => showWindow());

  app.whenReady().then(() => {
    Menu.setApplicationMenu(null);
    loadState();
    applyServiceTheme();
    app.on('before-quit', () => {
      quitting = true;
      for (const [id, view] of views) {
        if (!view.webContents.isDestroyed()) rememberPage(id, view.webContents.getURL());
      }
    });
    registerIpc();
    applySettings();
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
