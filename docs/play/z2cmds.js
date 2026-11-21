/* z2term play — z2-* コマンド。モックのスマホ（Z2P.phone）と z2term（Z2P.zt）につなぐ。 */
(function (G) {
  'use strict';
  var Z = G.Z2P = G.Z2P || {};
  var C = Z.cmds = Z.cmds || {};
  var T = function () { return Z.t.apply(null, arguments); };
  var color = function (s, c) { return Z.color(s, c); };

  function helpText(name) {
    var tbl = (Z.HELP[Z.lang] || Z.HELP.en);
    if (tbl[name]) return tbl[name];
    var line = Z.HELP_LIST[Z.lang].split('\n').filter(function (l) { return l.trim().indexOf(name + ' ') === 0 || l.trim() === name; })[0];
    return line ? 'usage:' + line.replace(/^\s+/, ' ') : name;
  }
  Z.helpText = helpText;

  /* --help（と、-h を別の意味に使わないコマンドでは -h も）でヘルプを出す包み。 */
  function def(name, fn, hIsFlag) {
    C[name] = function (c) {
      if (c.args[0] === '--help' || (!hIsFlag && c.args[0] === '-h' && c.args.length === 1)) { c.out(helpText(name) + '\n'); return 0; }
      return fn(c);
    };
  }
  Z.defz2 = def;
  function json(o) { return JSON.stringify(o); }

  /* ---------------- ヘルプ・版数 ---------------- */
  function z2help(c) {
    c.out((Z.lang === 'ja' ? 'Z2Term 独自コマンド' : 'Z2Term own commands') + ' (app ' + Z.VERSION + ')\n\n');
    c.out(Z.HELP_LIST[Z.lang] + '\n\n');
    c.out(color(T('z2.helpnote'), '33') + '\n');
  }
  def('z2help', z2help);
  def('z2term', z2help);

  def('z2version', function (c) {
    if (c.args[0] === '--short') { c.out(Z.VERSION + '\n'); return; }
    c.out('z2term ' + Z.VERSION + ' (play)\nengine : ' + T('z2.noengine') + '\nos     : ' + T('z2.noos') + '\nkernel : 6.1.0-z2mock\n');
  });

  def('z2doctor', function (c) {
    var p = Z.phone.state;
    function row(ok, label, fix) { c.out((ok ? color('OK', '32') : color('NG', '31')) + '  ' + label + (ok || !fix ? '' : '\n    → ' + fix) + '\n'); }
    c.out('z2doctor — z2term ' + Z.VERSION + ' (play)\n\n');
    row(true, T('doc.version', Z.VERSION));
    row(false, T('doc.os'), T('doc.osfix'));
    row(Z.store.available, T('doc.store', Z.bytes(Z.store.usage())), T('doc.storefix'));
    row(!p.airplane, T('doc.net'), T('doc.netfix'));
    row(true, T('doc.notify'));
    row(Z.when.rules().length > 0, T('doc.when', Z.when.rules().length), T('doc.whenfix'));
    c.out('\n' + T('doc.report') + ': z2term ' + Z.VERSION + ' play / os=none / store=' + Z.bytes(Z.store.usage()) + '\n');
  });

  /* ---------------- 通知・トースト ---------------- */
  def('z2-notify', function (c) {
    var o = { heads: false, buttons: [] };
    var rest = [];
    for (var i = 0; i < c.args.length; i++) {
      var a = c.args[i];
      if (a === '-h') o.heads = true;
      else if (a === '-n') o.name = c.args[++i];
      else if (a === '-c') o.copy = c.args[++i];
      else if (a === '-b') { if (o.buttons.length < 3) o.buttons.push(c.args[++i]); else i++; }
      else rest.push(a);
    }
    if (!rest.length) { c.err('usage: z2-notify [-h] [-n NAME] [-c TEXT] [-b LABEL]... "title" ["body"]\n'); return 2; }
    o.title = rest[0]; o.body = rest.slice(1).join(' ');
    o.app = 'z2term';
    Z.phone.notify(o);
  }, true);

  def('z2-toast', function (c) {
    var s = c.args.length ? c.args.join(' ') : (c.stdin || '').trim();
    if (!s) { c.err('usage: z2-toast "message"\n'); return 2; }
    Z.phone.toast(s);
  });

  def('z2-ask', function (c) {
    var d = null, q = [];
    for (var i = 0; i < c.args.length; i++) {
      if (c.args[i] === '-d') d = c.args[++i];
      else if (c.args[i] === '-t' || c.args[i] === '-H') i++;
      else q.push(c.args[i]);
    }
    if (!q.length) { c.err('usage: z2-ask [-d DEFAULT] "question"\n'); return 2; }
    return Z.phone.ask(q.join(' '), d, c.sig).then(function (ans) {
      if (ans == null) return 1;
      c.out(ans + '\n');
      return 0;
    });
  });

  /* ---------------- 状態・道具 ---------------- */
  def('z2-torch', function (c) {
    var a = c.args[0] || 'toggle';
    var s = Z.phone.state;
    if (a === 'status') { c.out((s.torch ? 'on' : 'off') + '\n'); return; }
    if (a === 'on') Z.phone.set('torch', true);
    else if (a === 'off') Z.phone.set('torch', false);
    else if (a === 'toggle') Z.phone.set('torch', !s.torch);
    else { c.err('usage: z2-torch [on|off|toggle|status]\n'); return 2; }
    c.out('torch=' + (Z.phone.state.torch ? 'on' : 'off') + '\n');
  });

  def('z2-clip', function (c) {
    var a = c.args[0];
    if (a === 'get' || a === undefined && c.stdin == null) { c.out(Z.phone.clip.get() + (c.args[1] === '-n' ? '' : '\n')); return; }
    if (a === 'set' || a === undefined) {
      var s = c.args.length > 1 ? c.args.slice(1).join(' ') : (c.stdin || '').replace(/\n$/, '');
      Z.phone.clip.set(s);
      return;
    }
    c.err('usage: z2-clip get | set [text]\n'); return 2;
  });

  function stateObj() {
    var s = Z.phone.state;
    return {
      screen: s.dim ? 'off' : 'on', locked: false, idle: false, charging: !!s.charging, plug: s.charging ? 'usb' : 'none',
      level: Math.round(s.level), temp: 31.5, wifi: !!(s.wifi && !s.airplane), ssid: s.wifi && !s.airplane ? 'z2-world' : '',
      ringer: s.ringer, airplane: !!s.airplane, torch: !!s.torch, headset: false, bt_audio: false,
      volume: s.volume, volume_max: 15
    };
  }
  Z.stateObj = stateObj;
  def('z2-state', function (c) {
    var o = stateObj();
    if (c.args[0]) {
      if (!(c.args[0] in o)) { c.err('z2-state: unknown key: ' + c.args[0] + '\n'); return 2; }
      c.out(String(o[c.args[0]]) + '\n'); return;
    }
    c.out(json(o) + '\n');
  });
  def('z2-battery', function (c) {
    var s = Z.phone.state;
    c.out(json({ level: Math.round(s.level), charging: !!s.charging, plug: s.charging ? 'usb' : 'none', status: s.charging ? 'CHARGING' : 'DISCHARGING', temperature: 31.5, real: !!s.realBattery }) + '\n');
  });
  def('z2-vibrate', function (c) { Z.phone.vibrate(parseInt(c.args[0] || '200', 10)); });
  def('z2-say', function (c) {
    var s = c.args.length ? c.args.join(' ') : (c.stdin || '').trim();
    if (!s) return 2;
    return Z.phone.say(s, c.sig);
  });
  def('z2-volume', function (c) {
    var s = Z.phone.state, a = c.args[0];
    if (!a) { c.err('usage: z2-volume <up|down|mute|unmute|N|N%>\n'); return 2; }
    var v = s.volume;
    if (a === 'up') v++; else if (a === 'down') v--; else if (a === 'mute') v = 0; else if (a === 'unmute') v = s.volume || 7;
    else if (/^\d+%$/.test(a)) v = Math.round(parseInt(a, 10) * 15 / 100); else if (/^\d+$/.test(a)) v = parseInt(a, 10);
    else { c.err('z2-volume: bad value\n'); return 2; }
    Z.phone.set('volume', Math.max(0, Math.min(15, v)));
    c.out(json({ current: Z.phone.state.volume, max: 15 }) + '\n');
  });
  def('z2-media', function (c) { Z.phone.toast(T('z2.media', c.args[0] || 'playpause')); });
  def('z2-sensor', function (c) {
    var k = c.args[0] || 'light';
    if (k === 'light') c.out(json({ sensor: 'light', lux: Z.phone.state.torch ? 1800 : 120 }) + '\n');
    else if (k === 'accel') c.out(json({ sensor: 'accel', x: 0.02, y: 9.79, z: 0.31, note: T('z2.desk') }) + '\n');
    else if (k === 'proximity' || k === 'prox') c.out(json({ sensor: 'proximity', cm: 5, near: false }) + '\n');
    else { c.err('usage: z2-sensor [light|accel|proximity]\n'); return 2; }
  });
  def('z2-screen', function (c) {
    if (c.args[0] === 'keepon' && c.args[1] && c.args[1] !== 'off') {
      var m = /^(\d+)([hms])$/.exec(c.args[1]);
      if (!m) { c.err('usage: z2-screen keepon <1h|30m|90s> | keepon off | status\n'); return 2; }
      var ms = parseInt(m[1], 10) * { h: 3600e3, m: 60e3, s: 1e3 }[m[2]];
      Z.phone.keepOnUntil = Date.now() + ms;
      c.out('keepon until ' + Z.hms(new Date(Z.phone.keepOnUntil)) + '\n'); return;
    }
    if (c.args[0] === 'keepon') { Z.phone.keepOnUntil = 0; c.out('keepon off\n'); return; }
    var left = Math.max(0, (Z.phone.keepOnUntil || 0) - Date.now());
    c.out(left ? 'keepon ' + Math.ceil(left / 1000) + 's left\n' : 'keepon off\n');
  });

  /* ---------------- タイル・ウィジェット・アラーム ---------------- */
  def('z2-tile', function (c) {
    var a = c.args;
    var tiles = Z.phone.tiles();
    if (a[0] === 'list' || !a.length) {
      for (var i = 1; i <= 12; i++) {
        var t = tiles[i];
        c.out(('  ' + i).slice(-2) + '  ' + (t ? (t.label + '  ' + color(t.cmd, '2')) : color('-', '2')) + '\n');
      }
      return;
    }
    if (a[0] === 'set') {
      var slot = parseInt(a[1], 10);
      if (!(slot >= 1 && slot <= 12)) { c.err('z2-tile: slot must be 1-12\n'); return 2; }
      var label = null, rest = [];
      for (var j = 2; j < a.length; j++) { if (a[j] === '-l') label = a[++j]; else rest.push(a[j]); }
      if (!rest.length) { c.err('z2-tile: command is missing\n'); return 2; }
      var cmd = rest.join(' ');
      Z.phone.setTile(slot, { label: label || cmd.split(/[\s/]/).filter(Boolean).pop().replace(/\.sh$/, ''), cmd: cmd });
      c.out('tile ' + slot + ' = ' + cmd + '\n' + color(T('z2.tilehint'), '2') + '\n');
      return;
    }
    if (a[0] === 'clear') {
      if (a[1] === 'all') { for (var k = 1; k <= 12; k++) Z.phone.setTile(k, null); return; }
      var s2 = parseInt(a[1], 10);
      if (!(s2 >= 1 && s2 <= 12)) { c.err('z2-tile: slot must be 1-12\n'); return 2; }
      Z.phone.setTile(s2, null); return;
    }
    c.err(helpText('z2-tile') + '\n'); return 2;
  });

  def('z2-widget', function (c) {
    if (c.args[0] === 'tail' && c.args[1]) {
      var p = c.abs(c.args[1]);
      Z.phone.setWidget({ file: p });
      c.out(T('z2.widget', c.fs.pretty(p)) + '\n'); return;
    }
    if (c.args[0] === 'off') { Z.phone.setWidget(null); return; }
    c.err(helpText('z2-widget') + '\n'); return 2;
  });

  def('z2-alarm', function (c) {
    var a = c.args;
    if (a[0] === 'list') { Z.phone.alarms().forEach(function (x) { c.out(x.name + '  ' + Z.hms(new Date(x.at)) + '\n'); }); return; }
    if (a[0] === 'cancel') { Z.phone.cancelAlarm(a[1] || 'all'); return; }
    if (a[0] === 'in' && a[1]) {
      var m = /^(\d+)([ms])$/.exec(a[1]);
      if (!m) { c.err('z2-alarm: in <N>m|<N>s\n'); return 2; }
      var at = Date.now() + parseInt(m[1], 10) * (m[2] === 'm' ? 60e3 : 1e3);
      var name = a[2] || 'alarm-' + Z.uid();
      Z.phone.addAlarm(name, at);
      c.out(name + ' at ' + Z.hms(new Date(at)) + '\n'); return;
    }
    if (a[0] === 'at' || a[0] === 'daily') { c.err(T('z2.alarmat') + '\n'); return 2; }
    c.err(helpText('z2-alarm') + '\n'); return 2;
  });

  /* ---------------- アプリ・画面 ---------------- */
  def('z2-app', function (c) {
    if (c.args[0] && c.args[0] !== 'list') { c.err(helpText('z2-app') + '\n'); return 2; }
    Z.APPS.forEach(function (a) { c.out(a.pkg + '\t' + a.name() + '\n'); });
  });
  def('z2-key', function (c) {
    var k = c.args[0];
    var ok = { back: 1, home: 1, recents: 1, notifications: 1, quick_settings: 1, lock: 1 };
    if (k === 'status' || k === 'permission') { c.out(T('z2.keyok') + '\n'); return; }
    if (!ok[k]) { c.err(helpText('z2-key') + '\n'); return 2; }
    Z.phone.key(k);
  });
  def('z2-open', function (c) {
    var u = c.args[0];
    if (!u) { c.err('usage: z2-open <URL>\n'); return 2; }
    if (/^https?:\/\//.test(u) || /^[\w-]+(\.[\w-]+)+(\/|$)/.test(u)) { Z.phone.open('browser', { url: u }); return; }
    var n = c.fs.stat(c.abs(u));
    if (n) { c.err(T('z2.openfile') + '\n'); return 1; }
    c.err('z2-open: ' + u + ': No such file or directory\n'); return 1;
  });
  def('z2-view', function (c) { c.err(T('z2.view') + '\n'); return 1; });
  def('z2-edit', function (c) {
    var f = c.args[0];
    if (!f) { c.err('usage: z2-edit <file>\n'); return 2; }
    var p = c.abs(f);
    var n = c.fs.stat(p);
    if (n && n.t === 'd') { c.err('z2-edit: ' + f + ': Is a directory\n'); return 1; }
    return Z.phone.edit(p, c.sig).then(function (saved) { if (saved) c.out(T('z2.saved', c.fs.pretty(p)) + '\n'); return 0; });
  });
  def('z2-share', function (c) {
    var s = c.args.length ? c.args.join(' ') : (c.stdin || '').trim();
    if (!s) { c.err('usage: z2-share "text"\n'); return 2; }
    Z.phone.share(s);
  });
  def('z2-photo', function (c) {
    var p = '/root/.z2term/photo/borrowed.txt';
    c.fs.mkdir('/root/.z2term/photo', true, true);
    c.fs.writeFile(p, T('z2.photo') + '\n');
    c.out(c.fs.pretty(p) + '\n');
  });
  def('z2-qr', function (c) { c.err(T('z2.nolens') + '\n'); return 1; });
  def('z2-listen', function (c) { c.err(T('z2.nomic') + '\n'); return 1; });
  def('z2-update', function (c) { c.out(T('z2.update', Z.VERSION) + '\n'); });
  def('z2-icon', function (c) { c.err(T('z2.icon') + '\n'); return 1; });
  /* z2-intent: このスマホのアプリを開く -p と、URL を開く -a …VIEW -d だけ受ける */
  def('z2-intent', function (c) {
    var a = c.args, pkg = null, data = null, action = null;
    for (var i = 0; i < a.length; i++) {
      if (a[i] === '-p') pkg = a[++i];
      else if (a[i] === '-n') pkg = (a[++i] || '').split('/')[0];
      else if (a[i] === '-d') data = a[++i];
      else if (a[i] === '-a') action = a[++i];
      else if (a[i] === '-c' || a[i] === '-e' || a[i] === '--es') i++;
    }
    if (data && /^https?:/.test(data)) { Z.phone.open('browser', { url: data }); return 0; }
    if (pkg) {
      var app = Z.appByPkg(pkg);
      if (!app) { c.err('z2-intent: ' + T('z2.nopkg', pkg) + '\n'); return 1; }
      Z.phone.open(app.id); return 0;
    }
    c.err(T('z2.intent') + '\n'); return 1;
  });
  def('z2-shot', function (c) { c.err(T('z2.shot') + '\n'); return 1; });
  def('z2-usb', function (c) { c.out(T('z2.usb') + '\n'); });
  def('z2-edge', function (c) { c.err(T('z2.edge') + '\n'); return 1; });
  def('z2-ui', function (c) { c.err(T('cmd.noai') + '\n'); return 1; });
  function needOs(c) { c.err(T('z2.needos', c.name) + '\n'); return 1; }
  ['z2gui', 'z2run', 'z2-audio', 'z2-img', 'z2-macro', 'z2scan', 'z2-file'].forEach(function (n) { def(n, needOs); });
  def('z2adb', function (c) { c.err(T('cmd.nonet', 'z2adb') + '\n'); return 1; });
  def('sshd', function (c) { c.err(T('cmd.nonet', 'sshd') + '\n'); return 1; });
  def('z2-server', function (c) { c.err(T('cmd.nonet', 'z2-server') + '\n'); return 1; });

  /* ---------------- タブ（z2-session） ---------------- */
  var KEYMAP = {
    'Enter': 'Enter', 'Tab': 'Tab', 'Esc': 'Escape', 'Escape': 'Escape', 'Up': 'ArrowUp', 'Down': 'ArrowDown',
    'Left': 'ArrowLeft', 'Right': 'ArrowRight', 'Home': 'Home', 'End': 'End', 'BSpace': 'Backspace', 'Backspace': 'Backspace', 'Delete': 'Delete', 'Space': ' '
  };
  function parseKey(k) {
    var m = /^C-(.)$/.exec(k);
    if (m) return { key: m[1].toLowerCase(), ctrl: true };
    m = /^M-(.)$/.exec(k);
    if (m) return { key: m[1], alt: true };
    if (KEYMAP[k]) return { key: KEYMAP[k] };
    if (/^F\d+$/.test(k)) return { key: k };
    return null;
  }
  def('z2-session', function (c) {
    var zt = Z.zt, a = c.args;
    if (!zt) { c.err('z2-session: z2term is not running\n'); return 1; }
    var sub = a[0] || 'list';
    function need(ref) {
      var t = zt.find(ref);
      if (!t) { c.err('z2-session: no such tab: ' + ref + '\n'); return null; }
      return t;
    }
    if (sub === 'list') {
      zt.tabs().forEach(function (t, i) {
        var mark = (t === zt.current() ? '*' : ' ') + (t.pane.running ? '!' : ' ') + (t.groupId ? 'g' : ' ');
        c.out((i + 1) + '\t' + t.id + '\t' + mark + '\t' + t.name + (t.groupId ? '\t[' + zt.group(t.groupId).name + ']' : '') + '\n');
      });
      return;
    }
    if (sub === 'new') { var nt = zt.newTab({ name: a[1], show: false }); c.out((zt.tabs().indexOf(nt) + 1) + '\t' + nt.id + '\t' + nt.name + '\n'); return; }
    if (sub === 'close') { var t1 = need(a[1]); if (!t1) return 1; zt.closeTab(t1, true); return; }
    if (sub === 'rename') { var t2 = need(a[1]); if (!t2) return 1; zt.renameTab(t2, a.slice(2).join(' ') || t2.name); return; }
    if (sub === 'show') { var t3 = need(a[1]); if (!t3) return 1; zt.showTab(t3); return; }
    if (sub === 'send') {
      var enter = a.indexOf('--enter') >= 0;
      var rest = a.filter(function (x) { return x !== '--enter'; });
      var t4 = need(rest[1]); if (!t4) return 1;
      zt.sendText(t4, rest.slice(2).join(' '), enter); return;
    }
    if (sub === 'key') {
      var t5 = need(a[1]); if (!t5) return 1;
      for (var i = 2; i < a.length; i++) {
        var k = parseKey(a[i]);
        if (!k) { c.err('z2-session: unknown key: ' + a[i] + '\n'); return 2; }
        t5.pane.key(k);
      }
      return;
    }
    if (sub === 'capture') { var t6 = need(a[1]); if (!t6) return 1; c.out(t6.pane.capture() + '\n'); return; }
    if (sub === 'attach') { c.err(T('z2.attach') + '\n'); return 1; }
    if (sub === 'group') return groupCmd(c, a.slice(1));
    c.err(helpText('z2-session') + '\n'); return 2;
  });

  function groupCmd(c, a) {
    var zt = Z.zt, sub = a[0] || 'list';
    function g(name) { var x = zt.findGroup(name); if (!x) c.err('z2-session: no such group: ' + name + '\n'); return x; }
    function tabsOf(refs) {
      var out = [];
      for (var i = 0; i < refs.length; i++) { var t = zt.find(refs[i]); if (!t) { c.err('z2-session: no such tab: ' + refs[i] + '\n'); return null; } out.push(t); }
      return out;
    }
    if (sub === 'list') {
      zt.groups().forEach(function (x) {
        c.out(x.name + '\t' + x.tabs.map(function (id) { var t = zt.byId(id); return t ? t.name : id; }).join(', ') + (x.bc && x.bc.on ? '\t📡 on' : '') + '\n');
      });
      return;
    }
    if (sub === 'new') {
      if (!a[1]) { c.err('usage: z2-session group new <NAME> <tab>...\n'); return 2; }
      var ts = tabsOf(a.slice(2)); if (!ts) return 1;
      var r = zt.newGroup(a[1], ts);
      if (r && r.error) { c.err('z2-session: ' + r.error + '\n'); return 1; }
      return;
    }
    if (sub === 'add' || sub === 'remove') {
      var gg = g(a[1]); if (!gg) return 1;
      var ts2 = tabsOf(a.slice(2)); if (!ts2) return 1;
      for (var i = 0; i < ts2.length; i++) {
        var r2 = sub === 'add' ? zt.addToGroup(gg, ts2[i]) : zt.removeFromGroup(ts2[i]);
        if (r2 && r2.error) { c.err('z2-session: ' + r2.error + '\n'); return 1; }
      }
      return;
    }
    if (sub === 'delete') { var g3 = g(a[1]); if (!g3) return 1; zt.deleteGroup(g3); return; }
    if (sub === 'show') { var g4 = g(a[1]); if (!g4) return 1; zt.showGroup(g4); return; }
    if (sub === 'send') {
      var enter = a.indexOf('--enter') >= 0;
      var rest = a.filter(function (x) { return x !== '--enter'; });
      var g5 = g(rest[1]); if (!g5) return 1;
      g5.tabs.forEach(function (id) { var t = zt.byId(id); if (t) zt.sendText(t, rest.slice(2).join(' '), enter); });
      return;
    }
    if (sub === 'broadcast') {
      var g6 = g(a[1]); if (!g6) return 1;
      var op = a[2] || 'status';
      if (op === 'on' || op === 'off') zt.setBroadcast(g6, op === 'on');
      else if (op === 'add' || op === 'remove') { var t7 = zt.find(a[3]); if (!t7) { c.err('z2-session: no such tab: ' + a[3] + '\n'); return 1; } zt.setTarget(g6, t7, op === 'add'); }
      else if (op !== 'status') { c.err('usage: z2-session group broadcast <NAME> on|off|add <tab>|remove <tab>\n'); return 2; }
      c.out('broadcast ' + (g6.bc && g6.bc.on ? 'on' : 'off') + ' → ' + ((g6.bc && g6.bc.targets) || []).map(function (id) { var t = zt.byId(id); return t ? t.name : id; }).join(', ') + '\n');
      return;
    }
    c.err(helpText('z2-session') + '\n'); return 2;
  }

  def('z2-when', function (c) { return Z.when.cmd(c); });
  def('z2-action', function (c) { return Z.action.cmd(c); });
})(typeof window !== 'undefined' ? window : globalThis);
