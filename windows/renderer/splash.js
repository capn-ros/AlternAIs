'use strict';
// Animación de arranque del logo (~1,7 s). Misma coreografía que en Android: las dos mitades
// (Claude y Replit) entran y se encajan, giran para alternar, aparecen las cuentas, se voltean
// cambiando de color y un anillo se completa (el tiempo de descanso).
(() => {
  const root = document.getElementById('splash');
  if (!root) return;
  const NS = 'http://www.w3.org/2000/svg';
  const CORAL = '#D97757';
  const CREAM = '#F5EFE6';
  const DOTS = ['#E07A5F', '#F2A33A', '#81B29A', '#3FB0C4', '#9B72CF'];
  const TOTAL = 1700;
  const reduce = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  if (reduce) { root.remove(); return; }

  const mk = (name, attrs, parent) => {
    const el = document.createElementNS(NS, name);
    for (const k of Object.keys(attrs)) el.setAttribute(k, attrs[k]);
    (parent || svg).appendChild(el);
    return el;
  };
  const svg = document.createElementNS(NS, 'svg');
  svg.setAttribute('viewBox', '-90 -90 180 180');
  svg.setAttribute('class', 'splash-svg');
  svg.setAttribute('aria-hidden', 'true');
  root.appendChild(svg);

  const ringEl = mk('circle', { r: 46, fill: 'none', stroke: CORAL, 'stroke-width': 3, 'stroke-linecap': 'round', transform: 'rotate(-90)' });
  const C = 2 * Math.PI * 46;
  ringEl.setAttribute('stroke-dasharray', C + ' ' + C);
  const dotEls = DOTS.map((c) => mk('circle', { r: 0, fill: c }));
  const group = mk('g', {});
  const a = mk('path', { d: 'M0 -32A32 32 0 0 0 0 32Z' }, group); // mitad izquierda
  const b = mk('path', { d: 'M0 -32A32 32 0 0 1 0 32Z' }, group); // mitad derecha

  const clamp = (x) => Math.min(1, Math.max(0, x));
  const easeInOut = (x) => (x < 0.5 ? 2 * x * x : 1 - Math.pow(-2 * x + 2, 2) / 2);
  const easeOut = (x) => 1 - Math.pow(1 - x, 3);
  const easeOutBack = (x) => { const c1 = 1.70158, c3 = c1 + 1; return 1 + c3 * Math.pow(x - 1, 3) + c1 * Math.pow(x - 1, 2); };

  function frame(t) {
    const appear = clamp(t / 150);
    const slide = easeOutBack(clamp(t / 300));
    const rot = 180 * easeInOut(clamp((t - 300) / 450));
    const flip = easeInOut(clamp((t - 750) / 350));
    const ring = easeOut(clamp((t - 1100) / 350));
    const pulse = 1 + 0.08 * Math.sin(Math.PI * clamp((t - 1350) / 200));
    const swapped = flip >= 0.5;
    const sx = Math.max(0.02, Math.abs(Math.cos(Math.PI * flip)));

    group.setAttribute('transform', `scale(${pulse}) rotate(${rot}) scale(${sx} 1)`);
    a.setAttribute('transform', `translate(${-1 - 60 * (1 - slide)} 5)`);
    b.setAttribute('transform', `translate(${1 + 60 * (1 - slide)} -5)`);
    a.setAttribute('fill', swapped ? CREAM : CORAL);
    b.setAttribute('fill', swapped ? CORAL : CREAM);
    a.setAttribute('opacity', appear);
    b.setAttribute('opacity', appear);
    ringEl.setAttribute('stroke-dashoffset', C * (1 - ring));
    ringEl.setAttribute('opacity', ring > 0 ? 1 : 0);
    dotEls.forEach((d, i) => {
      const s = easeOutBack(clamp((t - 400 - 60 * i) / 220)) * (1 - ring);
      const ang = ((-90 + 72 * i) + rot * 0.5) * Math.PI / 180;
      d.setAttribute('cx', Math.cos(ang) * 54);
      d.setAttribute('cy', Math.sin(ang) * 54);
      d.setAttribute('r', Math.max(0, 5.5 * s));
    });
    root.style.opacity = String(1 - clamp((t - 1500) / 200));
  }

  const done = () => {
    root.remove();
  };
  root.addEventListener('click', done);
  const t0 = performance.now();
  const tick = (now) => {
    if (!root.isConnected) return;
    const t = now - t0;
    if (t >= TOTAL) { done(); return; }
    frame(t);
    requestAnimationFrame(tick);
  };
  frame(0);
  requestAnimationFrame(tick);
})();
