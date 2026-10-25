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

function renderServiceTabs() {
  document.documentElement.dataset.service = state.service;
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
  setDrawer(false);
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
  const data = { name, purpose: $('#acc-purpose').value.trim(), color: pickedColor };
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

function setDrawer(open, fromMain = false) {
  if (open === drawerOpen) return;
  drawerOpen = open;
  const token = ++drawerToken;
  const d = $('#drawer');
  d.setAttribute('aria-hidden', String(!open));
  $('#settings').classList.toggle('on', open);
  if (open) {
    // Primero la página se corre a la derecha, y recién ahí sale el cajón.
    window.api.drawer(true).then(() => {
      if (token === drawerToken) requestAnimationFrame(() => d.classList.add('open'));
    });
  } else {
    d.classList.remove('open');
    // Espera a que termine de guardarse para devolverle el lugar a la página.
    if (fromMain) return;
    setTimeout(() => {
      if (token === drawerToken) window.api.drawer(false);
    }, 240);
  }
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
    setDrawer(false, true);
    return;
  }
  const acc = state.accounts.find((x) => x.id === a.id);
  switch (a.type) {
    case 'add':
      if (!document.querySelector('dialog[open]')) openAccountDialog(null);
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
