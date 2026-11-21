/* z2term play — ふつうのコマンド（ls cat echo … less）。
   OS（ディストロ）は入っていない設定なので、パッケージ管理やネットワークの道具は一言で断る。 */
(function (G) {
  'use strict';
  var Z = G.Z2P = G.Z2P || {};
  var C = Z.cmds = Z.cmds || {};
  var T = function () { return Z.t.apply(null, arguments); };

  /* -abc 形式のフラグと残りを分ける。valued は値を取るフラグ（-n 5）。 */
  function opts(args, valued) {
    var f = {}, rest = [], i = 0;
    valued = valued || '';
    for (; i < args.length; i++) {
      var a = args[i];
      if (a === '--') { rest = rest.concat(args.slice(i + 1)); break; }
      if (a.length > 1 && a[0] === '-' && a[1] !== '-') {
        if (/^-\d+$/.test(a) && valued.indexOf('n') >= 0) { f.n = a.slice(1); continue; }
        for (var j = 1; j < a.length; j++) {
          var ch = a[j];
          if (valued.indexOf(ch) >= 0) {
            f[ch] = a.slice(j + 1) || args[++i];
            break;
          }
          f[ch] = true;
        }
      } else if (a.indexOf('--') === 0 && a.length > 2) {
        var eq = a.indexOf('=');
        if (eq > 0) f[a.slice(2, eq)] = a.slice(eq + 1); else f[a.slice(2)] = true;
      } else rest.push(a);
    }
    return { f: f, a: rest };
  }
  Z.opts = opts;

  function readInputs(c, files, cmd) {
    /* ファイルがあれば順に、無ければ標準入力。読めないものは err に書いて飛ばす。 */
    var res = [], bad = false;
    if (!files.length) return { list: [{ name: '-', text: c.stdin || '' }], bad: false };
    files.forEach(function (p) {
      if (p === '-') { res.push({ name: '-', text: c.stdin || '' }); return; }
      try { res.push({ name: p, text: c.fs.readFile(c.abs(p)) }); }
      catch (e) { c.err(Z.fsErr(cmd, e, p) + '\n'); bad = true; }
    });
    return { list: res, bad: bad };
  }
  function lines(t) { if (!t) return []; var a = t.split('\n'); if (a[a.length - 1] === '') a.pop(); return a; }

  var ESC = '\x1b[';
  function color(s, code) { return ESC + code + 'm' + s + ESC + '0m'; }
  Z.color = color;

  /* ---------------- ファイル ---------------- */
  C.ls = function (c) {
    var o = opts(c.args);
    var targets = o.a.length ? o.a : ['.'];
    var st = 0;
    var self = this;
    var long = o.f.l, all = o.f.a || o.f.A, one = o.f['1'] || long;
    function fmtName(name, node) {
      if (node && node.t === 'd') return color(name, '1;34') + (o.f.F ? '/' : '');
      if (/\.(sh|actions)$/.test(name)) return color(name, '32');
      return name;
    }
    function fmtLong(name, node) {
      var d = new Date(node.m || Date.now());
      var mon = d.toLocaleString('en-US', { month: 'short' });
      var size = node.t === 'd' ? 4096 : node.d.length;
      return (node.t === 'd' ? 'drwxr-xr-x' : '-rw-r--r--') + ' 1 root root ' +
        ('      ' + size).slice(-7) + ' ' + mon + ' ' + ('  ' + d.getDate()).slice(-2) + ' ' +
        Z.pad(d.getHours()) + ':' + Z.pad(d.getMinutes()) + ' ' + fmtName(name, node);
    }
    targets.forEach(function (t, idx) {
      var p = c.abs(t);
      var node = c.fs.stat(p);
      if (!node) { c.err("ls: cannot access '" + t + "': No such file or directory\n"); st = 2; return; }
      if (node.t === 'f') { c.out((long ? fmtLong(t, node) : t) + '\n'); return; }
      if (targets.length > 1) c.out((idx ? '\n' : '') + t + ':\n');
      var names = Object.keys(node.c).sort();
      if (!all) names = names.filter(function (n) { return n[0] !== '.'; });
      else if (o.f.a) names = ['.', '..'].concat(names);
      if (long) {
        c.out('total ' + names.length + '\n');
        names.forEach(function (n) {
          var nn = n === '.' || n === '..' ? { t: 'd', m: node.m } : node.c[n];
          c.out(fmtLong(n, nn) + '\n');
        });
      } else if (names.length) {
        var parts = names.map(function (n) { return fmtName(n, n === '.' || n === '..' ? { t: 'd' } : node.c[n]); });
        c.out(parts.join(one ? '\n' : '  ') + '\n');
      }
    });
    return st;
  };

  C.cd = function (c) {
    var sh = c.sh;
    var t = c.args[0];
    if (t === undefined) t = sh.env.HOME || '/root';
    if (t === '-') { t = sh.env.OLDPWD || '/root'; c.out(c.fs.pretty(t) + '\n'); }
    var p = c.abs(t);
    var n = c.fs.stat(p);
    if (!n) { c.err('sh: cd: ' + t + ': No such file or directory\n'); return 1; }
    if (n.t !== 'd') { c.err('sh: cd: ' + t + ': Not a directory\n'); return 1; }
    sh.env.OLDPWD = sh.env.PWD;
    sh.env.PWD = p;
    return 0;
  };

  C.pwd = function (c) { c.out(c.sh.cwd() + '\n'); };

  C.cat = function (c) {
    var o = opts(c.args);
    var r = readInputs(c, o.a, 'cat');
    var n = 0;
    r.list.forEach(function (x) {
      if (o.f.n) lines(x.text).forEach(function (l) { c.out(('     ' + (++n)).slice(-6) + '\t' + l + '\n'); });
      else c.out(x.text);
    });
    return r.bad ? 1 : 0;
  };

  C.mkdir = function (c) {
    var o = opts(c.args), st = 0;
    if (!o.a.length) { c.err('mkdir: missing operand\n'); return 1; }
    o.a.forEach(function (d) {
      try { c.fs.mkdir(c.abs(d), !!o.f.p); }
      catch (e) { c.err("mkdir: can't create directory '" + d + "': " + Z.fsErr('', e).split(': ').pop() + '\n'); st = 1; }
    });
    return st;
  };

  C.rmdir = function (c) {
    var st = 0;
    c.args.forEach(function (d) {
      try { c.fs.rmdir(c.abs(d)); } catch (e) { c.err(Z.fsErr('rmdir', e, d) + '\n'); st = 1; }
    });
    return st;
  };

  C.rm = function (c) {
    var o = opts(c.args), st = 0;
    if (!o.a.length) { if (!o.f.f) { c.err('rm: missing operand\n'); return 1; } return 0; }
    o.a.forEach(function (d) {
      try { c.fs.rm(c.abs(d), !!(o.f.r || o.f.R)); }
      catch (e) {
        if (e.code === 'ENOENT' && o.f.f) return;
        if (e.code === 'EISDIR') c.err("rm: '" + d + "' is a directory\n");
        else c.err(Z.fsErr('rm', e, d) + '\n');
        st = 1;
      }
    });
    return st;
  };

  C.touch = function (c) {
    var st = 0;
    c.args.filter(function (a) { return a[0] !== '-'; }).forEach(function (f) {
      var p = c.abs(f);
      try {
        var n = c.fs.stat(p);
        if (n) { n.m = Date.now(); c.fs.onChange(p); } else c.fs.writeFile(p, '');
      } catch (e) { c.err(Z.fsErr('touch', e, f) + '\n'); st = 1; }
    });
    return st;
  };

  C.cp = function (c) {
    var o = opts(c.args);
    if (o.a.length < 2) { c.err('cp: missing destination file operand\n'); return 1; }
    var dst = o.a.pop(), st = 0;
    o.a.forEach(function (s) {
      try { c.fs.cp(c.abs(s), c.abs(dst), !!(o.f.r || o.f.R || o.f.a)); }
      catch (e) {
        if (e.code === 'EISDIR') c.err("cp: -r not specified; omitting directory '" + s + "'\n");
        else c.err(Z.fsErr('cp', e, s) + '\n');
        st = 1;
      }
    });
    return st;
  };

  C.mv = function (c) {
    var o = opts(c.args);
    if (o.a.length < 2) { c.err('mv: missing destination file operand\n'); return 1; }
    var dst = o.a.pop(), st = 0;
    o.a.forEach(function (s) {
      try { c.fs.mv(c.abs(s), c.abs(dst)); } catch (e) { c.err(Z.fsErr('mv', e, s) + '\n'); st = 1; }
    });
    return st;
  };

  C.tree = function (c) {
    var root = c.args[0] || '.';
    var n = c.fs.stat(c.abs(root));
    if (!n || n.t !== 'd') { c.err(root + ' [error opening dir]\n'); return 2; }
    var dirs = 0, files = 0;
    c.out(color(root, '1;34') + '\n');
    (function walk(node, pre) {
      var names = Object.keys(node.c).filter(function (x) { return x[0] !== '.'; }).sort();
      names.forEach(function (name, i) {
        var last = i === names.length - 1;
        var ch = node.c[name];
        c.out(pre + (last ? '└── ' : '├── ') + (ch.t === 'd' ? color(name, '1;34') : name) + '\n');
        if (ch.t === 'd') { dirs++; walk(ch, pre + (last ? '    ' : '│   ')); } else files++;
      });
    })(n, '');
    c.out('\n' + dirs + ' directories, ' + files + ' files\n');
  };

  C.du = function (c) {
    var t = c.args.filter(function (a) { return a[0] !== '-'; })[0] || '.';
    var n = c.fs.stat(c.abs(t));
    if (!n) { c.err(Z.fsErr('du', { code: 'ENOENT' }, t) + '\n'); return 1; }
    c.out(Z.bytes(c.fs.size(n)) + '\t' + t + '\n');
  };

  /* ---------------- 文字 ---------------- */
  function unescape(s) {
    return s.replace(/\\(n|t|r|\\|e|a|0[0-7]{0,3}|x[0-9a-fA-F]{1,2}|c)/g, function (m, x) {
      if (x === 'n') return '\n'; if (x === 't') return '\t'; if (x === 'r') return '\r';
      if (x === '\\') return '\\'; if (x === 'e') return '\x1b'; if (x === 'a') return '';
      if (x === 'c') return '\u0000STOP';
      if (x[0] === 'x') return String.fromCharCode(parseInt(x.slice(1), 16));
      return String.fromCharCode(parseInt(x.slice(1) || '0', 8));
    });
  }

  C.echo = function (c) {
    var a = c.args.slice(), nl = true, esc = false;
    while (a.length && /^-[neE]+$/.test(a[0])) {
      if (a[0].indexOf('n') >= 0) nl = false;
      if (a[0].indexOf('e') >= 0) esc = true;
      a.shift();
    }
    var s = a.join(' ');
    if (esc) { s = unescape(s); var k = s.indexOf('\u0000STOP'); if (k >= 0) { s = s.slice(0, k); nl = false; } }
    c.out(s + (nl ? '\n' : ''));
  };

  C.printf = function (c) {
    if (!c.args.length) { c.err('printf: usage: printf FORMAT [ARGUMENT]...\n'); return 2; }
    var fmt = c.args[0], args = c.args.slice(1), out = '';
    var first = true;
    while (first || args.length) {
      first = false;
      var used = 0;
      out += fmt.replace(/%([-0-9.]*)([sdbxc%])|\\(n|t|r|\\|e|x[0-9a-fA-F]{1,2}|0[0-7]{0,3})/g, function (m, w, conv, esc) {
        if (esc !== undefined) return unescape('\\' + esc);
        if (conv === '%') return '%';
        var v = args.length ? args.shift() : '';
        used++;
        if (conv === 'd') v = String(parseInt(v, 10) || 0);
        else if (conv === 'x') v = (parseInt(v, 10) || 0).toString(16);
        else if (conv === 'b') v = unescape(v);
        else if (conv === 'c') v = v.charAt(0);
        var width = parseInt(w, 10);
        if (width) { var padc = w[0] === '0' ? '0' : ' '; while (v.length < Math.abs(width)) v = w[0] === '-' ? v + ' ' : padc + v; }
        return v;
      });
      if (!used) break;
    }
    c.out(out);
  };

  C.head = function (c) {
    var o = opts(c.args, 'nc');
    var n = parseInt(o.f.n || '10', 10);
    var r = readInputs(c, o.a, 'head');
    r.list.forEach(function (x, i) {
      if (r.list.length > 1) c.out((i ? '\n' : '') + '==> ' + x.name + ' <==\n');
      if (o.f.c) { c.out(x.text.slice(0, parseInt(o.f.c, 10))); return; }
      var l = lines(x.text).slice(0, n);
      if (l.length) c.out(l.join('\n') + '\n');
    });
    return r.bad ? 1 : 0;
  };

  C.tail = function (c) {
    var o = opts(c.args, 'nc');
    var nstr = String(o.f.n || '10');
    var r = readInputs(c, o.a, 'tail');
    r.list.forEach(function (x, i) {
      if (r.list.length > 1) c.out((i ? '\n' : '') + '==> ' + x.name + ' <==\n');
      var l = lines(x.text);
      l = nstr[0] === '+' ? l.slice(parseInt(nstr.slice(1), 10) - 1) : l.slice(Math.max(0, l.length - parseInt(nstr, 10)));
      if (l.length) c.out(l.join('\n') + '\n');
    });
    if (o.f.f) c.err('tail: -f ' + T('cmd.nofollow') + '\n');
    return r.bad ? 1 : 0;
  };

  C.wc = function (c) {
    var o = opts(c.args);
    var r = readInputs(c, o.a, 'wc');
    var only = o.f.l || o.f.w || o.f.c || o.f.m;
    var tot = [0, 0, 0];
    function row(x, name) {
      var v = [];
      if (!only || o.f.l) v.push(x[0]);
      if (!only || o.f.w) v.push(x[1]);
      if (!only || o.f.c || o.f.m) v.push(x[2]);
      c.out(v.map(function (n) { return ('       ' + n).slice(-7); }).join(' ') + (name && name !== '-' ? ' ' + name : '') + '\n');
    }
    r.list.forEach(function (x) {
      var t = x.text;
      var v = [(t.match(/\n/g) || []).length, (t.match(/\S+/g) || []).length, new TextEncoder().encode(t).length];
      tot[0] += v[0]; tot[1] += v[1]; tot[2] += v[2];
      row(v, x.name);
    });
    if (r.list.length > 1) row(tot, 'total');
    return r.bad ? 1 : 0;
  };

  C.grep = function (c) {
    var o = opts(c.args, 'e');
    var pat = o.f.e || o.a.shift();
    if (pat == null) { c.err('Usage: grep [OPTION]... PATTERNS [FILE]...\n'); return 2; }
    var re;
    try { re = new RegExp(o.f.F ? pat.replace(/[.*+?^${}()|[\]\\]/g, '\\$&') : pat.replace(/\\\|/g, '|'), o.f.i ? 'i' : ''); }
    catch (e) { c.err('grep: ' + e.message + '\n'); return 2; }
    var r = readInputs(c, o.a, 'grep');
    var hit = 0;
    var multi = r.list.length > 1;
    r.list.forEach(function (x) {
      var cnt = 0;
      lines(x.text).forEach(function (l, i) {
        var m = re.test(l);
        if (o.f.v) m = !m;
        if (!m) return;
        hit++; cnt++;
        if (o.f.c || o.f.l || o.f.q) return;
        var shown = o.f.v ? l : l.replace(new RegExp(re.source, re.flags + 'g'), function (s) { return color(s, '1;31'); });
        c.out((multi ? color(x.name, '35') + ':' : '') + (o.f.n ? color(String(i + 1), '32') + ':' : '') + shown + '\n');
      });
      if (o.f.c) c.out((multi ? x.name + ':' : '') + cnt + '\n');
      if (o.f.l && cnt) c.out(x.name + '\n');
    });
    return r.bad ? 2 : hit ? 0 : 1;
  };

  C.sort = function (c) {
    var o = opts(c.args);
    var r = readInputs(c, o.a, 'sort');
    var all = [];
    r.list.forEach(function (x) { all = all.concat(lines(x.text)); });
    if (o.f.n) all.sort(function (a, b) { return (parseFloat(a) || 0) - (parseFloat(b) || 0); });
    else all.sort(function (a, b) { return a < b ? -1 : a > b ? 1 : 0; });
    if (o.f.r) all.reverse();
    if (o.f.u) all = all.filter(function (v, i) { return i === 0 || v !== all[i - 1]; });
    if (all.length) c.out(all.join('\n') + '\n');
  };

  C.uniq = function (c) {
    var o = opts(c.args);
    var r = readInputs(c, o.a, 'uniq');
    var l = lines(r.list[0] ? r.list[0].text : '');
    var res = [];
    l.forEach(function (v) {
      var last = res[res.length - 1];
      if (last && last.v === v) last.n++; else res.push({ v: v, n: 1 });
    });
    res.forEach(function (x) {
      if (o.f.d && x.n < 2) return;
      if (o.f.u && x.n > 1) return;
      c.out((o.f.c ? ('      ' + x.n).slice(-7) + ' ' : '') + x.v + '\n');
    });
  };

  C.tee = function (c) {
    var o = opts(c.args);
    var s = c.stdin || '';
    o.a.forEach(function (f) {
      try { c.fs.writeFile(c.abs(f), s, !!o.f.a); } catch (e) { c.err(Z.fsErr('tee', e, f) + '\n'); }
    });
    c.out(s);
  };

  C.tr = function (c) {
    var a = c.args, s = c.stdin || '';
    if (a[0] === '-d') { var set = a[1] || ''; c.out(s.split('').filter(function (ch) { return set.indexOf(ch) < 0; }).join('')); return; }
    function expand(x) { return x.replace(/(.)-(.)/g, function (m, p, q) { var r = ''; for (var i = p.charCodeAt(0); i <= q.charCodeAt(0); i++) r += String.fromCharCode(i); return r; }); }
    var f = expand(a[0] || ''), t = expand(a[1] || '');
    c.out(s.split('').map(function (ch) { var i = f.indexOf(ch); return i < 0 ? ch : (t[Math.min(i, t.length - 1)] || ''); }).join(''));
  };

  C.rev = function (c) { lines(c.stdin || '').forEach(function (l) { c.out(l.split('').reverse().join('') + '\n'); }); };

  C.seq = function (c) {
    var a = c.args.map(Number);
    var s = 1, step = 1, e;
    if (a.length === 1) e = a[0]; else if (a.length === 2) { s = a[0]; e = a[1]; } else { s = a[0]; step = a[1]; e = a[2]; }
    if (!(step) || Math.abs((e - s) / step) > 100000) { c.err('seq: invalid\n'); return 1; }
    var out = [];
    for (var i = s; step > 0 ? i <= e : i >= e; i += step) out.push(i);
    if (out.length) c.out(out.join('\n') + '\n');
  };

  C.basename = function (c) { var p = (c.args[0] || '').replace(/\/+$/, ''); var b = p.split('/').pop(); if (c.args[1] && b.slice(-c.args[1].length) === c.args[1]) b = b.slice(0, -c.args[1].length); c.out(b + '\n'); };
  C.dirname = function (c) { var p = (c.args[0] || '').replace(/\/+$/, ''); var i = p.lastIndexOf('/'); c.out((i < 0 ? '.' : i === 0 ? '/' : p.slice(0, i)) + '\n'); };

  /* ---------------- 時刻・待ち ---------------- */
  C.date = function (c) {
    var d = new Date();
    var f = c.args.filter(function (a) { return a[0] === '+'; })[0];
    var wd = ['Sun', 'Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat'][d.getDay()];
    var mo = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'][d.getMonth()];
    var tz = '';
    try { tz = new Intl.DateTimeFormat('en-US', { timeZoneName: 'short' }).formatToParts(d).filter(function (p) { return p.type === 'timeZoneName'; })[0].value; } catch (e) { tz = ''; }
    if (!f) { c.out(wd + ' ' + mo + ' ' + ('  ' + d.getDate()).slice(-2) + ' ' + Z.hms(d) + ' ' + tz + ' ' + d.getFullYear() + '\n'); return; }
    var map = {
      Y: d.getFullYear(), m: Z.pad(d.getMonth() + 1), d: Z.pad(d.getDate()), H: Z.pad(d.getHours()),
      M: Z.pad(d.getMinutes()), S: Z.pad(d.getSeconds()), s: Math.floor(d.getTime() / 1000), a: wd, b: mo,
      F: d.getFullYear() + '-' + Z.pad(d.getMonth() + 1) + '-' + Z.pad(d.getDate()), T: Z.hms(d), Z: tz,
      j: Z.pad(Math.floor((d - new Date(d.getFullYear(), 0, 0)) / 864e5), 3), u: d.getDay() || 7, n: '\n', '%': '%'
    };
    c.out(f.slice(1).replace(/%(.)/g, function (m, k) { return k in map ? String(map[k]) : m; }) + '\n');
  };

  C.sleep = async function (c) {
    var s = parseFloat(c.args[0]);
    if (isNaN(s)) { c.err('sleep: invalid time interval\n'); return 1; }
    await Z.sleep(Math.min(s, 3600) * 1000, c.sig);
    return c.sig.aborted ? 130 : 0;
  };

  /* ---------------- シェルの道具 ---------------- */
  C.clear = function (c) { if (c.io.pane) c.io.pane.clear(); };
  C.reset = C.clear;

  C.history = function (c) {
    if (c.args[0] === '-c') { c.sh.history.length = 0; return; }
    var h = c.sh.history;
    var n = parseInt(c.args[0], 10);
    var start = n ? Math.max(0, h.length - n) : 0;
    for (var i = start; i < h.length; i++) c.out(('    ' + (i + 1)).slice(-5) + '  ' + h[i] + '\n');
  };

  C.env = function (c) {
    if (c.args.length) return c.sh.exec(c.args, c.stdin, c.out, c.err, c.sig);
    Object.keys(c.env).sort().forEach(function (k) { c.out(k + '=' + c.env[k] + '\n'); });
  };
  C.printenv = function (c) {
    if (c.args[0]) { if (c.env[c.args[0]] == null) return 1; c.out(c.env[c.args[0]] + '\n'); return 0; }
    return C.env(c);
  };
  C.export = function (c) {
    c.args.forEach(function (a) {
      var i = a.indexOf('=');
      if (i > 0) c.sh.env[a.slice(0, i)] = a.slice(i + 1);
    });
    if (!c.args.length) Object.keys(c.env).sort().forEach(function (k) { c.out("export " + k + "='" + c.env[k] + "'\n"); });
  };
  C.unset = function (c) { c.args.forEach(function (a) { delete c.sh.env[a]; }); };
  C.set = function (c) { if (!c.args.length) return C.env(c); };

  C.alias = function (c) {
    var al = c.sh.aliases;
    if (!c.args.length) { Object.keys(al).sort().forEach(function (k) { c.out('alias ' + k + "='" + al[k] + "'\n"); }); return; }
    var st = 0;
    c.args.forEach(function (a) {
      var i = a.indexOf('=');
      if (i > 0) al[a.slice(0, i)] = a.slice(i + 1);
      else if (al[a] != null) c.out('alias ' + a + "='" + al[a] + "'\n");
      else { c.err('sh: alias: ' + a + ' not found\n'); st = 1; }
    });
    if (c.io.save) c.io.save();
    return st;
  };
  C.unalias = function (c) { c.args.forEach(function (a) { delete c.sh.aliases[a]; }); };

  var BUILTIN = { cd: 1, pwd: 1, echo: 1, printf: 1, export: 1, unset: 1, alias: 1, unalias: 1, history: 1, exit: 1, type: 1, set: 1, source: 1, '.': 1, 'true': 1, 'false': 1, test: 1, '[': 1 };
  function where(name) {
    if (BUILTIN[name]) return null;
    if (/^z2/.test(name) || name === 'sshd') return '/usr/local/bin/' + name;
    if (Object.prototype.hasOwnProperty.call(C, name)) return '/bin/' + name;
    return undefined;
  }
  C.which = function (c) {
    var st = 0;
    c.args.forEach(function (n) {
      var w = where(n);
      if (w) c.out(w + '\n'); else st = 1;
    });
    return st;
  };
  C.type = function (c) {
    var st = 0;
    c.args.forEach(function (n) {
      if (c.sh.aliases[n] != null) c.out(n + " is an alias for '" + c.sh.aliases[n] + "'\n");
      else if (BUILTIN[n]) c.out(n + ' is a shell builtin\n');
      else { var w = where(n); if (w) c.out(n + ' is ' + w + '\n'); else { c.out(n + ': not found\n'); st = 1; } }
    });
    return st;
  };
  C.command = function (c) { if (c.args[0] === '-v') return C.which({ args: c.args.slice(1), out: c.out }); return c.sh.exec(c.args, c.stdin, c.out, c.err, c.sig); };

  C['true'] = function () { return 0; };
  C['false'] = function () { return 1; };
  C[':'] = C['true'];

  C.test = function (c) {
    var a = c.args.slice();
    if (c.name === '[') { if (a[a.length - 1] !== ']') { c.err("[: missing ']'\n"); return 2; } a.pop(); }
    var neg = false;
    if (a[0] === '!') { neg = true; a.shift(); }
    var r = false;
    if (a.length === 0) r = false;
    else if (a.length === 1) r = a[0] !== '';
    else if (a.length === 2) {
      var p = c.fs.stat(c.abs(a[1]));
      r = { '-n': a[1] !== '', '-z': a[1] === '', '-e': !!p, '-f': !!(p && p.t === 'f'), '-d': !!(p && p.t === 'd'), '-s': !!(p && p.t === 'f' && p.d.length) }[a[0]] || false;
    } else if (a.length === 3) {
      var x = a[0], op = a[1], y = a[2], nx = parseInt(x, 10), ny = parseInt(y, 10);
      r = { '=': x === y, '==': x === y, '!=': x !== y, '-eq': nx === ny, '-ne': nx !== ny, '-lt': nx < ny, '-le': nx <= ny, '-gt': nx > ny, '-ge': nx >= ny }[op] || false;
    }
    return (neg ? !r : r) ? 0 : 1;
  };
  C['['] = C.test;

  C.exit = function (c) { var e = new Error('exit'); e.exit = parseInt(c.args[0] || String(c.sh.status), 10) || 0; throw e; };
  C.logout = C.exit;

  C.sh = function (c) {
    if (c.args[0] === '-c') return c.sh.child(c.args.slice(2)).run(c.args[1] || '', c.out, c.err, c.sig, c.stdin);
    if (!c.args.length) {
      if (c.stdin) return c.sh.child().run(c.stdin, c.out, c.err, c.sig);
      c.err('sh: ' + T('sh.nested') + '\n'); return 0;
    }
    return c.sh.runScript(c.abs(c.args[0]), c.args.slice(1), c);
  };
  C.bash = C.sh; C.zsh = C.sh; C.ash = C.sh;
  C.source = function (c) {
    if (!c.args.length) { c.err('sh: .: filename argument required\n'); return 2; }
    return c.sh.runScript(c.abs(c.args[0]), c.args.slice(1), c, true);
  };
  C['.'] = C.source;

  C.whoami = function (c) { c.out('root\n'); };
  C.id = function (c) { c.out('uid=0(root) gid=0(root) groups=0(root)\n'); };
  C.hostname = function (c) { c.out('localhost\n'); };
  C.uname = function (c) {
    var a = c.args.join(' ');
    if (/-a/.test(a)) c.out('Linux localhost 6.1.0-z2mock #1 SMP ' + new Date().getFullYear() + ' aarch64 Android\n');
    else if (/-m/.test(a)) c.out('aarch64\n');
    else if (/-r/.test(a)) c.out('6.1.0-z2mock\n');
    else c.out('Linux\n');
  };
  C.uptime = function (c) {
    var s = Math.floor((Date.now() - (Z.bootAt || Date.now())) / 1000);
    c.out(' ' + Z.hms(new Date()) + ' up ' + Math.floor(s / 60) + ' min,  1 user,  load average: 0.00, 0.00, 0.00\n');
  };
  C.free = function (c) {
    c.out('               total        used        free\nMem:      5242880         ' + Math.round(Z.store.usage() / 1024) + '     5242880\n');
    c.out(T('cmd.free') + '\n');
  };
  C.df = function (c) {
    var used = Z.store.usage();
    c.out('Filesystem      Size  Used Avail Use% Mounted on\n');
    c.out('localStorage    5.0M  ' + (used / 1048576).toFixed(1) + 'M  ' + (5 - used / 1048576).toFixed(1) + 'M ' + Math.round(used / 52428.8) + '% /root\n');
  };
  C.yes = async function (c) {
    var s = (c.args.join(' ') || 'y') + '\n';
    for (var i = 0; i < 2000 && !c.sig.aborted; i++) { c.out(s); if (i % 50 === 49) await Z.sleep(30, c.sig); }
    return c.sig.aborted ? 130 : 0;
  };

  /* ---------------- 読む（less） ---------------- */
  C.less = async function (c) {
    var o = opts(c.args);
    var text, name;
    if (o.a.length) {
      try { text = c.fs.readFile(c.abs(o.a[0])); name = o.a[0]; }
      catch (e) { c.err(Z.fsErr('less', e, o.a[0]) + '\n'); return 1; }
    } else if (c.stdin != null) { text = c.stdin; name = '(stdin)'; }
    else { c.err('Missing filename ("less --help" for help)\n'); return 1; }
    if (!c.io.pane || !c.io.pane.pager) { c.out(text); return 0; }
    await c.io.pane.pager(text, name, c.sig);
    return 0;
  };
  C.more = C.less;
  C.man = function (c) {
    if (!c.args[0]) { c.err('What manual page do you want?\n'); return 1; }
    c.err(T('cmd.man', c.args[0]) + '\n');
    return 16;
  };

  /* ---------------- OS が無い・ネットワークが無い ---------------- */
  function noOs(c) { c.err(T('cmd.noos', c.name) + '\n'); return 127; }
  ['apk', 'apt', 'apt-get', 'pacman', 'dnf', 'yum', 'pip', 'pip3', 'npm', 'node', 'python', 'python3', 'git', 'make', 'gcc', 'fastfetch', 'neofetch', 'htop', 'top'].forEach(function (n) { C[n] = noOs; });
  function noNet(c) { c.err(T('cmd.nonet', c.name) + '\n'); return 1; }
  ['ssh', 'sftp', 'scp', 'curl', 'wget', 'ping', 'nc', 'telnet', 'ftp', 'rsync', 'sshd'].forEach(function (n) { C[n] = noNet; });
  C.claude = function (c) { c.err(T('cmd.noai') + '\n'); return 1; };
  C.codex = C.claude;
  C.sudo = function (c) {
    if (!c.args.length) { c.err(T('cmd.sudo') + '\n'); return 1; }
    c.err(T('cmd.sudo') + '\n');
    return c.sh.exec(c.args, c.stdin, c.out, c.err, c.sig);
  };
  function editor(c) { c.err(T('cmd.editor', c.name) + '\n'); return 127; }
  ['vi', 'vim', 'nvim', 'nano', 'emacs', 'micro'].forEach(function (n) { C[n] = editor; });
})(typeof window !== 'undefined' ? window : globalThis);
