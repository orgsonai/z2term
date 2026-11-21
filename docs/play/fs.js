/* z2term play — 仮想ファイル。
   / の下に root（= ~）と tmp だけを持つ。中身は木構造のまま localStorage（z2mock:fs）へ。
   ノード: 置き場 { t:'d', c:{名前:ノード}, m:時刻 } / ファイル { t:'f', d:'中身', m:時刻 }
   入出力だけの作りなので node でも確認できる（docs/play/test/）。 */
(function (G) {
  'use strict';
  var Z = G.Z2P = G.Z2P || {};

  function now() { return Date.now(); }
  function dir() { return { t: 'd', c: {}, m: now() }; }
  function err(code, path) { var e = new Error(code + ': ' + path); e.code = code; e.path = path; return e; }

  function FS(data, onChange) {
    this.root = (data && data.t === 'd') ? data : dir();
    this.onChange = onChange || function () {};
    var self = this;
    ['/root', '/tmp', '/root/.z2term', '/root/.z2term/actions', '/root/.z2term/macros'].forEach(function (p) {
      if (!self.stat(p)) self.mkdir(p, true, true);
    });
  }

  /* パスを正規化する。~ と ~/… は /root。 */
  FS.prototype.norm = function (p, cwd) {
    p = String(p == null ? '' : p);
    if (p === '~' || p.indexOf('~/') === 0) p = '/root' + p.slice(1);
    if (p.charAt(0) !== '/') p = (cwd || '/root') + '/' + p;
    var out = [];
    p.split('/').forEach(function (s) {
      if (!s || s === '.') return;
      if (s === '..') { out.pop(); return; }
      out.push(s);
    });
    return '/' + out.join('/');
  };

  /* 表示用: /root を ~ に戻す。 */
  FS.prototype.pretty = function (p) {
    if (p === '/root') return '~';
    if (p.indexOf('/root/') === 0) return '~' + p.slice(5);
    return p;
  };

  FS.prototype.stat = function (p) {
    var parts = p.split('/').filter(Boolean);
    var n = this.root;
    for (var i = 0; i < parts.length; i++) {
      if (!n || n.t !== 'd') return null;
      if (!Object.prototype.hasOwnProperty.call(n.c, parts[i])) return null;
      n = n.c[parts[i]];
    }
    return n;
  };

  FS.prototype.parent = function (p) {
    var i = p.lastIndexOf('/');
    var dp = i <= 0 ? '/' : p.slice(0, i);
    var name = p.slice(i + 1);
    var d = this.stat(dp);
    if (!d) throw err('ENOENT', dp);
    if (d.t !== 'd') throw err('ENOTDIR', dp);
    return { dir: d, name: name, path: dp };
  };

  FS.prototype.readFile = function (p) {
    var n = this.stat(p);
    if (!n) throw err('ENOENT', p);
    if (n.t === 'd') throw err('EISDIR', p);
    return n.d;
  };

  FS.prototype.writeFile = function (p, s, append) {
    if (p === '/dev/null') return;
    var pr = this.parent(p);
    if (!pr.name) throw err('EISDIR', p);
    var n = pr.dir.c[pr.name];
    if (n && n.t === 'd') throw err('EISDIR', p);
    if (!n) { n = pr.dir.c[pr.name] = { t: 'f', d: '', m: now() }; }
    n.d = append ? n.d + String(s) : String(s);
    n.m = now();
    this.onChange(p);
  };

  FS.prototype.mkdir = function (p, parents, quiet) {
    var parts = p.split('/').filter(Boolean);
    var n = this.root;
    for (var i = 0; i < parts.length; i++) {
      var name = parts[i];
      var next = Object.prototype.hasOwnProperty.call(n.c, name) ? n.c[name] : null;
      var last = i === parts.length - 1;
      if (next) {
        if (next.t !== 'd') throw err('ENOTDIR', '/' + parts.slice(0, i + 1).join('/'));
        if (last && !parents) throw err('EEXIST', p);
      } else {
        if (!last && !parents) throw err('ENOENT', p);
        next = n.c[name] = dir();
      }
      n = next;
    }
    if (!quiet) this.onChange(p);
  };

  FS.prototype.readdir = function (p) {
    var n = this.stat(p);
    if (!n) throw err('ENOENT', p);
    if (n.t !== 'd') throw err('ENOTDIR', p);
    return Object.keys(n.c).sort();
  };

  FS.prototype.rm = function (p, recursive) {
    if (p === '/' || p === '/root' || p === '/tmp') throw err('EPERM', p);
    var pr = this.parent(p);
    var n = pr.dir.c[pr.name];
    if (!n) throw err('ENOENT', p);
    if (n.t === 'd' && !recursive) throw err('EISDIR', p);
    delete pr.dir.c[pr.name];
    this.onChange(p);
  };

  FS.prototype.rmdir = function (p) {
    var n = this.stat(p);
    if (!n) throw err('ENOENT', p);
    if (n.t !== 'd') throw err('ENOTDIR', p);
    if (Object.keys(n.c).length) throw err('ENOTEMPTY', p);
    this.rm(p, true);
  };

  function clone(n) { return JSON.parse(JSON.stringify(n)); }

  FS.prototype.cp = function (a, b, recursive) {
    var n = this.stat(a);
    if (!n) throw err('ENOENT', a);
    if (n.t === 'd' && !recursive) throw err('EISDIR', a);
    var bn = this.stat(b);
    if (bn && bn.t === 'd') b = b.replace(/\/$/, '') + '/' + a.split('/').pop();
    if (b === a || b.indexOf(a + '/') === 0) throw err('EINVAL', b);
    var pr = this.parent(b);
    var c = clone(n); c.m = now();
    pr.dir.c[pr.name] = c;
    this.onChange(b);
  };

  FS.prototype.mv = function (a, b) {
    var n = this.stat(a);
    if (!n) throw err('ENOENT', a);
    var bn = this.stat(b);
    if (bn && bn.t === 'd') b = b.replace(/\/$/, '') + '/' + a.split('/').pop();
    if (b === a) return;
    if (b.indexOf(a + '/') === 0) throw err('EINVAL', b);
    var pr = this.parent(b);
    var pa = this.parent(a);
    pr.dir.c[pr.name] = n;
    delete pa.dir.c[pa.name];
    this.onChange(b);
  };

  FS.prototype.size = function (n) {
    if (!n) return 0;
    if (n.t === 'f') return n.d.length;
    var s = 0;
    for (var k in n.c) s += this.size(n.c[k]);
    return s;
  };

  /* 簡単なワイルドカード（* と ?）。1 階層ぶんだけ。 */
  FS.prototype.glob = function (pat, cwd) {
    var abs = pat.charAt(0) === '/' || pat.charAt(0) === '~';
    var full = this.norm(pat, cwd);
    var i = full.lastIndexOf('/');
    var dp = i <= 0 ? '/' : full.slice(0, i);
    var base = full.slice(i + 1);
    if (!/[*?]/.test(base) || /[*?]/.test(dp)) return null;
    var names;
    try { names = this.readdir(dp); } catch (e) { return null; }
    var re = new RegExp('^' + base.replace(/[.+^${}()|[\]\\]/g, '\\$&').replace(/\*/g, '.*').replace(/\?/g, '.') + '$');
    var hit = names.filter(function (n) { return re.test(n) && (n.charAt(0) !== '.' || base.charAt(0) === '.'); });
    if (!hit.length) return null;
    var prefix = pat.slice(0, pat.lastIndexOf('/') + 1);
    return hit.map(function (n) { return abs || prefix ? prefix + n : n; });
  };

  FS.prototype.toJSON = function () { return this.root; };

  Z.FS = FS;
  Z.fsErr = function (cmd, e, path) {
    var m = {
      ENOENT: 'No such file or directory', ENOTDIR: 'Not a directory', EISDIR: 'Is a directory',
      EEXIST: 'File exists', ENOTEMPTY: 'Directory not empty', EPERM: 'Operation not permitted',
      EINVAL: 'Invalid argument'
    }[e && e.code] || String(e && e.message || e);
    return cmd + ': ' + (path || (e && e.path) || '') + ': ' + m;
  };
})(typeof window !== 'undefined' ? window : globalThis);
