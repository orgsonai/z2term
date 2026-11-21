/* z2term play — 起動と開閉。
   画面全体に重ねる → スマホの枠 → 中身（phone.js）。閉じるのは ✕ だけ（ESC は端末へ届ける）。
   保存: z2mock:settings（言語・配色・z2term の設定）/ z2mock:fs（仮想ファイル） */
(function (G) {
  'use strict';
  var Z = G.Z2P = G.Z2P || {};
  var h = function () { return Z.h.apply(null, arguments); };
  var T = function () { return Z.t.apply(null, arguments); };

  var root = null, frame = null, opener = null, open = false, booted = false;
  var lastQuota = 0;

  Z.isOpen = function () { return open; };
  Z.rootEl = function () { return root; };
  Z.saveCfg = function () { Z.store.set('settings', Z.cfg); };

  /* 裏で動かすシェル（z2-when・タイル・z2-action の command） */
  Z.runBackground = function (cmd, sig, label, env) {
    var sh = new Z.Shell({ fs: Z.fs });
    if (env) for (var k in env) sh.env[k] = env[k];
    sh.io = { background: label || true };
    var out = '';
    var w = function (s) { out += s; if (out.length > 8192) out = out.slice(-8192); };
    return sh.run(String(cmd), w, w, sig || { aborted: false }).then(function (st) { return { status: st, out: out }; },
      function (e) { return { status: 1, out: out + String(e && e.message || e) }; });
  };

  Z.applyTheme = function () {
    if (!root) return;
    var t = Z.cfg.theme || 'system';
    if (t === 'system') t = G.matchMedia && G.matchMedia('(prefers-color-scheme: light)').matches ? 'light' : 'dark';
    root.setAttribute('data-theme', t);
  };

  function loadState() {
    Z.cfg = Z.store.get('settings', null) || {};
    if (!Z.cfg.theme) Z.cfg.theme = 'system';
    Z.cfg.z2 = Z.cfg.z2 || {};
    Z.lang = Z.cfg.lang || (document.documentElement.lang === 'ja' ? 'ja' : 'en');
    Z.fs = new Z.FS(Z.store.get('fs', null), function () { Z.store.set('fs', Z.fs.root); Z.bus.emit('fs'); });
    Z.store.set('fs', Z.fs.root);
    Z.phone.load();
    Z.when.load();
    Z.guide.init();
    Z.when.init();
    Z.bus.on('quota', function () {
      if (Date.now() - lastQuota < 60000) return;
      lastQuota = Date.now();
      Z.phone.notify({ title: T('main.quota'), body: T('main.quotasub'), app: 'z2term', heads: true });
    });
  }

  function build() {
    root = h('div.z2p', { id: 'z2p', role: 'dialog', 'aria-modal': 'true', 'aria-label': T('main.label'), lang: Z.lang, hidden: true });
    var close = h('button.z2p-ctl-b.close', { type: 'button', 'aria-label': T('main.close'), title: T('main.close'), on: { click: Z.close } }, h('span', { text: '✕' }), h('span.z2p-ctl-l', { text: T('main.closel') }));
    var swap = h('button.z2p-ctl-b', { type: 'button', 'aria-label': T('main.swap'), title: T('main.swap'), 'data-id': 'swap', on: { click: function () { Z.guide.toggle(); } } }, h('span', { text: '⇄' }), h('span.z2p-ctl-l', { text: T('app.guide') }));
    root.appendChild(h('div.z2p-ctl', null, close, swap));
    frame = h('div.z2p-frame');
    root.appendChild(frame);
    root.appendChild(h('p.z2p-foot', { text: T('main.foot') }));
    document.body.appendChild(root);
    Z.applyTheme();
    Z.phone.build(frame);
    Z.guide.renderFloat();
    if (G.matchMedia) {
      var mq = G.matchMedia('(prefers-color-scheme: light)');
      if (mq.addEventListener) mq.addEventListener('change', Z.applyTheme);
    }
  }

  function boot() {
    Z.bootAt = Z.bootAt || Date.now();
    loadState();
    build();
    booted = true;
  }

  function teardown() {
    if (Z.zt && Z.zt.save) Z.zt.save();
    Z.store.flush();
    Z.phone.destroy();
    if (root) root.remove();
    root = null; frame = null;
    Z.bus.reset();
  }

  /* 言語を変えたら作り直す（状態は保存してから読み直す） */
  Z.setLang = function (v) {
    Z.cfg.lang = v;
    Z.saveCfg();
    teardown();
    boot();
    root.hidden = !open;
    Z.phone.open('settings');
    Z.phone.toast(T('main.lang'));
  };
  Z.wipe = function () {
    teardown();
    Z.store.clearAll();
    boot();
    root.hidden = !open;
    Z.phone.toast(T('main.wiped'));
  };

  Z.open = function () {
    opener = document.activeElement;
    if (!booted) boot();
    root.hidden = false;
    open = true;
    document.documentElement.classList.add('z2p-lock');
    Z.phone.wake();
    setTimeout(function () {
      if (Z.phone.current() === 'z2term') Z.zt.focus();
      else root.querySelector('.z2p-ctl-b').focus();
    }, 30);
  };
  Z.close = function () {
    if (!root) return;
    if (Z.zt && Z.zt.save) Z.zt.save();
    Z.store.flush();
    root.hidden = true;
    open = false;
    document.documentElement.classList.remove('z2p-lock');
    if (opener && opener.focus) opener.focus();
  };

  function editable(el) {
    if (!el) return false;
    if (el.classList && el.classList.contains('z2t-sink')) return false;
    return el.tagName === 'INPUT' || el.tagName === 'TEXTAREA' || el.tagName === 'SELECT' || el.isContentEditable;
  }

  document.addEventListener('keydown', function (e) {
    if (!open || e.defaultPrevented) return;
    var t = e.target;
    if (editable(t)) {
      if (e.key === 'Escape') { t.blur(); e.preventDefault(); }
      return;
    }
    if (Z.phone.modalOpen()) { if (e.key === 'Escape') { e.preventDefault(); Z.phone.back(); } return; }
    var cur = Z.phone.current();
    if (cur === 'z2term' && !Z.phone.shadeOpen()) {
      if (t && t.tagName === 'BUTTON' && (e.key === 'Enter' || e.key === ' ')) return;
      if (Z.zt.keydown(e)) return;
      return;
    }
    if (e.key === 'Escape') { e.preventDefault(); Z.phone.back(); return; }
    var a = Z.appById(cur);
    if (a && a.key && a.key(e)) { e.preventDefault(); return; }
  });

  G.addEventListener('pagehide', function () { if (booted) { if (Z.zt && Z.zt.save) Z.zt.save(); Z.store.flush(); } });
  G.addEventListener('resize', function () { if (open && Z.guide.renderFloat) Z.guide.renderFloat(); });
})(typeof window !== 'undefined' ? window : globalThis);
