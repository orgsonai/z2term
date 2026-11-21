/* z2term play — z2-when（自動化ハブ）。
   モックのスマホで起こせる出来事だけを拾う: 時刻・充電・電池・Wi‑Fi・機内モード・消灯・ライト・アラーム・通知。
   発火したらコマンドを裏のシェル（タブなし）で動かし、結果をルールのログに残す。
   保存: z2mock:auto.rules */
(function (G) {
  'use strict';
  var Z = G.Z2P = G.Z2P || {};
  var h = function () { return Z.h.apply(null, arguments); };
  var T = function () { return Z.t.apply(null, arguments); };

  var W = Z.when = {};
  var auto;
  var EVENTS = ['airplane_on', 'airplane_off', 'screen_on', 'screen_off', 'torch_on', 'torch_off', 'alarm', 'ringer_normal', 'ringer_vibrate', 'ringer_silent', 'notify_action'];
  var MAP = { charge_start: 'charge:start', charge_stop: 'charge:stop', wifi_connect: 'wifi:connect', wifi_disconnect: 'wifi:disconnect' };
  var KNOWN = /^(time:daily=\d{1,2}:\d{2}|charge:(start|stop)|battery:below=\d+|wifi:(connect|disconnect)|event:[\w*]+|notify:.+)$/;

  W.load = function () {
    auto = Z.store.get('auto', null) || {};
    auto.rules = auto.rules || [];
    auto.seq = auto.seq || 1;
  };
  function save() { Z.store.set('auto', auto); Z.bus.emit('rules'); }
  W.rules = function () { return auto ? auto.rules : []; };

  /* ---------------- 条件（if=） ---------------- */
  function cond(expr, any) {
    if (!expr) return true;
    var s = Z.stateObj();
    var parts = expr.split(',').map(function (x) { return x.trim(); }).filter(Boolean);
    var test = function (p) {
      var neg = p[0] === '!';
      if (neg) p = p.slice(1);
      var m = /^(\w+)\s*(<=|>=|=|<|>)\s*(.+)$/.exec(p);
      var r;
      if (m) {
        var v = s[m[1]], x = m[3];
        var nv = parseFloat(v), nx = parseFloat(x);
        if (m[2] === '=') r = String(v) === x;
        else if (m[2] === '<') r = nv < nx; else if (m[2] === '>') r = nv > nx;
        else if (m[2] === '<=') r = nv <= nx; else r = nv >= nx;
      } else r = !!s[p] && s[p] !== 'off';
      return neg ? !r : r;
    };
    return any ? parts.some(test) : parts.every(test);
  }
  W.cond = cond;

  /* ---------------- 発火 ---------------- */
  var running = {};
  function fire(rule, info) {
    if (!rule.on) return;
    if (!cond(rule.cond, false) || (rule.condAny && !cond(rule.condAny, true))) {
      addLog(rule, { t: Date.now(), skip: true, out: T('when.skip') });
      return;
    }
    if (running[rule.id]) return;
    if (rule.last && Date.now() - rule.last < 1500) return;
    running[rule.id] = true;
    rule.last = Date.now();
    rule.fired = (rule.fired || 0) + 1;
    var env = { Z2_WHEN_ID: rule.id, Z2_WHEN_EVENT: info.event || rule.trig, Z2_WHEN_TRIGGER: rule.trig };
    Z.runBackground(rule.cmd, { aborted: false }, 'z2-when ' + rule.id, env).then(function (r) {
      delete running[rule.id];
      addLog(rule, { t: Date.now(), st: r.status, out: r.out.slice(-400) });
      Z.bus.emit('when-fired', { rule: rule, status: r.status });
    });
  }
  function addLog(rule, entry) {
    rule.log = (rule.log || []).concat([entry]).slice(-10);
    save();
  }
  W.fireTest = fire;

  function match(trig, name) {
    if (trig === name) return true;
    if (trig.indexOf('*') >= 0) return new RegExp('^' + trig.replace(/[.+^${}()|[\]\\]/g, '\\$&').replace(/\*/g, '.*') + '$').test(name);
    return false;
  }

  W.init = function () {
    Z.bus.on('phone-event', function (ev) {
      var key = MAP[ev.name] || 'event:' + ev.name;
      auto.rules.forEach(function (r) {
        if (match(r.trig, key) || match(r.trig, 'event:' + ev.name)) fire(r, { event: ev.name });
      });
    });
    Z.bus.on('battery', function (b) {
      auto.rules.forEach(function (r) {
        var m = /^battery:below=(\d+)$/.exec(r.trig);
        if (m && b.was >= +m[1] && b.level < +m[1]) fire(r, { event: 'battery_low' });
      });
    });
    var lastMin = '';
    Z.bus.on('tick', function (d) {
      var hm = Z.pad(d.getHours()) + ':' + Z.pad(d.getMinutes());
      if (hm === lastMin) return;
      lastMin = hm;
      auto.rules.forEach(function (r) {
        var m = /^time:daily=(\d{1,2}):(\d{2})$/.exec(r.trig);
        if (m && Z.pad(+m[1]) + ':' + m[2] === hm) fire(r, { event: 'time' });
      });
    });
    Z.bus.on('notify', function (n) {
      auto.rules.forEach(function (r) {
        if (r.trig.indexOf('notify:') !== 0) return;
        if (running[r.id]) return;
        var w = r.trig.slice(7);
        if ((n.title + ' ' + n.body).indexOf(w) >= 0) fire(r, { event: 'notify' });
      });
    });
  };

  /* ---------------- コマンド ---------------- */
  function fmt(r) {
    return r.id + '  ' + (r.on ? 'ON ' : 'OFF') + '  ' + r.trig + (r.name ? '  [' + r.name + ']' : '') + (r.cond ? '  if=' + r.cond : '') + '\n      → ' + r.cmd + (r.fired ? '   (' + T('when.fired', r.fired) + ')' : '');
  }
  W.cmd = function (c) {
    var a = c.args;
    var sub = a[0];
    if (!sub) { c.out(Z.helpText('z2-when') + '\n'); return 0; }
    if (sub === 'list') {
      if (!auto.rules.length) { c.out(T('when.none') + '\n'); return 0; }
      auto.rules.forEach(function (r) { c.out(fmt(r) + '\n'); });
      return 0;
    }
    if (sub === 'events') { c.out(EVENTS.join('\n') + '\n'); return 0; }
    if (sub === 'remove') {
      if (!a[1]) { c.err('usage: z2-when remove <id|all>\n'); return 2; }
      var before = auto.rules.length;
      auto.rules = a[1] === 'all' ? [] : auto.rules.filter(function (r) { return r.id !== a[1]; });
      if (auto.rules.length === before && a[1] !== 'all') { c.err('z2-when: no such rule: ' + a[1] + '\n'); return 1; }
      save(); return 0;
    }
    if (sub === 'on' || sub === 'off') {
      var r = find(a[1]);
      if (!r) { c.err('z2-when: no such rule: ' + a[1] + '\n'); return 1; }
      r.on = sub === 'on'; save(); c.out(r.id + ' ' + sub + '\n'); return 0;
    }
    if (sub === 'log') {
      var r2 = find(a[1]);
      if (!r2) { c.err('z2-when: no such rule: ' + a[1] + '\n'); return 1; }
      if (!(r2.log || []).length) { c.out(T('when.nolog') + '\n'); return 0; }
      r2.log.forEach(function (l) {
        c.out(Z.hms(new Date(l.t)) + '  ' + (l.skip ? 'skip' : 'exit=' + l.st) + (l.out ? '  ' + l.out.replace(/\n+$/, '').replace(/\n/g, ' ⏎ ') : '') + '\n');
      });
      return 0;
    }
    if (sub === 'test' || sub === 'fire') {
      var r3 = find(a[1]);
      if (!r3) { c.err('z2-when: no such rule: ' + a[1] + '\n'); return 1; }
      fire(r3, { event: 'test' }); return 0;
    }
    /* 登録: <trigger> [name=] [if=] [if_any=] run <cmd> */
    var ri = a.indexOf('run');
    if (ri < 1 || ri === a.length - 1) { c.err('usage: z2-when <trigger> [name=NAME] [if=COND] run <command>\n'); return 2; }
    var rule = { id: 'r' + (auto.seq++), trig: a[0], on: true, cmd: a.slice(ri + 1).join(' '), log: [], fired: 0 };
    for (var i = 1; i < ri; i++) {
      var m = /^(name|if|if_any)=(.*)$/.exec(a[i]);
      if (!m) { c.err('z2-when: unknown option: ' + a[i] + '\n'); return 2; }
      if (m[1] === 'name') rule.name = m[2];
      else if (m[1] === 'if') rule.cond = m[2];
      else rule.condAny = m[2];
    }
    auto.rules.push(rule);
    save();
    c.out(T('when.added', rule.id, rule.trig) + '\n');
    if (!KNOWN.test(rule.trig)) c.err(T('when.unknown', rule.trig) + '\n');
    return 0;
  };
  function find(id) { return auto.rules.filter(function (r) { return r.id === id || r.name === id; })[0]; }

  /* ---------------- 📜 → 自動化 ---------------- */
  var TRIGS = ['event:airplane_on', 'event:airplane_off', 'wifi:connect', 'wifi:disconnect', 'event:torch_on', 'event:screen_off', 'event:screen_on', 'charge:start', 'charge:stop', 'battery:below=20', 'time:daily=07:00', 'notify:'];
  W.renderList = function (body) {
    function draw() {
      body.textContent = '';
      var l = h('div.z2t-list');
      if (!auto.rules.length) l.appendChild(h('p.z2p-empty', { text: T('when.none') }));
      auto.rules.forEach(function (r) {
        var cb = h('input', { type: 'checkbox', checked: r.on, 'aria-label': T('when.toggle', r.id) });
        cb.addEventListener('change', function () { r.on = cb.checked; save(); });
        var last = (r.log || [])[r.log.length - 1];
        l.appendChild(h('div.z2t-rule', null,
          h('div.z2t-rule-h', null, h('b', { text: r.name || r.trig }), h('span.z2t-ph-gap'), cb,
            h('button.z2t-s-b', { type: 'button', 'aria-label': T('when.del'), text: '✕', on: { click: function () { auto.rules = auto.rules.filter(function (x) { return x !== r; }); save(); draw(); } } })),
          r.name ? h('div.z2p-dim-note', { text: r.trig }) : null,
          h('code.z2t-rule-c', { text: r.cmd }),
          h('div.z2p-dim-note', { text: last ? T('when.last', Z.hms(new Date(last.t)), last.skip ? 'skip' : 'exit=' + last.st) : T('when.nolog') })));
      });
      body.appendChild(l);
      /* 追加 */
      var sel = h('select', { 'aria-label': T('when.trigger') });
      TRIGS.forEach(function (t) { sel.appendChild(h('option', { value: t, text: t })); });
      var extra = h('input.z2p-input', { type: 'text', placeholder: T('when.notifyword'), 'aria-label': T('when.notifyword'), hidden: true });
      sel.addEventListener('change', function () { extra.hidden = sel.value !== 'notify:'; });
      var cmd = h('input.z2p-input', { type: 'text', placeholder: 'z2-notify "…"', 'aria-label': T('when.command') });
      [extra, cmd].forEach(function (x) { x.addEventListener('keydown', function (ev) { ev.stopPropagation(); }); });
      var line = h('code.z2t-rule-c');
      function upd() { line.textContent = 'z2-when ' + (sel.value + (sel.value === 'notify:' ? extra.value : '')) + " run '" + cmd.value + "'"; }
      [sel, extra, cmd].forEach(function (x) { x.addEventListener('input', upd); x.addEventListener('change', upd); });
      upd();
      body.appendChild(h('div.z2t-form', null,
        h('h3.z2t-fh', { text: '＋ ' + T('when.new') }),
        h('label.z2t-row', null, h('span', { text: T('when.trigger') }), sel), extra,
        h('label.z2t-row.col', null, h('span', { text: T('when.command') }), cmd),
        h('p.z2p-dim-note', { text: T('when.same') }), line,
        h('button.z2p-btn', { type: 'button', text: T('when.save'), on: { click: function () {
          if (!cmd.value.trim()) { Z.phone.toast(T('when.needcmd')); return; }
          var trig = sel.value + (sel.value === 'notify:' ? extra.value : '');
          auto.rules.push({ id: 'r' + (auto.seq++), trig: trig, on: true, cmd: cmd.value.trim(), log: [], fired: 0 });
          save(); draw();
        } } })));
    }
    draw();
  };
})(typeof window !== 'undefined' ? window : globalThis);
