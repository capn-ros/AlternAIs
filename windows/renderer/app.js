'use strict';

const $ = (sel) => document.querySelector(sel);
const PALETTE = ['#E07A5F', '#F2A541', '#81B29A', '#4FB0C6', '#6C8EAD', '#9A7BD1', '#D96C9F', '#8D99AE'];
const HOUR = 3600 * 1000;

let state = { accounts: [], selectedId: null };
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

const currentAccount = () => state.accounts.find((a) => a.id === state.selectedId);

// ---------------------------------------------------------------- Render

function render() {
  const list = $('#accounts');
  list.replaceChildren();
  state.accounts.forEach((acc, i) => {
    const st = statusOf(acc);
    const btn = document.createElement('button');
    btn.className = 'avatar' + (acc.id === state.selectedId ? ' selected' : '') + (st.limited ? ' limited' : '');
    btn.style.setProperty('--c', acc.color);
    btn.textContent = (acc.name.trim()[0] || '?').toUpperCase();
    const lines = [acc.name];
    if (acc.purpose) lines.push(acc.purpose);
    lines.push(st.text);
    if (i < 9) lines.push(`Ctrl+${i + 1}`);
    btn.title = lines.join('\n');
    if (st.limited) {
      const b = document.createElement('span');
      b.className = 'badge';
      b.textContent = '⏳';
      btn.append(b);
    }
    btn.addEventListener('click', () => window.api.select(acc.id));
    btn.addEventListener('contextmenu', (e) => {
      e.preventDefault();
      window.api.menu(acc.id);
    });
    list.append(btn);
  });
  renderTopbar();
}

function renderTopbar() {
  const acc = currentAccount();
  const has = !!acc;
  $('#who').hidden = !has;
  $('#t-actions').hidden = !has;
  $('#empty').hidden = state.accounts.length > 0;
  if (!has) {
    $('#t-status').textContent = '';
    return;
  }
  const st = statusOf(acc);
  $('#t-dot').style.setProperty('--c', acc.color);
  $('#t-name').textContent = acc.name;
  $('#t-purpose').textContent = acc.purpose ? `· ${acc.purpose}` : '';
  const s = $('#t-status');
  s.textContent = st.text;
  s.classList.toggle('limited', st.limited);
}

// ---------------------------------------------------------------- Diálogos

function openDialog(dlg) {
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
          `Se quita «${acc.name}» de la app y se borran sus datos en esta computadora. Tu cuenta de Claude no se toca.`,
          () => window.api.remove(acc.id)
        );
      }
      break;
  }
});

(async () => {
  state = await window.api.getState();
  render();
  // Refresca los contadores de descanso.
  setInterval(render, 30000);
})();
