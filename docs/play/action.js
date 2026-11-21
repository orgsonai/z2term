/* z2term play — z2-action（名前付きの画面操作マクロ）。
   本物（docs/ja/ACTION-MACROS.md）の書き方のうち、モックで意味のあるものを読む。
   実行はモックのスマホを本当に操作する: 座標を画面の点に直して、その点の部品に指の出来事を送る。
   指の軌跡は開発者向けオプション「軌跡を表示」で出し入れ（既定 ON）。
   保存先は本物と同じ ~/.z2term/actions/NAME.actions（仮想ファイル） */
(function (G) {
  'use strict';
  var Z = G.Z2P = G.Z2P || {};
  var T = function () { return Z.t.apply(null, arguments); };
  var A = Z.action = {};
  var DIR = '/root/.z2term/actions';

  /* ======================= 読み取り ======================= */
  function perr(line, msg) { var e = new Error(msg); e.line = line; return e; }

  function stripComment(s) {
    /* 空白の後の # で、直後が空白か行末のものから行末までがコメント */
    var m = /(^|\s)#(\s|$)/.exec(s);
    return m ? s.slice(0, m.index) : s;
  }

  function parseSel(s, ln) {
    var m = /^(text|desc|id)(~?=)(.+)$/.exec(s.trim());
    if (!m) throw perr(ln, T('act.badsel', s));
    if (m[3].length > 256) throw perr(ln, T('act.badsel', s));
    return { k: m[1], partial: m[2] === '~=', v: m[3].trim() };
  }
  function parseCond(s, ln) {
    var ors = s.split('|').map(function (x) { return x.trim(); }).filter(Boolean);
    if (!ors.length) throw perr(ln, T('act.badcond', s));
    return ors.map(function (o) {
      return o.split(',').map(function (t) {
        t = t.trim();
        var neg = false;
        if (t[0] === '!') { neg = true; t = t.slice(1).trim(); }
        if (/^ui\s+/.test(t)) return { neg: neg, ui: parseSel(t.replace(/^ui\s+/, ''), ln) };
        if (/^(airplane|wifi|charging|torch|screen)$/.test(t) || /^level\s*[<>=]\s*\d+$/.test(t)) return { neg: neg, st: t };
        throw perr(ln, T('act.badcond', t));
      });
    });
  }
  function num(v, ln, name) {
    var n = parseFloat(v);
    if (!isFinite(n)) throw perr(ln, T('act.badnum', name || v));
    return n;
  }

  A.parse = function (text) {
    var head = { version: 1, timeout: 30, human: 0 };
    var root = [], stack = [{ body: root }];
    var lines = String(text).replace(/\r/g, '').split('\n');
    var count = 0;
    for (var i = 0; i < lines.length; i++) {
      var ln = i + 1;
      var raw = lines[i];
      var s = raw.trim();
      if (!s || s[0] === '#') continue;
      var top = stack[stack.length - 1];
      var mh = /^(version|timeout|human|screen)=(\S*)/.exec(s);
      if (mh) {
        var v = stripComment(s).split('=')[1].trim();
        if (mh[1] === 'version') { if (v !== '1' && v !== '2') throw perr(ln, T('act.badver')); head.version = +v; }
        else if (mh[1] === 'timeout') head.timeout = Math.max(1, Math.min(600, num(v, ln, 'timeout')));
        else if (mh[1] === 'human') head.human = v === 'on' ? 5 : v === 'off' ? 0 : Math.max(0, Math.min(20, num(v, ln, 'human')));
        else head.screen = v;
        continue;
      }
      var cmdLine = /^command\s/.test(s);
      if (!cmdLine) s = stripComment(s).trim();
      var w = s.split(/\s+/);
      var op = w[0];
      var st = null;
      function need(n) { if (w.length < n + 1) throw perr(ln, T('act.args', op)); }
      function v2() { if (head.version < 2) throw perr(ln, T('act.v2', op)); }
      switch (op) {
        case 'launch': case 'target': need(1); st = { op: op, pkg: w[1] }; break;
        case 'wait': need(1); st = { op: 'wait', ms: num(w[1], ln) }; break;
        case 'tap':
          need(1);
          if (w[1] === 'ui') { need(2); st = { op: 'tapui', sel: parseSel(s.replace(/^tap\s+ui\s+/, ''), ln) }; }
          else { need(3); st = { op: 'tap', unit: w[1], x: num(w[2], ln), y: num(w[3], ln) }; }
          break;
        case 'click': need(1); st = { op: 'click', sel: parseSel(s.replace(/^click\s+/, ''), ln) }; break;
        case 'long-press': need(3); st = { op: 'long', unit: w[1], x: num(w[2], ln), y: num(w[3], ln), ms: w[4] ? num(w[4], ln) : 700 }; break;
        case 'double-tap': need(3); st = { op: 'dtap', unit: w[1], x: num(w[2], ln), y: num(w[3], ln) }; break;
        case 'swipe': need(5); st = { op: 'swipe', unit: w[1], x1: num(w[2], ln), y1: num(w[3], ln), x2: num(w[4], ln), y2: num(w[5], ln), ms: w[6] ? num(w[6], ln) : 400 }; break;
        case 'scroll': need(1); st = { op: 'scroll', dy: num(w[1], ln), ms: w[2] ? num(w[2], ln) : 500, x: w[3] ? num(w[3], ln) : 50, y: w[4] ? num(w[4], ln) : 50 }; break;
        case 'drag':
          need(3);
          var pts = [], ms = 200;
          w.slice(1).forEach(function (p) {
            var m = /^(-?[\d.]+),(-?[\d.]+)$/.exec(p);
            if (m) pts.push([+m[1], +m[2]]); else ms = num(p, ln);
          });
          if (pts.length < 2) throw perr(ln, T('act.args', op));
          st = { op: 'drag', pts: pts, ms: ms };
          break;
        case 'type': st = { op: 'type', text: s.replace(/^type\s?/, '') }; break;
        case 'enter': st = { op: 'enter' }; break;
        case 'key': need(1); if (!/^(back|home|recents|notifications|quick_settings)$/.test(w[1])) throw perr(ln, T('act.badkey', w[1])); st = { op: 'key', k: w[1] }; break;
        case 'wait-ui': need(2); st = { op: 'waitui', ms: num(w[1], ln), sel: parseSel(s.replace(/^wait-ui\s+\S+\s+/, ''), ln) }; break;
        case 'wait-until': need(2); st = { op: 'waituntil', ms: num(w[1], ln), cond: parseCond(s.replace(/^wait-until\s+\S+\s+/, ''), ln) }; break;
        case 'command': need(1); st = { op: 'command', cmd: raw.trim().replace(/^command\s+/, '') }; break;
        case 'call': v2(); need(1); st = { op: 'call', name: w[1] }; break;
        case 'repeat':
          v2();
          var rm = /^repeat(?:\s+(\d+))?(?:\s+until\s+(.+))?$/.exec(s);
          if (!rm || (!rm[1] && !rm[2])) throw perr(ln, T('act.args', op));
          st = { op: 'repeat', n: rm[1] ? +rm[1] : 1000, until: rm[2] ? parseCond(rm[2], ln) : null, body: [] };
          (top.inElse ? top.els : top.body).push(st); count++;
          stack.push(st);
          continue;
        case 'if':
          v2(); need(1);
          st = { op: 'if', cond: parseCond(s.replace(/^if\s+/, ''), ln), body: [], els: null };
          (top.inElse ? top.els : top.body).push(st); count++;
          stack.push(st);
          continue;
        case 'else':
          if (top.op !== 'if' || top.els) throw perr(ln, T('act.else'));
          top.els = []; top.inElse = true;
          continue;
        case 'end':
          if (stack.length < 2) throw perr(ln, T('act.end'));
          stack.pop();
          continue;
        default: throw perr(ln, T('act.unknown', op));
      }
      if (st.unit && st.unit !== 'percent' && st.unit !== 'px') throw perr(ln, T('act.unit', st.unit));
      (top.inElse ? top.els : top.body).push(st);
      count++;
    }
    if (stack.length > 1) throw perr(lines.length, T('act.noend'));
    if (count > 64) throw perr(lines.length, T('act.toomany'));
    return { head: head, steps: root };
  };

  /* ======================= 画面の点と部品 ======================= */
  function screenRect() { return Z.phone.screen().getBoundingClientRect(); }
  function toPoint(unit, x, y, human) {
    var r = screenRect();
    var px = unit === 'px' ? x / 1080 * r.width : x / 100 * r.width;
    var py = unit === 'px' ? y / 2400 * r.height : y / 100 * r.height;
    if (human) { px += (Math.random() - 0.5) * human * 1.6; py += (Math.random() - 0.5) * human * 1.6; }
    return [r.left + Math.max(1, Math.min(r.width - 1, px)), r.top + Math.max(1, Math.min(r.height - 1, py))];
  }

  function visible(el) {
    if (!el.getClientRects().length) return false;
    if (el.closest('[hidden]')) return false;
    var cs = getComputedStyle(el);
    if (cs.visibility === 'hidden' || cs.display === 'none') return false;
    var r = el.getBoundingClientRect(), s = screenRect();
    return r.width > 0 && r.height > 0 && r.bottom > s.top && r.top < s.bottom && r.right > s.left && r.left < s.right;
  }
  function roots() {
    var els = Z.phone.els;
    var list = [];
    if (Z.phone.modalOpen()) list.push(els.modal);
    if (Z.phone.shadeOpen()) list.push(els.shade);
    list.push(els.heads);
    if (!els.recents.hidden) list.push(els.recents);
    var cur = Z.appById(Z.phone.current());
    if (cur && cur.el) list.push(cur.el);
    list.push(els.status, els.nav);
    return list;
  }
  function own(el) {
    if (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA') return el.getAttribute('placeholder') || '';
    return (el.textContent || '').replace(/\s+/g, ' ').trim();
  }
  function findUi(sel) {
    var cands = [];
    roots().forEach(function (r) {
      Array.prototype.forEach.call(r.querySelectorAll('*'), function (el) {
        var v;
        if (sel.k === 'text') v = own(el);
        else if (sel.k === 'desc') v = el.getAttribute('aria-label') || el.getAttribute('title') || '';
        else v = el.getAttribute('data-id') || '';
        if (!v) return;
        var ok = sel.partial ? v.indexOf(sel.v) >= 0 : v === sel.v;
        if (ok && visible(el)) cands.push(el);
      });
    });
    if (sel.k === 'text') cands = cands.filter(function (el) { return !cands.some(function (o) { return o !== el && el.contains(o); }); });
    return cands.filter(function (el, i) { return cands.indexOf(el) === i; });
  }
  A.findUi = findUi;
  function center(el) { var r = el.getBoundingClientRect(); return [r.left + r.width / 2, r.top + r.height / 2]; }

  function pev(type, el, p) {
    var o = { bubbles: true, cancelable: true, clientX: p[0], clientY: p[1], pointerId: 77, pointerType: 'touch', isPrimary: true, button: 0, buttons: type === 'pointerup' ? 0 : 1 };
    try { el.dispatchEvent(new PointerEvent(type, o)); } catch (e) { el.dispatchEvent(new MouseEvent(type.replace('pointer', 'mouse'), o)); }
  }
  function hitAt(p) {
    var el = document.elementFromPoint(p[0], p[1]);
    var scr = Z.phone.screen();
    if (!el || !scr.contains(el)) return null;
    if (el.classList.contains('z2p-trail') || el.closest('svg.z2p-trail')) return null;
    return el;
  }
  /* 何が起きているか追えるように、指の印をそこまで動かしてから押す（ゆっくり見せる） */
  var PACE = { move: 420, press: 260, gap: 650, typeChar: 130, launch: 900 };
  async function fingerTo(p, r) {
    var ms = r.human ? PACE.move * (1 + (Math.random() - 0.5) * 0.3) : PACE.move;
    Z.phone.finger(p, ms);
    await wait(ms + 40, r);
  }
  async function press(p, r, kind) {
    await fingerTo(p, r);
    Z.phone.fingerDown(true);
    await wait(PACE.press, r);
    tapAt(p, kind);
    Z.phone.fingerDown(false);
  }
  function tapAt(p, kind) {
    Z.phone.trail([p], kind);
    var el = hitAt(p);
    if (!el) return false;
    pev('pointerdown', el, p);
    pev('pointerup', el, p);
    var f = el.closest('input, textarea, select, button, [tabindex]');
    if (f && (f.tagName === 'INPUT' || f.tagName === 'TEXTAREA')) { f.focus(); return true; }
    if (f && f.tagName === 'BUTTON') { f.click(); return true; }
    el.dispatchEvent(new MouseEvent('click', { bubbles: true, cancelable: true, clientX: p[0], clientY: p[1] }));
    return true;
  }
  function scrollerAt(el) {
    while (el && el !== document.body) {
      var cs = getComputedStyle(el);
      if (/(auto|scroll)/.test(cs.overflowY) && el.scrollHeight > el.clientHeight) return el;
      if (/(auto|scroll)/.test(cs.overflowX) && el.scrollWidth > el.clientWidth) return el;
      el = el.parentElement;
    }
    return null;
  }

  /* ======================= 実行 ======================= */
  var run = null;

  function condOk(cond) {
    var s = Z.stateObj();
    return cond.some(function (and) {
      return and.every(function (t) {
        var r;
        if (t.ui) r = findUi(t.ui).length > 0;
        else {
          var m = /^level\s*([<>=])\s*(\d+)$/.exec(t.st);
          if (m) r = m[1] === '<' ? s.level < +m[2] : m[1] === '>' ? s.level > +m[2] : s.level === +m[2];
          else r = !!s[t.st] && s[t.st] !== 'off';
        }
        return t.neg ? !r : r;
      });
    });
  }

  function Stop(msg) { var e = new Error(msg); e.stop = true; return e; }

  async function wait(ms, r) {
    var hm = r.human ? 1 + (Math.random() - 0.5) * r.human * 0.04 : 1;
    var end = Date.now() + ms * hm;
    while (Date.now() < end) {
      if (r.sig.aborted) throw Stop(T('act.stopped'));
      if (Date.now() > r.deadline) throw Stop(T('act.timeout'));
      await Z.sleep(Math.min(40, end - Date.now()));
    }
  }

  async function animate(points, ms, r, onStep) {
    var n = Math.max(2, Math.round(ms / 16));
    var path = [];
    for (var i = 0; i <= n; i++) {
      var f = i / n;
      var seg = f * (points.length - 1);
      var k = Math.min(points.length - 2, Math.floor(seg));
      var lf = seg - k;
      var p = [points[k][0] + (points[k + 1][0] - points[k][0]) * lf, points[k][1] + (points[k + 1][1] - points[k][1]) * lf];
      if (r.human && points.length === 2) { var bow = Math.sin(f * Math.PI) * r.human * 1.2; p[0] += bow; }
      path.push(p);
      Z.phone.finger(p, 0);
      if (onStep) onStep(p, i / n);
      await wait(ms / n, r);
    }
    Z.phone.trail(path, 'swipe');
    return path;
  }

  async function swipe(a, b, ms, r) {
    ms = Math.max(ms, 600);
    await fingerTo(a, r);
    Z.phone.fingerDown(true);
    await wait(PACE.press, r);
    var el = hitAt(a);
    if (!el) { Z.phone.fingerDown(false); return; }
    if (el.closest('.z2p-status') && b[1] > a[1]) { await animate([a, b], ms, r); Z.phone.shade(true); return; }
    if (Z.phone.shadeOpen() && b[1] < a[1]) { await animate([a, b], ms, r); Z.phone.shade(false); return; }
    var sc = scrollerAt(el);
    var top0 = sc ? sc.scrollTop : 0, left0 = sc ? sc.scrollLeft : 0;
    pev('pointerdown', el, a);
    await animate([a, b], ms, r, function (p) {
      if (sc) { sc.scrollTop = top0 - (p[1] - a[1]); sc.scrollLeft = left0 - (p[0] - a[0]); }
      pev('pointermove', el, p);
    });
    pev('pointerup', el, b);
    Z.phone.fingerDown(false);
  }

  function activeInput() {
    var a = document.activeElement;
    if (a && (a.tagName === 'INPUT' || a.tagName === 'TEXTAREA') && Z.phone.screen().contains(a) && !a.classList.contains('z2t-sink')) return a;
    return null;
  }

  async function exec(steps, r) {
    for (var i = 0; i < steps.length; i++) {
      var s = steps[i];
      if (r.sig.aborted) throw Stop(T('act.stopped'));
      if (Date.now() > r.deadline) throw Stop(T('act.timeout'));
      if (++r.count > 5000) throw Stop(T('act.toolong'));
      r.stepNo++;
      bar(r, s);
      await step(s, r);
      if (s.op !== 'wait' && s.op !== 'repeat' && s.op !== 'if') await wait(r.human ? PACE.gap + Math.random() * r.human * 40 : PACE.gap, r);
    }
  }

  function checkTarget(r) {
    if (!r.target || r.target === 'current') return;
    var a = Z.appByPkg(r.target);
    if (!a || Z.phone.current() !== a.id) throw Stop(T('act.notfront', r.target));
  }

  async function step(s, r) {
    var p, el, list;
    switch (s.op) {
      case 'launch':
        var a = Z.appByPkg(s.pkg);
        if (!a) throw Stop(T('act.nopkg', s.pkg));
        Z.phone.open(a.id);
        r.target = s.pkg;
        await wait(PACE.launch, r);
        return;
      case 'target':
        if (s.pkg !== 'current' && !Z.appByPkg(s.pkg)) throw Stop(T('act.nopkg', s.pkg));
        r.target = s.pkg; return;
      case 'wait': await wait(s.ms, r); return;
      case 'tap': checkTarget(r); await press(toPoint(s.unit, s.x, s.y, r.human), r); return;
      case 'dtap': checkTarget(r); p = toPoint(s.unit, s.x, s.y, r.human); await press(p, r); await wait(120, r); Z.phone.fingerDown(true); await wait(90, r); tapAt(p); Z.phone.fingerDown(false); return;
      case 'long':
        checkTarget(r);
        p = toPoint(s.unit, s.x, s.y, r.human);
        el = hitAt(p);
        await fingerTo(p, r);
        el = hitAt(p);
        Z.phone.trail([p], 'long');
        Z.phone.fingerDown(true);
        if (!el) { await wait(s.ms, r); Z.phone.fingerDown(false); return; }
        pev('pointerdown', el, p);
        await wait(s.ms, r);
        pev('pointerup', el, p);
        Z.phone.fingerDown(false);
        return;
      case 'tapui': case 'click':
        checkTarget(r);
        list = findUi(s.sel);
        if (!list.length) throw Stop(T('act.noui', s.sel.k + '=' + s.sel.v));
        if (list.length > 1) throw Stop(T('act.manyui', s.sel.k + '=' + s.sel.v, list.length));
        el = list[0];
        if (s.op === 'tapui') {
          p = center(el);
          if (r.human) { p[0] += (Math.random() - 0.5) * r.human * 1.4; p[1] += (Math.random() - 0.5) * r.human * 1.4; }
          await press(p, r);
        } else {
          await fingerTo(center(el), r);
          Z.phone.trail([center(el)], 'tap');
          if (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA') el.focus(); else el.click();
        }
        return;
      case 'swipe':
        checkTarget(r);
        await swipe(toPoint(s.unit, s.x1, s.y1, r.human), toPoint(s.unit, s.x2, s.y2, r.human), s.ms, r);
        return;
      case 'scroll':
        checkTarget(r);
        var c = toPoint('percent', s.x, s.y, 0);
        await swipe(c, [c[0], c[1] - s.dy * screenRect().height / 2400], s.ms, r);
        return;
      case 'drag':
        checkTarget(r);
        var dp = s.pts.map(function (q) { return toPoint('percent', q[0], q[1], r.human); });
        await fingerTo(dp[0], r);
        Z.phone.fingerDown(true);
        await animate(dp, Math.max(s.ms, 250) * (dp.length - 1), r);
        Z.phone.fingerDown(false);
        return;
      case 'type':
        var inp = activeInput();
        if (!inp) {
          if (Z.phone.current() === 'z2term') { Z.zt.deliver({ text: s.text }); return; }
          throw Stop(T('act.noinput'));
        }
        inp.value = '';
        for (var k = 0; k < s.text.length; k++) {
          inp.value += s.text[k];
          inp.dispatchEvent(new Event('input', { bubbles: true }));
          await wait(r.human ? PACE.typeChar + Math.random() * r.human * 12 : PACE.typeChar, r);
        }
        return;
      case 'enter':
        await wait(300, r);
        var inp2 = activeInput();
        if (inp2) {
          var kd = new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true });
          var go = inp2.dispatchEvent(kd);
          if (go && inp2.form) { if (inp2.form.requestSubmit) inp2.form.requestSubmit(); else inp2.form.dispatchEvent(new Event('submit', { cancelable: true })); }
          return;
        }
        if (Z.phone.current() === 'z2term') { Z.zt.deliver({ key: 'Enter' }); return; }
        return;
      case 'key': Z.phone.key(s.k); return;
      case 'waitui':
        var end = Date.now() + s.ms;
        while (!findUi(s.sel).length) {
          if (Date.now() > end) throw Stop(T('act.noui', s.sel.k + '=' + s.sel.v));
          await wait(100, r);
        }
        return;
      case 'waituntil':
        var end2 = s.ms > 0 ? Date.now() + s.ms : Infinity;
        while (!condOk(s.cond)) {
          if (Date.now() > end2) throw Stop(T('act.untilfail'));
          await wait(100, r);
        }
        return;
      case 'command':
        var res = await Z.runBackground(s.cmd, r.sig, 'z2-action ' + r.name);
        if (r.out && res.out) r.out(res.out);
        return;
      case 'call':
        if (r.depth > 4) throw Stop(T('act.deep'));
        var sub = load(s.name);
        if (sub.error) throw Stop(sub.error);
        r.depth++;
        await exec(sub.prog.steps, r);
        r.depth--;
        return;
      case 'repeat':
        for (var n = 0; n < s.n; n++) {
          if (s.until && condOk(s.until)) break;
          await exec(s.body, r);
        }
        return;
      case 'if':
        await exec(condOk(s.cond) ? s.body : (s.els || []), r);
        return;
    }
  }

  function bar(r, s) {
    Z.phone.stopBar({
      name: r.name, step: T('act.step', r.stepNo, s.op), human: r.human0 ? !!r.human : null,
      onStop: function () { r.sig.aborted = true; },
      onHuman: function () { r.human = r.human ? 0 : r.human0; bar(r, s); }
    });
  }

  function load(name) {
    if (!/^[A-Za-z0-9_-]{1,64}$/.test(name)) return { error: T('act.badname', name) };
    var p = DIR + '/' + name + '.actions';
    var txt;
    try { txt = Z.fs.readFile(p); } catch (e) { return { error: T('act.noname', name) }; }
    try { return { prog: A.parse(txt) }; } catch (e2) { return { error: name + ':' + (e2.line || '?') + ': ' + e2.message }; }
  }

  A.start = function (name, sig, out) {
    if (run) return Promise.resolve({ ok: false, error: T('act.busy', run.name) });
    var l = load(name);
    if (l.error) return Promise.resolve({ ok: false, error: l.error });
    var r = run = {
      name: name, sig: sig || { aborted: false }, deadline: Date.now() + l.prog.head.timeout * 1000,
      human: l.prog.head.human, human0: l.prog.head.human, count: 0, stepNo: 0, depth: 0, target: null, out: out
    };
    Z.bus.emit('action-start', { name: name });
    return exec(l.prog.steps, r).then(function () {
      return { ok: true };
    }, function (e) {
      return { ok: false, error: e.message, stopped: !!(e.stop && r.sig.aborted) };
    }).then(function (res) {
      run = null;
      Z.phone.stopBar(null);
      Z.phone.fingerHide();
      res.name = name;
      Z.bus.emit('action-done', res);
      return res;
    });
  };
  A.running = function () { return run; };

  /* ======================= コマンド ======================= */
  function names() {
    var list = [];
    try { list = Z.fs.readdir(DIR).filter(function (n) { return /\.actions$/.test(n); }).map(function (n) { return n.replace(/\.actions$/, ''); }); } catch (e) { list = []; }
    return list;
  }
  A.cmd = async function (c) {
    var a = c.args, sub = a[0];
    if (!sub) { c.out(Z.helpText('z2-action') + '\n'); return 0; }
    if (sub === 'list') {
      var ns = names();
      if (!ns.length) c.out(T('act.none') + '\n');
      ns.forEach(function (n) { c.out(n + '\n'); });
      return 0;
    }
    if (sub === 'show') {
      if (!a[1]) { c.err('usage: z2-action show NAME\n'); return 2; }
      try { c.out(Z.fs.readFile(DIR + '/' + a[1] + '.actions')); } catch (e) { c.err(T('act.noname', a[1]) + '\n'); return 1; }
      return 0;
    }
    if (sub === 'remove') {
      try { Z.fs.rm(DIR + '/' + a[1] + '.actions'); } catch (e) { c.err(T('act.noname', a[1]) + '\n'); return 1; }
      return 0;
    }
    if (sub === 'save') {
      var name = a[1], src = a[2];
      if (!name || !src) { c.err('usage: z2-action save NAME FILE|-\n'); return 2; }
      if (!/^[A-Za-z0-9_-]{1,64}$/.test(name)) { c.err(T('act.badname', name) + '\n'); return 2; }
      var text;
      if (src === '-') text = c.stdin || '';
      else { try { text = c.fs.readFile(c.abs(src)); } catch (e) { c.err(Z.fsErr('z2-action', e, src) + '\n'); return 1; } }
      try { A.parse(text); }
      catch (e2) { c.err('z2-action: ' + (e2.line ? T('act.line', e2.line) + ': ' : '') + e2.message + '\n'); return 1; }
      var r = screenRect();
      text = text.replace(/^(\s*)screen=current/m, '$1screen=' + Math.round(r.width) + 'x' + Math.round(r.height));
      Z.fs.mkdir(DIR, true, true);
      Z.fs.writeFile(DIR + '/' + name + '.actions', text);
      c.out(T('act.saved', name) + '\n');
      Z.bus.emit('action-saved', name);
      return 0;
    }
    if (sub === 'stop') { if (run) run.sig.aborted = true; return 0; }
    if (sub === 'run' || sub === 'start') {
      if (!a[1]) { c.err('usage: z2-action ' + sub + ' NAME\n'); return 2; }
      if (sub === 'start') {
        A.start(a[1], { aborted: false }).then(function (res) { if (!res.ok) Z.phone.toast('z2-action: ' + res.error); });
        c.out(T('act.started', a[1]) + '\n');
        return 0;
      }
      var sig = { aborted: false };
      var iv = setInterval(function () { if (c.sig.aborted) sig.aborted = true; }, 50);
      var res = await A.start(a[1], sig, function (s) { c.out(s); });
      clearInterval(iv);
      if (res.ok) { c.out(T('act.done', a[1]) + '\n'); return 0; }
      c.err('z2-action: ' + res.error + '\n');
      return res.stopped ? 130 : 1;
    }
    if (sub === 'pick') { c.err(T('act.pick') + '\n'); return 1; }
    c.err(Z.helpText('z2-action') + '\n');
    return 2;
  };
})(typeof window !== 'undefined' ? window : globalThis);
