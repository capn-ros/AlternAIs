'use strict';
// Logo animado. Coreografía (la misma que en Android, Splash.kt): las dos mitades (Claude y Replit)
// entran y se encajan, giran para alternar, aparecen las cuentas, se voltean cambiando de color y un
// anillo se completa (el tiempo de descanso). `mount(el, {loop})`: con loop se repite sin la entrada
// (se usa mientras carga una cuenta); sin loop es la animación de arranque (~1,7 s) y se quita sola.
(() => {
  const NS = 'http://www.w3.org/2000/svg';
  const CORAL = '#D97757';
  const CREAM = '#F5EFE6';
  const DOTS = ['#E07A5F', '#F2A33A', '#81B29A', '#3FB0C4', '#9B72CF'];
  const TOTAL = 1700;
  const LOOP_START = 300;
  const LOOP = 1300;
  const clamp = (x) => Math.min(1, Math.max(0, x));
  const easeInOut = (x) => (x < 0.5 ? 2 * x * x : 1 - Math.pow(-2 * x + 2, 2) / 2);
  const easeOut = (x) => 1 - Math.pow(1 - x, 3);
  const easeOutBack = (x) => { const c1 = 1.70158, c3 = c1 + 1; return 1 + c3 * Math.pow(x - 1, 3) + c1 * Math.pow(x - 1, 2); };

  function mount(root, opts) {
    const loop = !!(opts && opts.loop);
    const svg = document.createElementNS(NS, 'svg');
    svg.setAttribute('viewBox', '-90 -90 180 180');
    svg.setAttribute('class', 'splash-svg');
    svg.setAttribute('aria-hidden', 'true');
    root.appendChild(svg);
    const mk = (name, attrs, parent) => {
      const el = document.createElementNS(NS, name);
      for (const k of Object.keys(attrs)) el.setAttribute(k, attrs[k]);
      (parent || svg).appendChild(el);
      return el;
    };
    const C = 2 * Math.PI * 46;
    const ringEl = mk('circle', { r: 46, fill: 'none', stroke: CORAL, 'stroke-width': 3, 'stroke-linecap': 'round', transform: 'rotate(-90)' });
    ringEl.setAttribute('stroke-dasharray', C + ' ' + C);
    const dotEls = DOTS.map((c) => mk('circle', { r: 0, fill: c }));
    const group = mk('g', {});
    const a = mk('path', { d: 'M0 -32A32 32 0 0 0 0 32Z' }, group);
    const b = mk('path', { d: 'M0 -32A32 32 0 0 1 0 32Z' }, group);

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
      ringEl.setAttribute('opacity', ring > 0 ? (loop ? 1 - clamp((t - 1450) / 150) : 1) : 0);
      dotEls.forEach((d, i) => {
        const s = easeOutBack(clamp((t - 400 - 60 * i) / 220)) * (1 - ring);
        const ang = ((-90 + 72 * i) + rot * 0.5) * Math.PI / 180;
        d.setAttribute('cx', Math.cos(ang) * 54);
        d.setAttribute('cy', Math.sin(ang) * 54);
        d.setAttribute('r', Math.max(0, 5.5 * s));
      });
      if (!loop) root.style.opacity = String(1 - clamp((t - 1500) / 200));
    }

    let raf = 0;
    let stopped = false;
    const stop = () => {
      stopped = true;
      cancelAnimationFrame(raf);
      svg.remove();
    };
    const t0 = performance.now();
    const tick = (now) => {
      if (stopped) return;
      const e = now - t0;
      if (loop) {
        frame(LOOP_START + (e % LOOP));
      } else {
        if (e >= TOTAL) { stop(); root.remove(); return; }
        frame(e);
      }
      raf = requestAnimationFrame(tick);
    };
    frame(loop ? LOOP_START : 0);
    raf = requestAnimationFrame(tick);
    return { stop, root };
  }

  window.AlternLogo = { mount };

  // Arranque: se muestra al abrir la app, salvo que se hayan pedido menos animaciones.
  const intro = document.getElementById('splash');
  if (intro) {
    const reduce = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (reduce) intro.remove();
    else {
      const m = mount(intro, { loop: false });
      intro.addEventListener('click', () => { m.stop(); intro.remove(); });
    }
  }
})();
