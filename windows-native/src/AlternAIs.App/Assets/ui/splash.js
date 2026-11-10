'use strict';
// Logo animado. Coreografía (la misma que en Android, Splash.kt): las dos mitades (Claude y Replit)
// entran y se encajan, giran para alternar, aparecen las cuentas, se voltean cambiando de color y un
// anillo se completa (el tiempo de descanso). Debajo va el nombre "AlternAIs" (trazo redondeado): en el
// arranque se dibuja letra a letra; en el bucle cada letra se voltea y cambia de color en cascada, al compás del logo. `mount(el, {loop})`: con loop se repite sin la entrada
// (se usa mientras carga una cuenta); sin loop es la animación de arranque (~1,7 s) y se quita sola.
(() => {
  const NS = 'http://www.w3.org/2000/svg';
  const CORAL = '#D97757';
  // En tema claro (loading.html?light=1) lo crema pasa a carbón para que se vea sobre fondo blanco.
  const CREAM = window.AlternLight ? '#1C1917' : '#F5EFE6';
  const DOTS = ['#E07A5F', '#F2A33A', '#81B29A', '#3FB0C4', '#9B72CF'];
  const TOTAL = 1700;
  const LOOP_START = 300;
  const LOOP = 1300;
  // Nombre: [trazo, ancho, color base]. Trazo uniforme de puntas redondas, como el anillo del logo.
  const WSW = 6, WGAP = 8, WSCALE = 0.7, WBASE = 122;
  const GLYPHS = [
    ['M0 0L13 -36L26 0M5 -13H21', 26, CREAM],
    ['M3 -40V0', 6, CREAM],
    ['M6 -36V-9A9 9 0 0 0 15 0M0 -26H16', 17, CREAM],
    ['M0 -13H26A13 13 0 1 0 22.2 -3.8', 26, CREAM],
    ['M3 0V-26M3 -14C3 -22 8 -26 16 -26', 17, CREAM],
    ['M3 0V-26M3 -15C3 -23 8 -26 14 -26C21 -26 25 -22 25 -14V0', 28, CREAM],
    ['M0 0L13 -36L26 0M5 -13H21', 26, CORAL],
    ['M3 -36V0', 6, CORAL],
    ['M17 -21C15 -26 4 -28 2.5 -20C1.5 -13 18 -14 18 -6C18 1 5 1 1 -6', 20, CREAM]
  ];
  const WLOOP = 2600, WAVE1 = 250, WAVE2 = 1550, WSTAG = 55, WDUR = 460; // 2 vueltas del logo en bucle
  const IN_START = 450, IN_STAG = 60, IN_DUR = 380;
  let wTotal = 0;
  const wPos = GLYPHS.map((g) => { const x = wTotal; wTotal += g[1] + WGAP; return x; });
  wTotal -= WGAP;
  const clamp = (x) => Math.min(1, Math.max(0, x));
  const easeInOut = (x) => (x < 0.5 ? 2 * x * x : 1 - Math.pow(-2 * x + 2, 2) / 2);
  const easeOut = (x) => 1 - Math.pow(1 - x, 3);
  const easeOutBack = (x) => { const c1 = 1.70158, c3 = c1 + 1; return 1 + c3 * Math.pow(x - 1, 3) + c1 * Math.pow(x - 1, 2); };

  function mount(root, opts) {
    const loop = !!(opts && opts.loop);
    const svg = document.createElementNS(NS, 'svg');
    svg.setAttribute('viewBox', '-110 -90 220 230');
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

    const word = mk('g', { transform: `translate(${-wTotal * WSCALE / 2} ${WBASE}) scale(${WSCALE})` });
    const letters = GLYPHS.map((g, i) => {
      const grp = mk('g', {}, word);
      const p = mk('path', { d: g[0], fill: 'none', 'stroke-width': WSW, 'stroke-linecap': 'round', 'stroke-linejoin': 'round', pathLength: 1, 'stroke-dasharray': '1 1' }, grp);
      return { grp, p, w: g[1], x: wPos[i], base: g[2], alt: g[2] === CORAL ? CREAM : CORAL };
    });
    // Nombre: intro = se dibuja letra a letra; bucle = voltea y cambia de color en cascada.
    function wordFrame(t, intro) {
      letters.forEach((e, i) => {
        let sx = 1, hop = 0, swapped = false, draw = 1;
        if (intro) {
          draw = easeOut(clamp((t - IN_START - IN_STAG * i) / IN_DUR));
        } else {
          const p1 = easeInOut(clamp((t - WAVE1 - WSTAG * i) / WDUR));
          const p2 = easeInOut(clamp((t - WAVE2 - WSTAG * i) / WDUR));
          swapped = p1 >= 0.5 && p2 < 0.5;
          sx = Math.max(0.02, Math.abs(Math.cos(Math.PI * (p2 > 0 && p2 < 1 ? p2 : p1))));
          hop = -4 * (Math.sin(Math.PI * p1) + Math.sin(Math.PI * p2));
        }
        e.grp.setAttribute('transform', `translate(${e.x + e.w / 2} ${-18 + hop}) scale(${sx} 1) translate(${-e.w / 2} 18)`);
        e.p.setAttribute('stroke', swapped ? e.alt : e.base);
        e.p.setAttribute('stroke-dashoffset', 1 - draw);
        e.p.setAttribute('opacity', draw > 0.001 ? 1 : 0);
      });
    }

    function frame(t, e) {
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
      if (loop) wordFrame(e % WLOOP, false); else wordFrame(t, true);
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
        frame(LOOP_START + (e % LOOP), e);
      } else {
        if (e >= TOTAL) { stop(); root.remove(); return; }
        frame(e, e);
      }
      raf = requestAnimationFrame(tick);
    };
    frame(loop ? LOOP_START : 0, 0);
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
