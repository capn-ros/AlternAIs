'use strict';

const $ = (sel) => document.querySelector(sel);
const PALETTE = ['#E07A5F', '#F2A541', '#81B29A', '#4FB0C6', '#6C8EAD', '#9A7BD1', '#D96C9F', '#8D99AE'];
const HOUR = 3600 * 1000;

let state = { accounts: [], selectedId: null, service: 'claude' };
const SERVICE_LABELS = { claude: 'Claude', replit: 'Replit' };
let editingId = null;
let pickedColor = PALETTE[0];
let limitTargetId = null;
let confirmAction = null;

// ---------------------------------------------------------------- Formato

function fmtRemaining(ms) {
  const totalMin = Math.max(1, Math.floor(ms / 60000));
  const d = Math.floor(totalMin / 1440);
  const h = Math.floor((totalMin % 1440) / 60);
  const m = totalMin % 60;
  if (d > 0) return `${d} d ${h} h`;
  if (h > 0) return `${h} h ${m} min`;
  return `${m} min`;
}

function fmtUntil(ts) {
  return new Intl.DateTimeFormat('es-AR', { weekday: 'short', hour: '2-digit', minute: '2-digit' }).format(ts);
}

function statusOf(acc) {
  const now = Date.now();
  if (acc.limitUntil > now) {
    return {
      limited: true,
      text: `En descanso hasta ${fmtUntil(acc.limitUntil)} · faltan ${fmtRemaining(acc.limitUntil - now)}`,
    };
  }
  return { limited: false, text: 'Disponible' };
}

// Qué fracción del descanso falta (1 = recién marcado, 0 = terminó). Igual que en Android.
function fractionOf(acc, now = Date.now()) {
  if (!(acc.limitUntil > now)) return 0;
  const remaining = acc.limitUntil - now;
  let total;
  if (acc.limitStart > 0 && acc.limitStart < acc.limitUntil) total = acc.limitUntil - acc.limitStart;
  else total = [5 * HOUR, 24 * HOUR, 7 * 24 * HOUR, 30 * 24 * HOUR].find((t) => t >= remaining) || remaining;
  return Math.min(1, Math.max(0, remaining / total));
}

const SVG_NS = 'http://www.w3.org/2000/svg';
const RING_R = 25;
const RING_LEN = 2 * Math.PI * RING_R;

function makeRing(fraction) {
  const svg = document.createElementNS(SVG_NS, 'svg');
  svg.setAttribute('viewBox', '0 0 56 56');
  svg.setAttribute('class', 'ring');
  svg.setAttribute('aria-hidden', 'true');
  const mk = (cls) => {
    const c = document.createElementNS(SVG_NS, 'circle');
    c.setAttribute('class', cls);
    c.setAttribute('cx', '28');
    c.setAttribute('cy', '28');
    c.setAttribute('r', String(RING_R));
    c.setAttribute('transform', 'rotate(-90 28 28)');
    return c;
  };
  svg.append(mk('track'));
  const arc = mk('arc');
  arc.setAttribute('stroke-dasharray', `${RING_LEN * Math.max(0.02, fraction)} ${RING_LEN}`);
  svg.append(arc);
  return svg;
}

// Color del texto sobre el relleno de la barra (oscuro si el color es claro).
function textOn(hex) {
  const n = parseInt(hex.slice(1), 16);
  const lin = (v) => {
    v /= 255;
    return v <= 0.03928 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
  };
  const L = 0.2126 * lin((n >> 16) & 255) + 0.7152 * lin((n >> 8) & 255) + 0.0722 * lin(n & 255);
  return L > 0.55 ? '#1f1f1f' : '#ffffff';
}

const currentAccount = () => state.accounts.find((a) => a.id === state.selectedId);

// ---------------------------------------------------------------- Render

function render() {
  const list = $('#accounts');
  list.replaceChildren();
  state.accounts.forEach((acc, i) => {
    const st = statusOf(acc);
    const btn = document.createElement('button');
    btn.className = 'avatar' + (acc.id === state.selectedId ? ' selected' : '');
    btn.style.setProperty('--c', acc.color);
    const frac = fractionOf(acc);
    if (frac > 0) btn.append(makeRing(frac));
    const disk = document.createElement('span');
    disk.className = 'disk';
    disk.textContent = (acc.name.trim()[0] || '?').toUpperCase();
    btn.append(disk);
    const lines = [acc.name];
    if (acc.purpose) lines.push(acc.purpose);
    if (acc.email) lines.push(acc.email);
    lines.push(st.text);
    if (i < 9) lines.push(`Ctrl+${i + 1}`);
    btn.title = lines.join('\n');
    btn.addEventListener('click', () => window.api.select(acc.id));
    btn.addEventListener('contextmenu', (e) => {
      e.preventDefault();
      window.api.menu(acc.id);
    });
    list.append(btn);
  });
  renderTopbar();
  renderServiceTabs();
  renderLoading();
  if (snippetsOpen) renderSnippets();
  renderSettings();
}

// Posición de la barra, inicio con Windows y comportamiento al cerrar.
function renderSettings() {
  const st = state.settings || { railPos: 'left', launchAtLogin: false, closeToTray: false };
  $('#app').dataset.rail = st.railPos;
  for (const b of document.querySelectorAll('#seg-rail button')) {
    b.classList.toggle('on', b.dataset.rail === st.railPos);
  }
  for (const b of document.querySelectorAll('#seg-close button')) {
    b.classList.toggle('on', (b.dataset.close === 'tray') === st.closeToTray);
  }
  $('#d-login').setAttribute('aria-checked', String(st.launchAtLogin));
}

let loadingAnim = null;
function renderLoading() {
  const el = $('#loading');
  const want = !!(state.loading && state.selectedId);
  if (want) {
    if (!loadingAnim && window.AlternLogo) loadingAnim = window.AlternLogo.mount(el, { loop: true });
    el.classList.add('on');
  } else {
    el.classList.remove('on');
    if (loadingAnim) {
      const a = loadingAnim;
      loadingAnim = null;
      setTimeout(() => { if (!loadingAnim) a.stop(); }, 260);
    }
  }
}

function renderServiceTabs() {
  document.documentElement.dataset.service = state.service;
  document.documentElement.dataset.theme = (state.themes && state.themes[state.service]) || (state.service === 'replit' ? 'light' : 'dark');
  for (const key of Object.keys(SERVICE_LABELS)) {
    const tab = $('#svc-' + key);
    const on = state.service === key;
    tab.classList.toggle('on', on);
    tab.setAttribute('aria-selected', String(on));
  }
}

function renderTopbar() {
  const acc = currentAccount();
  const has = !!acc;
  $('#who').hidden = !has;
  $('#t-actions').hidden = !has;
  $('#t-bar').hidden = !has;
  $('#empty').hidden = state.accounts.length > 0;
  const root = document.documentElement.style;
  if (!has) {
    root.removeProperty('--accent');
    return;
  }
  const now = Date.now();
  // Borde, íconos y barra toman el color de la cuenta activa (en Claude y en Replit).
  const accent = acc.color;
  root.setProperty('--accent', accent);
  $('#t-dot').style.setProperty('--c', accent);
  $('#t-name').textContent = acc.name;
  $('#t-purpose').textContent = acc.purpose ? `· ${acc.purpose}` : '';

  const limited = acc.limitUntil > now;
  const label = limited
    ? `faltan ${fmtRemaining(acc.limitUntil - now)} · hasta ${fmtUntil(acc.limitUntil)}`
    : 'Disponible';
  const bar = $('#t-bar');
  bar.style.setProperty('--p', limited ? String(fractionOf(acc, now)) : '1');
  bar.style.setProperty('--fg', textOn(accent));
  $('#t-label').textContent = label;
  $('#t-fill-label').textContent = label;
}

// ---------------------------------------------------------------- Diálogos

function openDialog(dlg) {
  setPanel(null);
  window.api.modal(true);
  dlg.showModal();
}
for (const dlg of document.querySelectorAll('dialog')) {
  dlg.addEventListener('close', () => window.api.modal(false));
}

function renderSwatches() {
  const box = $('#swatches');
  box.replaceChildren();
  for (const c of PALETTE) {
    const b = document.createElement('button');
    b.type = 'button';
    b.className = 'swatch' + (c === pickedColor ? ' on' : '');
    b.style.setProperty('--c', c);
    b.setAttribute('aria-label', `Color ${c}`);
    b.addEventListener('click', () => {
      pickedColor = c;
      renderSwatches();
    });
    box.append(b);
  }
}

function openAccountDialog(acc) {
  editingId = acc ? acc.id : null;
  $('#acc-title').textContent = acc ? 'Editar cuenta' : 'Nueva cuenta';
  $('#acc-name').value = acc ? acc.name : '';
  $('#acc-purpose').value = acc ? acc.purpose : '';
  $('#acc-email').value = acc ? acc.email || '' : '';
  pickedColor = acc ? acc.color : PALETTE[state.accounts.length % PALETTE.length];
  renderSwatches();
  openDialog($('#dlg-account'));
  $('#acc-name').focus();
}

$('#form-account').addEventListener('submit', async (e) => {
  e.preventDefault();
  const name = $('#acc-name').value.trim();
  if (!name) {
    $('#acc-name').focus();
    return;
  }
  const data = { name, purpose: $('#acc-purpose').value.trim(), email: $('#acc-email').value.trim(), color: pickedColor };
  $('#dlg-account').close();
  if (editingId) await window.api.update(editingId, data);
  else await window.api.add(data);
});
$('#acc-cancel').addEventListener('click', () => $('#dlg-account').close());

function openLimitDialog(id) {
  const acc = state.accounts.find((a) => a.id === id);
  if (!acc) return;
  limitTargetId = id;
  $('#limit-sub').textContent = `Cuenta «${acc.name}». Mientras tanto se muestra como "en descanso" y te aviso cuando pase.`;
  $('#lim-clear').hidden = !(acc.limitUntil > Date.now());
  // Opciones según la cuota de cada IA: Replit tiene Modo gratis (5 h y tope semanal), créditos
  // diarios (Starter) y créditos mensuales que se renuevan al empezar el ciclo de facturación.
  const replit = state.service === 'replit';
  $('#lim-5h').textContent = replit ? 'En 5 horas (Modo gratis)' : 'En 5 horas';
  $('#lim-24h').textContent = replit ? 'En 24 horas (créditos diarios)' : 'En 24 horas';
  $('#lim-7d').textContent = replit ? 'En 7 días (tope semanal)' : 'En 7 días';
  $('#lim-renew-row').hidden = !replit;
  $('#lim-date').value = '';
  const now = new Date();
  $('#lim-time').value = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`;
  openDialog($('#dlg-limit'));
}

async function applyLimit(until) {
  const id = limitTargetId;
  $('#dlg-limit').close();
  if (id) await window.api.setLimit(id, until);
}
$('#lim-5h').addEventListener('click', () => applyLimit(Date.now() + 5 * HOUR));
$('#lim-24h').addEventListener('click', () => applyLimit(Date.now() + 24 * HOUR));
$('#lim-7d').addEventListener('click', () => applyLimit(Date.now() + 7 * 24 * HOUR));
$('#lim-exact').addEventListener('click', () => {
  const v = $('#lim-time').value;
  if (!v) return;
  const [h, m] = v.split(':').map(Number);
  const t = new Date();
  t.setHours(h, m, 0, 0);
  if (t.getTime() <= Date.now()) t.setDate(t.getDate() + 1);
  applyLimit(t.getTime());
});
$('#lim-renew').addEventListener('click', () => {
  const v = $('#lim-date').value;
  if (!v) return;
  const [y, mo, d] = v.split('-').map(Number);
  const t = new Date(y, mo - 1, d, 0, 0, 0, 0);
  if (t.getTime() <= Date.now()) t.setDate(t.getDate() + 1);
  applyLimit(t.getTime());
});
$('#lim-clear').addEventListener('click', () => applyLimit(0));
$('#lim-cancel').addEventListener('click', () => $('#dlg-limit').close());

// ---------------------------------------------------------------- Mensajes guardados (cajón)

let snipEditing = -1;
function renderSnippets() {
  const box = $('#snip-list');
  box.replaceChildren();
  const list = state.snippets || [];
  if (!list.length) {
    const p = document.createElement('div');
    p.className = 'snip-empty';
    p.textContent = 'Todavía no hay mensajes guardados. Escribí uno abajo y tocá «Guardar».';
    box.append(p);
  }
  list.forEach((text, i) => {
    const row = document.createElement('div');
    row.className = 'snip';
    const pick = document.createElement('button');
    pick.type = 'button';
    pick.className = 'pick';
    pick.textContent = text;
    pick.title = 'Escribir en el chat';
    pick.addEventListener('click', () => insertSnippet(text));
    const mk = (label, title, fn) => {
      const b = document.createElement('button');
      b.type = 'button';
      b.className = 'mini';
      b.textContent = label;
      b.title = title;
      b.setAttribute('aria-label', title);
      b.addEventListener('click', fn);
      return b;
    };
    row.append(
      pick,
      mk('✎', 'Editar', () => {
        snipEditing = i;
        $('#snip-text').value = text;
        $('#snip-save').textContent = 'Guardar cambios';
        $('#snip-text').focus();
      }),
      mk('↑', 'Subir', () => {
        if (i === 0) return;
        const l = list.slice();
        l.splice(i - 1, 0, l.splice(i, 1)[0]);
        window.api.setSnippets(l);
      }),
      mk('🗑', 'Borrar', () => window.api.setSnippets(list.filter((_, j) => j !== i)))
    );
    box.append(row);
  });
}

function openSnippets() {
  if (!state.selectedId) return;
  setSnippets(!snippetsOpen);
}

function snippetsOpened() {
  snipEditing = -1;
  $('#snip-text').value = '';
  $('#snip-save').textContent = 'Guardar';
  renderSnippets();
  $('#snippets').focus();
}

async function insertSnippet(text) {
  if (!text.trim()) return;
  setSnippets(false);
  // Esperar a que el cajón cierre y la vista de la cuenta vuelva a estar visible antes de escribir.
  await new Promise((r) => setTimeout(r, 300));
  await window.api.insertSnippet(text);
}
$('#btn-snippets').addEventListener('click', openSnippets);
$('#snip-insert').addEventListener('click', () => insertSnippet($('#snip-text').value));
$('#snip-save').addEventListener('click', () => {
  const t = $('#snip-text').value;
  if (!t.trim()) {
    $('#snip-text').focus();
    return;
  }
  const list = (state.snippets || []).slice();
  if (snipEditing >= 0 && snipEditing < list.length) list[snipEditing] = t;
  else list.push(t);
  snipEditing = -1;
  $('#snip-text').value = '';
  $('#snip-save').textContent = 'Guardar';
  window.api.setSnippets(list);
});
$('#snip-x').addEventListener('click', () => setSnippets(false));

function openConfirm(title, text, action) {
  $('#cf-title').textContent = title;
  $('#cf-text').textContent = text;
  confirmAction = action;
  openDialog($('#dlg-confirm'));
}
$('#cf-no').addEventListener('click', () => $('#dlg-confirm').close());
$('#cf-yes').addEventListener('click', async () => {
  const fn = confirmAction;
  confirmAction = null;
  $('#dlg-confirm').close();
  if (fn) await fn();
});

// ---------------------------------------------------------------- Cajón de ajustes

let drawerOpen = false;
let drawerToken = 0;

function showSnap(snap) {
  const box = $('#snap');
  const img = $('#snap-img');
  // Sin captura (todavía no hay página), se cubre igual el área de la página.
  const r = snap || $('#stage').getBoundingClientRect();
  box.style.left = `${r.x !== undefined ? r.x : r.left}px`;
  box.style.top = `${r.y !== undefined ? r.y : r.top}px`;
  box.style.width = `${r.width}px`;
  box.style.height = `${r.height}px`;
  if (snap) {
    img.src = snap.url;
    img.hidden = false;
  } else {
    img.hidden = true;
  }
  box.hidden = false;
}

function hideSnap() {
  $('#snap').hidden = true;
  $('#snap-img').removeAttribute('src');
}
$('#snap').addEventListener('click', () => setPanel(null));

// Un solo panel abierto a la vez (ajustes o archivos): ambos comparten la captura de la página.
let activePanel = null; // 'settings' | 'files' | null
let filesOpen = false;
const PANEL_IDS = { settings: '#drawer', files: '#files', snippets: '#snippets', browser: '#browser' };
let snippetsOpen = false;
let browserOpen = false;

function setPanel(name, fromMain = false) {
  if (name === activePanel) return;
  const prev = activePanel;
  activePanel = name;
  drawerOpen = name === 'settings';
  filesOpen = name === 'files';
  snippetsOpen = name === 'snippets';
  browserOpen = name === 'browser';
  const token = ++drawerToken;
  if (prev === 'browser') window.api.browserHide(); // la vista web nativa se dibuja encima: se esconde ya
  if (prev) {
    const p = $(PANEL_IDS[prev]);
    p.classList.remove('open');
    p.setAttribute('aria-hidden', 'true');
  }
  $('#settings').classList.toggle('on', drawerOpen);
  $('#btn-files').classList.toggle('on', filesOpen);
  $('#btn-snippets').classList.toggle('on', snippetsOpen);
  $('#btn-browser').classList.toggle('on', browserOpen);
  if (name) {
    const d = $(PANEL_IDS[name]);
    d.setAttribute('aria-hidden', 'false');
    const reveal = () => {
      requestAnimationFrame(() => d.classList.add('open'));
      if (name === 'files') filesOpened();
      if (name === 'snippets') snippetsOpened();
      if (name === 'browser') browserOpened();
    };
    if (prev) {
      reveal(); // la captura de la página ya está puesta
    } else {
      // La página se congela en una captura (las vistas se dibujan sobre el HTML) y el cajón sale encima, con desenfoque.
      window.api.drawer(true).then((snap) => {
        if (token !== drawerToken) return;
        showSnap(snap);
        reveal();
      });
    }
  } else {
    setTimeout(() => {
      if (token !== drawerToken) return;
      if (!fromMain) window.api.drawer(false);
      hideSnap();
    }, 240);
  }
}

function setDrawer(open, fromMain = false) {
  if (open) setPanel('settings', fromMain);
  else if (activePanel === 'settings') setPanel(null, fromMain);
}

function setSnippets(open, fromMain = false) {
  if (open) setPanel('snippets', fromMain);
  else if (activePanel === 'snippets') setPanel(null, fromMain);
}

function setBrowser(open, fromMain = false) {
  if (open) setPanel('browser', fromMain);
  else if (activePanel === 'browser') setPanel(null, fromMain);
}

function setFiles(open, fromMain = false) {
  if (open) setPanel('files', fromMain);
  else if (activePanel === 'files') setPanel(null, fromMain);
}

$('#settings').addEventListener('click', () => setDrawer(!drawerOpen));
for (const key of Object.keys(SERVICE_LABELS)) {
  $('#svc-' + key).addEventListener('click', () => {
    setDrawer(false);
    if (state.service !== key) window.api.setService(key);
  });
}
for (const b of document.querySelectorAll('#seg-rail button')) {
  b.addEventListener('click', () => window.api.setSettings({ railPos: b.dataset.rail }));
}
for (const b of document.querySelectorAll('#seg-close button')) {
  b.addEventListener('click', () => window.api.setSettings({ closeToTray: b.dataset.close === 'tray' }));
}
$('#theme-logo').addEventListener('click', () => window.api.toggleTheme());
$('#d-login').addEventListener('click', () => {
  const on = $('#d-login').getAttribute('aria-checked') === 'true';
  window.api.setSettings({ launchAtLogin: !on });
});
$('#d-reload').addEventListener('click', () => {
  if (state.selectedId) window.api.reload(state.selectedId);
  setDrawer(false);
});
$('#d-downloads').addEventListener('click', () => {
  window.api.openDownloads();
  setDrawer(false);
});
$('#d-add').addEventListener('click', () => {
  setDrawer(false);
  openAccountDialog(null);
});
function showMessage(title, text) {
  $('#msg-title').textContent = title;
  $('#msg-text').textContent = text;
  openDialog($('#dlg-msg'));
}
$('#msg-close').addEventListener('click', () => $('#dlg-msg').close());
$('#d-export').addEventListener('click', async () => {
  setDrawer(false);
  const r = await window.api.exportAccounts();
  if (r.ok) {
    showMessage('Cuentas exportadas', `Se guardaron ${r.count} cuentas. El archivo no incluye las sesiones: al importarlo en otro dispositivo vas a tener que iniciar sesión en cada cuenta.`);
  } else if (!r.canceled) {
    showMessage('No se pudo exportar', 'Probá guardar el archivo en otra carpeta.');
  }
});
$('#d-import').addEventListener('click', async () => {
  setDrawer(false);
  const r = await window.api.importAccounts();
  if (r.ok) {
    const extra = r.skipped ? ` (${r.skipped} ya existían o no eran válidas)` : '';
    showMessage('Cuentas importadas', `Se agregaron ${r.added} cuentas${extra}. Iniciá sesión en cada una: las sesiones no se copian entre dispositivos.`);
  } else if (!r.canceled) {
    showMessage('El archivo no es válido', 'Elegí un archivo .json exportado desde AlternAIs.');
  }
});
$('#d-about').addEventListener('click', () => {
  setDrawer(false);
  openDialog($('#dlg-about'));
  window.api.getVersion().then((version) => {
    $('#about-version').textContent = `Versión ${version}`;
  });
});
$('#about-close').addEventListener('click', () => $('#dlg-about').close());
document.addEventListener('keydown', (e) => {
  if (e.key === 'Escape' && drawerOpen && !document.querySelector('dialog[open]')) setDrawer(false);
});

// ---------------------------------------------------------------- Marco de ventana y tutorial

$('#window-minimize').addEventListener('click', () => window.api.minimizeWindow());
$('#window-maximize').addEventListener('click', () => window.api.toggleMaximizeWindow());
$('#window-close').addEventListener('click', () => window.api.closeWindow());
$('#chrome').addEventListener('dblclick', (e) => {
  if (!e.target.closest('.window-controls')) window.api.toggleMaximizeWindow();
});
window.api.onWindowState(({ maximized }) => {
  const button = $('#window-maximize');
  button.title = maximized ? 'Restaurar' : 'Maximizar';
  button.setAttribute('aria-label', button.title);
});

const tourSteps = [
  {
    title: 'Bienvenido a AlternAIs',
    copy: 'Cada cuenta conserva su propia sesión. Cuando Claude te pida iniciar sesión, elegí tu correo electrónico y el código que te envía; «Continuar con Google» puede fallar en un navegador integrado.',
    target: () => $('#stage'),
  },
  {
    title: 'Agregá tus cuentas',
    copy: 'Usá este botón para crear una sesión independiente. El tutorial no agrega cuentas ni guarda datos de ejemplo.',
    target: () => $('#add'),
  },
  {
    title: 'Cambiá y administrá sesiones',
    copy: 'Las cuentas aparecen en esta barra. Elegí una con un clic; con clic derecho abrís sus opciones, como recargar, marcar un límite o borrar sus datos locales.',
    target: () => $('#accounts'),
  },
  {
    title: 'Ajustes',
    copy: 'Abrí el cajón de ajustes para recargar la página, revisar Descargas, agregar otra cuenta, mover la barra de cuentas, iniciar con Windows o elegir qué pasa al cerrar. Lo abrí para que veas dónde están esas funciones.',
    target: () => $('#drawer'),
    enter: () => setDrawer(true),
  },
  {
    title: 'Acerca de',
    copy: 'En este menú también está Acerca de AlternAIs, con la información de la app y su autoría. AlternAIs no es una aplicación oficial de Anthropic.',
    target: () => $('#d-about'),
    enter: () => setDrawer(true),
  },
];

let tourIndex = 0;
let tourActive = false;

function positionTour() {
  if (!tourActive) return;
  const step = tourSteps[tourIndex];
  const focus = $('#tour-focus');
  const target = step.target ? step.target() : null;
  const rect = target && target.getBoundingClientRect();
  const inView = rect && rect.width > 0 && rect.height > 0 &&
    rect.right > 0 && rect.bottom > 0 && rect.left < window.innerWidth && rect.top < window.innerHeight;
  if (inView) {
    const pad = 6;
    focus.hidden = false;
    focus.style.left = `${Math.max(4, rect.left - pad)}px`;
    focus.style.top = `${Math.max(4, rect.top - pad)}px`;
    focus.style.width = `${Math.min(window.innerWidth - 8, rect.width + pad * 2)}px`;
    focus.style.height = `${Math.min(window.innerHeight - 8, rect.height + pad * 2)}px`;
  } else {
    focus.hidden = true;
  }
  const card = document.querySelector('.tour-card');
  const cardHeight = card.getBoundingClientRect().height;
  let top = rect && inView ? rect.bottom + 18 : Math.max(36, window.innerHeight * 0.28);
  if (top + cardHeight > window.innerHeight - 16 && rect && inView) {
    top = Math.max(16, rect.top - cardHeight - 18);
  }
  card.style.top = `${Math.max(16, Math.min(top, window.innerHeight - cardHeight - 16))}px`;
}

function renderTourStep() {
  if (!tourActive) return;
  const step = tourSteps[tourIndex];
  if (step.enter) step.enter();
  $('#tour-progress').textContent = `Tutorial · ${tourIndex + 1} de ${tourSteps.length}`;
  $('#tour-title').textContent = step.title;
  $('#tour-copy').textContent = step.copy;
  $('#tour-next').textContent = tourIndex === tourSteps.length - 1 ? 'Terminar' : 'Siguiente';
  requestAnimationFrame(() => setTimeout(positionTour, 290));
}

function finishTour() {
  if (!tourActive) return;
  tourActive = false;
  $('#tour').hidden = true;
  const wasDrawerOpen = drawerOpen;
  setDrawer(false);
  window.api.completeTutorial();
  if (wasDrawerOpen) setTimeout(() => window.api.modal(false), 280);
  else window.api.modal(false);
}

function startTour() {
  if (tourActive || state.tutorialShown) return;
  if (state.accounts.length) {
    state.tutorialShown = true;
    window.api.completeTutorial();
    return;
  }
  tourActive = true;
  state.tutorialShown = true;
  window.api.completeTutorial();
  $('#tour').hidden = false;
  window.api.modal(true).then(renderTourStep);
}

$('#tour-next').addEventListener('click', () => {
  if (tourIndex >= tourSteps.length - 1) {
    finishTour();
    return;
  }
  tourIndex += 1;
  renderTourStep();
});
$('#tour-skip').addEventListener('click', finishTour);
document.addEventListener('keydown', (e) => {
  if (tourActive && e.key === 'Escape') finishTour();
});
window.addEventListener('resize', positionTour);

// ---------------------------------------------------------------- Eventos

$('#add').addEventListener('click', () => openAccountDialog(null));
$('#add-empty').addEventListener('click', () => openAccountDialog(null));
$('#btn-reload').addEventListener('click', () => state.selectedId && window.api.reload(state.selectedId));
$('#btn-limit').addEventListener('click', () => state.selectedId && openLimitDialog(state.selectedId));

window.api.onState((s) => {
  state = s;
  render();
});

window.api.onAction((a) => {
  if (a.type === 'drawer-closed') {
    setPanel(null, true);
    return;
  }
  const acc = state.accounts.find((x) => x.id === a.id);
  switch (a.type) {
    case 'add':
      if (!document.querySelector('dialog[open]')) openAccountDialog(null);
      break;
    case 'files':
      if (!document.querySelector('dialog[open]')) setFiles(!filesOpen);
      break;
    case 'snippets':
      if (!document.querySelector('dialog[open]')) openSnippets();
      break;
    case 'browser':
      if (!document.querySelector('dialog[open]')) setBrowser(!browserOpen);
      break;
    case 'browser-url':
      if (browserOpen) { $('#br-url').focus(); $('#br-url').select(); }
      break;
    case 'edit':
      if (acc) openAccountDialog(acc);
      break;
    case 'limit':
      if (acc) openLimitDialog(acc.id);
      break;
    case 'confirm-wipe':
      if (acc) {
        openConfirm(
          'Cerrar sesión y borrar datos',
          `Se cierra la sesión de «${acc.name}» y se borran sus cookies y datos en esta computadora.`,
          () => window.api.wipe(acc.id)
        );
      }
      break;
    case 'confirm-remove':
      if (acc) {
        openConfirm(
          'Eliminar cuenta',
          `Se quita «${acc.name}» de la app y se borran sus datos en esta computadora. Tu cuenta de ${SERVICE_LABELS[state.service] || 'Claude'} no se toca.`,
          () => window.api.remove(acc.id)
        );
      }
      break;
  }
});

(async () => {
  state = await window.api.getState();
  render();
  if (!state.tutorialShown) startTour();
  // Refresca los contadores de descanso.
  setInterval(render, 30000);
})();

// ---------------------------------------------------------------- Explorador de archivos

const FX_ICONS = {
  folder: 'M3,7a2,2 0 0,1 2,-2h4l2,2h8a2,2 0 0,1 2,2v8a2,2 0 0,1 -2,2H5a2,2 0 0,1 -2,-2z',
  file: 'M7,3h7l5,5v11a2,2 0 0,1 -2,2H7a2,2 0 0,1 -2,-2V5a2,2 0 0,1 2,-2z M14,3v5h5',
  zip: 'M7,3h7l5,5v11a2,2 0 0,1 -2,2H7a2,2 0 0,1 -2,-2V5a2,2 0 0,1 2,-2z M14,3v5h5 M10,10h2 M10,13h2 M10,16h2',
  up: 'M12,19V6 M6,11l6,-6l6,6',
  close: 'M6,6l12,12 M18,6L6,18',
  sort: 'M7,4v16 M4,17l3,3l3,-3 M17,20V4 M14,7l3,-3l3,3',
  copy: 'M9,9h10a1,1 0 0,1 1,1v10a1,1 0 0,1 -1,1H9a1,1 0 0,1 -1,-1V10a1,1 0 0,1 1,-1z M5,15H4a1,1 0 0,1 -1,-1V4a1,1 0 0,1 1,-1h10a1,1 0 0,1 1,1v1',
  move: 'M4,12h13 M13,8l4,4l-4,4 M20,5v14',
  paste: 'M9,4h6v3H9z M7,5H6a1,1 0 0,0 -1,1v14a1,1 0 0,0 1,1h12a1,1 0 0,0 1,-1V6a1,1 0 0,0 -1,-1h-1',
  newfolder: 'M3,7a2,2 0 0,1 2,-2h4l2,2h8a2,2 0 0,1 2,2v8a2,2 0 0,1 -2,2H5a2,2 0 0,1 -2,-2z M12,10v6 M9,13h6',
  trash: 'M5,7h14 M10,7V5h4v2 M7,7l1,12h8l1,-12 M10,11v5 M14,11v5',
  edit: 'M4,20l1,-4l10.5,-10.5a2,2 0 0,1 2.8,0l0.7,0.7a2,2 0 0,1 0,2.8L8.5,19.5z M14,7.5l2.5,2.5',
  extract: 'M12,4v11 M7.5,11l4.5,4.5l4.5,-4.5 M5,20h14',
  open: 'M14,4h6v6 M20,4l-9,9 M18,14v5a1,1 0 0,1 -1,1H5a1,1 0 0,1 -1,-1V7a1,1 0 0,1 1,-1h5',
  all: 'M4,4h16v16H4z M8,12l3,3l5,-6',
};
function fxIcon(name, cls) {
  const svg = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
  svg.setAttribute('viewBox', '0 0 24 24');
  svg.setAttribute('aria-hidden', 'true');
  if (cls) svg.setAttribute('class', cls);
  const p = document.createElementNS('http://www.w3.org/2000/svg', 'path');
  p.setAttribute('d', FX_ICONS[name]);
  svg.append(p);
  return svg;
}

const fx = {
  dir: null, parent: null, entries: [], sel: new Set(), last: null,
  clip: { paths: [], cut: false }, sort: 'date', places: [], busy: false, started: false, multi: false,
};
const FX_SORTS = { date: 'Fecha', name: 'Nombre', size: 'Tamaño' };

$('#fx-up').append(fxIcon('up'));
$('#fx-close').append(fxIcon('close'));

function fxJoin(dir, name) {
  const sep = dir.includes('\\') ? '\\' : '/';
  return dir.endsWith(sep) ? dir + name : dir + sep + name;
}
const fxPaths = () => fx.entries.filter((e) => fx.sel.has(e.name)).map((e) => fxJoin(fx.dir, e.name));
const FX_KINDS = {
  archive: 'zip rar 7z tar gz tgz bz2 xz',
  image: 'png jpg jpeg gif webp bmp heic svg ico',
  video: 'mp4 mkv webm avi mov wmv',
  audio: 'mp3 wav ogg flac m4a aac opus',
  pdf: 'pdf',
  doc: 'doc docx odt rtf ppt pptx odp',
  sheet: 'xls xlsx ods csv',
  code: 'js ts kt java py html htm css json xml sh ps1 c cpp h go rs yml yaml sql',
  exe: 'exe msi bat cmd apk lnk',
  text: 'txt md log ini cfg',
};
function fxKind(e) {
  if (e.isDir) return 'folder';
  const ext = (e.name.split('.').pop() || '').toLowerCase();
  if (ext === e.name.toLowerCase()) return 'other';
  for (const [k, list] of Object.entries(FX_KINDS)) if (list.split(' ').includes(ext)) return k;
  return 'other';
}
const fxIsZip = (e) => !e.isDir && /\.zip$/i.test(e.name);

function fxSay(text, busy) {
  const el = $('#fx-status');
  el.textContent = text || '';
  el.classList.toggle('busy', !!busy);
}
let fxSayTimer = 0;
function fxMsg(text) {
  fxSay(text);
  clearTimeout(fxSayTimer);
  fxSayTimer = setTimeout(() => { if (!fx.busy) fxStatusDefault(); }, 5000);
}
function fxStatusDefault() {
  const n = fx.entries.length;
  const s = fx.sel.size;
  fxSay(`${n} ${n === 1 ? 'elemento' : 'elementos'}${s ? ` · ${s} ${s === 1 ? 'seleccionado' : 'seleccionados'}` : ''}${fx.clip.paths.length ? ` · para ${fx.clip.cut ? 'mover' : 'copiar'}: ${fx.clip.paths.length}` : ''}`);
}

function fxFmtSize(b) {
  if (b < 1024) return `${b} B`;
  const u = ['KB', 'MB', 'GB', 'TB'];
  let v = b;
  let i = -1;
  do { v /= 1024; i++; } while (v >= 1024 && i < u.length - 1);
  return `${v >= 10 ? Math.round(v) : v.toFixed(1)} ${u[i]}`;
}
const fxFmtDate = (ms) => new Date(ms).toLocaleString('es-AR', { day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' });

function fxSorted() {
  const by = {
    date: (a, b) => b.mtime - a.mtime,
    name: (a, b) => a.name.localeCompare(b.name, 'es', { numeric: true, sensitivity: 'base' }),
    size: (a, b) => b.size - a.size,
  }[fx.sort];
  return fx.entries.slice().sort((a, b) => (a.isDir !== b.isDir ? (a.isDir ? -1 : 1) : by(a, b)));
}

async function fxLoad(dir) {
  fx.busy = true;
  fxSay('Leyendo…', true);
  const r = await window.api.fsList(dir);
  fx.busy = false;
  if (!r.ok) {
    fxMsg(r.message || 'No se pudo abrir la carpeta');
    renderFx();
    return false;
  }
  fx.dir = r.dir;
  fx.parent = r.parent;
  fx.entries = r.entries;
  fx.entries = fxSorted();
  fx.sel.clear();
  fx.last = null;
  renderFx();
  fxStatusDefault();
  $('#fx-list').scrollTop = 0;
  return true;
}

async function filesOpened() {
  if (!fx.started) {
    fx.started = true;
    fx.places = await window.api.fsRoots();
    const first = fx.places[0] && fx.places[0].path;
    if (first) await fxLoad(first);
  } else if (fx.dir) {
    fxReload();
  }
  $('#files').focus();
}

async function fxReload() {
  const keep = new Set(fx.sel);
  const r = await window.api.fsList(fx.dir);
  if (!r.ok) return;
  fx.entries = r.entries;
  fx.entries = fxSorted();
  fx.sel = new Set([...keep].filter((n) => fx.entries.some((e) => e.name === n)));
  renderFx();
  fxStatusDefault();
}

async function fxRun(work) {
  fx.busy = true;
  fxSay('Trabajando…', true);
  const r = await work();
  fx.busy = false;
  fx.sel.clear();
  await fxReload();
  fxMsg(r && r.message ? (r.ok ? r.message : 'No se pudo: ' + r.message) : '');
}

function fxOpenEntry(e) {
  const full = fxJoin(fx.dir, e.name);
  if (e.isDir) fxLoad(full);
  else window.api.fsOpen(full).then((r) => { if (!r.ok) fxMsg('No se pudo abrir: ' + (r.message || '')); });
}

let fxMenuEl = null;
function fxCloseMenu() {
  if (fxMenuEl) fxMenuEl.remove();
  fxMenuEl = null;
}

function fxMenu(e, x, y) {
  fxCloseMenu();
  if (!fx.sel.has(e.name)) { fx.sel = new Set([e.name]); fxSelChanged(); }
  const multi = fx.sel.size > 1;
  const full = fxJoin(fx.dir, e.name);
  const items = [];
  if (!multi) {
    items.push([e.isDir ? 'Abrir carpeta' : fxKind(e) === 'exe' ? 'Ejecutar' : 'Abrir', e.isDir ? 'folder' : 'open', () => fxOpenEntry(e)]);
    items.push(['Mostrar en el Explorador', 'folder', () => window.api.fsReveal(full)]);
    if (fxIsZip(e)) {
      items.push(['Extraer en carpeta', 'extract', () => fxExtract(true)]);
      items.push(['Extraer aquí', 'extract', () => fxExtract(false)]);
    }
  }
  items.push(['Copiar', 'copy', () => fxSetClip(false)]);
  items.push(['Mover', 'move', () => fxSetClip(true)]);
  if (!multi) items.push(['Renombrar', 'edit', () => fxRename()]);
  items.push(['Borrar', 'trash', () => fxDelete()]);

  const m = document.createElement('div');
  m.className = 'fx-menu';
  m.setAttribute('role', 'menu');
  const t = document.createElement('div');
  t.className = 'fx-menu-title';
  t.textContent = multi ? `${fx.sel.size} elementos` : e.name;
  m.append(t);
  for (const [label, icon, fn] of items) {
    const b = document.createElement('button');
    b.type = 'button';
    b.setAttribute('role', 'menuitem');
    if (label === 'Borrar') b.className = 'danger';
    b.append(fxIcon(icon), document.createTextNode(label));
    b.addEventListener('click', () => { fxCloseMenu(); fn(); });
    m.append(b);
  }
  document.body.append(m);
  fxMenuEl = m;
  const r = m.getBoundingClientRect();
  m.style.left = Math.max(8, Math.min(x, innerWidth - r.width - 8)) + 'px';
  m.style.top = Math.max(8, Math.min(y, innerHeight - r.height - 8)) + 'px';
  m.querySelector('button').focus();
}
document.addEventListener('pointerdown', (ev) => { if (fxMenuEl && !fxMenuEl.contains(ev.target)) fxCloseMenu(); }, true);
document.addEventListener('keydown', (ev) => { if (fxMenuEl && ev.key === 'Escape') { ev.stopPropagation(); fxCloseMenu(); } }, true);

function fxSetClip(cut) {
  const paths = fxPaths();
  if (!paths.length) return;
  fx.clip = { paths, cut };
  fx.sel.clear();
  renderFx();
  fxMsg(`${cut ? 'Para mover' : 'Para copiar'}: ${paths.length}. Andá a la carpeta destino y tocá «Pegar aquí»`);
}

function fxPaste() {
  if (!fx.clip.paths.length) return;
  const { paths, cut } = fx.clip;
  if (cut) fx.clip = { paths: [], cut: false };
  fxRun(() => (cut ? window.api.fsMove(paths, fx.dir) : window.api.fsCopy(paths, fx.dir)));
}

function fxDelete() {
  const paths = fxPaths();
  if (!paths.length) return;
  const what = paths.length === 1 ? `«${paths[0].split(/[\\/]/).pop()}»` : `${paths.length} elementos`;
  openConfirm('Borrar', `Se envía ${what} a la Papelera de reciclaje.`, () => fxRun(() => window.api.fsDelete(paths)));
  // openConfirm cierra los paneles: el explorador se vuelve a abrir cuando se cierra el diálogo.
  fx.reopen = true;
}

let promptAction = null;
function fxPrompt(title, label, value, okText, action) {
  $('#pr-title').textContent = title;
  $('#pr-label').textContent = label;
  $('#pr-ok').textContent = okText;
  $('#pr-input').value = value;
  promptAction = action;
  fx.reopen = true;
  openDialog($('#dlg-prompt'));
  const dot = value.lastIndexOf('.');
  $('#pr-input').focus();
  $('#pr-input').setSelectionRange(0, dot > 0 ? dot : value.length);
}
$('#form-prompt').addEventListener('submit', (e) => {
  e.preventDefault();
  const v = $('#pr-input').value;
  const act = promptAction;
  promptAction = null;
  $('#dlg-prompt').close();
  if (act) act(v);
});
$('#pr-cancel').addEventListener('click', () => { promptAction = null; $('#dlg-prompt').close(); });

function fxRename() {
  const paths = fxPaths();
  if (paths.length !== 1) return;
  const name = paths[0].split(/[\\/]/).pop();
  fxPrompt('Renombrar', 'Nuevo nombre', name, 'Renombrar', (v) => fxRun(() => window.api.fsRename(paths[0], v)));
}

function fxNewFolder() {
  fxPrompt('Nueva carpeta', 'Nombre', '', 'Crear', (v) => fxRun(() => window.api.fsMkdir(fx.dir, v)));
}

function fxExtract(intoFolder) {
  const [zip] = fxPaths();
  if (!zip) return;
  const base = zip.split(/[\\/]/).pop().replace(/\.zip$/i, '');
  const dest = intoFolder ? fxJoin(fx.dir, base) : fx.dir;
  fxRun(() => window.api.fsUnzip(zip, dest));
}

// Cuando se cierra un diálogo abierto desde el explorador, vuelve el explorador (los diálogos cierran los paneles).
for (const id of ['#dlg-prompt', '#dlg-confirm']) {
  $(id).addEventListener('close', () => {
    if (fx.reopen) {
      fx.reopen = false;
      setTimeout(() => setFiles(true), 60);
    }
  });
}

function fxChip(label, icon, onClick, opts) {
  const b = document.createElement('button');
  b.type = 'button';
  b.className = 'fx-chip' + (opts && opts.on ? ' on' : '');
  b.append(fxIcon(icon), document.createTextNode(label));
  if (opts && opts.title) b.title = opts.title;
  b.addEventListener('click', onClick);
  return b;
}

// Cambia la selección sin rehacer las filas (así el doble clic sobre una fila sigue funcionando).
function fxSelChanged() {
  document.querySelectorAll('#fx-list .fx-row').forEach((row, i) => {
    const e = fx.entries[i];
    if (!e) return;
    const on = fx.sel.has(e.name);
    row.classList.toggle('sel', on);
    row.setAttribute('aria-selected', String(on));
  });
  renderFxBar();
  fxStatusDefault();
}

function renderFxBar() {
  const bar = $('#fx-actions');
  bar.replaceChildren();
  const n = fx.sel.size;
  const only = n === 1 ? fx.entries.find((e) => fx.sel.has(e.name)) : null;
  if (fx.clip.paths.length) {
    bar.append(fxChip(`Pegar aquí (${fx.clip.paths.length})`, 'paste', fxPaste, { on: true, title: 'Ctrl+V' }));
    bar.append(fxChip('Descartar', 'close', () => { fx.clip = { paths: [], cut: false }; renderFx(); fxStatusDefault(); }));
    const sep = document.createElement('span');
    sep.className = 'fx-sep';
    bar.append(sep);
  }
  bar.append(fxChip(fx.multi ? 'Selección múltiple: sí' : 'Seleccionar varios', 'all', () => {
    fx.multi = !fx.multi;
    if (!fx.multi) fx.sel.clear();
    renderFx();
    fxStatusDefault();
  }, { on: fx.multi, title: 'Con Ctrl o Mayús también se seleccionan varios' }));
  if (fx.multi && fx.entries.length) {
    bar.append(fxChip(n === fx.entries.length ? 'Ninguno' : 'Todo', 'all', () => {
      if (fx.sel.size === fx.entries.length) fx.sel.clear();
      else fx.entries.forEach((e) => fx.sel.add(e.name));
      fxSelChanged();
    }, { title: 'Ctrl+A' }));
  }
  bar.append(fxChip('Nueva carpeta', 'newfolder', fxNewFolder, { title: 'Ctrl+Shift+N' }));
  bar.append(fxChip(`Orden: ${FX_SORTS[fx.sort]}`, 'sort', () => {
    fx.sort = fx.sort === 'date' ? 'name' : fx.sort === 'name' ? 'size' : 'date';
    fx.entries = fxSorted();
    renderFx();
  }));
}

function renderFx() {
  // Cabecera
  const atTop = !fx.parent;
  $('#fx-up').disabled = atTop;
  $('#fx-name').textContent = fx.dir ? (fx.dir.replace(/[\\/]+$/, '').split(/[\\/]/).pop() || fx.dir) : 'Archivos';
  $('#fx-path').textContent = fx.dir || '';
  $('#fx-path').title = fx.dir || '';

  // Lugares
  const places = $('#fx-places');
  places.replaceChildren();
  for (const p of fx.places) {
    places.append(fxChip(p.label, p.label.length <= 3 ? 'open' : 'folder', () => fxLoad(p.path), { on: fx.dir === p.path }));
  }

  renderFxBar();

  // Lista
  const list = $('#fx-list');
  list.replaceChildren();
  if (!fx.entries.length) {
    const d = document.createElement('div');
    d.className = 'fx-empty';
    d.textContent = fx.dir ? 'Carpeta vacía' : 'Elegí un lugar arriba';
    list.append(d);
    return;
  }
  const cutSet = new Set(fx.clip.cut ? fx.clip.paths : []);
  fx.entries.forEach((e, i) => {
    const row = document.createElement('div');
    row.className = 'fx-row' + (fx.sel.has(e.name) ? ' sel' : '');
    row.setAttribute('role', 'option');
    row.setAttribute('aria-selected', String(fx.sel.has(e.name)));
    const kind = fxKind(e);
    const ico = fxIcon(e.isDir ? 'folder' : kind === 'archive' ? 'zip' : 'file', 'fx-ico');
    ico.dataset.kind = kind;
    const nm = document.createElement('span');
    nm.className = 'nm' + (cutSet.has(fxJoin(fx.dir, e.name)) ? ' cut' : '');
    nm.textContent = e.name;
    const meta = document.createElement('span');
    meta.className = 'meta';
    meta.textContent = `${e.isDir ? '' : fxFmtSize(e.size) + ' · '}${fxFmtDate(e.mtime)}`;
    if (fx.multi) {
      const chk = document.createElement('span');
      chk.className = 'chk';
      row.append(chk);
    }
    row.append(ico, nm, meta);
    row.addEventListener('click', (ev) => {
      if (row.dataset.held) { delete row.dataset.held; return; }
      if (ev.shiftKey && fx.last !== null) {
        const [a, b] = [Math.min(fx.last, i), Math.max(fx.last, i)];
        if (!ev.ctrlKey) fx.sel.clear();
        for (let k = a; k <= b; k++) fx.sel.add(fx.entries[k].name);
      } else if (ev.ctrlKey || ev.metaKey || fx.multi) {
        if (!fx.sel.delete(e.name)) fx.sel.add(e.name);
        fx.last = i;
      } else {
        fx.sel = new Set([e.name]);
        fx.last = i;
      }
      fxSelChanged();
    });
    row.addEventListener('dblclick', () => fxOpenEntry(e));
    row.addEventListener('contextmenu', (ev) => {
      ev.preventDefault();
      fxMenu(e, ev.clientX, ev.clientY);
    });
    // Mantener apretado (táctil o mouse) abre el mismo menú.
    let hold = null;
    const cancel = () => { clearTimeout(hold); hold = null; };
    row.addEventListener('pointerdown', (ev) => {
      if (ev.button !== 0) return;
      const { clientX: x, clientY: y } = ev;
      hold = setTimeout(() => { hold = null; row.dataset.held = '1'; fxMenu(e, x, y); }, 500);
    });
    row.addEventListener('pointermove', (ev) => { if (hold && Math.abs(ev.movementX) + Math.abs(ev.movementY) > 3) cancel(); });
    row.addEventListener('pointerup', cancel);
    row.addEventListener('pointerleave', cancel);
    list.append(row);
  });
}

$('#fx-up').addEventListener('click', () => fx.parent && fxLoad(fx.parent));
$('#fx-close').addEventListener('click', () => setFiles(false));
$('#btn-files').prepend(fxIcon('folder'));
$('#btn-files').addEventListener('click', () => setFiles(!filesOpen));

document.addEventListener('keydown', (e) => {
  if (snippetsOpen && e.key === 'Escape' && !document.querySelector('dialog[open]')) { setSnippets(false); return; }
  if (!filesOpen || document.querySelector('dialog[open]')) return;
  if (e.target && /^(INPUT|TEXTAREA)$/.test(e.target.tagName)) return;
  const k = e.key.toLowerCase();
  const mod = e.ctrlKey || e.metaKey;
  if (k === 'escape') setFiles(false);
  else if (k === 'backspace' && fx.parent) fxLoad(fx.parent);
  else if (k === 'delete') fxDelete();
  else if (k === 'f2') fxRename();
  else if (k === 'enter') {
    const only = fx.sel.size === 1 ? fx.entries.find((x) => fx.sel.has(x.name)) : null;
    if (only) fxOpenEntry(only);
  } else if (mod && k === 'a') {
    e.preventDefault();
    fx.sel = new Set(fx.entries.map((x) => x.name));
    fxSelChanged();
  } else if (mod && k === 'c') fxSetClip(false);
  else if (mod && k === 'x') fxSetClip(true);
  else if (mod && k === 'v') fxPaste();
  else if (mod && e.shiftKey && k === 'n') { e.preventDefault(); fxNewFolder(); }
});

// ---------------------------------------------------------------- Navegador interno

const BR_QUICK = [
  ['GitHub', 'https://github.com/'],
  ['Google', 'https://www.google.com/'],
  ['DuckDuckGo', 'https://duckduckgo.com/'],
  ['Stack Overflow', 'https://stackoverflow.com/'],
];
FX_ICONS.back = 'M15,5l-7,7l7,7';
FX_ICONS.fwd = 'M9,5l7,7l-7,7';
FX_ICONS.reload = 'M20,12a8,8 0 1,1 -2.5,-5.8 M20,4v5h-5';
FX_ICONS.globe = 'M3,12a9,9 0 1,0 18,0a9,9 0 1,0 -18,0 M3,12h18 M12,3c2.5,2.6 3.8,5.6 3.8,9s-1.3,6.4 -3.8,9c-2.5,-2.6 -3.8,-5.6 -3.8,-9s1.3,-6.4 3.8,-9z';

const br = { loading: false, canBack: false, canFwd: false, started: false };
$('#snip-x').append(fxIcon('close'));
$('#br-back').append(fxIcon('back'));
$('#br-fwd').append(fxIcon('fwd'));
$('#br-reload').append(fxIcon('reload'));
$('#br-ext').append(fxIcon('open'));
$('#br-close').append(fxIcon('close'));
$('#btn-browser').prepend(fxIcon('globe'));
for (const [label, url] of BR_QUICK) {
  $('#br-quick').append(fxChip(label, 'globe', () => window.api.browserGo(url)));
}

function renderBrowser(st) {
  br.loading = !!st.loading;
  br.canBack = !!st.canGoBack;
  br.canFwd = !!st.canGoForward;
  $('#br-back').disabled = !br.canBack;
  $('#br-fwd').disabled = !br.canGoForward && !br.canFwd;
  $('#br-progress').classList.toggle('busy', br.loading);
  const rb = $('#br-reload');
  rb.replaceChildren(fxIcon(br.loading ? 'close' : 'reload'));
  rb.title = br.loading ? 'Detener' : 'Recargar (Ctrl+R)';
  if (document.activeElement !== $('#br-url')) $('#br-url').value = st.url || '';
}
window.api.onBrowserState(renderBrowser);

function brRect() {
  const r = $('#br-body').getBoundingClientRect();
  return { x: r.left, y: r.top, width: r.width, height: r.height };
}

async function browserOpened() {
  const st = await window.api.browserOpen();
  renderBrowser(st);
  // La vista web se dibuja encima del HTML: aparece cuando el cajón terminó de salir.
  setTimeout(() => {
    if (browserOpen) window.api.browserShow(brRect());
  }, 290);
}

new ResizeObserver(() => {
  if (browserOpen) window.api.browserShow(brRect());
}).observe($('#br-body'));

$('#btn-browser').addEventListener('click', () => setBrowser(!browserOpen));
$('#br-close').addEventListener('click', () => setBrowser(false));
$('#br-back').addEventListener('click', () => window.api.browserNav('back'));
$('#br-fwd').addEventListener('click', () => window.api.browserNav('forward'));
$('#br-reload').addEventListener('click', () => window.api.browserNav(br.loading ? 'stop' : 'reload'));
$('#br-ext').addEventListener('click', () => window.api.browserExternal());
$('#br-url').addEventListener('keydown', async (e) => {
  if (e.key !== 'Enter') return;
  e.preventDefault();
  const r = await window.api.browserGo($('#br-url').value);
  if (r && !r.ok) $('#br-url').select();
  else $('#br-url').blur();
});
$('#br-url').addEventListener('focus', () => $('#br-url').select());
