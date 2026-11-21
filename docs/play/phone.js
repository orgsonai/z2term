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

  var DEF = {
    airplane: false, wifi: true, torch: false, ringer: 'normal', volume: 7,
    notifs: [], nid: 1, tiles: {}, widget: null, homeOrder: null,
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
    battery();
    render();
    tick();
    if (tickTimer) clearInterval(tickTimer);
    tickTimer = setInterval(tick, 1000);
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
    els.recents.hidden = true;
    if (current && current !== id) {
      var prev = appById[current];
      if (prev) { prev.el.hidden = true; if (prev.hide) prev.hide(); }
    }
    var was = current;
    current = id;
    a.el.hidden = false;
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
  P.back = function () {
    if (!els.modal.hidden && P._modalBack) { P._modalBack(); return; }
    if (els.shade.classList.contains('open')) { P.shade(false); return; }
    if (!els.recents.hidden) { els.recents.hidden = true; return; }
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
  P.recents = function () {
    if (!els.recents.hidden) { els.recents.hidden = true; return; }
    P.shade(false);
    var box = els.recents;
    box.textContent = '';
    var list = st.recents.filter(function (id) { return appById[id]; });
    var head = h('div.z2p-rec-head', null, h('span', { text: T('ph.recents') }),
      h('button.z2p-link', { type: 'button', text: T('ph.clearall'), on: { click: function () {
        list.slice().forEach(function (id) { closeApp(id); });
        renderRecents();
      } } }));
    box.appendChild(head);
    var wrap = h('div.z2p-rec-list');
    box.appendChild(wrap);
    function renderRecents() {
      wrap.textContent = '';
      var l = st.recents.filter(function (id) { return appById[id]; });
      if (!l.length) wrap.appendChild(h('p.z2p-empty', { text: T('ph.norecent') }));
      l.forEach(function (id) {
        var a = appById[id];
        var card = h('div.z2p-rec-card', { role: 'button', tabindex: '0', 'aria-label': a.name() },
          h('span.z2p-icon.sm', { 'data-app': id, text: a.icon }),
          h('span.z2p-rec-name', { text: a.name() }),
          id === 'z2term' && Z.zt && Z.zt.persistent ? h('span.z2p-rec-pin', { text: '🔒' }) : null,
          h('button.z2p-rec-x', { type: 'button', 'aria-label': T('ph.closeapp', a.name()), text: '✕', on: { click: function (e) { e.stopPropagation(); closeApp(id); renderRecents(); } } }));
        card.addEventListener('click', function () { P.open(id); });
        swipeAway(card, function () { closeApp(id); renderRecents(); });
        wrap.appendChild(card);
      });
    }
    renderRecents();
    box.hidden = false;
  };
  function swipeAway(el, fn) {
    var x0 = null, dx = 0;
    el.addEventListener('pointerdown', function (e) { x0 = e.clientX; dx = 0; });
    el.addEventListener('pointermove', function (e) { if (x0 == null) return; dx = e.clientX - x0; if (Math.abs(dx) > 8) el.style.transform = 'translateX(' + dx + 'px)'; });
    function end() {
      if (x0 == null) return;
      x0 = null;
      el.style.transform = '';
      if (Math.abs(dx) > 90) { dx = 0; fn(); }
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
    if (current === id) P.home();
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
    if (o.heads) heads(n);
    else if (P.state.ringer === 'normal') pulse();
    Z.bus.emit('notify', n);
    return n.id;
  };
  P.notifs = function () { return st.notifs; };
  P.dismiss = function (id) { st.notifs = st.notifs.filter(function (x) { return x.id !== id; }); save(); render(); };
  function heads(n) {
    var el = notifEl(n, true);
    el.classList.add('heads');
    els.heads.appendChild(el);
    setTimeout(function () { el.classList.add('out'); setTimeout(function () { el.remove(); }, 300); }, 5000);
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
      function finish(v) { clearInterval(iv); if (el) el.remove(); ok(v); }
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
        try { Z.fs.writeFile(path, ta.value); saved = true; stateEl.textContent = T('ph.savedok'); }
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
    setTimeout(function () { g.classList.add('out'); setTimeout(function () { g.remove(); }, 600); }, 700);
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

  P.isTrail = function () { return !!st.trail; };
  P.setTrail = function (v) { st.trail = !!v; save(); };
})(typeof window !== 'undefined' ? window : globalThis);
