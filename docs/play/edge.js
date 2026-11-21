/* z2term play — エッジパネル（z2-edge）。
   本物（proot/Z2EdgeScripts.kt）と同じ書き方で、取っ手（端のバー / 浮かぶボタン）と板の項目を定義する。
   バーを内側へ払う（--open tap|both なら押しても）と板が端から出る。外側を押すと閉じる。
   項目の型: run（既定）/ text / toggle / note。ほかの型は「このモックには無い」と断る。
   保存: z2mock:phone の edge */
(function (G) {
  'use strict';
  var Z = G.Z2P = G.Z2P || {};
  var h = function () { return Z.h.apply(null, arguments); };
  var T = function () { return Z.t.apply(null, arguments); };
  var E = Z.edge = {};
  var layer = null, openId = null, timers = [];

  function data() {
    var st = Z.phone.st;
    if (!st.edge || typeof st.edge !== 'object') st.edge = { on: false, panels: {} };
    st.edge.panels = st.edge.panels || {};
    return st.edge;
  }
  function save() { Z.phone.save(); render(); }
  function panel(id, create) {
    var d = data();
    if (!d.panels[id] && create) d.panels[id] = { label: id, handle: null, opts: {}, items: {}, seq: 0, badge: '' };
    return d.panels[id] || null;
  }
  var IDRE = /^[A-Za-z0-9_-]{1,64}$/;

  /* ======================= 画面 ======================= */
  E.build = function (screen) {
    layer = h('div.z2p-edge');
    screen.appendChild(layer);
    openId = null;
    render();
  };

  function render() {
    if (!layer) return;
    layer.textContent = '';
    var d = data();
    if (!d.on) return;
    Object.keys(d.panels).forEach(function (id) {
      var p = d.panels[id];
      if (!p.handle) return;
      layer.appendChild(p.handle.kind === 'button' ? buttonHandle(id, p) : barHandle(id, p));
    });
    if (openId && d.panels[openId]) layer.appendChild(board(openId, d.panels[openId]));
  }
  E.render = render;

  function pct(v, def) { var n = parseFloat(v); return isFinite(n) ? n : def; }

  function barHandle(id, p) {
    var hd = p.handle;
    var side = hd.side === 'left' ? 'left' : 'right';
    var el = h('div.z2p-ep-bar', { role: 'button', tabindex: '0', 'aria-label': T('edge.handle', p.label || id), 'data-id': 'edge_' + id, 'data-side': side,
      style: { top: pct(hd.offset, 30) + '%', height: 'max(44px, ' + pct(hd.length, 8) + '%)', '--w': Math.max(3, Math.min(48, pct(hd.size, 6))) + 'px' } });
    if (p.badge) el.appendChild(h('span.z2p-ep-badge', { text: p.badge }));
    var openBy = hd.open || 'swipe';
    var x0 = null, done = false;
    el.addEventListener('pointerdown', function (e) { x0 = e.clientX; done = false; try { el.setPointerCapture(e.pointerId); } catch (er) { /* 無視 */ } });
    el.addEventListener('pointermove', function (e) {
      if (x0 == null || done) return;
      var dx = e.clientX - x0;
      if ((side === 'right' && dx < -18) || (side === 'left' && dx > 18)) { done = true; open(id); }
    });
    el.addEventListener('pointerup', function () { x0 = null; });
    el.addEventListener('click', function () {
      if (done) return;
      if (openBy === 'tap' || openBy === 'both') open(id);
      else Z.phone.toast(T('edge.swipehint'));
    });
    el.addEventListener('keydown', function (e) { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); open(id); } });
    return el;
  }

  function buttonHandle(id, p) {
    var hd = p.handle;
    var size = Math.max(32, Math.min(96, pct(hd.size, 48)));
    var at = hd.at || [85, 60];
    var el = h('button.z2p-ep-btn', { type: 'button', 'aria-label': hd.label || p.label || id, 'data-id': 'edge_' + id,
      style: { left: 'calc(' + at[0] + '% - ' + size / 2 + 'px)', top: 'calc(' + at[1] + '% - ' + size / 2 + 'px)', width: size + 'px', height: size + 'px' } },
      h('span', { text: hd.label || (hd.run ? '▶' : '☰') }));
    if (p.badge) el.appendChild(h('span.z2p-ep-badge', { text: p.badge }));
    var d = null;
    el.addEventListener('pointerdown', function (e) { d = { x: e.clientX, y: e.clientY, moved: false }; try { el.setPointerCapture(e.pointerId); } catch (er) { /* 無視 */ } });
    el.addEventListener('pointermove', function (e) {
      if (!d) return;
      if (!d.moved && Math.abs(e.clientX - d.x) + Math.abs(e.clientY - d.y) < 8) return;
      d.moved = true;
      var r = layer.getBoundingClientRect();
      hd.at = [Math.round((e.clientX - r.left) / r.width * 100), Math.round((e.clientY - r.top) / r.height * 100)];
      el.style.left = 'calc(' + hd.at[0] + '% - ' + size / 2 + 'px)';
      el.style.top = 'calc(' + hd.at[1] + '% - ' + size / 2 + 'px)';
    });
    el.addEventListener('pointerup', function () {
      if (d && d.moved) { Z.phone.save(); Z.eatClick(el); }
      d = null;
    });
    el.addEventListener('click', function () {
      if (hd.run) { runCmd(hd.run, 'toast'); return; }
      open(id);
    });
    return el;
  }

  /* ---------------- 板 ---------------- */
  function open(id) {
    var p = panel(id);
    if (!p) return false;
    Z.phone.shade(false);
    openId = id;
    render();
    Z.bus.emit('edge-open', id);
    refreshAll(p);
    return true;
  }
  function close() {
    if (!openId) return;
    var b = layer && layer.querySelector('.z2p-ep-board');
    openId = null;
    timers.forEach(clearInterval); timers = [];
    if (b && !Z.reduceMotion() && b.animate) {
      var side = b.getAttribute('data-side');
      var scrim = b.parentNode;
      b.animate([{ transform: 'none', opacity: 1 }, { transform: 'translateX(' + (side === 'left' ? '-' : '') + '105%)', opacity: 0.4 }], { duration: 200, easing: 'cubic-bezier(0.2, 0, 0, 1)' }).onfinish = function () { if (scrim) scrim.remove(); render(); };
      if (scrim) scrim.animate([{ opacity: 1 }, { opacity: 0 }], { duration: 200 });
      return;
    }
    render();
  }
  E.open = open;
  E.close = close;
  E.isOpen = function () { return !!openId; };

  function sortedItems(p) {
    return Object.keys(p.items).map(function (k) { return [k, p.items[k]]; })
      .sort(function (a, b) { return ((+a[1].order || a[1]._n) - (+b[1].order || b[1]._n)); });
  }

  function board(id, p) {
    var side = (p.opts.place === 'left' || (p.handle && p.handle.side === 'left' && p.opts.place !== 'right')) ? 'left' : 'right';
    var scrim = h('div.z2p-ep-scrim', { on: { click: function (e) { if (e.target === scrim) close(); } } });
    var cols = p.opts.columns && p.opts.columns !== 'auto' ? Math.max(1, Math.min(16, parseInt(p.opts.columns, 10) || 3)) : 3;
    var b = h('div.z2p-ep-board', { role: 'dialog', 'aria-label': p.label || id, 'data-side': side,
      style: { width: p.opts.width || '74%', 'max-height': p.opts.height || '78%', '--cols': String(p.opts.flow === 'vertical' ? 1 : cols) } });
    if (p.opts.title !== 'off') {
      b.appendChild(h('div.z2p-ep-title', null, h('span', { text: p.label || id }),
        p.opts.settings === 'off' ? null : h('button.z2p-ep-gear', { type: 'button', 'aria-label': T('edge.settings'), text: '⚙', on: { click: function () { showDef(id); } } })));
    }
    var grid = h('div.z2p-ep-grid');
    var items = sortedItems(p);
    if (!items.length) grid.appendChild(h('p.z2p-empty', { text: T('edge.noitems', id) }));
    items.forEach(function (kv) { grid.appendChild(itemEl(id, kv[0], kv[1])); });
    b.appendChild(grid);
    scrim.appendChild(b);
    if (!Z.reduceMotion() && b.animate) {
      b.animate([{ transform: 'translateX(' + (side === 'left' ? '-' : '') + '105%)', opacity: 0.4 }, { transform: 'none', opacity: 1 }], { duration: 260, easing: 'cubic-bezier(0.2, 0, 0, 1)' });
      scrim.animate([{ opacity: 0 }, { opacity: 1 }], { duration: 220 });
    }
    return scrim;
  }

  function iconOf(it) {
    var ic = it.icon || '';
    if (!ic && /^\s*z2-action\s+pick\b/.test(it.run || '')) return h('span.z2p-ep-ic.pick', { text: '▶≡' });
    var m = /^@app:([\w.]+)/.exec(ic) || (!ic && /^z2-intent\b.*\s-p\s+([\w.]+)/.exec(it.run || ''));
    if (m) { var a = Z.appByPkg(m[1]); if (a) return Z.iconEl(a.id, true); }
    if (ic && ic[0] !== '@') return h('span.z2p-ep-ic', { text: ic });
    var t = it.type || 'run';
    return h('span.z2p-ep-ic', { text: t === 'text' ? '≡' : t === 'toggle' ? '◐' : t === 'note' ? '✎' : '▶' });
  }

  function itemEl(pid, name, it) {
    var type = it.type || 'run';
    var label = it.label || name;
    var el = h('button.z2p-ep-item', { type: 'button', 'data-id': 'edge_' + pid + '_' + name, 'data-type': type, 'aria-label': label },
      iconOf(it), h('span.z2p-ep-l', { text: label }));
    if (type === 'text') el.appendChild(h('span.z2p-ep-v', { text: it._v != null ? it._v : '…' }));
    if (type === 'toggle' || it['button-state'] === 'on') el.setAttribute('aria-pressed', it._on ? 'true' : 'false');
    if (it._res) el.appendChild(h('span.z2p-ep-v.res', { text: it._res }));
    el.addEventListener('click', function () { act(pid, name, it); });
    return el;
  }

  function out(s) { return String(s || '').replace(/\x1b\[[0-9;]*m/g, '').replace(/\n+$/, ''); }
  function lastLine(s) { var l = out(s).split('\n'); return l[l.length - 1] || ''; }
  function parseOn(s) { return /^(on|true|1|yes)$/i.test(lastLine(s).trim().replace(/^.*=/, '')); }

  function runCmd(cmd, mode) {
    return Z.runBackground(cmd, { aborted: false }, 'z2-edge').then(function (r) {
      if (mode === 'toast' && out(r.out)) Z.phone.toast(lastLine(r.out));
      if (mode === 'notify' && out(r.out)) Z.phone.notify({ title: 'z2-edge', body: out(r.out), app: 'z2term' });
      return r;
    });
  }

  function act(pid, name, it) {
    var type = it.type || 'run';
    var p = panel(pid);
    Z.bus.emit('edge-run', { panel: pid, item: name, type: type });
    if (type === 'note') {
      var f = it.file ? Z.fs.norm(it.file, '/root') : '/root/.z2term/edge/' + pid + '/' + name + '.txt';
      try { Z.fs.mkdir(f.replace(/\/[^/]*$/, ''), true, true); } catch (e) { /* 無視 */ }
      Z.phone.edit(f);
      return;
    }
    if (!it.run) { Z.phone.toast(T('edge.norun')); return; }
    if (type === 'text') { refresh(p, name, it); return; }
    if (type === 'toggle' || it['button-state'] === 'on') {
      var cmd = it._on && it.off ? it.off : it.run;
      runCmd(cmd, it.out || 'none').then(function () {
        if (it.state) return refreshState(p, name, it);
        it._on = !it._on; render();
      });
      return;
    }
    var before = Z.phone.current();
    runCmd(it.run, it.out || 'none').then(function (r) {
      if (it.out === 'panel') { it._res = lastLine(r.out); render(); return; }
      if (Z.phone.current() !== before || it.out !== 'panel') close();
    });
  }

  function refresh(p, name, it) {
    if (!it.run) return Promise.resolve();
    return Z.runBackground(it.run, { aborted: false }, 'z2-edge').then(function (r) {
      it._v = lastLine(r.out);
      if (openId) updateItem(p, name, it);
    });
  }
  function refreshState(p, name, it) {
    return Z.runBackground(it.state, { aborted: false }, 'z2-edge').then(function (r) {
      it._on = parseOn(r.out);
      if (openId) updateItem(p, name, it);
    });
  }
  function updateItem(p, name, it) {
    var pid = Object.keys(data().panels).filter(function (k) { return data().panels[k] === p; })[0];
    var el = layer && layer.querySelector('[data-id="edge_' + pid + '_' + name + '"]');
    if (!el) return;
    el.replaceWith(itemEl(pid, name, it));
  }
  function refreshAll(p) {
    timers.forEach(clearInterval); timers = [];
    sortedItems(p).forEach(function (kv) {
      var name = kv[0], it = kv[1], type = it.type || 'run';
      if (type === 'text') {
        refresh(p, name, it);
        var ev = parseInt(it.every, 10);
        if (ev > 0) timers.push(setInterval(function () { refresh(p, name, it); }, Math.max(1, ev) * 1000));
      }
      if ((type === 'toggle' || it['button-state'] === 'on') && it.state) refreshState(p, name, it);
    });
  }

  function showDef(id) {
    Z.phone.alert(T('edge.settings'), defText(id) + '\n\n' + T('edge.settingsnote'));
  }

  /* ======================= コマンド ======================= */
  function kv(args, from) {
    var o = {};
    for (var i = from; i < args.length; i++) {
      var m = /^([\w-]+)=([\s\S]*)$/.exec(args[i]);
      if (!m) return { error: args[i] };
      o[m[1]] = m[2];
    }
    return o;
  }
  var TYPES = { run: 1, text: 1, toggle: 1, note: 1 };
  var OTHER = { list: 1, input: 1, terminal: 1, macro: 1, argument: 1, result: 1, view: 1 };

  function defText(id) {
    var p = panel(id);
    if (!p) return '';
    var lines = ['label=' + (p.label || id)];
    if (p.handle) {
      var hd = p.handle;
      lines.push('handle=' + hd.kind + (hd.kind === 'bar' ? ' side=' + (hd.side || 'right') + ' offset=' + (hd.offset || '30%') + ' length=' + (hd.length || '6%') + ' open=' + (hd.open || 'swipe') : ' at=' + (hd.at || [85, 60]).join('%,') + '%' + (hd.run ? ' run=' + hd.run : '')));
    }
    Object.keys(p.opts).forEach(function (k) { lines.push(k + '=' + p.opts[k]); });
    sortedItems(p).forEach(function (x) {
      var f = Object.keys(x[1]).filter(function (k) { return k[0] !== '_'; }).map(function (k) { return k + '=' + x[1][k]; });
      lines.push(id + ':' + x[0] + '  ' + f.join('  '));
    });
    return lines.join('\n');
  }

  E.cmd = function (c) {
    var a = c.args, sub = a[0];
    var d = data();
    if (!sub) { c.out(Z.helpText('z2-edge') + '\n'); return 0; }
    if (sub === 'permission') { c.out(T('edge.perm') + '\n'); return 0; }
    if (sub === 'on' || sub === 'off' || sub === 'toggle') {
      d.on = sub === 'toggle' ? !d.on : sub === 'on';
      if (!d.on) close();
      save();
      c.out('edge ' + (d.on ? 'on' : 'off') + '\n');
      if (d.on && !Object.keys(d.panels).some(function (k) { return d.panels[k].handle; })) c.err(T('edge.nohandle') + '\n');
      return 0;
    }
    if (sub === 'status') {
      c.out('edge ' + (d.on ? 'on' : 'off') + '  panels=' + Object.keys(d.panels).length + (openId ? '  open=' + openId : '') + '\n');
      return 0;
    }
    if (sub === 'reload') { render(); return 0; }
    if (sub === 'close') { close(); return 0; }
    if (sub === 'open') {
      if (!a[1]) { c.err('usage: z2-edge open ID [--toggle]\n'); return 2; }
      if (!panel(a[1])) { c.err('z2-edge: no such panel: ' + a[1] + '\n'); return 1; }
      if (!d.on) { c.err(T('edge.isoff') + '\n'); return 1; }
      if (a[2] === '--toggle' && openId === a[1]) close(); else open(a[1]);
      return 0;
    }
    if (sub === 'handle') {
      var id = a[1], kind = a[2];
      if (!id || !IDRE.test(id) || !kind) { c.err('usage: z2-edge handle ID bar|button|off [options]\n'); return 2; }
      var p = panel(id, true);
      if (kind === 'off') { p.handle = null; save(); return 0; }
      if (kind !== 'bar' && kind !== 'button') { c.err('z2-edge: handle kind must be bar or button\n'); return 2; }
      var hd = { kind: kind };
      for (var i = 3; i < a.length; i++) {
        var k = a[i], v = a[i + 1];
        if (k === '--side') { hd.side = v; i++; }
        else if (k === '--offset') { hd.offset = v; i++; }
        else if (k === '--length') { hd.length = v; i++; }
        else if (k === '--size') { hd.size = v; i++; }
        else if (k === '--open') { hd.open = v; i++; }
        else if (k === '--label') { hd.label = v; i++; }
        else if (k === '--run') { hd.run = v; i++; }
        else if (k === '--at') { var m = /^(\d+)%?,(\d+)%?$/.exec(v || ''); if (!m) { c.err('z2-edge: --at X%,Y%\n'); return 2; } hd.at = [+m[1], +m[2]]; i++; }
        else if (k === '--alpha' || k === '--bar-color') { i++; }
        else { c.err('z2-edge: unknown option: ' + k + '\n'); return 2; }
      }
      p.handle = hd;
      save();
      if (!d.on) c.out(T('edge.thenon') + '\n');
      return 0;
    }
    if (sub === 'panel') {
      var pid = a[1];
      if (!pid || !IDRE.test(pid)) { c.err('usage: z2-edge panel ID key=value...\n'); return 2; }
      var o = kv(a, 2);
      if (o.error) { c.err('z2-edge: not key=value: ' + o.error + '\n'); return 2; }
      var pp = panel(pid, true);
      Object.keys(o).forEach(function (k) { if (k === 'label') pp.label = o[k]; else pp.opts[k] = o[k]; });
      save(); return 0;
    }
    if (sub === 'set') {
      var m2 = /^([A-Za-z0-9_-]+):([A-Za-z0-9_-]+)$/.exec(a[1] || '');
      if (!m2) { c.err('usage: z2-edge set ID:ITEM key=value...\n'); return 2; }
      var o2 = kv(a, 2);
      if (o2.error) { c.err('z2-edge: not key=value: ' + o2.error + '\n'); return 2; }
      var ty = o2.type || 'run';
      if (OTHER[ty]) { c.err(T('edge.notype', ty) + '\n'); return 1; }
      if (!TYPES[ty]) { c.err('z2-edge: unknown type: ' + ty + '\n'); return 2; }
      var p2 = panel(m2[1], true);
      var it = p2.items[m2[2]] || { _n: ++p2.seq };
      Object.keys(o2).forEach(function (k) { if (o2[k] === '') delete it[k]; else it[k] = o2[k]; });
      p2.items[m2[2]] = it;
      save();
      if (openId === m2[1]) refreshAll(p2);
      return 0;
    }
    if (sub === 'remove') {
      var m3 = /^([A-Za-z0-9_-]+):([A-Za-z0-9_-]+)$/.exec(a[1] || '');
      var p3 = m3 && panel(m3[1]);
      if (!p3 || !p3.items[m3[2]]) { c.err('z2-edge: no such item: ' + a[1] + '\n'); return 1; }
      delete p3.items[m3[2]]; save(); return 0;
    }
    if (sub === 'delete') {
      if (!panel(a[1])) { c.err('z2-edge: no such panel: ' + a[1] + '\n'); return 1; }
      if (openId === a[1]) close();
      delete d.panels[a[1]]; save(); return 0;
    }
    if (sub === 'list') {
      var ids = a[1] ? [a[1]] : Object.keys(d.panels);
      ids.forEach(function (x) {
        var p4 = panel(x);
        if (!p4) { c.err('z2-edge: no such panel: ' + x + '\n'); return; }
        if (a[1]) sortedItems(p4).forEach(function (kv2) { c.out(x + ':' + kv2[0] + '\t' + (kv2[1].type || 'run') + '\t' + (kv2[1].label || '') + '\n'); });
        else c.out(x + '\t' + (p4.label || '') + '\t' + Object.keys(p4.items).length + ' items' + (p4.handle ? '\t' + p4.handle.kind : '') + '\n');
      });
      return 0;
    }
    if (sub === 'get') {
      var m4 = /^([A-Za-z0-9_-]+)(?::([A-Za-z0-9_-]+))?$/.exec(a[1] || '');
      var p5 = m4 && panel(m4[1]);
      if (!p5) { c.err('z2-edge: no such panel: ' + a[1] + '\n'); return 1; }
      if (m4[2]) {
        var it2 = p5.items[m4[2]];
        if (!it2) { c.err('z2-edge: no such item: ' + a[1] + '\n'); return 1; }
        Object.keys(it2).filter(function (k) { return k[0] !== '_'; }).forEach(function (k) { c.out(k + '=' + it2[k] + '\n'); });
        return 0;
      }
      c.out(defText(m4[1]) + '\n');
      return 0;
    }
    if (sub === 'push' || sub === 'state') {
      var m5 = /^([A-Za-z0-9_-]+):([A-Za-z0-9_-]+)$/.exec(a[1] || '');
      var p6 = m5 && panel(m5[1]);
      var it3 = p6 && p6.items[m5[2]];
      if (!it3) { c.err('z2-edge: no such item: ' + a[1] + '\n'); return 1; }
      if (sub === 'push') it3._v = a[2] === '-' ? lastLine(c.stdin) : (a[2] || '');
      else it3._on = /^(on|true|1)$/.test(a[2] || '');
      if (openId === m5[1]) updateItem(p6, m5[2], it3);
      Z.phone.save();
      return 0;
    }
    if (sub === 'badge') {
      var p7 = panel(a[1]);
      if (!p7) { c.err('z2-edge: no such panel: ' + a[1] + '\n'); return 1; }
      p7.badge = a[2] || '';
      save(); return 0;
    }
    if (sub === 'tab') { c.err(T('edge.notab') + '\n'); return 1; }
    c.err(Z.helpText('z2-edge') + '\n');
    return 2;
  };

  /* ヘルプ（本物の z2-edge --help のうち、このモックで効く行だけを写した） */
  Z.HELP.ja['z2-edge'] = [
    'Android 画面に浮かべるパネル。中身はシェルコマンドとテキスト定義です。',
    'z2-edge handle ID bar --side right --offset 30% --length 6% --size 6 --open swipe',
    'z2-edge handle ID button --at 85%,60% [--size 48] [--run \'command\']',
    'z2-edge handle ID off                  取っ手を隠す',
    'z2-edge panel ID label=名前 width=80% height=60% columns=auto|1..16 flow=vertical|grid place=left|right title=on|off',
    'z2-edge set ID:項目 \'run=z2-intent -p z2.mock.browser\' label=ブラウザ',
    'z2-edge set ID:項目 type=text \'run=date\' label=時計 every=30',
    'z2-edge set ID:項目 type=toggle \'run=z2-torch toggle\' \'state=z2-torch status\' label=ライト',
    'z2-edge set ID:memo type=note file=~/memo.txt',
    'z2-edge get ID | get ID:項目 | list [ID] | remove ID:項目 | delete ID',
    'z2-edge on | off | toggle | status | reload     有効化・停止・状態・定義の再読込',
    'z2-edge open ID [--toggle] | close                パネルを開く・閉じる',
    'z2-edge push ID:項目 \'文字列\'           表示を外から更新（- なら標準入力）',
    'z2-edge state ID:項目 on|off           toggle の表示を更新',
    'z2-edge badge ID \'87%\'                 取っ手へ文字を表示（空文字で消去）',
    '型: run / text / toggle / note（このモック）。out=none|panel|toast|notify、order=整数。',
    'バーは内側へスワイプすると開きます（--open tap|both なら押しても開く）。外側を押すと閉じます。'
  ].join('\n');
  Z.HELP.en['z2-edge'] = [
    'Panels floating on the Android screen. Contents are shell commands and text definitions.',
    'z2-edge handle ID bar --side right --offset 30% --length 6% --size 6 --open swipe',
    'z2-edge handle ID button --at 85%,60% [--size 48] [--run \'command\']',
    'z2-edge handle ID off                  Hide the handle',
    'z2-edge panel ID label=Name width=80% height=60% columns=auto|1..16 flow=vertical|grid place=left|right title=on|off',
    'z2-edge set ID:item \'run=z2-intent -p z2.mock.browser\' label=Browser',
    'z2-edge set ID:item type=text \'run=date\' label=Clock every=30',
    'z2-edge set ID:item type=toggle \'run=z2-torch toggle\' \'state=z2-torch status\' label=Light',
    'z2-edge set ID:memo type=note file=~/memo.txt',
    'z2-edge get ID | get ID:item | list [ID] | remove ID:item | delete ID',
    'z2-edge on | off | toggle | status | reload | open ID [--toggle] | close',
    'z2-edge push ID:item \'text\'             Use - to read stdin',
    'z2-edge state ID:item on|off',
    'z2-edge badge ID \'87%\'                  Empty string clears the badge',
    'Types: run / text / toggle / note (in this mock-up). out=none|panel|toast|notify, order=integer.',
    'Swipe the bar inward to open (--open tap|both also opens on tap). Tap outside to close.'
  ].join('\n');

  Z.defz2('z2-edge', function (c) { return E.cmd(c); });
})(typeof window !== 'undefined' ? window : globalThis);
