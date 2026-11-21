/* z2term play — スマホのアプリ: ホーム・電話・カメラ・設定。
   z2term / ブラウザ / 手順は別ファイル。実在の製品の名前・見た目は写さない。 */
(function (G) {
  'use strict';
  var Z = G.Z2P = G.Z2P || {};
  var h = function () { return Z.h.apply(null, arguments); };
  var T = function () { return Z.t.apply(null, arguments); };

  /* ======================= ホーム ======================= */
  var home = { id: 'home', pkg: 'z2.mock.home', icon: '⌂', name: function () { return T('app.home'); } };
  var hm = {};
  /* 1 ページ目のアイコンと、画面下に固定のドック（どのページでも出る） */
  var GRID = ['camera', 'guide'];
  var DOCKAPPS = ['z2term', 'dialer', 'browser', 'settings'];   /* z2term はいちばん左 */
  var PAGES = 10;   /* 最後のページにだけ「hack」がいる（おまけ） */

  function weekStart() {
    try {
      var loc = new Intl.Locale(Z.locale());
      var wi = loc.getWeekInfo ? loc.getWeekInfo() : loc.weekInfo;
      if (wi && wi.firstDay) return wi.firstDay % 7;
    } catch (e) { /* 古いブラウザ */ }
    return /^(ja|en-US|en-CA|ko|zh-TW|he|pt-BR)/.test(Z.locale()) || Z.locale() === 'en' ? 0 : 1;
  }

  home.build = function (el) {
    hm.clock = h('div.z2p-w-clock', null, hm.time = h('div.z2p-w-time'), hm.date = h('div.z2p-w-date'));
    hm.cal = h('div.z2p-w-cal', { role: 'grid', 'aria-label': T('home.cal') });
    hm.tail = h('div.z2p-w-tail', { hidden: true });
    hm.grid = h('div.z2p-grid', { role: 'list' });
    hm.dock = h('div.z2p-dock', { role: 'list', 'aria-label': T('home.dock') });
    hm.jiggle = h('p.z2p-jiggle-note', { hidden: true });
    hm.pages = h('div.z2p-pages', { 'aria-label': T('home.pages') });
    hm.pages.appendChild(h('div.z2p-page', { 'aria-label': T('home.page', 1, PAGES) }, hm.clock, hm.cal, hm.tail, h('div.z2p-home-gap'), hm.grid));
    for (var i = 2; i < PAGES; i++) hm.pages.appendChild(h('div.z2p-page.empty', { 'aria-label': T('home.page', i, PAGES) }));
    hm.hackPage = h('div.z2p-page.z2p-hackpage', { 'aria-label': T('home.page', PAGES, PAGES) });
    hm.pages.appendChild(hm.hackPage);
    renderHackPage();
    Z.bus.on('gift', renderHackPage);
    hm.dots = h('div.z2p-dots');
    for (var j = 0; j < PAGES; j++) {
      (function (k) {
        hm.dots.appendChild(h('button.z2p-dot', { type: 'button', 'aria-label': T('home.page', k + 1, PAGES), on: { click: function () { goPage(k); } } }));
      })(j);
    }
    el.appendChild(h('div.z2p-home', null, hm.pages, hm.jiggle, hm.dots, hm.dock));
    hm.page = 0;
    hm.pages.addEventListener('scroll', function () {
      var w = hm.pages.clientWidth || 1;
      var p = Math.round(hm.pages.scrollLeft / w);
      if (p !== hm.page) { hm.page = p; renderDots(); }
    }, { passive: true });
    mouseSwipe(hm.pages);
    Z.bus.on('tick', function (d) {
      if (el.hidden) return;
      var t = Z.fmtTime(d);
      if (hm.time.textContent !== t) { hm.time.textContent = t; renderCal(); }
      renderTail();
    });
    Z.bus.on('widget', renderTail);
    renderIcons();
    renderDots();
  };
  home.show = function (arg, was) {
    var d = new Date();
    hm.time.textContent = Z.fmtTime(d);
    renderCal();
    renderTail();
    stopJiggle();
    if (was === 'home') goPage(0);
  };
  home.page = function () { return hm.page; };
  home.iconRect = function (id) {
    var b = hm.dock.querySelector('[data-id="icon_' + id + '"] .z2p-icon');
    if (!b && hm.page === 0) b = hm.grid.querySelector('[data-id="icon_' + id + '"] .z2p-icon');
    return b ? b.getBoundingClientRect() : null;
  };

  function renderHackPage() {
    var pg = hm.hackPage;
    pg.textContent = '';
    var b = h('button.z2p-appicon', { type: 'button', 'data-id': 'icon_hack', 'aria-label': 'hack' },
      h('span.z2p-icon.z2p-hack-icon', { 'aria-hidden': 'true', text: '#!' }),
      h('span.z2p-appicon-l', { text: 'hack' }));
    b.addEventListener('click', function () { Z.gift.launch(); });
    var wrap = h('div.z2p-hack-wrap', null, h('p.z2p-hack-whisper', { text: T('home.hackwhisper') }), b);
    if (Z.gift.unlocked()) {
      wrap.appendChild(h('button.z2p-gift-btn', { type: 'button', 'data-id': 'gift_open', text: T('gift.open'), on: { click: Z.gift.open } }));
    }
    pg.appendChild(wrap);
  }
  function renderDots() {
    Array.prototype.forEach.call(hm.dots.children, function (d, i) { d.setAttribute('aria-current', i === hm.page ? 'true' : 'false'); });
  }
  function goPage(k) {
    k = Math.max(0, Math.min(PAGES - 1, k));
    hm.pages.scrollTo({ left: k * hm.pages.clientWidth, behavior: Z.reduceMotion() ? 'auto' : 'smooth' });
  }
  home.goPage = goPage;

  /* PC のマウスでも横に払ってページを変えられるように（指はブラウザのスクロールに任せる） */
  function mouseSwipe(el) {
    var d = null;
    el.addEventListener('pointerdown', function (e) {
      if (e.pointerType !== 'mouse' || e.button !== 0) return;
      d = { x: e.clientX, left: el.scrollLeft, moved: false, id: e.pointerId };
    });
    el.addEventListener('pointermove', function (e) {
      if (!d) return;
      var dx = e.clientX - d.x;
      if (!d.moved && Math.abs(dx) < 6) return;
      if (!d.moved) { d.moved = true; el.classList.add('dragging'); try { el.setPointerCapture(d.id); } catch (er) { /* 無視 */ } }
      el.scrollLeft = d.left - dx;
    });
    function end(e) {
      if (!d) return;
      var dd = d; d = null;
      el.classList.remove('dragging');
      if (!dd.moved) return;
      var dx = e.clientX - dd.x, w = el.clientWidth;
      var base = Math.round(dd.left / w);
      goPage(Math.abs(dx) > w * 0.18 ? base + (dx < 0 ? 1 : -1) : base);
      Z.eatClick(el);
    }
    el.addEventListener('pointerup', end);
    el.addEventListener('pointercancel', end);
  }
  home.back = function () { if (hm.jiggling) { stopJiggle(); return true; } return true; };

  function renderCal() {
    var d = new Date();
    hm.date.textContent = Z.fmtDate(d, { year: 'numeric', month: 'long', day: 'numeric', weekday: 'long' });
    var y = d.getFullYear(), m = d.getMonth();
    var ws = weekStart();
    hm.cal.textContent = '';
    hm.cal.appendChild(h('div.z2p-cal-title', { text: Z.fmtDate(d, { year: 'numeric', month: 'long' }) }));
    var row = h('div.z2p-cal-row.head', { role: 'row' });
    for (var i = 0; i < 7; i++) {
      var wd = (ws + i) % 7;
      var ref = new Date(2023, 0, 1 + wd); /* 2023-01-01 は日曜 */
      row.appendChild(h('span', { role: 'columnheader', cls: wd === 0 ? 'sun' : wd === 6 ? 'sat' : '', text: Z.fmtDate(ref, { weekday: 'narrow' }) }));
    }
    hm.cal.appendChild(row);
    var first = new Date(y, m, 1);
    var off = (first.getDay() - ws + 7) % 7;
    var days = new Date(y, m + 1, 0).getDate();
    var cells = [];
    for (var b = 0; b < off; b++) cells.push(null);
    for (var k = 1; k <= days; k++) cells.push(k);
    while (cells.length % 7) cells.push(null);
    for (var r = 0; r < cells.length; r += 7) {
      var rr = h('div.z2p-cal-row', { role: 'row' });
      for (var c = 0; c < 7; c++) {
        var v = cells[r + c];
        var wd2 = (ws + c) % 7;
        rr.appendChild(h('span', { role: 'gridcell', cls: (v === d.getDate() ? 'today ' : '') + (wd2 === 0 ? 'sun' : wd2 === 6 ? 'sat' : ''), text: v ? String(v) : '', 'aria-current': v === d.getDate() ? 'date' : null }));
      }
      hm.cal.appendChild(rr);
    }
  }

  function renderTail() {
    var w = Z.phone.widget();
    if (!w || !w.file) { hm.tail.hidden = true; return; }
    hm.tail.hidden = false;
    var txt;
    try { txt = Z.fs.readFile(w.file); } catch (e) { txt = T('home.nofile'); }
    var lines = txt.replace(/\n$/, '').split('\n').slice(-6).join('\n');
    if (hm.tail._last === w.file + lines) return;
    hm.tail._last = w.file + lines;
    hm.tail.textContent = '';
    hm.tail.appendChild(h('div.z2p-w-tail-h', { text: 'tail · ' + Z.fs.pretty(w.file) }));
    hm.tail.appendChild(h('pre', { text: lines }));
  }

  function order(list, key) {
    var o = (Z.phone.st[key] || []).filter(function (id) { return list.indexOf(id) >= 0; });
    /* 前の版で保存した並び（z2term がドックに無かった）でも、z2term は左端に入れる */
    if (key === 'dockOrder' && o.length && o.indexOf('z2term') < 0) o.unshift('z2term');
    list.forEach(function (id) { if (o.indexOf(id) < 0) o.push(id); });
    return o;
  }
  function iconBtn(id, inDock) {
    var a = Z.appById(id);
    if (!a) return null;
    var b = h('button.z2p-appicon', { type: 'button', role: 'listitem', 'data-id': 'icon_' + id, 'aria-label': a.name() },
      Z.iconEl(id),
      inDock ? null : h('span.z2p-appicon-l', { text: a.name() }));
    b.addEventListener('click', function () {
      if (hm.jiggling) { pick(id, inDock); return; }
      Z.phone.open(id);
    });
    Z.longPress(b, function () { startJiggle(); });
    return b;
  }
  function renderIcons() {
    hm.grid.textContent = '';
    hm.dock.textContent = '';
    order(GRID, 'homeOrder').forEach(function (id) { var b = iconBtn(id, false); if (b) hm.grid.appendChild(b); });
    order(DOCKAPPS, 'dockOrder').forEach(function (id) { var b = iconBtn(id, true); if (b) hm.dock.appendChild(b); });
  }
  function startJiggle() {
    hm.jiggling = true; hm.picked = null;
    hm.grid.classList.add('jiggle'); hm.dock.classList.add('jiggle');
    hm.jiggle.hidden = false;
    hm.jiggle.textContent = T('home.jiggle');
  }
  function stopJiggle() {
    if (!hm.jiggling) return;
    hm.jiggling = false; hm.picked = null;
    hm.grid.classList.remove('jiggle'); hm.dock.classList.remove('jiggle');
    hm.jiggle.hidden = true;
    renderIcons();
  }
  function pick(id, inDock) {
    if (!hm.picked || hm.pickedDock !== inDock) {
      renderIcons();
      hm.grid.classList.add('jiggle'); hm.dock.classList.add('jiggle');
      hm.picked = id; hm.pickedDock = inDock;
      hm.jiggle.textContent = T('home.jiggle2', Z.appById(id).name());
      var b = (inDock ? hm.dock : hm.grid).querySelector('[data-id="icon_' + id + '"]');
      if (b) b.classList.add('picked');
      return;
    }
    var key = inDock ? 'dockOrder' : 'homeOrder';
    var o = order(inDock ? DOCKAPPS : GRID, key);
    var a = o.indexOf(hm.picked), b2 = o.indexOf(id);
    if (a >= 0 && b2 >= 0 && a !== b2) { o[a] = id; o[b2] = hm.picked; Z.phone.st[key] = o; Z.phone.save(); }
    hm.picked = null;
    renderIcons();
    hm.grid.classList.add('jiggle'); hm.dock.classList.add('jiggle');
    hm.jiggle.textContent = T('home.jiggle');
  }
  Z.registerApp(home);

  /* ======================= 電話 ======================= */
  var dialer = { id: 'dialer', pkg: 'z2.mock.dialer', icon: '☎', name: function () { return T('app.dialer'); } };
  var dl = {};
  var CONTACTS = [
    { n: 'contact.1', num: '090-0000-0001' },
    { n: 'contact.2', num: '090-0000-0002' },
    { n: 'contact.3', num: '0120-000-000' },
    { n: 'contact.4', num: '03-0000-0042' }
  ];
  var EMERGENCY = /^(110|118|119|112|911|999|000|117|177|171)$/;

  dialer.build = function (el) {
    dl.num = h('output.z2p-dial-num', { 'aria-live': 'polite', 'data-id': 'dial_number' });
    dl.msg = h('p.z2p-dial-msg', { 'aria-live': 'polite' });
    var pad = h('div.z2p-dial-pad');
    '123456789*0#'.split('').forEach(function (k) {
      pad.appendChild(h('button.z2p-dial-k', { type: 'button', text: k, 'data-id': 'dial_' + (k === '*' ? 'star' : k === '#' ? 'hash' : k), on: { click: function () { press(k); } } }));
    });
    dl.tabs = h('div.z2p-seg', { role: 'tablist' },
      dl.t1 = h('button', { type: 'button', role: 'tab', text: T('dial.keypad'), on: { click: function () { tab(1); } } }),
      dl.t2 = h('button', { type: 'button', role: 'tab', text: T('dial.contacts'), on: { click: function () { tab(2); } } }));
    dl.keypad = h('div.z2p-dial', null, dl.num, dl.msg, pad,
      h('div.z2p-dial-row', null,
        h('span'),
        h('button.z2p-dial-call', { type: 'button', 'aria-label': T('dial.call'), 'data-id': 'dial_call', text: '📞', on: { click: call } }),
        h('button.z2p-dial-del', { type: 'button', 'aria-label': T('dial.del'), text: '⌫', on: { click: function () { dl.n = dl.n.slice(0, -1); upd(); } } })));
    dl.contacts = h('div.z2p-contacts', { hidden: true });
    CONTACTS.forEach(function (c) {
      dl.contacts.appendChild(h('button.z2p-contact', { type: 'button', on: { click: function () { dl.n = c.num.replace(/-/g, ''); tab(1); upd(); } } },
        h('span.z2p-contact-i', { text: T(c.n).charAt(0) }), h('span', null, h('b', { text: T(c.n) }), h('small', { text: c.num }))));
    });
    dl.screen = h('div.z2p-calling', { hidden: true });
    el.appendChild(h('div.z2p-dialer', null, dl.tabs, dl.keypad, dl.contacts, dl.screen));
    dl.n = '';
    tab(1);
    upd();
  };
  function tab(i) {
    dl.t1.setAttribute('aria-selected', i === 1 ? 'true' : 'false');
    dl.t2.setAttribute('aria-selected', i === 2 ? 'true' : 'false');
    dl.keypad.hidden = i !== 1; dl.contacts.hidden = i !== 2;
  }
  function press(k) { if (dl.n.length < 20) dl.n += k; dl.msg.textContent = ''; upd(); }
  function upd() { dl.num.textContent = dl.n.replace(/^(\d{3})(\d{4})(\d+)$/, '$1-$2-$3'); }
  function call() {
    if (!dl.n) { dl.msg.textContent = T('dial.empty'); return; }
    if (EMERGENCY.test(dl.n)) { dl.msg.textContent = T('dial.emergency'); return; }
    dl.screen.textContent = '';
    dl.screen.appendChild(h('div.z2p-calling-n', { text: dl.num.textContent }));
    dl.screen.appendChild(h('div.z2p-calling-s', { text: T('dial.calling') }));
    var end = h('button.z2p-dial-end', { type: 'button', 'aria-label': T('dial.end'), text: '✕', on: { click: hang } });
    dl.screen.appendChild(end);
    dl.screen.hidden = false;
    dl.keypad.hidden = true; dl.tabs.hidden = true;
    clearTimeout(dl.t);
    dl.t = setTimeout(function () {
      dl.screen.querySelector('.z2p-calling-s').textContent = T('dial.nosim');
      Z.phone.vibrate(120);
      dl.t = setTimeout(hang, 2400);
    }, 1100);
    Z.bus.emit('dial', dl.n);
  }
  function hang() { clearTimeout(dl.t); dl.screen.hidden = true; dl.tabs.hidden = false; tab(1); }
  dialer.back = function () { if (!dl.screen.hidden) { hang(); return true; } return false; };
  dialer.key = function (e) {
    if (/^[0-9*#]$/.test(e.key)) { press(e.key); return true; }
    if (e.key === 'Backspace') { dl.n = dl.n.slice(0, -1); upd(); return true; }
    if (e.key === 'Enter') { call(); return true; }
    return false;
  };
  dialer.reset = function () { dl.n = ''; upd(); hang(); };
  Z.registerApp(dialer);

  /* ======================= カメラ ======================= */
  var camera = { id: 'camera', pkg: 'z2.mock.camera', icon: '◉', name: function () { return T('app.camera'); } };
  var cm = {};
  camera.build = function (el) {
    cm.msg = h('p.z2p-cam-msg', { 'aria-live': 'polite' });
    el.appendChild(h('div.z2p-camera', null,
      h('div.z2p-cam-view', null, h('div.z2p-cam-grid'), h('p.z2p-cam-nolens', { text: T('cam.nolens') })),
      cm.msg,
      h('div.z2p-cam-row', null,
        h('span.z2p-cam-thumb', { text: '🖼' }),
        h('button.z2p-cam-shutter', { type: 'button', 'aria-label': T('cam.shutter'), 'data-id': 'cam_shutter', on: { click: function () {
          cm.n = (cm.n || 0) + 1;
          cm.msg.textContent = cm.n < 3 ? T('cam.click') : T('cam.click3');
          Z.phone.vibrate(40);
        } } }),
        h('button.z2p-cam-flip', { type: 'button', 'aria-label': T('cam.flip'), text: '⟲', on: { click: function () { cm.msg.textContent = T('cam.flipmsg'); } } }))));
  };
  camera.show = function () { cm.msg.textContent = ''; cm.n = 0; };
  Z.registerApp(camera);

  /* ======================= 設定（スマホ） ======================= */
  var settings = { id: 'settings', pkg: 'z2.mock.settings', icon: '⚙', name: function () { return T('app.settings'); } };
  var sg = {};
  settings.build = function (el) {
    sg.el = el;
    sg.body = h('div.z2p-set');
    el.appendChild(sg.body);
  };
  settings.show = function () { renderSettings(); };
  settings.back = function () { if (sg.page) { sg.page = null; renderSettings(); return true; } return false; };

  function row(label, sub, ctrl, onClick, id) {
    var r = h(onClick ? 'button.z2p-set-row' : 'div.z2p-set-row', { type: onClick ? 'button' : null, 'data-id': id || null, on: onClick ? { click: onClick } : null },
      h('span.z2p-set-l', null, h('span', { text: label }), sub ? h('small', { text: sub }) : null), ctrl || null);
    return r;
  }
  function seg(name, opts, cur, fn) {
    var s = h('div.z2p-seg.small', { role: 'radiogroup', 'aria-label': name });
    opts.forEach(function (o) {
      s.appendChild(h('button', { type: 'button', role: 'radio', 'aria-checked': o[0] === cur ? 'true' : 'false', text: o[1], on: { click: function () { fn(o[0]); } } }));
    });
    return s;
  }
  function renderSettings() {
    var b = sg.body;
    b.textContent = '';
    var st = Z.phone.st;
    if (sg.page === 'dev') {
      b.appendChild(h('h2.z2p-set-h', { text: T('set.dev') }));
      var cb = h('input', { type: 'checkbox', checked: st.trail, 'aria-label': T('set.trail') });
      cb.addEventListener('change', function () { Z.phone.setTrail(cb.checked); });
      b.appendChild(row(T('set.trail'), T('set.trailsub'), cb));
      b.appendChild(h('p.z2p-set-note', { text: T('set.devnote') }));
      return;
    }
    if (sg.page === 'about') {
      b.appendChild(h('h2.z2p-set-h', { text: T('set.about') }));
      b.appendChild(row(T('set.model'), 'z2phone (play)'));
      b.appendChild(row(T('set.android'), T('set.androidv')));
      b.appendChild(row('z2term', Z.VERSION));
      b.appendChild(row(T('set.build'), 'Z2P.' + Z.VERSION.replace(/\./g, '') + '.play', null, function () {
        if (st.dev) { Z.phone.toast(T('set.already')); return; }
        st.buildTaps = (st.buildTaps || 0) + 1;
        var left = 7 - st.buildTaps;
        if (left <= 0) { st.dev = true; st.buildTaps = 0; Z.phone.toast(T('set.nowdev')); }
        else if (left <= 4) Z.phone.toast(T('set.taps', left));
        Z.phone.save();
      }, 'set_build'));
      b.appendChild(h('p.z2p-set-note', { text: T('set.aboutnote') }));
      return;
    }
    b.appendChild(h('h2.z2p-set-h', { text: T('app.settings') }));
    b.appendChild(h('h3.z2p-set-sh', { text: T('set.display') }));
    b.appendChild(row(T('set.theme'), null, seg(T('set.theme'), [['system', T('set.sys')], ['dark', T('set.dark')], ['light', T('set.light')]], Z.cfg.theme, function (v) { Z.cfg.theme = v; Z.saveCfg(); Z.applyTheme(); renderSettings(); })));
    b.appendChild(row(T('set.lang'), T('set.langsub'), seg(T('set.lang'), [['ja', '日本語'], ['en', 'English']], Z.lang, function (v) { if (v !== Z.lang) Z.setLang(v); })));
    b.appendChild(h('h3.z2p-set-sh', { text: T('set.storage') }));
    var used = Z.store.usage();
    var pct = Math.min(100, used / Z.LIMIT.store * 100);
    b.appendChild(row(T('set.used'), Z.bytes(used) + ' / 5 MB', h('span.z2p-meter', { role: 'meter', 'aria-valuemin': '0', 'aria-valuemax': '100', 'aria-valuenow': String(Math.round(pct)), 'aria-label': T('set.used') }, h('span', { style: { width: pct.toFixed(1) + '%' } }))));
    var parts = [['fs', T('set.p.fs')], ['term', T('set.p.term')], ['phone', T('set.p.phone')], ['auto', T('set.p.auto')], ['guide', T('set.p.guide')]];
    var detail = parts.map(function (p) {
      var v = Z.store.get(p[0], null);
      return p[1] + ' ' + Z.bytes(v ? JSON.stringify(v).length : 0);
    }).join(' · ');
    b.appendChild(h('p.z2p-set-note', { text: detail }));
    if (!Z.store.available) b.appendChild(h('p.z2p-set-note.z2p-warn', { text: T('set.nostore') }));
    b.appendChild(row(T('set.wipe'), T('set.wipesub'), null, function () {
      Z.phone.confirm(T('set.wipeq'), T('set.wipeok')).then(function (ok) { if (ok) Z.wipe(); });
    }, 'set_wipe'));
    b.appendChild(h('h3.z2p-set-sh', { text: T('set.system') }));
    if (st.dev) b.appendChild(row(T('set.dev'), null, null, function () { sg.page = 'dev'; renderSettings(); }));
    b.appendChild(row(T('set.about'), null, null, function () { sg.page = 'about'; renderSettings(); }, 'set_about'));
    b.appendChild(row(T('set.mock'), T('set.mocksub'), null, function () { Z.phone.alert(T('set.mock'), T('set.mocktext')); }));
  }
  settings.rerender = renderSettings;
  Z.registerApp(settings);
})(typeof window !== 'undefined' ? window : globalThis);
