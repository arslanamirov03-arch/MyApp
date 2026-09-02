/* ===================================================================
   Sprechzeit — 30 Tage, 90 TestDaF-Themen + 90 freie Dialoge.
   Die App sagt nur, WIE VIELE Themen heute dran sind und in welchem
   Format. Die Themen selbst waehlt der Nutzer.
   Der Plan ist flexibel: Was offen bleibt, wird gleichmaessig auf die
   verbleibenden Tage verteilt — nie alles auf einen Tag.
   =================================================================== */
(function () {
  'use strict';

  var D = window.DATA;

  var START = new Date(2026, 8, 2);          // 2. September 2026
  var DAYS = 30;
  var GOAL = { td: 90, dg: 90 };             // Monatsziel je Format
  var TOTAL = GOAL.td + GOAL.dg;
  var CATS = ['td', 'dg'];

  var WD = ['So', 'Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa'];
  var MONTH = ['Januar', 'Februar', 'März', 'April', 'Mai', 'Juni', 'Juli',
               'August', 'September', 'Oktober', 'November', 'Dezember'];

  /* ---------------- Zustand ---------------- */

  // quota[tag] = {td, dg}  — Soll des Tages
  // done[tag]  = {td, dg}  — abgehakt
  var state = { v: 2, quota: {}, done: {}, lastBalance: -1, finaleSeen: false };

  function rawLoad() {
    try { if (window.Native && Native.load) { return Native.load() || ''; } } catch (e) { /* Fallback */ }
    try { return localStorage.getItem('sprechzeit') || ''; } catch (e) { return ''; }
  }

  function rawSave(json) {
    try { if (window.Native && Native.save) { Native.save(json); } } catch (e) { /* Fallback */ }
    try { localStorage.setItem('sprechzeit', json); } catch (e) { /* egal */ }
  }

  function loadState() {
    var s = rawLoad();
    if (!s) { return; }
    try {
      var o = JSON.parse(s);
      if (o && typeof o === 'object' && o.v === 2) {
        state.quota = o.quota || {};
        state.done = o.done || {};
        state.lastBalance = typeof o.lastBalance === 'number' ? o.lastBalance : -1;
        state.finaleSeen = !!o.finaleSeen;
      }
    } catch (e) { /* beschaedigter Stand: frisch anfangen */ }
  }

  var saveTimer = 0;
  function save() {
    clearTimeout(saveTimer);
    saveTimer = setTimeout(function () { rawSave(JSON.stringify(state)); }, 60);
  }

  function haptic(ms) {
    try { if (window.Native && Native.haptic) { Native.haptic(ms); } } catch (e) { /* egal */ }
  }

  /* ---------------- Zugriff auf Soll und Ist ---------------- */

  function getQ(d, c) { var o = state.quota[d]; return (o && o[c]) || 0; }
  function getD(d, c) { var o = state.done[d]; return (o && o[c]) || 0; }
  function setQ(d, c, v) { (state.quota[d] || (state.quota[d] = { td: 0, dg: 0 }))[c] = v; }
  function setD(d, c, v) { (state.done[d] || (state.done[d] = { td: 0, dg: 0 }))[c] = v; }

  function totalDone(c) {
    var n = 0;
    for (var d = 0; d < DAYS; d++) { n += getD(d, c); }
    return n;
  }

  /** Angezeigtes Soll: nie mehr, als bis zum Monatsziel ueberhaupt noch fehlt. */
  function slots(d, c) {
    var here = getD(d, c);
    var left = Math.max(0, GOAL[c] - totalDone(c));
    return Math.max(here, Math.min(getQ(d, c), here + left));
  }

  function dayStat(d) {
    var all = 0, done = 0;
    CATS.forEach(function (c) { all += slots(d, c); done += getD(d, c); });
    return { all: all, done: done, full: all > 0 && done >= all };
  }

  /* ---------------- Datum ---------------- */

  function startOfDay(x) { return new Date(x.getFullYear(), x.getMonth(), x.getDate()); }

  function todayIndex() {
    return Math.max(0, Math.round((startOfDay(new Date()).getTime() - START.getTime()) / 86400000));
  }

  function dateOf(i) { return new Date(2026, 8, 2 + i); }

  function longDate(i) {
    var d = dateOf(i);
    return WD[d.getDay()] + ', ' + d.getDate() + '. ' + MONTH[d.getMonth()];
  }

  /* ---------------- Verteilung ---------------- */

  function initQuota() {
    for (var d = 0; d < DAYS; d++) {
      if (!state.quota[d]) {
        state.quota[d] = { td: GOAL.td / DAYS, dg: GOAL.dg / DAYS };
      }
    }
  }

  /**
   * Verteilt den offenen Rest gleichmaessig auf die Tage from..29.
   * Tage vor `from` bleiben unangetastet — sie sind Geschichte.
   * Der Rest landet zuerst auf den fruehen Tagen: aufholen statt aufschieben.
   */
  function rebalance(from) {
    var last = DAYS - 1;
    from = Math.min(Math.max(from, 0), last);
    var days = last - from + 1;

    CATS.forEach(function (c) {
      var remaining = Math.max(0, GOAL[c] - totalDone(c));
      var base = Math.floor(remaining / days);
      var rem = remaining % days;
      for (var i = 0; i < days; i++) {
        var d = from + i;
        setQ(d, c, getD(d, c) + base + (i < rem ? 1 : 0));
      }
    });
  }

  function streakOf(today) {
    var d = today;
    if (!dayStat(d).full) { d--; }
    var s = 0;
    while (d >= 0) {
      var st = dayStat(d);
      if (st.full) { s++; d--; }
      else if (st.all === 0) { d--; }      // leerer Tag unterbricht die Serie nicht
      else { break; }
    }
    return s;
  }

  function fullDays() {
    var n = 0;
    for (var d = 0; d < DAYS; d++) { if (dayStat(d).full) { n++; } }
    return n;
  }

  /* ---------------- Feuerwerk & Konfetti ---------------- */

  var fx = (function () {
    var cv = document.getElementById('fx');
    var ctx = cv.getContext('2d');
    var dpr = Math.min(window.devicePixelRatio || 1, 2);
    var W = 0, H = 0, parts = [], raf = 0, finale = false, nextRocket = 0;

    var PALETTE = ['#B8663A', '#3F6F63', '#C08A2C', '#7A6EA8', '#CE6B79', '#4E8F5E', '#D9954A'];

    function resize() {
      W = window.innerWidth; H = window.innerHeight;
      cv.width = Math.round(W * dpr); cv.height = Math.round(H * dpr);
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    }
    resize();
    window.addEventListener('resize', resize);

    function rnd(a, b) { return a + Math.random() * (b - a); }
    function pick(a) { return a[(Math.random() * a.length) | 0]; }

    function confetti(x, y, n) {
      for (var i = 0; i < n; i++) {
        var a = rnd(-Math.PI * 0.92, -Math.PI * 0.08) + rnd(-0.5, 0.5);
        var sp = rnd(3.4, 9.5);
        parts.push({
          k: 'c', x: x, y: y,
          vx: Math.cos(a) * sp * rnd(0.7, 1.35),
          vy: Math.sin(a) * sp,
          g: 0.30, dr: 0.986,
          w: rnd(4.5, 8), h: rnd(7, 12),
          rot: rnd(0, 6.28), vr: rnd(-0.25, 0.25),
          c: pick(PALETTE), life: rnd(70, 120), age: 0
        });
      }
      start();
    }

    function burst(x, y, n, spread) {
      var c1 = pick(PALETTE), c2 = pick(PALETTE);
      for (var i = 0; i < n; i++) {
        var a = rnd(0, Math.PI * 2);
        var sp = rnd(1.1, spread);
        parts.push({
          k: 's', x: x, y: y,
          vx: Math.cos(a) * sp, vy: Math.sin(a) * sp,
          g: 0.055, dr: 0.962,
          r: rnd(1.6, 3.4),
          c: Math.random() < 0.5 ? c1 : c2,
          life: rnd(52, 96), age: 0
        });
      }
      start();
    }

    function rocket(tx) {
      parts.push({
        k: 'r', x: tx === undefined ? rnd(W * 0.15, W * 0.85) : tx, y: H + 8,
        vx: rnd(-0.5, 0.5), vy: rnd(-13, -9.5),
        g: 0.11, dr: 1,
        target: rnd(H * 0.13, H * 0.46),
        c: pick(PALETTE), life: 200, age: 0
      });
      start();
    }

    function step() {
      raf = 0;
      ctx.clearRect(0, 0, W, H);

      if (finale) {
        var now = performance.now();
        if (now > nextRocket) {
          rocket();
          if (Math.random() < 0.55) { rocket(); }
          nextRocket = now + rnd(190, 420);
        }
        // Dauerregen aus Konfetti, damit zwischen den Raketen nie Leere entsteht.
        if (Math.random() < 0.28) {
          parts.push({
            k: 'c', x: rnd(-10, W + 10), y: -16,
            vx: rnd(-0.7, 0.7), vy: rnd(0.8, 2.1),
            g: 0.012, dr: 0.999,
            w: rnd(4.5, 8), h: rnd(7, 12),
            rot: rnd(0, 6.28), vr: rnd(-0.16, 0.16),
            c: pick(PALETTE), life: 460, age: 0
          });
        }
      }

      for (var i = parts.length - 1; i >= 0; i--) {
        var p = parts[i];
        p.age++;
        p.vy += p.g;
        p.vx *= p.dr; p.vy *= p.dr;
        p.x += p.vx; p.y += p.vy;

        if (p.k === 'r') {
          ctx.globalAlpha = 0.85;
          ctx.fillStyle = p.c;
          ctx.beginPath();
          ctx.arc(p.x, p.y, 2.3, 0, 6.284);
          ctx.fill();
          ctx.globalAlpha = 0.22;
          ctx.fillRect(p.x - 1, p.y, 2, 12);
          if (p.y <= p.target || p.vy >= 0) {
            burst(p.x, p.y, 64 + ((Math.random() * 34) | 0), 6.6);
            parts.splice(i, 1);
          }
          continue;
        }

        var t = p.age / p.life;
        if (t >= 1 || p.y > H + 60) { parts.splice(i, 1); continue; }
        ctx.globalAlpha = t < 0.72 ? 1 : (1 - t) / 0.28;

        if (p.k === 'c') {
          p.rot += p.vr;
          ctx.save();
          ctx.translate(p.x, p.y);
          ctx.rotate(p.rot);
          ctx.fillStyle = p.c;
          ctx.fillRect(-p.w / 2, -p.h / 2, p.w, p.h);
          ctx.restore();
        } else {
          ctx.fillStyle = p.c;
          ctx.beginPath();
          ctx.arc(p.x, p.y, p.r, 0, 6.284);
          ctx.fill();
        }
      }
      ctx.globalAlpha = 1;

      if (parts.length || finale) { raf = requestAnimationFrame(step); }
      else { ctx.clearRect(0, 0, W, H); }
    }

    function start() { if (!raf) { raf = requestAnimationFrame(step); } }

    return {
      confetti: confetti,
      burst: burst,
      rocket: rocket,
      show: function (n, delay) {
        for (var i = 0; i < n; i++) { setTimeout(function () { rocket(); }, i * (delay || 280)); }
      },
      setFinale: function (v) {
        finale = v;
        if (!v) { return; }
        nextRocket = 0;
        // Sofort ein paar Explosionen, damit das Finale nicht erst anlaeuft.
        burst(W * 0.28, H * 0.28, 80, 7);
        burst(W * 0.72, H * 0.36, 80, 7);
        setTimeout(function () { burst(W * 0.5, H * 0.2, 90, 7.4); }, 260);
        start();
      },
      clear: function () { finale = false; parts.length = 0; }
    };
  })();

  /* ---------------- DOM ---------------- */

  var $ = function (id) { return document.getElementById(id); };
  var el = {
    headSub: $('headSub'), strip: $('strip'), scroll: $('mainScroll'),
    ringWrap: $('ringWrap'), ringFg: $('ringFg'), ringDone: $('ringDone'), ringAll: $('ringAll'),
    heroTitle: $('heroTitle'), heroSub: $('heroSub'), heroChips: $('heroChips'),
    slotsTd: $('slotsTd'), slotsDg: $('slotsDg'), cntTd: $('cntTd'), cntDg: $('cntDg'),
    qDe: $('qDe'), qRu: $('qRu'), qA: $('qA'), quoteBox: $('quoteBox'), tail: $('tailNote'),
    viewMain: $('viewMain'), viewSet: $('viewSet'),
    stats: $('stats'), quoteList: $('quoteList'), qCount: $('qCount'),
    overlay: $('overlay'), ovKicker: $('ovKicker'), ovTitle: $('ovTitle'),
    ovSub: $('ovSub'), ovBtn: $('ovBtn'), toast: $('toast')
  };

  function esc(s) {
    return String(s).replace(/[&<>"]/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' }[c];
    });
  }

  var T = todayIndex();
  var sel = 0;
  var finaleOpen = false;

  /* ---------------- Rendern ---------------- */

  var TICK = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="3.4" ' +
             'stroke-linecap="round" stroke-linejoin="round"><path d="M5 12.5l4.6 4.5L19 7.5"/></svg>';

  function renderSlots(node, c) {
    var n = slots(sel, c);
    var done = getD(sel, c);
    if (n === 0) {
      node.innerHTML = '<div class="empty">Für diesen Tag ist nichts offen.</div>';
      return;
    }
    var h = '';
    for (var i = 0; i < n; i++) {
      h += '<button class="slot ' + c + (i < done ? ' on' : '') + '" data-c="' + c + '" data-i="' + i +
           '" style="--i:' + i + '" aria-pressed="' + (i < done) + '">' +
             '<span class="slot-n">' + (i + 1) + '</span>' +
             '<span class="slot-ok">' + TICK + '</span>' +
           '</button>';
    }
    node.innerHTML = h;
  }

  function renderStrip() {
    var h = '';
    for (var d = 0; d < DAYS; d++) {
      var st = dayStat(d);
      var cls = 'day';
      if (st.full) { cls += ' is-full'; }
      else if (st.done > 0) { cls += ' is-part'; }
      else if (d < T && st.all > 0) { cls += ' is-miss'; }
      if (d === T) { cls += ' is-today'; }
      if (d === sel) { cls += ' is-sel'; }
      var dt = dateOf(d);
      h += '<button class="' + cls + '" data-day="' + d + '">' +
             '<span class="dw">' + WD[dt.getDay()] + '</span>' +
             '<span class="dn">' + dt.getDate() + '</span>' +
             '<span class="db"></span>' +
           '</button>';
    }
    el.strip.innerHTML = h;
  }

  function centerStrip(d, smooth) {
    var node = el.strip.querySelector('[data-day="' + d + '"]');
    if (!node) { return; }
    var left = node.offsetLeft - (el.strip.clientWidth - node.offsetWidth) / 2;
    if (el.strip.scrollTo) {
      el.strip.scrollTo({ left: Math.max(0, left), behavior: smooth ? 'smooth' : 'auto' });
    } else {
      el.strip.scrollLeft = Math.max(0, left);
    }
  }

  function render(smoothStrip) {
    var st = dayStat(sel);

    el.headSub.textContent = 'Tag ' + (Math.min(T, DAYS - 1) + 1) + ' von ' + DAYS +
                             ' · ' + longDate(Math.min(T, DAYS - 1));

    renderStrip();
    centerStrip(sel, smoothStrip !== false);

    var C = 219.9;
    var ratio = st.all ? st.done / st.all : 1;
    el.ringFg.style.strokeDashoffset = (C * (1 - Math.min(1, ratio))).toFixed(1);
    el.ringWrap.classList.toggle('is-full', st.full);
    el.ringDone.textContent = st.done;
    el.ringAll.textContent = st.all;

    var title = 'Tag ' + (sel + 1);
    if (sel === T) { title = 'Heute'; }
    else if (sel === T - 1) { title = 'Gestern'; }
    else if (sel === T + 1) { title = 'Morgen'; }
    el.heroTitle.textContent = title;
    el.heroSub.textContent = longDate(sel);

    var chips = [];
    var s = streakOf(T);
    if (s > 0) { chips.push(['hot', 'Serie · ' + s + (s === 1 ? ' Tag' : ' Tage')]); }
    chips.push(['', 'Gesamt · ' + (totalDone('td') + totalDone('dg')) + '/' + TOTAL]);
    var plan = GOAL.td / DAYS + GOAL.dg / DAYS;          // 6 im Grundplan
    if (st.all > plan) { chips.push(['cool', 'Nachholer · +' + (st.all - plan)]); }
    if (st.full) { chips.push(['cool', 'Tag geschafft']); }
    el.heroChips.innerHTML = chips.map(function (x) {
      return '<span class="chip ' + x[0] + '">' + esc(x[1]) + '</span>';
    }).join('');

    renderSlots(el.slotsTd, 'td');
    renderSlots(el.slotsDg, 'dg');
    el.cntTd.textContent = getD(sel, 'td') + '/' + slots(sel, 'td');
    el.cntDg.textContent = getD(sel, 'dg') + '/' + slots(sel, 'dg');

    if (sel <= T) {
      var q = D.QUOTES[sel % D.QUOTES.length];
      el.qDe.textContent = '„' + q.d + '"';
      el.qRu.textContent = q.r;
      el.qA.textContent = q.a;
      el.quoteBox.style.display = '';
    } else {
      el.quoteBox.style.display = 'none';
    }

    el.tail.textContent = T > DAYS - 1
      ? 'Der 30-Tage-Plan ist beendet. Der Rest sammelt sich auf Tag 30.'
      : 'Plan: 2. September – 1. Oktober 2026 · 90 + 90 Themen';
  }

  /* ---------------- Meldungen ---------------- */

  var toastTimer = 0;
  function toast(msg) {
    el.toast.textContent = msg;
    el.toast.classList.add('show');
    clearTimeout(toastTimer);
    toastTimer = setTimeout(function () { el.toast.classList.remove('show'); }, 1700);
  }
  function hideToast() {
    clearTimeout(toastTimer);
    el.toast.classList.remove('show');
  }

  var ovTimer = 0;
  function showOverlay(kicker, title, sub, btn, endless) {
    clearTimeout(ovTimer);
    hideToast();
    el.ovKicker.textContent = kicker;
    el.ovTitle.textContent = title;
    el.ovSub.textContent = sub;
    el.ovBtn.textContent = btn;
    el.overlay.classList.add('show');
    finaleOpen = !!endless;
    if (endless) { fx.setFinale(true); }
  }

  function hideOverlay() {
    el.overlay.classList.remove('show');
    finaleOpen = false;
    fx.setFinale(false);
    clearTimeout(ovTimer);
  }

  function celebrateDay(day, count) {
    haptic(45);
    var w = window.innerWidth, h = window.innerHeight;
    fx.burst(w * 0.5, h * 0.42, 90, 7.2);
    fx.show(5, 240);
    showOverlay('Tagesziel', 'Geschafft!',
      'Alle ' + count + ' Themen von Tag ' + (day + 1) + ' sind erledigt. Das zählt.',
      'Weiter', false);
    ovTimer = setTimeout(hideOverlay, 5200);
  }

  function celebrateMonth() {
    haptic(90);
    showOverlay('180 von 180', 'Ein ganzer Monat.',
      '90 TestDaF-Themen und 90 freie Dialoge. Dreißig Tage lang jeden Tag gesprochen. ' +
      'Dieses Feuerwerk hört nicht mehr auf.',
      'Schließen', true);
  }

  /* ---------------- Interaktion ---------------- */

  function tapSlot(c, i, node) {
    var before = dayStat(sel);
    var done = getD(sel, c);
    var adding = i >= done;

    // Tippen auf einen offenen Platz hakt bis dorthin ab,
    // Tippen auf einen gesetzten Haken nimmt ihn (und die dahinter) zurueck.
    setD(sel, c, adding ? i + 1 : i);

    if (sel === T) { rebalance(T + 1); }   // heute bleibt stabil, nur die Zukunft wird neu verteilt
    save();

    if (adding) {
      var r = node.getBoundingClientRect();
      fx.confetti(r.left + r.width / 2, r.top + r.height / 2, 34);
      haptic(18);
    }

    var after = dayStat(sel);
    var all = totalDone('td') + totalDone('dg');

    render(false);

    if (!adding) { return; }

    if (all >= TOTAL) {
      state.finaleSeen = true;
      save();
      setTimeout(celebrateMonth, 380);
    } else if (after.full && !before.full) {
      setTimeout(function () { celebrateDay(sel, after.all); }, 300);
    } else {
      toast(after.done + ' von ' + after.all + ' geschafft');
    }
  }

  function onSlots(ev) {
    var b = ev.target.closest ? ev.target.closest('.slot') : null;
    if (!b) { return; }
    tapSlot(b.getAttribute('data-c'), parseInt(b.getAttribute('data-i'), 10), b);
  }

  el.slotsTd.addEventListener('click', onSlots);
  el.slotsDg.addEventListener('click', onSlots);

  el.strip.addEventListener('click', function (ev) {
    var b = ev.target.closest ? ev.target.closest('[data-day]') : null;
    if (!b) { return; }
    var d = parseInt(b.getAttribute('data-day'), 10);
    if (d === sel) { return; }
    sel = d;
    render(true);
    el.scroll.scrollTop = 0;
  });

  el.overlay.addEventListener('click', function (ev) {
    if (finaleOpen && ev.target !== el.ovBtn) { return; }   // Finale nur ueber den Knopf schliessen
    hideOverlay();
  });

  /* ---------------- Übersicht ---------------- */

  function renderSettings() {
    var rows = [
      ['', (totalDone('td') + totalDone('dg')) + '/' + TOTAL, 'Themen gesamt'],
      ['td', totalDone('td') + '/' + GOAL.td, 'TestDaF'],
      ['dg', totalDone('dg') + '/' + GOAL.dg, 'Dialoge'],
      ['', fullDays() + '/' + DAYS, 'Volle Tage'],
      ['', String(streakOf(T)), 'Serie (Tage)'],
      ['', String(Math.max(0, DAYS - T)), 'Tage übrig']
    ];
    el.stats.innerHTML = rows.map(function (x) {
      return '<div class="stat ' + x[0] + '"><b>' + esc(x[1]) + '</b><span>' + esc(x[2]) + '</span></div>';
    }).join('');

    $('btnFinale').hidden = (totalDone('td') + totalDone('dg')) < TOTAL;

    var unlocked = Math.min(T, DAYS - 1);
    el.qCount.textContent = (unlocked + 1) + '/' + DAYS;

    var lock = '<svg class="qi-lock" viewBox="0 0 24 24" width="15" height="15" fill="none" ' +
               'stroke="currentColor" stroke-width="1.8"><rect x="4.5" y="10.5" width="15" height="10" rx="2.5"/>' +
               '<path d="M8 10.5V7.8a4 4 0 0 1 8 0v2.7"/></svg>';

    var h = '';
    for (var d = 0; d < DAYS; d++) {
      var q = D.QUOTES[d % D.QUOTES.length];
      if (d <= unlocked) {
        h += '<div class="qi">' +
               '<p class="qi-d">„' + esc(q.d) + '"</p>' +
               '<p class="qi-r">' + esc(q.r) + '</p>' +
               '<p class="qi-m">' + esc(q.a) + ' · Tag ' + (d + 1) + '</p>' +
             '</div>';
      } else {
        h += '<div class="qi locked">' + lock +
               '<span class="qi-t">Tag ' + (d + 1) + ' — noch verschlossen</span>' +
             '</div>';
      }
    }
    el.quoteList.innerHTML = h;
  }

  function openSettings() {
    renderSettings();
    el.viewMain.classList.remove('is-active');
    el.viewMain.classList.add('is-left');
    el.viewSet.classList.add('is-active');
  }

  function closeSettings() {
    el.viewSet.classList.remove('is-active');
    el.viewMain.classList.remove('is-left');
    el.viewMain.classList.add('is-active');
  }

  $('btnSettings').addEventListener('click', openSettings);
  $('btnBack').addEventListener('click', closeSettings);
  $('btnFinale').addEventListener('click', celebrateMonth);

  var resetArmed = 0;
  $('btnReset').addEventListener('click', function () {
    var b = this;
    if (!resetArmed) {
      resetArmed = 1;
      b.classList.add('armed');
      b.textContent = 'Wirklich alles löschen?';
      $('resetNote').textContent = 'Noch einmal tippen. Nach fünf Sekunden wird abgebrochen.';
      setTimeout(function () {
        if (resetArmed === 1) {
          resetArmed = 0;
          b.classList.remove('armed');
          b.textContent = 'Fortschritt zurücksetzen';
          $('resetNote').textContent = 'Löscht alle Häkchen. Zum Bestätigen zweimal tippen.';
        }
      }, 5000);
      return;
    }
    resetArmed = 0;
    state.quota = {};
    state.done = {};
    state.finaleSeen = false;
    initQuota();
    rebalance(T);
    state.lastBalance = T;
    save();
    b.classList.remove('armed');
    b.textContent = 'Fortschritt zurückgesetzt';
    $('resetNote').textContent = 'Alles steht wieder am Anfang.';
    renderSettings();
    render(false);
    setTimeout(function () { b.textContent = 'Fortschritt zurücksetzen'; }, 2500);
  });

  /* ---------------- Zurück-Taste (Android) ---------------- */

  window.appHandleBack = function () {
    if (el.overlay.classList.contains('show')) { hideOverlay(); return true; }
    if (el.viewSet.classList.contains('is-active')) { closeSettings(); return true; }
    if (sel !== Math.min(T, DAYS - 1)) { sel = Math.min(T, DAYS - 1); render(true); return true; }
    return false;
  };

  /* ---------------- Start ---------------- */

  function boot() {
    T = todayIndex();
    loadState();
    initQuota();

    if (state.lastBalance !== T) {
      rebalance(T);                 // beim Tageswechsel: offener Rest neu verteilt
      state.lastBalance = T;
      save();
    }

    sel = Math.min(T, DAYS - 1);
    render(false);
    setTimeout(function () { centerStrip(sel, false); }, 30);
  }

  boot();

  document.addEventListener('visibilitychange', function () {
    if (document.visibilityState === 'visible' && todayIndex() !== T) { boot(); }
  });

})();
