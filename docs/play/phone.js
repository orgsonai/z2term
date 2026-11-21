/* z2term play — モックのスマホ本体。
   状態バー・アプリの入れ物・ナビゲーション・通知欄とクイック設定・トースト・最近・ダイアログ・
   消灯の演出・指の軌跡の層を持つ。アプリ（home / z2term / browser …）は Z2P.registerApp で足す。
   保存: z2mock:phone */
(function (G) {
  'use strict';
  var Z = G.Z2P = G.Z2P || {};
  var h = function () { return Z.h.apply(null, arguments); };
  var T = function () { return Z.t.apply(null, arguments); };

  Z.APPS = [];
  var appById = {};
  Z.registerApp = function (a) {
    if (appById[a.id]) { Z.APPS.splice(Z.APPS.indexOf(appById[a.id]), 1); }
    appById[a.id] = a;
    Z.APPS.push(a);
  };
  Z.appById = function (id) { return appById[id]; };
  Z.appByPkg = function (pkg) { return Z.APPS.filter(function (a) { return a.pkg === pkg; })[0]; };

  Z.reduceMotion = function () { return !!(G.matchMedia && G.matchMedia('(prefers-reduced-motion: reduce)').matches); };

  /* アプリのアイコン。z2term は本物（app/src/main/res/drawable/ic_launcher_foreground.xml）と同じ形:
     黒地に緑の「>」と「_」。適応アイコンの安全域（108 のうち中央 72）を丸く切り抜いて描く。 */
  Z.iconEl = function (id, small) {
    var a = appById[id];
    var el = h('span.z2p-icon' + (small ? '.sm' : ''), { 'data-app': id, 'aria-hidden': 'true' });
    if (id === 'z2term') {
      var ns = 'http://www.w3.org/2000/svg';
      var svg = document.createElementNS(ns, 'svg');
      svg.setAttribute('viewBox', '18 18 72 72');
      var g = document.createElementNS(ns, 'g');
      g.setAttribute('transform', 'translate(5.4 5.4) scale(0.9)');
      g.setAttribute('fill', '#22c55e');
      ['M30,36 L40,36 L60,54 L40,72 L30,72 L50,54 Z', 'M65,68 L80,68 L80,72 L65,72 Z'].forEach(function (d) {
        var path = document.createElementNS(ns, 'path');
        path.setAttribute('d', d);
        g.appendChild(path);
      });
      svg.appendChild(g);
      el.appendChild(svg);
    } else if (a) el.textContent = a.icon;
    return el;
  };

  /* 動きの共通部品（Android の標準に近い減速の曲線） */
  var EASE = 'cubic-bezier(0.2, 0, 0, 1)';
  var running = [];
  function anim(el, frames, ms, done) {
    if (Z.reduceMotion() || !el.animate) { if (done) done(); return; }
    var a = el.animate(frames, { duration: ms, easing: EASE });
    running.push(a);
    a.onfinish = a.oncancel = function () {
      running = running.filter(function (x) { return x !== a; });
      if (done) done();
    };
  }
  function finishAll() { running.slice().forEach(function (a) { try { a.finish(); } catch (e) { /* 無視 */ } }); }
  function originIn(el, rect) {
    var r = el.getBoundingClientRect();
    if (!rect) return '50% 50%';
    return (rect.left + rect.width / 2 - r.left) + 'px ' + (rect.top + rect.height / 2 - r.top) + 'px';
  }

  var DEF = {
    airplane: false, wifi: true, torch: false, ringer: 'normal', volume: 7,
    notifs: [], nid: 1, tiles: {}, widget: null, homeOrder: null, dockOrder: null, edge: { on: false, panels: {} },
    clip: { cur: '', hist: [] }, dev: false, trail: true, alarms: [], recents: [],
    lowWarned: false, buildTaps: 0
  };

  var P = Z.phone = {};
  var st;           /* 保存する状態 */
  var els = {};     /* DOM */
  var current = null;
  var tickTimer = null;
  var lastInput = Date.now();

  P.load = function () {
    var s = Z.store.get('phone', null) || {};
    st = {};
    for (var k in DEF) st[k] = (k in s) ? s[k] : JSON.parse(JSON.stringify(DEF[k]));
    P.state = {
      airplane: st.airplane, wifi: st.wifi, torch: st.torch, ringer: st.ringer, volume: st.volume,
      level: 100, charging: false, realBattery: false, dim: false
    };
    P.st = st;
    return st;
  };
  function save() {
    st.airplane = P.state.airplane; st.wifi = P.state.wifi; st.torch = P.state.torch;
    st.ringer = P.state.ringer; st.volume = P.state.volume;
    Z.store.set('phone', st);
  }
  P.save = save;

  /* ---------------- 出来事（events.jsonl と z2-when へ） ---------------- */
  P.event = function (name, extra) {
    var o = { ts: new Date().toISOString(), type: name };
    if (extra) for (var k in extra) o[k] = extra[k];
    try {
      var path = '/root/.z2term/events.jsonl';
      var cur = Z.fs.stat(path) ? Z.fs.readFile(path) : '';
      if (cur.length > 64 * 1024) cur = cur.slice(cur.indexOf('\n', cur.length - 32 * 1024) + 1);
      Z.fs.writeFile(path, cur + JSON.stringify(o) + '\n');
    } catch (e) { /* 書けなくても続ける */ }
    Z.bus.emit('phone-event', { name: name, data: o });
  };

  P.set = function (k, v) {
    var s = P.state;
    var old = s[k];
    if (old === v) { render(); return; }
    s[k] = v;
    if (k === 'airplane') {
      P.event(v ? 'airplane_on' : 'airplane_off');
      if (s.wifi) P.event(v ? 'wifi_disconnect' : 'wifi_connect');
    }
    if (k === 'wifi' && !s.airplane) P.event(v ? 'wifi_connect' : 'wifi_disconnect');
    if (k === 'torch') {
      P.event(v ? 'torch_on' : 'torch_off');
      if (v) flash();
    }
    if (k === 'ringer') P.event('ringer_' + v);
    save();
    render();
    Z.bus.emit('state', { key: k, value: v });
  };

  function flash() {
    if (!els.flash) return;
    els.flash.classList.remove('on');
    void els.flash.offsetWidth;
    els.flash.classList.add('on');
  }

  /* ---------------- 電池（あれば本物） ---------------- */
  function battery() {
    var nav = G.navigator;
    if (!nav || !nav.getBattery) return;
    nav.getBattery().then(function (b) {
      function upd(first) {
        var was = P.state.charging, wasLevel = P.state.level;
        P.state.level = Math.round(b.level * 100);
        P.state.charging = !!b.charging;
        P.state.realBattery = true;
        if (!first && was !== P.state.charging) P.event(P.state.charging ? 'charge_start' : 'charge_stop');
        if (!first && wasLevel !== P.state.level) Z.bus.emit('battery', { level: P.state.level, was: wasLevel });
        if (P.state.level < 20 && !P.state.charging && !st.lowWarned) { st.lowWarned = true; save(); P.toast(T('ph.lowbat')); }
        if (P.state.level >= 30) st.lowWarned = false;
        render();
      }
      upd(true);
      b.addEventListener('chargingchange', function () { upd(false); });
      b.addEventListener('levelchange', function () { upd(false); });
    }, function () { /* 使えない */ });
  }

  /* ---------------- 組み立て ---------------- */
  P.build = function (root) {
    els.root = root;
    els.status = h('div.z2p-status', { role: 'button', tabindex: '0', 'aria-label': T('ph.shade') },
      els.time = h('span.z2p-st-time'),
      els.stNotif = h('span.z2p-st-notif'),
      h('span.z2p-st-gap'),
      els.stIcons = h('span.z2p-st-icons'),
      els.batt = h('span.z2p-st-batt'));
    els.apps = h('div.z2p-apps');
    els.nav = h('div.z2p-nav', { role: 'toolbar', 'aria-label': T('ph.nav') },
      h('button.z2p-nav-b', { type: 'button', 'aria-label': T('ph.back'), 'data-id': 'nav_back', text: '◁', on: { click: function () { P.back(); } } }),
      h('button.z2p-nav-b', { type: 'button', 'aria-label': T('ph.home'), 'data-id': 'nav_home', text: '○', on: { click: function () { P.home(); } } }),
      h('button.z2p-nav-b', { type: 'button', 'aria-label': T('ph.recents'), 'data-id': 'nav_recents', text: '▢', on: { click: function () { P.recents(); } } }));
    els.heads = h('div.z2p-heads', { 'aria-live': 'polite' });
    els.shade = buildShade();
    els.recents = h('div.z2p-recents', { hidden: true });
    els.toasts = h('div.z2p-toasts', { 'aria-live': 'polite' });
    els.dim = h('div.z2p-dim', { hidden: true, on: { pointerdown: wake } });
    els.flash = h('div.z2p-flash');
    els.trail = document.createElementNS('http://www.w3.org/2000/svg', 'svg');
    els.trail.setAttribute('class', 'z2p-trail');
    els.trail.setAttribute('aria-hidden', 'true');
    els.stop = h('div.z2p-stop', { hidden: true });
    els.modal = h('div.z2p-modal', { hidden: true });
    els.screen = h('div.z2p-screen', null, els.status, els.apps, els.nav, els.heads, els.shade, els.recents,
      els.toasts, els.stop, els.trail, els.modal, els.flash, els.dim);
    root.appendChild(els.screen);

    els.status.addEventListener('click', function () { P.shade(true); });
    els.status.addEventListener('keydown', function (e) { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); P.shade(true); } });
    pullDown(els.status);

    Z.APPS.forEach(function (a) {
      a.el = h('section.z2p-app', { 'data-app': a.id, hidden: true, 'aria-label': a.name() });
      els.apps.appendChild(a.el);
      a.build(a.el);
    });
    if (Z.edge) Z.edge.build(els.screen);
    battery();
    render();
    tick();
    if (tickTimer) clearInterval(tickTimer);
    tickTimer = setInterval(tick, 1000);
    els.screen.addEventListener('pointerdown', ripple);
    ['pointerdown', 'keydown', 'wheel'].forEach(function (ev) { root.addEventListener(ev, function () { if (P.state.dim) wake(); lastInput = Date.now(); }, true); });
    var start = 'home';
    if (st.recents.length && appById[st.recents[0]]) start = st.recents[0];
    P.open(start, null, true);
  };
  P.screen = function () { return els.screen; };
  P.els = els;

  P.destroy = function () {
    if (tickTimer) clearInterval(tickTimer);
    tickTimer = null;
    Z.APPS.forEach(function (a) { if (a.destroy) a.destroy(); });
  };

  function pullDown(el) {
    var y0 = null;
    el.addEventListener('pointerdown', function (e) { y0 = e.clientY; });
    G.addEventListener('pointerup', function (e) { if (y0 != null && e.clientY - y0 > 40) P.shade(true); y0 = null; });
  }

  /* ---------------- 毎秒 ---------------- */
  function tick() {
    var d = new Date();
    if (els.time) els.time.textContent = Z.fmtTime(d);
    Z.bus.emit('tick', d);
    /* アラーム */
    var due = st.alarms.filter(function (a) { return a.at <= d.getTime(); });
    if (due.length) {
      st.alarms = st.alarms.filter(function (a) { return a.at > d.getTime(); });
      save();
      due.forEach(function (a) {
        P.event('alarm', { name: a.name });
        P.notify({ title: '⏰ ' + a.name, body: T('ph.alarm'), app: 'z2term', heads: true });
      });
    }
    /* 消灯の演出（🔅 が OFF で 90 秒触らないと暗くなる） */
    var keep = (Z.zt && Z.zt.keepAwake && current === 'z2term') || (P.keepOnUntil && P.keepOnUntil > Date.now());
    if (!P.state.dim && !keep && Date.now() - lastInput > 90000 && Z.isOpen && Z.isOpen()) {
      P.state.dim = true;
      els.dim.hidden = false;
      P.event('screen_off');
    }
  }
  function wake() {
    if (!P.state.dim) return;
    P.state.dim = false;
    els.dim.hidden = true;
    lastInput = Date.now();
    P.event('screen_on');
  }
  P.wake = wake;

  /* ---------------- 表示の更新 ---------------- */
  function render() {
    if (!els.root) return;
    var s = P.state;
    var icons = [];
    if (s.torch) icons.push('🔦');
    if (s.ringer === 'vibrate') icons.push('📳');
    if (s.ringer === 'silent') icons.push('🔕');
    if (s.airplane) icons.push('✈');
    else {
      if (s.wifi) icons.push('Wi‑Fi');
      icons.push(T('ph.nosim'));
    }
    els.stIcons.textContent = icons.join('  ');
    els.batt.textContent = (s.charging ? '⚡' : '') + Math.round(s.level) + '%';
    els.batt.classList.toggle('low', s.level < 20 && !s.charging);
    var n = st.notifs.length;
    els.stNotif.textContent = n ? '●' + (n > 1 ? n : '') : '';
    renderShade();
    Z.bus.emit('phone-render');
  }
  P.render = render;

  /* ---------------- アプリの切り替え ---------------- */
  P.current = function () { return current; };
  P.open = function (id, arg, quiet) {
    var a = appById[id];
    if (!a) return false;
    P.shade(false);
    if (!(arg && arg.keepRecents)) els.recents.hidden = true;
    finishAll();
    var prev = current && current !== id ? appById[current] : null;
    if (prev && prev.hide) prev.hide();
    var was = current;
    current = id;
    a.el.hidden = false;
    if (prev) transition(prev, a, quiet);
    els.screen.setAttribute('data-current', id);
    if (id !== 'home') {
      st.recents = [id].concat(st.recents.filter(function (x) { return x !== id; })).slice(0, 8);
      save();
    }
    if (a.show) a.show(arg, was);
    Z.bus.emit('app', { id: id, from: was });
    return true;
  };
  P.home = function () { P.open('home'); };

  /* アプリの切り替えの動き:
     ホーム → アプリ: 押したアイコンの位置から広がる
     アプリ → ホーム: アプリが自分のアイコンへ縮み、ホームが少し奥から戻る
     アプリ → アプリ: 新しい画面が右から重なって入る */
  function transition(prev, next, quiet) {
    var pe = prev.el, ne = next.el;
    function hidePrev() { pe.hidden = true; pe.style.zIndex = ''; ne.style.zIndex = ''; }
    if (quiet || Z.reduceMotion()) { hidePrev(); return; }
    var homeApp = appById.home;
    if (prev.id === 'home') {
      var r = homeApp.iconRect && homeApp.iconRect(next.id);
      ne.style.zIndex = 2;
      ne.style.transformOrigin = originIn(ne, r);
      anim(ne, [{ transform: 'scale(0.12)', opacity: 0, borderRadius: '40px' }, { transform: 'scale(1)', opacity: 1, borderRadius: '0px' }], 320, function () { ne.style.transformOrigin = ''; });
      anim(pe, [{ transform: 'scale(1)', opacity: 1 }, { transform: 'scale(0.94)', opacity: 0.4 }], 320, hidePrev);
    } else if (next.id === 'home') {
      var r2 = homeApp.iconRect && homeApp.iconRect(prev.id);
      pe.style.zIndex = 2;
      pe.style.transformOrigin = originIn(pe, r2);
      anim(ne, [{ transform: 'scale(0.94)', opacity: 0.4 }, { transform: 'scale(1)', opacity: 1 }], 300);
      anim(pe, [{ transform: 'scale(1)', opacity: 1, borderRadius: '0px' }, { transform: 'scale(0.12)', opacity: 0, borderRadius: '40px' }], 300, function () { pe.style.transformOrigin = ''; hidePrev(); });
    } else {
      ne.style.zIndex = 2;
      anim(ne, [{ transform: 'translateX(22%)', opacity: 0 }, { transform: 'translateX(0)', opacity: 1 }], 280);
      anim(pe, [{ transform: 'translateX(0)', opacity: 1 }, { transform: 'translateX(-12%)', opacity: 0.5 }], 280, hidePrev);
    }
  }

  /* 押したところに波紋（ボタン・タイル・カード） */
  function ripple(e) {
    if (Z.reduceMotion()) return;
    var t = e.target.closest && e.target.closest('button, .z2p-rec-card, .z2p-status');
    if (!t || !els.screen.contains(t) || t.disabled) return;
    if (t.classList.contains('z2p-appicon')) t = t.querySelector('.z2p-icon') || t;
    var r = t.getBoundingClientRect();
    var size = Math.max(r.width, r.height) * 2.2;
    t.classList.add('z2p-rip-host');
    var sp = h('span.z2p-ripple', { style: { width: size + 'px', height: size + 'px', left: (e.clientX - r.left - size / 2) + 'px', top: (e.clientY - r.top - size / 2) + 'px' } });
    t.appendChild(sp);
    setTimeout(function () { sp.remove(); }, 600);
  }
  P.back = function () {
    if (!els.modal.hidden && P._modalBack) { P._modalBack(); return; }
    if (els.shade.classList.contains('open')) { P.shade(false); return; }
    if (Z.edge && Z.edge.isOpen()) { Z.edge.close(); return; }
    if (!els.recents.hidden) { closeRecents(); return; }
    var a = appById[current];
    if (a && a.back && a.back()) return;
    if (current !== 'home') P.home();
  };
  P.key = function (k) {
    if (k === 'back') P.back();
    else if (k === 'home') P.home();
    else if (k === 'recents') P.recents();
    else if (k === 'notifications' || k === 'quick_settings') P.shade(true);
    else if (k === 'lock') { P.state.dim = true; els.dim.hidden = false; P.event('screen_off'); }
  };

  /* ---------------- 最近 ---------------- */
  /* 最近: アプリの画面を縮めたカードを横に並べる。上へ払う（または ✕）で閉じる。 */
  P.recents = function () {
    if (!els.recents.hidden) { closeRecents(); return; }
    P.shade(false);
    var box = els.recents;
    box.textContent = '';
    var wrap = h('div.z2p-rec-list');
    var foot = h('div.z2p-rec-foot', null,
      h('button.z2p-rec-clear', { type: 'button', text: T('ph.clearall'), on: { click: function () {
        st.recents.filter(function (id) { return appById[id]; }).forEach(function (id) { closeApp(id); });
        renderRecents();
      } } }));
    box.appendChild(wrap);
    box.appendChild(foot);
    box.addEventListener('click', function (e) { if (e.target === box || e.target === wrap) closeRecents(); });
    function renderRecents() {
      wrap.textContent = '';
      var l = st.recents.filter(function (id) { return appById[id]; });
      foot.hidden = !l.length;
      if (!l.length) wrap.appendChild(h('p.z2p-empty', { text: T('ph.norecent') }));
      l.forEach(function (id) { wrap.appendChild(card(id, renderRecents)); });
    }
    renderRecents();
    box.hidden = false;
    anim(box, [{ opacity: 0 }, { opacity: 1 }], 200);
    Array.prototype.forEach.call(wrap.children, function (c, i) {
      anim(c, [{ transform: 'translateY(40px) scale(0.92)', opacity: 0 }, { transform: 'none', opacity: 1 }], 300 + i * 40);
    });
  };
  function closeRecents() {
    var box = els.recents;
    anim(box, [{ opacity: 1 }, { opacity: 0 }], 160, function () { box.hidden = true; box.textContent = ''; });
    if (Z.reduceMotion()) { box.hidden = true; box.textContent = ''; }
  }
  function card(id, redraw) {
    var a = appById[id];
    var prevBox = h('div.z2p-rec-prev');
    var c = h('div.z2p-rec-card', { role: 'button', tabindex: '0', 'aria-label': a.name(), 'data-id': 'recent_' + id },
      h('div.z2p-rec-head', null,
        Z.iconEl(id, true),
        h('span.z2p-rec-name', { text: a.name() }),
        id === 'z2term' && Z.zt && Z.zt.persistent ? h('span.z2p-rec-pin', { text: '🔒', title: T('zt.lock') }) : null,
        h('button.z2p-rec-x', { type: 'button', 'aria-label': T('ph.closeapp', a.name()), text: '✕', on: { click: function (e) { e.stopPropagation(); flingUp(c, function () { closeApp(id); redraw(); }); } } })),
      prevBox);
    /* 画面の写し（操作はできない縮小表示） */
    var cl = a.el.cloneNode(true);
    cl.removeAttribute('hidden');
    cl.setAttribute('inert', '');
    cl.setAttribute('aria-hidden', 'true');
    cl.classList.add('z2p-rec-clone');
    Array.prototype.forEach.call(cl.querySelectorAll('[id]'), function (x) { x.removeAttribute('id'); });
    var sr = els.apps.getBoundingClientRect();
    cl.style.width = sr.width + 'px';
    cl.style.height = sr.height + 'px';
    prevBox.appendChild(cl);
    requestAnimationFrame(function () {
      /* カードの幅に合わせて縮め、高さも画面と同じ比率にする */
      var k = prevBox.clientWidth / sr.width;
      cl.style.transform = 'scale(' + k + ')';
      var maxH = els.recents.clientHeight * 0.74;
      prevBox.style.height = Math.min(sr.height * k, maxH) + 'px';
      var src = a.el.querySelectorAll('*'), dst = cl.querySelectorAll('*');
      for (var i = 0; i < src.length && i < dst.length; i++) if (src[i].scrollTop) dst[i].scrollTop = src[i].scrollTop;
    });
    c.addEventListener('click', function () { els.recents.hidden = true; els.recents.textContent = ''; P.open(id); });
    c.addEventListener('keydown', function (e) { if (e.key === 'Enter') { e.preventDefault(); c.click(); } if (e.key === 'Delete') flingUp(c, function () { closeApp(id); redraw(); }); });
    swipeUp(c, function () { closeApp(id); redraw(); });
    return c;
  }
  function flingUp(el, fn) {
    if (Z.reduceMotion()) { fn(); return; }
    anim(el, [{ transform: el.style.transform || 'none', opacity: 1 }, { transform: 'translateY(-120%)', opacity: 0 }], 220, fn);
    el.style.transform = 'translateY(-120%)';
  }
  /* カードを上へ払うと閉じる（横の動きはカードの並びのスクロールに任せる） */
  function swipeUp(el, fn) {
    var y0 = null, x0 = 0, dy = 0, on = false;
    el.addEventListener('pointerdown', function (e) { y0 = e.clientY; x0 = e.clientX; dy = 0; on = false; });
    el.addEventListener('pointermove', function (e) {
      if (y0 == null) return;
      dy = e.clientY - y0;
      if (!on && Math.abs(dy) > 10 && Math.abs(dy) > Math.abs(e.clientX - x0)) { on = true; try { el.setPointerCapture(e.pointerId); } catch (er) { /* 無視 */ } }
      if (on) { el.style.transform = 'translateY(' + Math.min(0, dy) + 'px)'; el.style.opacity = String(Math.max(0.2, 1 + Math.min(0, dy) / 300)); }
    });
    function end() {
      if (y0 == null) return;
      y0 = null;
      if (!on) return;
      Z.eatClick(el);
      el.style.opacity = '';
      if (dy < -90) { flingUp(el, fn); return; }
      var back = Math.min(0, dy);
      el.style.transform = '';
      anim(el, [{ transform: 'translateY(' + back + 'px)' }, { transform: 'translateY(0)' }], 200);
    }
    el.addEventListener('pointerup', end);
    el.addEventListener('pointercancel', end);
  }
  function closeApp(id) {
    if (id === 'z2term' && Z.zt) {
      if (Z.zt.persistent) { P.toast(T('ph.camebak')); return; }
      Z.zt.killAll();
      P.toast(T('ph.z2killed'));
    }
    var a = appById[id];
    if (a && a.reset) a.reset();
    st.recents = st.recents.filter(function (x) { return x !== id; });
    save();
    if (current === id) P.open('home', { keepRecents: !els.recents.hidden }, !els.recents.hidden);
  }

  /* ---------------- 通知欄・クイック設定 ---------------- */
  function buildShade() {
    var sh = h('div.z2p-shade', { role: 'dialog', 'aria-label': T('ph.shade') });
    var top = h('div.z2p-shade-top', null,
      els.shTime = h('span.z2p-shade-time'),
      els.shDate = h('span.z2p-shade-date'),
      h('button.z2p-shade-x', { type: 'button', 'aria-label': T('ph.closeshade'), text: '︿', on: { click: function () { P.shade(false); } } }));
    els.tiles = h('div.z2p-tiles');
    els.nlist = h('div.z2p-nlist');
    els.nclear = h('button.z2p-link', { type: 'button', text: T('ph.clearall'), on: { click: function () { st.notifs = []; save(); render(); } } });
    sh.appendChild(top);
    sh.appendChild(els.tiles);
    sh.appendChild(h('div.z2p-nhead', null, h('span', { text: T('ph.notifs') }), els.nclear));
    sh.appendChild(els.nlist);
    var y0 = null;
    sh.addEventListener('pointerdown', function (e) { if (e.target === sh) y0 = e.clientY; });
    sh.addEventListener('pointerup', function (e) { if (y0 != null && y0 - e.clientY > 40) P.shade(false); y0 = null; });
    return sh;
  }
  P.shade = function (open) {
    if (!els.shade) return;
    if (open) {
      var d = new Date();
      els.shTime.textContent = Z.fmtTime(d);
      els.shDate.textContent = Z.fmtDate(d, { month: 'long', day: 'numeric', weekday: 'long' });
      renderShade();
    }
    els.shade.classList.toggle('open', !!open);
    els.heads.hidden = !!open;
    if (open) Z.bus.emit('shade', true);
  };
  P.shadeOpen = function () { return els.shade && els.shade.classList.contains('open'); };

  function baseTiles() {
    var s = P.state;
    return [
      { id: 'torch', label: T('ph.t.torch'), icon: '🔦', on: s.torch, act: function () { P.set('torch', !s.torch); } },
      { id: 'wifi', label: 'Wi‑Fi', icon: '📶', on: s.wifi && !s.airplane, sub: s.airplane ? T('ph.t.off') : (s.wifi ? 'z2-world' : T('ph.t.off')), act: function () { if (s.airplane) { P.toast(T('ph.t.airfirst')); return; } P.set('wifi', !s.wifi); } },
      { id: 'airplane', label: T('ph.t.air'), icon: '✈', on: s.airplane, act: function () { P.set('airplane', !s.airplane); } },
      { id: 'ringer', label: T('ph.t.ringer.' + s.ringer), icon: s.ringer === 'normal' ? '🔔' : s.ringer === 'vibrate' ? '📳' : '🔕', on: s.ringer !== 'normal', act: function () { P.set('ringer', s.ringer === 'normal' ? 'vibrate' : s.ringer === 'vibrate' ? 'silent' : 'normal'); } }
    ];
  }
  var tileRunning = {};
  function renderShade() {
    if (!els.tiles) return;
    els.tiles.textContent = '';
    var list = baseTiles();
    for (var i = 1; i <= 12; i++) {
      (function (slot) {
        var t = st.tiles[slot];
        if (!t) return;
        list.push({ id: 'z2tile' + slot, label: t.label, icon: '▶', on: !!tileRunning[slot], sub: 'z2-tile ' + slot, act: function () { runTile(slot, t); } });
      })(i);
    }
    list.forEach(function (t) {
      els.tiles.appendChild(h('button.z2p-tile', { type: 'button', 'aria-pressed': t.on ? 'true' : 'false', 'data-id': 'tile_' + t.id, on: { click: t.act } },
        h('span.z2p-tile-i', { text: t.icon, 'aria-hidden': 'true' }),
        h('span.z2p-tile-l', { text: t.label }),
        t.sub ? h('span.z2p-tile-s', { text: t.sub }) : null));
    });
    els.nlist.textContent = '';
    if (!st.notifs.length) els.nlist.appendChild(h('p.z2p-empty', { text: T('ph.nonotif') }));
    st.notifs.forEach(function (n) { els.nlist.appendChild(notifEl(n, false)); });
    els.nclear.hidden = !st.notifs.length;
  }
  function runTile(slot, t) {
    if (tileRunning[slot]) { tileRunning[slot].aborted = true; return; }
    var sig = { aborted: false };
    tileRunning[slot] = sig;
    renderShade();
    Z.bus.emit('tile-run', slot);
    Z.runBackground(t.cmd, sig, 'tile ' + slot).then(function () {
      delete tileRunning[slot];
      renderShade();
    });
  }
  P.tiles = function () { return st.tiles; };
  P.setTile = function (slot, t) { if (t) st.tiles[slot] = t; else delete st.tiles[slot]; save(); render(); };
  P.setWidget = function (w) { st.widget = w; save(); Z.bus.emit('widget'); };
  P.widget = function () { return st.widget; };
  P.alarms = function () { return st.alarms; };
  P.addAlarm = function (name, at) { st.alarms = st.alarms.filter(function (a) { return a.name !== name; }); st.alarms.push({ name: name, at: at }); save(); };
  P.cancelAlarm = function (name) { st.alarms = name === 'all' ? [] : st.alarms.filter(function (a) { return a.name !== name; }); save(); };

  /* ---------------- 通知 ---------------- */
  function notifEl(n, heads) {
    var el = h('div.z2p-notif', { 'data-nid': n.id },
      h('div.z2p-notif-h', null,
        h('span.z2p-notif-app', { text: (n.app === 'z2term' ? 'z2term' : n.app) + ' · ' + Z.fmtTime(new Date(n.time)) }),
        h('button.z2p-notif-x', { type: 'button', 'aria-label': T('ph.dismiss'), text: '✕', on: { click: function (e) { e.stopPropagation(); P.dismiss(n.id); if (heads) el.remove(); } } })),
      h('div.z2p-notif-t', { text: n.title }),
      n.body ? h('div.z2p-notif-b', { text: n.body }) : null);
    var acts = h('div.z2p-notif-acts');
    if (n.copy != null) acts.appendChild(h('button.z2p-act', { type: 'button', text: T('ph.copy'), on: { click: function () { P.clip.set(n.copy); P.toast(T('ph.copied')); } } }));
    (n.buttons || []).forEach(function (b) {
      acts.appendChild(h('button.z2p-act', { type: 'button', text: b, on: { click: function () {
        P.event('notify_action', { label: b, title: n.title, name: n.name || null });
        P.dismiss(n.id);
        if (heads) el.remove();
        P.toast(T('ph.sent', b));
      } } }));
    });
    if (acts.childNodes.length) el.appendChild(acts);
    return el;
  }
  P.notify = function (o) {
    var n = { id: st.nid++, app: o.app || 'z2term', title: String(o.title || ''), body: String(o.body || ''), copy: o.copy, buttons: o.buttons || [], name: o.name || null, time: Date.now() };
    if (n.name) st.notifs = st.notifs.filter(function (x) { return x.name !== n.name; });
    st.notifs.unshift(n);
    if (st.notifs.length > 50) st.notifs.length = 50;
    save();
    render();
    /* 通知は毎回、画面上部にしばらくポップアップで出す（-h はバナーとして長めに残す） */
    if (!P.shadeOpen()) heads(n, o.heads ? 8000 : 4000);
    pulse();
    Z.bus.emit('notify', n);
    return n.id;
  };
  P.notifs = function () { return st.notifs; };
  P.dismiss = function (id) { st.notifs = st.notifs.filter(function (x) { return x.id !== id; }); save(); render(); };
  function heads(n, ms) {
    var el = notifEl(n, true);
    el.classList.add('heads');
    /* 一度に出すのは 3 件まで（古いものから下げる） */
    while (els.heads.querySelectorAll('.z2p-notif.heads:not(.ask)').length >= 3) {
      var old = els.heads.querySelector('.z2p-notif.heads:not(.ask)');
      if (!old) break;
      old.remove();
    }
    els.heads.appendChild(el);
    var gone = false;
    function hide() {
      if (gone) return;
      gone = true;
      el.classList.add('out');
      setTimeout(function () { el.remove(); }, 300);
    }
    setTimeout(hide, ms || 4000);
    /* 上へ払うとすぐ消える（通知欄には残る）。押すと通知欄を開く */
    var y0 = null, moved = false;
    el.addEventListener('pointerdown', function (e) { if (e.target.closest('button')) return; y0 = e.clientY; moved = false; });
    el.addEventListener('pointermove', function (e) {
      if (y0 == null) return;
      var dy = e.clientY - y0;
      if (dy < -6) { moved = true; el.style.transform = 'translateY(' + dy + 'px)'; el.style.opacity = String(Math.max(0.2, 1 + dy / 120)); }
    });
    el.addEventListener('pointerup', function (e) {
      if (y0 == null) return;
      var dy = e.clientY - y0;
      y0 = null;
      if (moved && dy < -40) { hide(); return; }
      el.style.transform = ''; el.style.opacity = '';
      if (!moved) { hide(); P.shade(true); }
    });
  }
  function pulse() { els.stNotif.classList.remove('pulse'); void els.stNotif.offsetWidth; els.stNotif.classList.add('pulse'); }

  P.toast = function (text) {
    if (!els.toasts) return;
    var t = h('div.z2p-toast', { text: text });
    els.toasts.appendChild(t);
    setTimeout(function () { t.classList.add('out'); setTimeout(function () { t.remove(); }, 300); }, 2600);
  };

  P.vibrate = function (ms) {
    var f = els.root && els.root.closest('.z2p-frame');
    if (!f || P.state.ringer === 'silent') return;
    f.classList.remove('shake'); void f.offsetWidth;
    f.style.setProperty('--shake', Math.min(2000, ms || 200) + 'ms');
    f.classList.add('shake');
  };

  P.say = function (text, sig) {
    P.notify({ title: T('ph.said'), body: text, app: 'z2term', name: 'z2-say' });
    var ss = G.speechSynthesis;
    if (!ss || !G.SpeechSynthesisUtterance) { P.toast('🔊 ' + text); return Promise.resolve(0); }
    return new Promise(function (ok) {
      var u = new SpeechSynthesisUtterance(text);
      u.lang = /[぀-ヿ一-鿿]/.test(text) ? 'ja-JP' : 'en-US';
      u.volume = P.state.volume / 15;
      var iv = setInterval(function () { if (sig && sig.aborted) { ss.cancel(); } }, 100);
      u.onend = u.onerror = function () { clearInterval(iv); ok(0); };
      ss.speak(u);
      setTimeout(function () { clearInterval(iv); ok(0); }, 30000);
    });
  };

  /* ---------------- クリップボード ---------------- */
  P.clip = {
    get: function () { return st.clip.cur || ''; },
    set: function (s) {
      s = String(s);
      st.clip.cur = s;
      st.clip.hist = [s].concat(st.clip.hist.filter(function (x) { return x !== s; })).slice(0, 20);
      save();
      try { if (G.navigator.clipboard && G.navigator.clipboard.writeText) G.navigator.clipboard.writeText(s).catch(function () {}); } catch (e) { /* 無視 */ }
      Z.bus.emit('clip', s);
    },
    hist: function () { return st.clip.hist; }
  };

  /* ---------------- ダイアログ ---------------- */
  function openModal(node, onBack) {
    els.modal.textContent = '';
    els.modal.appendChild(node);
    els.modal.hidden = false;
    P._modalBack = onBack;
    var f = node.querySelector('[autofocus], input, textarea, button');
    if (f) setTimeout(function () { f.focus(); }, 30);
  }
  function closeModal() { els.modal.hidden = true; els.modal.textContent = ''; P._modalBack = null; Z.bus.emit('modal-closed'); }
  P.closeModal = closeModal;
  P.modalOpen = function () { return els.modal && !els.modal.hidden; };

  P.confirm = function (text, okLabel) {
    return new Promise(function (ok) {
      var box = h('div.z2p-dialog', { role: 'alertdialog', 'aria-label': text },
        h('p', { text: text }),
        h('div.z2p-dialog-b', null,
          h('button.z2p-btn.ghost', { type: 'button', text: T('ph.cancel'), on: { click: function () { closeModal(); ok(false); } } }),
          h('button.z2p-btn', { type: 'button', text: okLabel || 'OK', on: { click: function () { closeModal(); ok(true); } } })));
      openModal(box, function () { closeModal(); ok(false); });
    });
  };
  P.alert = function (title, text) {
    var box = h('div.z2p-dialog', { role: 'alertdialog', 'aria-label': title },
      h('p.z2p-dialog-t', { text: title }),
      text ? h('p', { text: text }) : null,
      h('div.z2p-dialog-b', null, h('button.z2p-btn', { type: 'button', text: 'OK', on: { click: closeModal } })));
    openModal(box, closeModal);
  };
  P.prompt = function (title, value) {
    return new Promise(function (ok) {
      var inp = h('input.z2p-input', { type: 'text', value: value || '', 'aria-label': title });
      function done(v) { closeModal(); ok(v); }
      inp.addEventListener('keydown', function (e) { if (e.key === 'Enter') { e.preventDefault(); done(inp.value); } e.stopPropagation(); });
      var box = h('div.z2p-dialog', { role: 'dialog', 'aria-label': title },
        h('p.z2p-dialog-t', { text: title }), inp,
        h('div.z2p-dialog-b', null,
          h('button.z2p-btn.ghost', { type: 'button', text: T('ph.cancel'), on: { click: function () { done(null); } } }),
          h('button.z2p-btn', { type: 'button', text: 'OK', on: { click: function () { done(inp.value); } } })));
      openModal(box, function () { done(null); });
    });
  };
  /* 小さなメニュー。items: [{label, act, disabled}] */
  P.menu = function (title, items) {
    var list = h('div.z2p-menu', { role: 'menu', 'aria-label': title || '' });
    if (title) list.appendChild(h('p.z2p-menu-t', { text: title }));
    items.forEach(function (it) {
      if (!it) return;
      list.appendChild(h('button.z2p-menu-i', { type: 'button', role: 'menuitem', disabled: !!it.disabled, text: it.label, on: { click: function () { closeModal(); it.act(); } } }));
    });
    list.appendChild(h('button.z2p-menu-i.dim', { type: 'button', text: T('ph.cancel'), on: { click: closeModal } }));
    openModal(list, closeModal);
  };

  /* 共有メニュー（受け取れるのはクリップボードだけ） */
  P.share = function (text) {
    var box = h('div.z2p-sheet', { role: 'dialog', 'aria-label': T('ph.share') },
      h('p.z2p-dialog-t', { text: T('ph.share') }),
      h('p.z2p-share-txt', { text: text }),
      h('div.z2p-share-row', null,
        h('button.z2p-share-i', { type: 'button', on: { click: function () { P.clip.set(text); closeModal(); P.toast(T('ph.copied')); } } }, h('span', { text: '📋' }), h('span', { text: T('ph.copy') })),
        h('button.z2p-share-i', { type: 'button', on: { click: function () { closeModal(); P.open('browser', { q: text }); } } }, h('span', { text: '🔍' }), h('span', { text: T('ph.searchit') }))),
      h('p.z2p-dim-note', { text: T('ph.shareonly') }));
    openModal(box, closeModal);
  };

  /* z2-ask: 返信欄つきの通知 */
  P.ask = function (q, def, sig) {
    return new Promise(function (ok) {
      var inp = h('input.z2p-input', { type: 'text', value: def || '', 'aria-label': q, placeholder: T('ph.reply') });
      var el;
      var iv = setInterval(function () { if (sig && sig.aborted) finish(null); }, 100);
      function finish(v) { clearInterval(iv); if (el) el.remove(); if (v != null) Z.bus.emit('ask-answered', v); ok(v); }
      el = h('div.z2p-notif.heads.ask', null,
        h('div.z2p-notif-h', null, h('span.z2p-notif-app', { text: 'z2term · z2-ask' }),
          h('button.z2p-notif-x', { type: 'button', 'aria-label': T('ph.dismiss'), text: '✕', on: { click: function () { finish(null); } } })),
        h('div.z2p-notif-t', { text: q }),
        h('form.z2p-ask-f', { on: { submit: function (e) { e.preventDefault(); finish(inp.value); } } },
          inp, h('button.z2p-act', { type: 'submit', text: T('ph.send') })));
      inp.addEventListener('keydown', function (e) { e.stopPropagation(); });
      els.heads.appendChild(el);
      setTimeout(function () { inp.focus(); }, 30);
    });
  };

  /* z2-edit: 編集画面 */
  P.edit = function (path, sig) {
    return new Promise(function (ok) {
      var text = '';
      try { text = Z.fs.stat(path) ? Z.fs.readFile(path) : ''; } catch (e) { text = ''; }
      var ta = h('textarea.z2p-edit-ta', { spellcheck: false, 'aria-label': Z.fs.pretty(path) });
      ta.value = text;
      var saved = false;
      var stateEl = h('span.z2p-edit-st');
      var iv = setInterval(function () { if (sig && sig.aborted) close(); }, 150);
      function doSave() {
        try { Z.fs.writeFile(path, ta.value); saved = true; stateEl.textContent = T('ph.savedok'); Z.bus.emit('edit-saved', path); }
        catch (e) { stateEl.textContent = Z.fsErr('z2-edit', e, path); }
      }
      function close() { clearInterval(iv); closeModal(); ok(saved); }
      ta.addEventListener('keydown', function (e) {
        e.stopPropagation();
        if ((e.ctrlKey || e.metaKey) && e.key === 's') { e.preventDefault(); doSave(); }
      });
      var box = h('div.z2p-edit', { role: 'dialog', 'aria-label': 'z2-edit' },
        h('div.z2p-edit-bar', null,
          h('span.z2p-edit-name', { text: Z.fs.pretty(path) }), stateEl,
          h('button.z2p-btn', { type: 'button', text: T('ph.save'), on: { click: doSave } }),
          h('button.z2p-btn.ghost', { type: 'button', text: T('ph.close'), on: { click: close } })),
        ta);
      openModal(box, close);
    });
  };

  /* ---------------- 指の軌跡（z2-action） ---------------- */
  P.trail = function (points, kind) {
    if (!st.trail || !els.trail) return;
    var r = els.screen.getBoundingClientRect();
    var svg = els.trail;
    var ns = 'http://www.w3.org/2000/svg';
    var g = document.createElementNS(ns, 'g');
    if (points.length === 1) {
      var c = document.createElementNS(ns, 'circle');
      c.setAttribute('cx', points[0][0] - r.left);
      c.setAttribute('cy', points[0][1] - r.top);
      c.setAttribute('r', kind === 'long' ? 22 : 16);
      g.appendChild(c);
    } else {
      var p = document.createElementNS(ns, 'polyline');
      p.setAttribute('points', points.map(function (q) { return (q[0] - r.left) + ',' + (q[1] - r.top); }).join(' '));
      g.appendChild(p);
      var e = document.createElementNS(ns, 'circle');
      var last = points[points.length - 1];
      e.setAttribute('cx', last[0] - r.left); e.setAttribute('cy', last[1] - r.top); e.setAttribute('r', 10);
      g.appendChild(e);
    }
    svg.appendChild(g);
    setTimeout(function () { g.classList.add('out'); setTimeout(function () { g.remove(); }, 700); }, 1600);
  };
  P.stopBar = function (info) {
    var b = els.stop;
    if (!b) return;
    if (!info) { b.hidden = true; b.textContent = ''; return; }
    b.textContent = '';
    b.appendChild(h('span.z2p-stop-n', { text: '▶ ' + info.name }));
    b.appendChild(h('span.z2p-stop-s', { text: info.step }));
    if (info.human != null) b.appendChild(h('button.z2p-stop-h', { type: 'button', 'aria-pressed': info.human ? 'true' : 'false', title: T('ph.human'), text: '～', on: { click: info.onHuman } }));
    b.appendChild(h('button.z2p-stop-x', { type: 'button', text: '■ ' + T('ph.stop'), on: { click: info.onStop } }));
    b.hidden = false;
  };

  /* マクロの指の印。次に押す場所までなめらかに動き、押している間は小さく沈む */
  P.finger = function (p, ms) {
    if (!els.screen) return;
    if (!els.finger) { els.finger = h('div.z2p-finger', { 'aria-hidden': 'true' }); els.screen.appendChild(els.finger); }
    var r = els.screen.getBoundingClientRect();
    var f = els.finger;
    var first = !f.classList.contains('on');
    f.style.transitionDuration = (first || !ms ? 0 : ms) + 'ms';
    f.style.transform = 'translate(' + (p[0] - r.left) + 'px, ' + (p[1] - r.top) + 'px)';
    f.classList.add('on');
  };
  P.fingerDown = function (down) { if (els.finger) els.finger.classList.toggle('down', !!down); };
  P.fingerHide = function () { if (els.finger) { els.finger.classList.remove('on', 'down'); } };
  P.isTrail = function () { return !!st.trail; };
  P.setTrail = function (v) { st.trail = !!v; save(); };
})(typeof window !== 'undefined' ? window : globalThis);
