/* z2term play — モックの中の z2term。
   本物の HANDBOOK「3. 画面の見かた」と同じ並び: ツールバー → タブ → 端末 → ▾ → 内蔵キーボード。
   ツールバー: 📋 📜 🔅 🔒 🔍 ⌨ ⚪/🔴 ⚙（並びは ⚙ で変えられる）
   タブ・グループ（枠をドラッグして上下左右に分割）・ブロードキャスト・端末ログ。
   保存: z2mock:term（タブ・グループ・履歴・画面の残り行）/ z2 の設定は z2mock:settings.z2 */
(function (G) {
  'use strict';
  var Z = G.Z2P = G.Z2P || {};
  var h = function () { return Z.h.apply(null, arguments); };
  var T = function () { return Z.t.apply(null, arguments); };

  var TOOLS = {
    clip: { glyph: '📋', label: 'zt.clip' },
    menu: { glyph: '📜', label: 'zt.menu' },
    light: { glyph: '🔅', label: 'zt.light' },
    lock: { glyph: '🔒', label: 'zt.lock' },
    search: { glyph: '🔍', label: 'zt.search' },
    kbd: { glyph: '⌨', label: 'zt.kbd' },
    log: { glyph: '⚪', label: 'zt.log' },
    gear: { glyph: '⚙', label: 'zt.gear' }
  };
  var DEFCFG = { font: 13, pal: 'green', order: ['clip', 'menu', 'light', 'lock', 'search', 'kbd', 'log', 'gear'], nowrap: false, maxGroup: 4, autoLog: false, mask: true, kbd: null, keepAwake: false, persistent: true, dimLevel: 0 };

  var app = { id: 'z2term', pkg: 'com.zerotoship.z2term', icon: 'z2', name: function () { return 'z2term'; } };
  var zt = Z.zt = {};
  var cfg, data, tabs = [], groups = [], view = null, focusId = null;
  var e = {};   /* DOM */

  /* ======================= 状態 ======================= */
  function loadCfg() {
    var s = Z.cfg.z2 || {};
    cfg = {};
    for (var k in DEFCFG) cfg[k] = (k in s) ? s[k] : JSON.parse(JSON.stringify(DEFCFG[k]));
    Object.keys(TOOLS).forEach(function (k2) { if (cfg.order.indexOf(k2) < 0) cfg.order.push(k2); });
    Z.cfg.z2 = cfg;
    zt.keepAwake = cfg.keepAwake;
    zt.persistent = cfg.persistent;
  }
  function saveCfg() { Z.cfg.z2 = cfg; Z.saveCfg(); zt.keepAwake = cfg.keepAwake; zt.persistent = cfg.persistent; }
  zt.cfg = function () { return cfg; };

  function persist() {
    data.tabs = tabs.map(function (t) {
      return { id: t.id, name: t.name, lines: t.pane.toJSON().lines, env: t.sh.env, hist: t.sh.history.slice(-200), log: t.log, groupId: t.groupId || null };
    });
    data.groups = groups.map(function (g) { return { id: g.id, name: g.name, tabs: g.tabs, layout: g.layout, bc: g.bc }; });
    data.view = view;
    data.focus = focusId;
    Z.store.set('term', data);
  }
  var persistT = null;
  function persistSoon() { if (persistT) return; persistT = setTimeout(function () { persistT = null; persist(); }, 400); }
  zt.save = persist;

  /* ======================= タブ ======================= */
  function nextName(base) {
    var names = tabs.map(function (t) { return t.name; });
    if (names.indexOf(base) < 0) return base;
    for (var i = 2; ; i++) if (names.indexOf(base + '-' + i) < 0) return base + '-' + i;
  }

  function makeTab(o) {
    var t = { id: o.id || 't' + Z.uid(), name: o.name || nextName('sh'), groupId: o.groupId || null, log: o.log || { on: !!cfg.autoLog, file: null, bytes: 0 } };
    var env = o.env || null;
    t.sh = new Z.Shell({ fs: Z.fs, env: env ? JSON.parse(JSON.stringify(env)) : null, aliases: data.aliases, history: o.hist || [] });
    t.sh.io = { tab: t, save: persistSoon };
    if (t.sh.fs && !Z.fs.stat(t.sh.cwd())) t.sh.env.PWD = '/root';
    t.pane = new Z.Pane({
      sh: t.sh, name: t.name, lines: o.lines || [], noPrompt: true,
      onLine: function (s) { writeLog(t, s); },
      onExit: function () { zt.closeTab(t, true); },
      onChange: persistSoon,
      onActivity: function () { if (view && !isShown(t)) markTabs(); }
    });
    t.sh.io.pane = t.pane;
    if (o.intro) o.intro(t.pane);
    if (!o.quiet) t.pane.prompt();
    t.pane.el.addEventListener('pointerdown', function () { setFocus(t); });
    return t;
  }

  zt.tabs = function () { return tabs; };
  zt.byId = function (id) { return tabs.filter(function (t) { return t.id === id; })[0] || null; };
  zt.find = function (ref) {
    if (ref == null) return null;
    var n = parseInt(ref, 10);
    if (String(n) === String(ref) && tabs[n - 1]) return tabs[n - 1];
    return zt.byId(ref) || tabs.filter(function (t) { return t.name === ref; })[0] || null;
  };
  zt.current = function () { return zt.byId(focusId); };
  zt.group = function (id) { return groups.filter(function (g) { return g.id === id; })[0] || null; };
  zt.groups = function () { return groups; };
  zt.findGroup = function (name) { return groups.filter(function (g) { return g.name === name || g.id === name; })[0] || null; };

  zt.newTab = function (o) {
    o = o || {};
    var t = makeTab({ name: o.name ? nextName(o.name) : null, intro: function (p) { p.write(T('zt.newtab') + '\n', 'd'); } });
    tabs.push(t);
    if (o.show !== false) zt.showTab(t);
    else { renderTabs(); persistSoon(); }
    Z.bus.emit('tabs');
    return t;
  };

  zt.closeTab = function (t, force) {
    if (!force && t.pane.running) {
      Z.phone.confirm(T('zt.closerun', t.name), T('zt.close')).then(function (ok) { if (ok) zt.closeTab(t, true); });
      return;
    }
    if (t.pane.sig) t.pane.sig.aborted = true;
    t.pane.dead = true;
    if (t.groupId) zt.removeFromGroup(t, true);
    var i = tabs.indexOf(t);
    if (i < 0) return;
    tabs.splice(i, 1);
    if (!tabs.length) { zt.newTab({}); return; }
    if (focusId === t.id || (view && view.kind === 'tab' && view.id === t.id)) {
      var nt = tabs[Math.max(0, i - 1)];
      if (nt.groupId) zt.showGroup(zt.group(nt.groupId), nt); else zt.showTab(nt);
    } else render();
    persistSoon();
    Z.bus.emit('tabs');
  };

  zt.renameTab = function (t, name) {
    name = String(name || '').trim();
    if (!name) return;
    t.name = name; t.pane.name = name;
    render(); persistSoon();
  };

  zt.showTab = function (t) {
    if (t.groupId) { zt.showGroup(zt.group(t.groupId), t); return; }
    view = { kind: 'tab', id: t.id };
    focusId = t.id;
    render();
    persistSoon();
  };

  zt.sendText = function (t, text, enter) {
    if (t.pane.running) { if (enter) t.pane.queue = (t.pane.queue || []).concat([text, '']); return; }
    t.pane.insert(text);
    if (enter) t.pane.enter();
  };

  zt.killAll = function () {
    tabs.forEach(function (t) { if (t.pane.sig) t.pane.sig.aborted = true; t.pane.dead = true; });
    tabs = []; groups = [];
    view = null; focusId = null;
    var t = makeTab({ intro: function (p) { p.write(T('zt.fresh') + '\n', 'd'); } });
    tabs.push(t);
    zt.showTab(t);
    persist();
  };

  function isShown(t) {
    if (!view) return false;
    if (view.kind === 'tab') return view.id === t.id;
    return t.groupId === view.id;
  }

  /* ======================= グループ ======================= */
  function leaf(id) { return { t: id }; }
  function removeLeaf(n, id) {
    if (!n) return null;
    if (n.t) return n.t === id ? null : n;
    var a = removeLeaf(n.a, id), b = removeLeaf(n.b, id);
    if (!a) return b;
    if (!b) return a;
    return { d: n.d, a: a, b: b, r: n.r };
  }
  function replaceLeaf(n, id, rep) {
    if (n.t) return n.t === id ? rep : n;
    return { d: n.d, a: replaceLeaf(n.a, id, rep), b: replaceLeaf(n.b, id, rep), r: n.r };
  }
  function countLeaves(n) { return !n ? 0 : n.t ? 1 : countLeaves(n.a) + countLeaves(n.b); }
  function appendLeaf(n, id) {
    if (!n) return leaf(id);
    return { d: countLeaves(n) % 2 ? 'h' : 'v', a: n, b: leaf(id) };
  }

  zt.newGroup = function (name, ts) {
    name = String(name || '').trim() || T('zt.group') + ' ' + (groups.length + 1);
    if (zt.findGroup(name)) return { error: 'group exists: ' + name };
    if (ts.length > cfg.maxGroup) return { error: T('zt.maxgroup', cfg.maxGroup) };
    var g = { id: 'g' + Z.uid(), name: name, tabs: [], layout: null, bc: { on: false, targets: [] } };
    groups.push(g);
    ts.forEach(function (t) { zt.addToGroup(g, t, true); });
    zt.showGroup(g);
    Z.bus.emit('groups');
    return g;
  };
  zt.addToGroup = function (g, t, quiet) {
    if (t.groupId === g.id) return null;
    if (g.tabs.length >= cfg.maxGroup) { if (!quiet) Z.phone.toast(T('zt.maxgroup', cfg.maxGroup)); return { error: T('zt.maxgroup', cfg.maxGroup) }; }
    if (t.groupId) zt.removeFromGroup(t, true);
    t.groupId = g.id;
    g.tabs.push(t.id);
    g.layout = appendLeaf(g.layout, t.id);
    if (g.bc.on && g.bc.targets.indexOf(t.id) < 0) g.bc.targets.push(t.id);
    render(); persistSoon();
    Z.bus.emit('groups');
    return null;
  };
  zt.removeFromGroup = function (t, quiet) {
    var g = zt.group(t.groupId);
    t.groupId = null;
    if (!g) return null;
    g.tabs = g.tabs.filter(function (id) { return id !== t.id; });
    g.bc.targets = g.bc.targets.filter(function (id) { return id !== t.id; });
    g.layout = removeLeaf(g.layout, t.id);
    if (!g.tabs.length) {
      groups = groups.filter(function (x) { return x !== g; });
      if (view && view.kind === 'group' && view.id === g.id) view = { kind: 'tab', id: t.id };
    }
    if (!quiet) render();
    persistSoon();
    Z.bus.emit('groups');
    return null;
  };
  zt.deleteGroup = function (g) {
    g.tabs.forEach(function (id) { var t = zt.byId(id); if (t) t.groupId = null; });
    groups = groups.filter(function (x) { return x !== g; });
    if (view && view.kind === 'group' && view.id === g.id) view = { kind: 'tab', id: g.tabs[0] || (tabs[0] && tabs[0].id) };
    render(); persistSoon();
    Z.bus.emit('groups');
  };
  zt.showGroup = function (g, focusTab) {
    if (!g) return;
    view = { kind: 'group', id: g.id };
    focusId = (focusTab && focusTab.id) || (g.tabs.indexOf(focusId) >= 0 ? focusId : g.tabs[0]);
    render(); persistSoon();
  };
  zt.setBroadcast = function (g, on) {
    g.bc.on = !!on;
    if (on && !g.bc.targets.length) g.bc.targets = g.tabs.slice();
    render(); persistSoon();
    Z.bus.emit('broadcast', { group: g, on: g.bc.on });
  };
  zt.setTarget = function (g, t, on) {
    g.bc.targets = g.bc.targets.filter(function (id) { return id !== t.id; });
    if (on) g.bc.targets.push(t.id);
    render(); persistSoon();
  };

  /* ======================= 入力の配達（ブロードキャスト込み） ======================= */
  function focusedTab() { return zt.byId(focusId) || tabs[0]; }
  function targetsFor(t) {
    var out = [t];
    var g = t && zt.group(t.groupId);
    if (g && g.bc.on && view && view.kind === 'group' && view.id === g.id) {
      g.bc.targets.forEach(function (id) { var x = zt.byId(id); if (x && x !== t) out.push(x); });
    }
    return out;
  }
  function deliver(k) {
    var t = focusedTab();
    if (!t) return;
    var list = targetsFor(t);
    list.forEach(function (x) {
      if (k.text != null) x.pane.input(k.text); else x.pane.key(k);
    });
    if (list.length > 1 && (k.key === 'Enter' || (k.text && /\n/.test(k.text)))) Z.bus.emit('broadcast-sent', { count: list.length });
    Z.phone.wake();
  }
  zt.deliver = deliver;

  function keyFromEvent(ev) {
    if (ev.isComposing || ev.keyCode === 229) return null;
    if (ev.metaKey) return null;
    var k = ev.key;
    var special = { Enter: 1, Backspace: 1, Delete: 1, Tab: 1, Escape: 1, ArrowUp: 1, ArrowDown: 1, ArrowLeft: 1, ArrowRight: 1, Home: 1, End: 1, PageUp: 1, PageDown: 1 };
    if (ev.ctrlKey && !ev.altKey && k.length === 1) {
      if ((k === 'v' || k === 'V')) return null;
      if ((k === 'c' || k === 'C') && String(G.getSelection && G.getSelection()).length) return null;
      return { key: k, ctrl: true };
    }
    if (ev.altKey && k.length === 1) return { key: ev.code && /^Key[A-Z]$/.test(ev.code) ? ev.code.slice(3).toLowerCase() : k, alt: true };
    if (special[k]) return { key: k, ctrl: ev.ctrlKey, alt: ev.altKey, shift: ev.shiftKey };
    return null;
  }

  /* main.js から: 端末の外にフォーカスがあるときのキー */
  zt.keydown = function (ev) {
    var k = keyFromEvent(ev);
    if (k) { ev.preventDefault(); deliver(k); focusSink(); return true; }
    if (!ev.ctrlKey && !ev.metaKey && !ev.altKey && ev.key && ev.key.length === 1) { ev.preventDefault(); deliver({ text: ev.key }); focusSink(); return true; }
    return false;
  };

  function focusSink() {
    if (!e.sink || Z.phone.modalOpen() || Z.phone.current() !== 'z2term') return;
    if (document.activeElement !== e.sink) e.sink.focus({ preventScroll: true });
  }
  zt.focus = focusSink;

  function bindSink() {
    var s = e.sink;
    s.addEventListener('keydown', function (ev) {
      var k = keyFromEvent(ev);
      if (k) { ev.preventDefault(); deliver(k); }
    });
    s.addEventListener('input', function (ev) {
      if (ev.isComposing) return;
      var it = ev.inputType;
      if (it === 'insertText' || it === 'insertReplacementText') { if (ev.data) deliver({ text: ev.data }); }
      else if (it === 'insertLineBreak' || it === 'insertParagraph') deliver({ key: 'Enter' });
      else if (it === 'deleteContentBackward') deliver({ key: 'Backspace' });
      s.value = '';
    });
    s.addEventListener('compositionupdate', function (ev) { var t = focusedTab(); if (t) { t.pane.compose = ev.data || ''; t.pane.renderInput(); } });
    s.addEventListener('compositionend', function (ev) {
      var t = focusedTab();
      if (t) { t.pane.compose = ''; t.pane.renderInput(); }
      if (ev.data) deliver({ text: ev.data });
      setTimeout(function () { s.value = ''; }, 0);
    });
    s.addEventListener('paste', function (ev) {
      var txt = ev.clipboardData && ev.clipboardData.getData('text/plain');
      ev.preventDefault();
      if (txt) deliver({ text: txt });
    });
    s.addEventListener('focus', function () { var t = focusedTab(); if (t) t.pane.setFocused(true); });
    s.addEventListener('blur', function () { tabs.forEach(function (t) { t.pane.setFocused(false); }); });
  }

  function setFocus(t) {
    if (focusId === t.id) { focusSink(); return; }
    focusId = t.id;
    tabs.forEach(function (x) { x.pane.setFocused(x === t && document.activeElement === e.sink); });
    markCells();
    renderTools();
    focusSink();
    persistSoon();
  }

  /* ======================= 端末ログ ======================= */
  function logName(t) {
    var d = new Date();
    var date = d.getFullYear() + '-' + Z.pad(d.getMonth() + 1) + '-' + Z.pad(d.getDate()) + '_' + Z.pad(d.getHours()) + Z.pad(d.getMinutes());
    return '/root/z2term-log/' + date + '-' + t.name.replace(/[^\w.\-]+/g, '_') + '.txt';
  }
  function writeLog(t, line) {
    if (!t.log || !t.log.on) return;
    try {
      if (!t.log.file) {
        Z.fs.mkdir('/root/z2term-log', true, true);
        t.log.file = logName(t);
        t.log.bytes = 0;
        if (Z.fs.stat(t.log.file)) t.log.bytes = Z.fs.readFile(t.log.file).length;
      }
      var s = (cfg.mask ? Z.mask(line) : line) + '\n';
      if (t.log.bytes + s.length > Z.LIMIT.logFile) {
        t.log.on = false;
        Z.phone.notify({ title: T('zt.logfull'), body: Z.fs.pretty(t.log.file), app: 'z2term' });
        renderTools(); markCells();
        return;
      }
      Z.fs.writeFile(t.log.file, s, true);
      t.log.bytes += s.length;
      trimLogs(t.log.file);
      Z.bus.emit('log', t);
    } catch (ex) { t.log.on = false; }
  }
  function trimLogs(keep) {
    var dir = Z.fs.stat('/root/z2term-log');
    if (!dir || dir.t !== 'd') return;
    var total = Z.fs.size(dir);
    if (total <= Z.LIMIT.logTotal) return;
    var files = Object.keys(dir.c).map(function (n) { return { p: '/root/z2term-log/' + n, n: dir.c[n] }; })
      .filter(function (x) { return x.n.t === 'f' && x.p !== keep; }).sort(function (a, b) { return a.n.m - b.n.m; });
    while (total > Z.LIMIT.logTotal && files.length) { var f = files.shift(); total -= f.n.d.length; Z.fs.rm(f.p); }
  }
  function setLog(t, on) {
    t.log.on = !!on;
    if (on) t.log.file = null;
    Z.phone.toast(on ? T('zt.logon') : T('zt.logoff'));
    renderTools(); markCells(); persistSoon();
  }

  /* ======================= 画面 ======================= */
  app.build = function (el) {
    loadCfg();
    e.root = h('div.z2t', { 'data-pal': cfg.pal });
    e.host = h('span.z2t-host', { text: 'z2term' });
    e.tools = h('div.z2t-tools', { role: 'toolbar', 'aria-label': T('zt.toolbar') });
    e.bar = h('div.z2t-bar', null, e.host, e.tools);
    e.tabs = h('div.z2t-tabs', { role: 'tablist', 'aria-label': T('zt.tabs') });
    e.tabfix = h('div.z2t-tabfix');
    e.tabbar = h('div.z2t-tabbar', null, e.tabs, e.tabfix);
    e.search = buildSearch();
    e.ghead = h('div.z2t-ghead', { hidden: true });
    e.work = h('div.z2t-work');
    e.dimmer = h('div.z2t-dimmer', { hidden: true });
    e.handle = h('button.z2t-handle', { type: 'button', 'aria-label': T('zt.kbd'), text: '▾', on: { click: function () { toggleKbd(); } } });
    e.kbd = buildKbd();
    e.sheet = h('div.z2t-sheet', { hidden: true });
    e.sink = h('textarea.z2t-sink', { 'aria-label': T('zt.input'), autocapitalize: 'off', autocomplete: 'off', autocorrect: 'off', spellcheck: false, rows: 1 });
    e.root.appendChild(e.bar);
    e.root.appendChild(e.tabbar);
    e.root.appendChild(e.search);
    e.root.appendChild(e.ghead);
    e.root.appendChild(e.work);
    e.root.appendChild(e.handle);
    e.root.appendChild(e.kbd);
    e.root.appendChild(e.sheet);
    e.root.appendChild(e.dimmer);
    e.root.appendChild(e.sink);
    el.appendChild(e.root);
    bindSink();
    /* 実行中の印（•）を、始まり・終わりのたびに付け直す */
    Z.bus.on('command', function () { renderTabs(); });
    Z.bus.on('command-done', function () { renderTabs(); });
    e.work.addEventListener('click', function () { focusSink(); });

    data = Z.store.get('term', null) || {};
    data.aliases = data.aliases || { ll: 'ls -l', la: 'ls -a', l: 'ls -CF' };
    tabs = (data.tabs || []).map(function (o) { return makeTab(o); });
    groups = (data.groups || []).map(function (g) { g.bc = g.bc || { on: false, targets: [] }; return g; })
      .filter(function (g) { g.tabs = g.tabs.filter(function (id) { return zt.byId(id); }); return g.tabs.length; });
    tabs.forEach(function (t) { if (t.groupId && !zt.group(t.groupId)) t.groupId = null; });
    view = data.view || null;
    focusId = data.focus || null;
    if (!tabs.length) {
      tabs.push(makeTab({ name: 'sh', intro: welcome }));
    }
    if (!view || (view.kind === 'tab' && !zt.byId(view.id)) || (view.kind === 'group' && !zt.group(view.id))) view = { kind: 'tab', id: tabs[0].id };
    if (!zt.byId(focusId)) focusId = view.kind === 'tab' ? view.id : zt.group(view.id).tabs[0];
    applyCfg();
    render();
  };

  function welcome(p) {
    p.write('z2term ' + Z.VERSION + ' — play\n', 'a32 b');
    T('zt.welcome').split('\n').forEach(function (l) { p.write(l + '\n', l.indexOf('  ') === 0 ? '' : 'd'); });
  }

  app.show = function () {
    applyCfg();
    render();
    setTimeout(function () { focusSink(); var t = focusedTab(); if (t) t.pane.scrollBottom(); }, 20);
  };
  app.hide = function () { closeSheet(); };
  app.back = function () {
    if (!e.sheet.hidden) { closeSheet(); return true; }
    if (!e.search.hidden) { closeSearch(); return true; }
    return false;
  };
  app.reset = function () { };
  app.destroy = function () { persist(); };

  function applyCfg() {
    e.root.style.setProperty('--fs', cfg.font + 'px');
    e.root.setAttribute('data-pal', cfg.pal);
    e.root.classList.toggle('nowrap', !!cfg.nowrap);
    var kv = kbdVisible();
    e.kbd.hidden = !kv;
    e.handle.textContent = kv ? '▾' : '▴';
    e.sink.setAttribute('inputmode', kv ? 'none' : 'text');
    e.dimmer.hidden = !cfg.dimLevel;
    e.dimmer.style.opacity = String((cfg.dimLevel || 0) / 100);
    renderTools();
  }
  function kbdVisible() {
    if (cfg.kbd != null) return cfg.kbd;
    var coarse = G.matchMedia && G.matchMedia('(pointer: coarse)').matches;
    return !!coarse || G.innerWidth < 600;
  }
  function toggleKbd() { cfg.kbd = !kbdVisible(); saveCfg(); applyCfg(); focusSink(); }

  function render() {
    if (!e.root) return;
    renderTools();
    renderTabs();
    renderWork();
  }
  zt.render = render;

  /* ---------------- ツールバー ---------------- */
  function renderTools() {
    if (!e.tools) return;
    e.tools.textContent = '';
    var t = focusedTab();
    var g = view && view.kind === 'group' ? zt.group(view.id) : null;
    cfg.order.forEach(function (id) {
      var d = TOOLS[id];
      if (!d) return;
      var glyph = d.glyph, on = false;
      if (id === 'light') { glyph = cfg.keepAwake ? '💡' : '🔅'; on = cfg.keepAwake; }
      if (id === 'lock') { glyph = cfg.persistent ? '🔒' : '🔓'; on = cfg.persistent; }
      if (id === 'log') {
        var logOn = g ? g.tabs.some(function (x) { var tt = zt.byId(x); return tt && tt.log.on; }) : (t && t.log.on);
        glyph = logOn ? '🔴' : '⚪'; on = logOn;
      }
      if (id === 'search') on = !e.search.hidden;
      if (id === 'kbd') on = kbdVisible();
      var b = h('button.z2t-tool', { type: 'button', 'aria-label': T(d.label), title: T(d.label), 'aria-pressed': on ? 'true' : 'false', 'data-id': 'tool_' + id, text: glyph });
      b.addEventListener('pointerdown', function (ev) { ev.preventDefault(); });
      if (id === 'clip') Z.clicks(b, pasteClip, clipHistory);
      else if (id === 'light') Z.clicks(b, function () { cfg.keepAwake = !cfg.keepAwake; saveCfg(); renderTools(); Z.phone.toast(cfg.keepAwake ? T('zt.lighton') : T('zt.lightoff')); }, dimSheet);
      else if (id === 'log') Z.clicks(b, toggleLogTool, logSheet);
      else b.addEventListener('click', function () { tool(id); });
      e.tools.appendChild(b);
    });
  }
  function tool(id) {
    if (id === 'menu') menuSheet();
    else if (id === 'lock') {
      cfg.persistent = !cfg.persistent; saveCfg(); renderTools();
      Z.phone.toast(cfg.persistent ? T('zt.lockon') : T('zt.lockoff'));
    }
    else if (id === 'search') { if (e.search.hidden) openSearch(); else closeSearch(); }
    else if (id === 'kbd') toggleKbd();
    else if (id === 'gear') gearSheet();
  }
  function toggleLogTool() {
    var g = view && view.kind === 'group' ? zt.group(view.id) : null;
    if (g) {
      var any = g.tabs.some(function (x) { var tt = zt.byId(x); return tt && tt.log.on; });
      g.tabs.forEach(function (x) { var tt = zt.byId(x); if (tt) { tt.log.on = !any; if (!any) tt.log.file = null; } });
      Z.phone.toast(!any ? T('zt.logon') : T('zt.logoff'));
      renderTools(); markCells(); persistSoon();
      return;
    }
    var t = focusedTab();
    if (t) setLog(t, !t.log.on);
    focusSink();
  }
  function pasteClip() {
    var s = Z.phone.clip.get();
    if (!s) { Z.phone.toast(T('zt.clipempty')); return; }
    deliver({ text: s });
    focusSink();
  }

  /* ---------------- タブ欄 ---------------- */
  function renderTabs() {
    var box = e.tabs;
    box.textContent = '';
    var seen = {};
    tabs.forEach(function (t) {
      if (t.groupId) {
        if (seen[t.groupId]) return;
        seen[t.groupId] = 1;
        var g = zt.group(t.groupId);
        var gsel = view && view.kind === 'group' && view.id === g.id;
        var gb = h('button.z2t-tab.grp', { type: 'button', role: 'tab', 'aria-selected': gsel ? 'true' : 'false', 'data-id': 'group_' + g.name },
          h('span.z2t-tab-dots', { text: '⋮ ' }), h('span', { text: g.name }), g.bc.on ? h('span.z2t-tab-bc', { text: ' 📡' }) : null,
          h('span.z2t-tab-n', { text: ' ' + g.tabs.length }));
        gb.addEventListener('click', function () { zt.showGroup(g); });
        Z.longPress(gb, function () { groupMenu(g); });
        box.appendChild(gb);
        return;
      }
      var sel = view && view.kind === 'tab' && view.id === t.id;
      var b = h('button.z2t-tab', { type: 'button', role: 'tab', 'aria-selected': sel ? 'true' : 'false', 'data-id': 'tab_' + t.name },
        h('span', { text: t.name }), t.log.on ? h('span.z2t-tab-rec', { text: ' ●', 'aria-label': T('zt.logging') }) : null,
        h('span.z2t-tab-act', { text: t.pane.running ? ' •' : '' }));
      Z.clicks(b, function () { zt.showTab(t); focusSink(); }, function () { zt.closeTab(t); });
      Z.longPress(b, function () { tabMenu(t); });
      box.appendChild(b);
    });
    var plus = h('button.z2t-tab.icon', { type: 'button', 'aria-label': T('zt.newtabl'), 'data-id': 'tab_new', text: '＋' });
    plus.addEventListener('click', function () {
      var g = view && view.kind === 'group' ? zt.group(view.id) : null;
      if (g) {
        if (g.tabs.length >= cfg.maxGroup) { Z.phone.toast(T('zt.maxgroup', cfg.maxGroup)); zt.newTab({}); return; }
        var t = zt.newTab({ show: false });
        zt.addToGroup(g, t);
        zt.showGroup(g, t);
        return;
      }
      zt.newTab({});
      focusSink();
    });
    var gui = h('button.z2t-tab.icon', { type: 'button', 'aria-label': T('zt.gui'), text: '🖥', on: { click: function () { Z.phone.alert(T('zt.gui'), T('zt.guimsg')); } } });
    /* ＋ と 🖥 はタブが増えても右端に固定（タブだけが横に流れる） */
    e.tabfix.textContent = '';
    e.tabfix.appendChild(plus);
    e.tabfix.appendChild(gui);
    var selEl = box.querySelector('[aria-selected="true"]');
    if (selEl && selEl.scrollIntoView) selEl.scrollIntoView({ block: 'nearest', inline: 'nearest' });
  }
  function markTabs() { renderTabs(); }

  function tabMenu(t) {
    var items = [
      { label: T('zt.rename'), act: function () { Z.phone.prompt(T('zt.rename'), t.name).then(function (v) { if (v) zt.renameTab(t, v); }); } },
      { label: T('zt.newgroup'), act: function () {
        Z.phone.prompt(T('zt.groupname'), T('zt.group') + ' ' + (groups.length + 1)).then(function (v) {
          if (v == null) return;
          var r = zt.newGroup(v, [t]);
          if (r && r.error) Z.phone.toast(r.error);
        });
      } }
    ];
    groups.forEach(function (g) {
      if (t.groupId !== g.id) items.push({ label: T('zt.addto', g.name), act: function () { zt.addToGroup(g, t); zt.showGroup(g, t); } });
    });
    if (t.groupId) items.push({ label: T('zt.leave'), act: function () { zt.removeFromGroup(t); zt.showTab(t); } });
    items.push({ label: t.log.on ? T('zt.logstop') : T('zt.logstart'), act: function () { setLog(t, !t.log.on); } });
    items.push({ label: T('zt.close'), act: function () { zt.closeTab(t); } });
    Z.phone.menu(t.name, items);
  }
  function groupMenu(g) {
    Z.phone.menu('⋮ ' + g.name, [
      { label: T('zt.rename'), act: function () { Z.phone.prompt(T('zt.rename'), g.name).then(function (v) { if (v && !zt.findGroup(v)) { g.name = v; render(); persistSoon(); } }); } },
      { label: g.bc.on ? T('zt.bcoff') : T('zt.bcon'), act: function () { zt.showGroup(g); zt.setBroadcast(g, !g.bc.on); } },
      { label: T('zt.ungroup'), act: function () { zt.deleteGroup(g); } }
    ]);
  }

  /* ---------------- 端末の場所（1 枚 / グループの枠） ---------------- */
  function renderWork() {
    var w = e.work;
    w.textContent = '';
    if (!view) return;
    if (view.kind === 'tab') {
      e.ghead.hidden = true;
      var t = zt.byId(view.id);
      if (!t) return;
      w.appendChild(h('div.z2t-single', null, t.pane.el));
      focusId = t.id;
      t.pane.setFocused(document.activeElement === e.sink);
      setTimeout(function () { t.pane.scrollBottom(); }, 0);
      return;
    }
    var g = zt.group(view.id);
    if (!g) return;
    renderGhead(g);
    w.appendChild(renderNode(g, g.layout));
    markCells();
    g.tabs.forEach(function (id) { var x = zt.byId(id); if (x) setTimeout(function () { x.pane.scrollBottom(); }, 0); });
  }

  function renderGhead(g) {
    var gh = e.ghead;
    gh.hidden = false;
    gh.textContent = '';
    gh.appendChild(h('span.z2t-gname', { text: '⋮ ' + g.name }));
    var bcb = h('button.z2t-bc', { type: 'button', 'aria-pressed': g.bc.on ? 'true' : 'false', 'data-id': 'broadcast', text: '📡 ' + (g.bc.on ? T('zt.bcstate.on') : T('zt.bcstate.off')) });
    bcb.addEventListener('click', function () { zt.setBroadcast(g, !g.bc.on); focusSink(); });
    gh.appendChild(bcb);
    gh.appendChild(h('span.z2t-ghint', { text: g.bc.on ? T('zt.bchint') : T('zt.draghint') }));
  }

  function renderNode(g, n) {
    if (!n) return h('div');
    if (n.t) return cell(g, zt.byId(n.t));
    var s = h('div.z2t-split', { 'data-d': n.d });
    s.appendChild(renderNode(g, n.a));
    s.appendChild(renderNode(g, n.b));
    return s;
  }

  function cell(g, t) {
    if (!t) return h('div');
    var c = h('div.z2t-cell', { 'data-tab': t.id });
    var head = h('div.z2t-ph', { 'data-id': 'pane_' + t.name });
    var grip = h('span.z2t-grip', { text: '⠿ ' + t.name, title: T('zt.drag') });
    head.appendChild(grip);
    head.appendChild(h('span.z2t-ph-gap'));
    if (g.bc.on) {
      var cb = h('input', { type: 'checkbox', checked: g.bc.targets.indexOf(t.id) >= 0, 'aria-label': T('zt.bctarget', t.name) });
      cb.addEventListener('change', function () { zt.setTarget(g, t, cb.checked); });
      head.appendChild(h('label.z2t-ph-bc', null, cb, h('span', { text: '📡' })));
    }
    head.appendChild(h('button.z2t-ph-b', { type: 'button', 'aria-label': T('zt.log'), text: t.log.on ? '🔴' : '⚪', on: { click: function (ev) { ev.stopPropagation(); setLog(t, !t.log.on); } } }));
    head.appendChild(h('button.z2t-ph-b', { type: 'button', 'aria-label': T('zt.leave'), text: '✕', on: { click: function (ev) { ev.stopPropagation(); zt.removeFromGroup(t); } } }));
    c.appendChild(head);
    c.appendChild(t.pane.el);
    dragSplit(g, t, head);
    return c;
  }

  function markCells() {
    var g = view && view.kind === 'group' ? zt.group(view.id) : null;
    Array.prototype.forEach.call(e.work.querySelectorAll('.z2t-cell'), function (c) {
      var id = c.getAttribute('data-tab');
      c.classList.toggle('focused', id === focusId);
      c.classList.toggle('bc-target', !!(g && g.bc.on && g.bc.targets.indexOf(id) >= 0 && id !== focusId));
    });
  }

  /* 枠の見出しをドラッグ → ほかの枠の上下左右へ落とすと、そこで分割 */
  function dragSplit(g, t, head) {
    var drag = null;
    head.addEventListener('pointerdown', function (ev) {
      if (ev.target.closest('button, input, label')) return;
      if (ev.button !== 0) return;
      setFocus(t);
      drag = { x: ev.clientX, y: ev.clientY, moved: false, ind: null };
      head.setPointerCapture(ev.pointerId);
    });
    head.addEventListener('pointermove', function (ev) {
      if (!drag) return;
      if (!drag.moved && Math.abs(ev.clientX - drag.x) + Math.abs(ev.clientY - drag.y) < 8) return;
      drag.moved = true;
      var hit = whereDrop(ev, t);
      if (!drag.ind) { drag.ind = h('div.z2t-drop'); e.work.appendChild(drag.ind); }
      if (!hit) { drag.ind.hidden = true; drag.hit = null; return; }
      drag.hit = hit;
      var wr = e.work.getBoundingClientRect(), r = hit.rect;
      var x = r.left - wr.left, y = r.top - wr.top, w = r.width, hh = r.height;
      if (hit.edge === 'right') x += w / 2;
      if (hit.edge === 'bottom') y += hh / 2;
      if (hit.edge === 'left' || hit.edge === 'right') w /= 2; else hh /= 2;
      drag.ind.hidden = false;
      drag.ind.style.cssText = 'left:' + x + 'px;top:' + y + 'px;width:' + w + 'px;height:' + hh + 'px';
    });
    function end() {
      if (!drag) return;
      var d = drag; drag = null;
      if (d.ind) d.ind.remove();
      if (d.moved && d.hit) dropOn(g, t.id, d.hit.id, d.hit.edge);
    }
    head.addEventListener('pointerup', end);
    head.addEventListener('pointercancel', end);
  }
  function whereDrop(ev, t) {
    var els = document.elementsFromPoint(ev.clientX, ev.clientY);
    var c = null;
    for (var i = 0; i < els.length; i++) { if (els[i].classList && els[i].classList.contains('z2t-cell')) { c = els[i]; break; } }
    if (!c || c.getAttribute('data-tab') === t.id) return null;
    var r = c.getBoundingClientRect();
    var fx = (ev.clientX - r.left) / r.width, fy = (ev.clientY - r.top) / r.height;
    var d = { left: fx, right: 1 - fx, top: fy, bottom: 1 - fy };
    var edge = Object.keys(d).sort(function (a, b) { return d[a] - d[b]; })[0];
    return { id: c.getAttribute('data-tab'), edge: edge, rect: r };
  }
  function dropOn(g, dragId, targetId, edge) {
    var tree = removeLeaf(g.layout, dragId);
    var a = leaf(dragId), b = leaf(targetId);
    var node = edge === 'left' ? { d: 'h', a: a, b: b } : edge === 'right' ? { d: 'h', a: b, b: a } : edge === 'top' ? { d: 'v', a: a, b: b } : { d: 'v', a: b, b: a };
    g.layout = replaceLeaf(tree, targetId, node);
    render(); persistSoon();
    Z.bus.emit('split', { group: g, edge: edge });
  }
  zt._tree = { removeLeaf: removeLeaf, replaceLeaf: replaceLeaf, appendLeaf: appendLeaf };

  /* ---------------- 🔍 検索 ---------------- */
  var sIdx = -1;
  function buildSearch() {
    var inp = h('input.z2t-s-in', { type: 'search', placeholder: T('zt.searchph'), 'aria-label': T('zt.search') });
    var cnt = h('span.z2t-s-n', { 'aria-live': 'polite' });
    var box = h('div.z2t-search', { hidden: true, role: 'search' }, inp, cnt,
      h('button.z2t-s-b', { type: 'button', 'aria-label': T('zt.prev'), text: '↑', on: { click: function () { jump(-1); } } }),
      h('button.z2t-s-b', { type: 'button', 'aria-label': T('zt.next'), text: '↓', on: { click: function () { jump(1); } } }),
      h('button.z2t-s-b', { type: 'button', 'aria-label': T('ph.close'), text: '✕', on: { click: closeSearch } }));
    inp.addEventListener('input', function () { doSearch(inp.value); });
    inp.addEventListener('keydown', function (ev) {
      ev.stopPropagation();
      if (ev.key === 'Enter') { ev.preventDefault(); jump(ev.shiftKey ? -1 : 1); }
      if (ev.key === 'Escape') { ev.preventDefault(); closeSearch(); }
    });
    e.sIn = inp; e.sCnt = cnt;
    return box;
  }
  function shownTabs() {
    if (!view) return [];
    if (view.kind === 'tab') { var t = zt.byId(view.id); return t ? [t] : []; }
    var g = zt.group(view.id);
    return g ? g.tabs.map(zt.byId).filter(Boolean) : [];
  }
  function allMarks() { var m = []; shownTabs().forEach(function (t) { if (t.pane.marks) m = m.concat(t.pane.marks); }); return m; }
  function doSearch(q) {
    var re = q ? new RegExp(q.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'gi') : null;
    var n = 0;
    shownTabs().forEach(function (t) { n += t.pane.highlight(re); });
    e.sCnt.textContent = q ? T('zt.hits', n) : '';
    sIdx = -1;
    if (n) jump(1, true);
  }
  function jump(d, fromEnd) {
    var m = allMarks();
    if (!m.length) return;
    if (sIdx < 0 && fromEnd) sIdx = m.length;
    m.forEach(function (x) { x.classList.remove('cur'); });
    sIdx = (sIdx + d + m.length) % m.length;
    m[sIdx].classList.add('cur');
    m[sIdx].scrollIntoView({ block: 'center' });
    e.sCnt.textContent = (sIdx + 1) + ' / ' + m.length;
  }
  function openSearch() { e.search.hidden = false; renderTools(); e.sIn.focus(); if (e.sIn.value) doSearch(e.sIn.value); }
  function closeSearch() {
    e.search.hidden = true;
    shownTabs().forEach(function (t) { t.pane.highlight(null); });
    renderTools(); focusSink();
  }

  /* ---------------- 内蔵キーボード ---------------- */
  var ROWS = [
    [['ESC', 'Escape', 'w15'], '1', '2', '3', '4', '5', '6', '7', '8', '9', '0', ['⌫', 'Backspace', 'w15']],
    [['TAB', 'Tab', 'w15'], 'q', 'w', 'e', 'r', 't', 'y', 'u', 'i', 'o', 'p'],
    [['⇧', 'Shift', 'w15'], 'a', 's', 'd', 'f', 'g', 'h', 'j', 'k', 'l', ['⏎', 'Enter', 'w15']],
    [['CTRL', 'Ctrl', 'w15'], 'z', 'x', 'c', 'v', 'b', 'n', 'm', ',', '.', '/'],
    [['?#', 'Sym', 'w15'], ['ALT', 'Alt', 'w15'], ['space', ' ', 'w3'], ['←', 'ArrowLeft'], ['↓', 'ArrowDown'], ['↑', 'ArrowUp'], ['→', 'ArrowRight']]
  ];
  var SYM = {
    '1': '!', '2': '@', '3': '#', '4': '$', '5': '%', '6': '^', '7': '&', '8': '*', '9': '(', '0': ')',
    q: '`', w: '~', e: '\'', r: '"', t: '<', y: '>', u: ':', i: ';', o: '{', p: '}',
    a: '-', s: '_', d: '+', f: '=', g: '|', h: '\\', j: '[', k: ']', l: '?',
    z: '`', x: '~', c: '\'', v: '"', b: '<', n: '>', m: '?', ',': ':', '.': ';', '/': '{'
  };
  var mods = { Shift: false, Ctrl: false, Alt: false, Sym: false };
  function buildKbd() {
    var k = h('div.z2t-kbd', { role: 'group', 'aria-label': T('zt.kbdlabel') });
    ROWS.forEach(function (row) {
      var r = h('div.z2t-krow');
      row.forEach(function (key) {
        var label, val, cls = '';
        if (Array.isArray(key)) { label = key[0]; val = key[1]; cls = key[2] || ''; } else { label = key; val = key; }
        var b = h('button.z2t-key', { type: 'button', cls: cls || null, 'data-k': val, 'aria-label': label === ' ' ? 'space' : label },
          h('span.z2t-key-l', { text: label }),
          SYM[val] ? h('span.z2t-key-s', { text: SYM[val] }) : null);
        b.addEventListener('pointerdown', function (ev) { ev.preventDefault(); });
        b.addEventListener('click', function () { kpress(val); });
        r.appendChild(b);
      });
      k.appendChild(r);
    });
    return k;
  }
  function kpress(v) {
    if (v === 'Shift' || v === 'Ctrl' || v === 'Alt' || v === 'Sym') {
      mods[v] = !mods[v];
      renderMods();
      return;
    }
    var named = { Escape: 1, Backspace: 1, Tab: 1, Enter: 1, ArrowLeft: 1, ArrowDown: 1, ArrowUp: 1, ArrowRight: 1 };
    if (named[v]) deliver({ key: v, ctrl: mods.Ctrl, alt: mods.Alt });
    else {
      var ch = v;
      if (mods.Sym && SYM[v]) ch = SYM[v];
      else if (mods.Shift) ch = v.toUpperCase();
      if (mods.Ctrl || mods.Alt) deliver({ key: ch, ctrl: mods.Ctrl, alt: mods.Alt });
      else deliver({ text: ch });
    }
    if (mods.Shift || mods.Ctrl || mods.Alt) { mods.Shift = mods.Ctrl = mods.Alt = false; renderMods(); }
  }
  function renderMods() {
    Array.prototype.forEach.call(e.kbd.querySelectorAll('.z2t-key'), function (b) {
      var v = b.getAttribute('data-k');
      if (mods[v] !== undefined) b.setAttribute('aria-pressed', mods[v] ? 'true' : 'false');
      var l = b.querySelector('.z2t-key-l');
      if (v.length === 1 && v !== ' ') l.textContent = mods.Sym && SYM[v] ? SYM[v] : mods.Shift ? v.toUpperCase() : v;
    });
  }
  zt.kpress = kpress;

  /* ---------------- 下から出る板（📜 ⚙ など） ---------------- */
  function openSheet(title, body) {
    var s = e.sheet;
    s.textContent = '';
    s.appendChild(h('div.z2t-sheet-h', null, h('span', { text: title }),
      h('button.z2t-s-b', { type: 'button', 'aria-label': T('ph.close'), text: '✕', on: { click: closeSheet } })));
    s.appendChild(body);
    s.hidden = false;
  }
  function closeSheet() { if (!e.sheet) return; e.sheet.hidden = true; e.sheet.textContent = ''; focusSink(); }
  zt.closeSheet = closeSheet;

  function tabbed(names, build, start) {
    var wrap = h('div.z2t-tabbed');
    var bar = h('div.z2p-seg.small', { role: 'tablist' });
    var body = h('div.z2t-tabbed-b');
    function sel(i) {
      Array.prototype.forEach.call(bar.children, function (b, j) { b.setAttribute('aria-selected', i === j ? 'true' : 'false'); });
      body.textContent = '';
      build(i, body);
    }
    names.forEach(function (n, i) { bar.appendChild(h('button', { type: 'button', role: 'tab', text: n, on: { click: function () { sel(i); } } })); });
    wrap.appendChild(bar); wrap.appendChild(body);
    sel(start || 0);
    return wrap;
  }

  var SNIPPETS = [
    'z2help', 'z2-notify "こんにちは"', 'z2-toast "やあ"', 'z2-torch', 'z2-state', 'z2-battery', 'ls -la ~',
    'z2-session list', 'z2-when list', 'z2-action list', 'z2-app list', 'z2-tile list', 'date', 'z2doctor'
  ];
  function insertCmd(s) { closeSheet(); var t = focusedTab(); if (t && !t.pane.running) { t.pane.insert(s); } focusSink(); }
  function menuSheet(start) {
    openSheet('📜', tabbed([T('zt.m.cmds'), T('zt.m.hist'), T('zt.m.auto'), 'SSH', T('zt.m.server')], function (i, body) {
      if (i === 0) {
        var l = h('div.z2t-list');
        (Z.lang === 'ja' ? SNIPPETS : SNIPPETS.map(function (s) { return s.replace('こんにちは', 'Hello').replace('やあ', 'Hi'); })).forEach(function (s) {
          l.appendChild(h('button.z2t-li.mono', { type: 'button', text: s, on: { click: function () { insertCmd(s); } } }));
        });
        body.appendChild(l);
      } else if (i === 1) {
        var f = h('input.z2p-input', { type: 'search', placeholder: T('zt.filter'), 'aria-label': T('zt.filter') });
        var l2 = h('div.z2t-list');
        function fill() {
          l2.textContent = '';
          var t = focusedTab();
          var hs = t ? t.sh.history.slice().reverse() : [];
          hs = hs.filter(function (x, idx, arr) { return arr.indexOf(x) === idx && (!f.value || x.toLowerCase().indexOf(f.value.toLowerCase()) >= 0); });
          if (!hs.length) l2.appendChild(h('p.z2p-empty', { text: T('zt.nohist') }));
          hs.slice(0, 100).forEach(function (s) { l2.appendChild(h('button.z2t-li.mono', { type: 'button', text: s, on: { click: function () { insertCmd(s); } } })); });
        }
        f.addEventListener('input', fill);
        f.addEventListener('keydown', function (ev) { ev.stopPropagation(); });
        body.appendChild(f); body.appendChild(l2); fill();
      } else if (i === 2) {
        Z.when.renderList(body);
      } else {
        body.appendChild(h('p.z2t-nonet', { text: T('zt.nonet') }));
        body.appendChild(h('p.z2p-dim-note', { text: T('zt.nonetsub') }));
      }
    }, start));
  }
  zt.menuSheet = menuSheet;

  function clipHistory() {
    var l = h('div.z2t-list');
    var hs = Z.phone.clip.hist();
    if (!hs.length) l.appendChild(h('p.z2p-empty', { text: T('zt.clipempty') }));
    hs.forEach(function (s) {
      l.appendChild(h('button.z2t-li.mono', { type: 'button', text: s.length > 120 ? s.slice(0, 120) + '…' : s, on: { click: function () { closeSheet(); deliver({ text: s }); } } }));
    });
    openSheet('📋 ' + T('zt.cliphist'), l);
  }

  function dimSheet() {
    var r = h('input', { type: 'range', min: '0', max: '80', value: String(cfg.dimLevel || 0), 'aria-label': T('zt.dim') });
    r.addEventListener('input', function () { cfg.dimLevel = parseInt(r.value, 10); saveCfg(); applyCfg(); });
    openSheet('🔅 ' + T('zt.dim'), h('div.z2t-form', null,
      h('label.z2t-row', null, h('span', { text: T('zt.dim') }), r),
      h('button.z2p-btn.ghost', { type: 'button', text: T('zt.dimreset'), on: { click: function () { cfg.dimLevel = 0; saveCfg(); applyCfg(); r.value = '0'; } } }),
      h('p.z2p-dim-note', { text: T('zt.dimnote') })));
  }

  function logSheet() {
    var mask = h('input', { type: 'checkbox', checked: cfg.mask });
    mask.addEventListener('change', function () { cfg.mask = mask.checked; saveCfg(); });
    var auto = h('input', { type: 'checkbox', checked: cfg.autoLog });
    auto.addEventListener('change', function () { cfg.autoLog = auto.checked; saveCfg(); });
    openSheet('⚪ ' + T('zt.log'), h('div.z2t-form', null,
      h('div.z2t-row', null, h('span', { text: T('zt.logdir') }), h('code', { text: '~/z2term-log/' })),
      h('div.z2t-row', null, h('span', { text: T('zt.logname') }), h('code', { text: '{date}-{tab}.txt' })),
      h('div.z2t-row', null, h('span', { text: T('zt.logtime') }), h('code', { text: 'yyyy-MM-dd_HHmm' })),
      h('label.z2t-row', null, h('span', { text: T('zt.logmask') }), mask),
      h('label.z2t-row', null, h('span', { text: T('zt.autolog') }), auto),
      h('p.z2p-dim-note', { text: T('zt.lognote') })));
  }

  function gearSheet() {
    var f = h('div.z2t-form');
    var fs = h('input', { type: 'range', min: '10', max: '20', value: String(cfg.font), 'aria-label': T('zt.font') });
    var fsv = h('output', { text: cfg.font + 'px' });
    fs.addEventListener('input', function () { cfg.font = parseInt(fs.value, 10); fsv.textContent = cfg.font + 'px'; saveCfg(); applyCfg(); });
    f.appendChild(h('h3.z2t-fh', { text: T('zt.display') }));
    f.appendChild(h('label.z2t-row', null, h('span', { text: T('zt.font') }), fs, fsv));
    var pal = h('select', { 'aria-label': T('zt.palette') });
    [['green', T('zt.pal.green')], ['amber', T('zt.pal.amber')], ['mono', T('zt.pal.mono')], ['paper', T('zt.pal.paper')]].forEach(function (p) {
      pal.appendChild(h('option', { value: p[0], text: p[1], selected: cfg.pal === p[0] }));
    });
    pal.addEventListener('change', function () { cfg.pal = pal.value; saveCfg(); applyCfg(); });
    f.appendChild(h('label.z2t-row', null, h('span', { text: T('zt.palette') }), pal));
    var nw = h('input', { type: 'checkbox', checked: cfg.nowrap });
    nw.addEventListener('change', function () { cfg.nowrap = nw.checked; saveCfg(); applyCfg(); });
    f.appendChild(h('label.z2t-row', null, h('span', { text: T('zt.nowrap') }), nw));
    f.appendChild(h('h3.z2t-fh', { text: T('zt.order') }));
    var ol = h('ol.z2t-order');
    function fillOrder() {
      ol.textContent = '';
      cfg.order.forEach(function (id, i) {
        ol.appendChild(h('li', null, h('span', { text: TOOLS[id].glyph + ' ' + T(TOOLS[id].label) }),
          h('button.z2t-s-b', { type: 'button', 'aria-label': T('zt.up'), text: '↑', disabled: i === 0, on: { click: function () { mv(i, -1); } } }),
          h('button.z2t-s-b', { type: 'button', 'aria-label': T('zt.down'), text: '↓', disabled: i === cfg.order.length - 1, on: { click: function () { mv(i, 1); } } })));
      });
    }
    function mv(i, d) { var o = cfg.order; var x = o[i]; o[i] = o[i + d]; o[i + d] = x; saveCfg(); renderTools(); fillOrder(); }
    fillOrder();
    f.appendChild(ol);
    f.appendChild(h('h3.z2t-fh', { text: T('zt.tabsgroups') }));
    var mg = h('input', { type: 'number', min: '2', max: '8', value: String(cfg.maxGroup), 'aria-label': T('zt.maxg') });
    mg.addEventListener('change', function () { cfg.maxGroup = Math.max(2, Math.min(8, parseInt(mg.value, 10) || 4)); mg.value = String(cfg.maxGroup); saveCfg(); });
    mg.addEventListener('keydown', function (ev) { ev.stopPropagation(); });
    f.appendChild(h('label.z2t-row', null, h('span', { text: T('zt.maxg') }), mg));
    var al = h('input', { type: 'checkbox', checked: cfg.autoLog });
    al.addEventListener('change', function () { cfg.autoLog = al.checked; saveCfg(); });
    f.appendChild(h('label.z2t-row', null, h('span', { text: T('zt.autolog') }), al));
    f.appendChild(h('h3.z2t-fh', { text: T('zt.other') }));
    f.appendChild(h('div.z2t-row', null, h('span', { text: T('set.lang') }), h('div.z2p-seg.small', null,
      h('button', { type: 'button', 'aria-checked': Z.lang === 'ja' ? 'true' : 'false', role: 'radio', text: '日本語', on: { click: function () { if (Z.lang !== 'ja') Z.setLang('ja'); } } }),
      h('button', { type: 'button', 'aria-checked': Z.lang === 'en' ? 'true' : 'false', role: 'radio', text: 'English', on: { click: function () { if (Z.lang !== 'en') Z.setLang('en'); } } }))));
    f.appendChild(h('button.z2t-li', { type: 'button', text: T('zt.linux'), on: { click: function () { Z.phone.alert(T('zt.linux'), T('cmd.noos', 'apk')); } } }));
    f.appendChild(h('button.z2t-li', { type: 'button', text: T('zt.servers'), on: { click: function () { Z.phone.alert(T('zt.servers'), T('zt.nonet')); } } }));
    openSheet('⚙ ' + T('zt.gear'), f);
  }

  Z.registerApp(app);
})(typeof window !== 'undefined' ? window : globalThis);
