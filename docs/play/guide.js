/* z2term play — ホームの「手順」フォルダ。
   カードごとに「やること」「打つこと（コピー可）」「うまくいったか（自動で判定）」。
   カードはモックの上に浮かべられる（ドラッグで移動・畳める）。⇄ で 手順 ⇄ z2term を行き来。
   保存: z2mock:guide */
(function (G) {
  'use strict';
  var Z = G.Z2P = G.Z2P || {};
  var h = function () { return Z.h.apply(null, arguments); };
  var T = function () { return Z.t.apply(null, arguments); };

  var N = 8;
  var gs;          /* 保存する状態 */
  var g = {};      /* DOM */
  var app = { id: 'guide', pkg: 'z2.mock.guide', icon: '☰', name: function () { return T('app.guide'); } };
  Z.guide = {};

  function load() {
    gs = Z.store.get('guide', null) || {};
    gs.done = gs.done || {};
    gs.flags = gs.flags || {};
  }
  function save() { Z.store.set('guide', gs); }

  function done(i) {
    if (gs.done[i]) return;
    gs.done[i] = Date.now();
    save();
    Z.phone.toast(T('g.ok', i, T('g.' + i + '.t')));
    renderList();
    renderDetail();
    renderFloat();
  }

  /* ---------------- 自動の判定 ---------------- */
  function watch() {
    Z.bus.on('notify', function (n) {
      if (/こんにちは|hello/i.test(n.title + ' ' + n.body) && n.app === 'z2term') done(1);
    });
    Z.bus.on('command-done', function (ev) {
      var line = ev.line;
      if (/^\s*ls\b.*z2term-log/.test(line)) {
        var d = Z.fs.stat('/root/z2term-log');
        if (d && d.t === 'd' && Object.keys(d.c).length) done(2);
      }
    });
    Z.bus.on('groups', function () {
      if (Z.zt.groups().some(function (x) { return x.tabs.length >= 2; })) done(3);
    });
    Z.bus.on('broadcast-sent', function (ev) { if (ev.count >= 2) done(4); });
    Z.bus.on('when-fired', function (ev) { if (/^event:airplane_on$/.test(ev.rule.trig) && ev.status === 0) done(5); });
    Z.bus.on('action-start', function () { gs.flags.searchedInAction = false; });
    Z.bus.on('search', function () { if (Z.action.running()) gs.flags.searchedInAction = true; });
    Z.bus.on('action-done', function (r) { if (r.ok && gs.flags.searchedInAction) done(6); });
    Z.bus.on('phone-event', function (ev) {
      if (ev.name === 'torch_on' && gs.flags.tileRun) done(7);
    });
    Z.bus.on('tile-run', function () { gs.flags.tileRun = true; setTimeout(function () { gs.flags.tileRun = false; }, 3000); });
    Z.bus.on('dial', function () { done(8); });
  }

  /* ---------------- フォルダ（アプリ） ---------------- */
  app.build = function (el) {
    g.el = el;
    g.head = h('div.z2p-g-head', null,
      h('h2', { text: T('app.guide') }),
      g.prog = h('span.z2p-g-prog'));
    g.list = h('div.z2p-g-list', { role: 'list' });
    g.detail = h('div.z2p-g-detail', { hidden: true });
    el.appendChild(h('div.z2p-guide', null, g.head, g.list, g.detail));
    renderList();
  };
  app.show = function (arg) { if (arg && arg.card) openCard(arg.card); else renderList(); };
  app.back = function () { if (!g.detail.hidden) { closeCard(); return true; } return false; };

  function renderList() {
    if (!g.list) return;
    var n = Object.keys(gs.done).length;
    g.prog.textContent = T('g.prog', n, N);
    g.list.textContent = '';
    g.list.appendChild(h('p.z2p-g-intro', { text: T('g.intro') }));
    for (var i = 1; i <= N; i++) {
      (function (k) {
        g.list.appendChild(h('button.z2p-g-item', { type: 'button', role: 'listitem', 'data-id': 'guide_' + k, on: { click: function () { openCard(k); } } },
          h('span.z2p-g-no', { text: String(k) }),
          h('span.z2p-g-tt', { text: T('g.' + k + '.t') }),
          h('span.z2p-g-ck', { text: gs.done[k] ? '✓' : '', 'aria-label': gs.done[k] ? T('g.donearia') : T('g.todoaria') })));
      })(i);
    }
    g.list.appendChild(h('button.z2p-link', { type: 'button', text: T('g.reset'), on: { click: function () { gs.done = {}; save(); renderList(); renderFloat(); } } }));
  }

  function cardBody(k, compact) {
    var box = h('div.z2p-g-card');
    if (!compact) box.appendChild(h('h3', null, h('span.z2p-g-no', { text: String(k) }), ' ', T('g.' + k + '.t')));
    box.appendChild(h('p.z2p-g-lbl', { text: T('g.do') }));
    T('g.' + k + '.do').split('\n').forEach(function (l) { box.appendChild(h('p.z2p-g-p', { text: l })); });
    var cmds = T('g.' + k + '.cmd');
    if (cmds && cmds !== 'g.' + k + '.cmd') {
      box.appendChild(h('p.z2p-g-lbl', { text: T('g.type') }));
      cmds.split('\n').forEach(function (c) {
        box.appendChild(h('div.z2p-g-cmd', null, h('code', { text: c }),
          h('button.z2p-g-copy', { type: 'button', text: T('ph.copy'), 'aria-label': T('g.copyaria', c), on: { click: function () { Z.phone.clip.set(c); Z.phone.toast(T('g.copied')); } } })));
      });
    }
    box.appendChild(h('p.z2p-g-lbl', { text: T('g.check') }));
    box.appendChild(h('p.z2p-g-state' + (gs.done[k] ? '.ok' : ''), { text: gs.done[k] ? '✓ ' + T('g.done') : T('g.notyet') + ' ' + T('g.' + k + '.ck') }));
    return box;
  }

  function openCard(k) {
    g.cur = k;
    g.list.hidden = true;
    g.detail.hidden = false;
    renderDetail();
  }
  function closeCard() { g.cur = null; g.detail.hidden = true; g.list.hidden = false; renderList(); }
  function renderDetail() {
    if (!g.detail || g.detail.hidden || !g.cur) return;
    var k = g.cur;
    g.detail.textContent = '';
    g.detail.appendChild(cardBody(k, false));
    g.detail.appendChild(h('div.z2p-g-acts', null,
      h('button.z2p-btn', { type: 'button', text: T('g.float'), on: { click: function () { floatCard(k); } } }),
      h('button.z2p-btn.ghost', { type: 'button', text: T('g.openz2'), on: { click: function () { Z.phone.open('z2term'); } } })));
    var nav = h('div.z2p-g-nav', null,
      h('button.z2p-link', { type: 'button', text: '← ' + T('g.list'), on: { click: closeCard } }),
      k < N ? h('button.z2p-link', { type: 'button', text: T('g.next') + ' →', on: { click: function () { openCard(k + 1); } } }) : null);
    g.detail.appendChild(nav);
  }

  /* ---------------- 浮かべる窓 ---------------- */
  function floatCard(k) {
    gs.float = { id: k, x: gs.float ? gs.float.x : null, y: gs.float ? gs.float.y : null, small: false };
    save();
    renderFloat();
    Z.phone.open('z2term');
  }
  function renderFloat() {
    var root = Z.rootEl && Z.rootEl();
    if (!root) return;
    if (g.float) { g.float.remove(); g.float = null; }
    if (!gs.float) return;
    var k = gs.float.id;
    var w = h('div.z2p-float', { role: 'dialog', 'aria-label': T('g.floataria', k) });
    var head = h('div.z2p-float-h', null,
      h('span.z2p-float-t', { text: (gs.done[k] ? '✓ ' : '') + k + '. ' + T('g.' + k + '.t') }),
      h('button.z2p-float-b', { type: 'button', 'aria-label': gs.float.small ? T('g.expand') : T('g.shrink'), text: gs.float.small ? '▴' : '▾', on: { click: function () { gs.float.small = !gs.float.small; save(); renderFloat(); } } }),
      k > 1 ? h('button.z2p-float-b', { type: 'button', 'aria-label': T('g.prev'), text: '‹', on: { click: function () { gs.float.id = k - 1; save(); renderFloat(); } } }) : null,
      k < N ? h('button.z2p-float-b', { type: 'button', 'aria-label': T('g.next'), text: '›', on: { click: function () { gs.float.id = k + 1; save(); renderFloat(); } } }) : null,
      h('button.z2p-float-b', { type: 'button', 'aria-label': T('ph.close'), text: '✕', on: { click: function () { gs.float = null; save(); renderFloat(); } } }));
    w.appendChild(head);
    if (!gs.float.small) w.appendChild(h('div.z2p-float-body', null, cardBody(k, true)));
    root.appendChild(w);
    g.float = w;
    place(w);
    drag(w, head);
  }
  function place(w) {
    var f = gs.float;
    var vw = G.innerWidth, vh = G.innerHeight;
    var r = w.getBoundingClientRect();
    var x = f.x == null ? vw - r.width - 16 : f.x;
    var y = f.y == null ? (vw <= 600 ? 112 : Math.max(56, vh - r.height - 16)) : f.y;
    x = Math.max(4, Math.min(vw - Math.min(r.width, vw) - 4, x));
    y = Math.max(4, Math.min(vh - 40, y));
    w.style.left = x + 'px'; w.style.top = y + 'px';
  }
  function drag(w, head) {
    var d = null;
    head.addEventListener('pointerdown', function (e) {
      if (e.target.closest('button')) return;
      var r = w.getBoundingClientRect();
      d = { dx: e.clientX - r.left, dy: e.clientY - r.top };
      head.setPointerCapture(e.pointerId);
    });
    head.addEventListener('pointermove', function (e) {
      if (!d) return;
      gs.float.x = e.clientX - d.dx; gs.float.y = e.clientY - d.dy;
      place(w);
    });
    function end() { if (d) { d = null; save(); } }
    head.addEventListener('pointerup', end);
    head.addEventListener('pointercancel', end);
  }

  Z.guide.toggle = function () {
    if (Z.phone.current() === 'guide') Z.phone.open('z2term');
    else Z.phone.open('guide');
  };
  Z.guide.init = function () { load(); watch(); };
  Z.guide.renderFloat = renderFloat;
  Z.guide.load = load;

  Z.registerApp(app);
})(typeof window !== 'undefined' ? window : globalThis);
