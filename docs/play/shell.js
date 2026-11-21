/* z2term play — 最小のシェル。
   読めるもの: 単語・'…'・"…"・\x・$VAR・${VAR}・$?・$1…・$(…)・~・* ?（1 階層）
               | && || ; & > >> < 2> 2>> 2>&1、行頭の NAME=値、別名（alias）
   持っていないもの: if / for / while / case / 関数 / ヒアドキュメント（来たら一言で断る）
   コマンドは Z2P.cmds[名前] = async function (c) { … return 終了コード }。
   DOM に触らないので node でも確かめられる（docs/play/test/）。 */
(function (G) {
  'use strict';
  var Z = G.Z2P = G.Z2P || {};
  Z.cmds = Z.cmds || {};

  /* ======================= 字句 ======================= */
  function SyntaxErr(msg) { var e = new Error(msg); e.syntax = true; return e; }

  function findClose(src, i) {
    /* src[i] は '(' の直後。対応する ')' の位置を返す（入れ子と引用符を数える）。 */
    var depth = 1, q = 0;
    for (; i < src.length; i++) {
      var ch = src[i];
      if (q === 1) { if (ch === "'") q = 0; continue; }
      if (ch === '\\') { i++; continue; }
      if (q === 2) { if (ch === '"') q = 0; continue; }
      if (ch === "'") q = 1;
      else if (ch === '"') q = 2;
      else if (ch === '(') depth++;
      else if (ch === ')') { depth--; if (!depth) return i; }
    }
    throw SyntaxErr("unexpected end of file (expecting ')')");
  }

  /* $ の後ろを読む。返り値 [セグメント or null, 次の位置] */
  function readDollar(src, i, q) {
    var ch = src[i + 1];
    if (ch === '(') {
      var e = findClose(src, i + 2);
      return [{ k: 'c', v: src.slice(i + 2, e), q: q }, e + 1];
    }
    if (ch === '{') {
      var e2 = src.indexOf('}', i + 2);
      if (e2 < 0) throw SyntaxErr('bad substitution');
      return [{ k: 'v', v: src.slice(i + 2, e2), q: q }, e2 + 1];
    }
    if (ch && /[?#$!0-9@*]/.test(ch)) return [{ k: 'v', v: ch, q: q }, i + 2];
    var m = /^[A-Za-z_][A-Za-z0-9_]*/.exec(src.slice(i + 1));
    if (m) return [{ k: 'v', v: m[0], q: q }, i + 1 + m[0].length];
    return [null, i + 1];
  }

  function lex(src) {
    var toks = [];
    var i = 0, n = src.length;
    var word = null;
    function lit(s, q) {
      if (!word) word = [];
      var last = word[word.length - 1];
      if (last && last.k === 's' && last.q === q) last.v += s;
      else word.push({ k: 's', v: s, q: q });
    }
    function endWord() { if (word) { toks.push({ w: word }); word = null; } }
    while (i < n) {
      var ch = src[i];
      if (ch === ' ' || ch === '\t') { endWord(); i++; continue; }
      if (ch === '\n') { endWord(); toks.push({ op: ';' }); i++; continue; }
      if (ch === '#' && !word) { while (i < n && src[i] !== '\n') i++; continue; }
      if (ch === '2' && !word && src[i + 1] === '>') {
        endWord();
        if (src.slice(i, i + 4) === '2>&1') { toks.push({ op: '2>&1' }); i += 4; continue; }
        if (src[i + 2] === '>') { toks.push({ op: '2>>' }); i += 3; continue; }
        toks.push({ op: '2>' }); i += 2; continue;
      }
      if (ch === '|' || ch === '&' || ch === ';' || ch === '<' || ch === '>') {
        endWord();
        var two = src.slice(i, i + 2);
        if (two === '||' || two === '&&' || two === '>>' || two === '<<' || two === ';;') {
          toks.push({ op: two }); i += 2;
        } else if (two === '&>') {
          toks.push({ op: '>' }); toks.push({ op: '2>&1' }); i += 2;
        } else { toks.push({ op: ch }); i++; }
        continue;
      }
      if (ch === '(' || ch === ')') {
        endWord(); toks.push({ op: ch }); i++; continue;
      }
      if (ch === "'") {
        var e = src.indexOf("'", i + 1);
        if (e < 0) throw SyntaxErr('unterminated quoted string');
        lit(src.slice(i + 1, e), 1);
        i = e + 1; continue;
      }
      if (ch === '"') {
        if (!word) word = [];
        i++;
        var buf = '';
        var closed = false;
        while (i < n) {
          var c2 = src[i];
          if (c2 === '"') { closed = true; i++; break; }
          if (c2 === '\\' && /["\\$`\n]/.test(src[i + 1] || '')) { buf += src[i + 1]; i += 2; continue; }
          if (c2 === '$') {
            var r = readDollar(src, i, 2);
            if (r[0]) { if (buf) { lit(buf, 2); buf = ''; } word.push(r[0]); }
            else buf += '$';
            i = r[1]; continue;
          }
          buf += c2; i++;
        }
        if (!closed) throw SyntaxErr('unterminated quoted string');
        lit(buf, 2);
        continue;
      }
      if (ch === '\\') {
        if (i + 1 < n) { if (src[i + 1] !== '\n') lit(src[i + 1], 1); i += 2; } else i++;
        continue;
      }
      if (ch === '$') {
        var r2 = readDollar(src, i, 0);
        if (r2[0]) { if (!word) word = []; word.push(r2[0]); }
        else lit('$', 0);
        i = r2[1]; continue;
      }
      if (ch === '`') throw SyntaxErr('`…` is not supported here; use $(…)');
      lit(ch, 0); i++;
    }
    endWord();
    return toks;
  }

  /* ======================= 構文 ======================= */
  var KEYWORDS = { 'if': 1, 'then': 1, 'fi': 1, 'for': 1, 'while': 1, 'until': 1, 'do': 1, 'done': 1, 'case': 1, 'esac': 1, 'function': 1 };

  function plain(w) {
    /* 引用符も展開も含まない単語なら文字列、そうでなければ null */
    if (w.length === 1 && w[0].k === 's' && w[0].q === 0) return w[0].v;
    return null;
  }

  function parse(toks) {
    var list = [];
    var pipe = [];
    var cmd = null;
    function newCmd() { return { words: [], redirs: [], assigns: [] }; }
    function endCmd() {
      if (cmd && (cmd.words.length || cmd.assigns.length || cmd.redirs.length)) pipe.push(cmd);
      else if (cmd) throw SyntaxErr('syntax error near unexpected token');
      cmd = null;
    }
    function endPipe(op) {
      if (!cmd && !pipe.length) {
        if (op === ';' || op === '&') return;
        throw SyntaxErr("syntax error near unexpected token `" + op + "'");
      }
      endCmd();
      list.push({ pipe: pipe, next: op });
      pipe = [];
    }
    for (var i = 0; i < toks.length; i++) {
      var t = toks[i];
      if (t.w) {
        if (!cmd) cmd = newCmd();
        var p = plain(t.w);
        if (!cmd.words.length && p !== null && /^[A-Za-z_][A-Za-z0-9_]*=/.test(p)) {
          cmd.assigns.push(t.w); continue;
        }
        if (!cmd.words.length && t.w.length && t.w[0].k === 's' && t.w[0].q === 0 &&
            /^[A-Za-z_][A-Za-z0-9_]*=/.test(t.w[0].v)) { cmd.assigns.push(t.w); continue; }
        if (!cmd.words.length && p !== null && KEYWORDS[p]) {
          var e = SyntaxErr('nokw'); e.keyword = p; throw e;
        }
        cmd.words.push(t.w);
        continue;
      }
      var op = t.op;
      if (op === '<<' ) { var e2 = SyntaxErr('noheredoc'); e2.heredoc = true; throw e2; }
      if (op === '(' || op === ')' || op === ';;') throw SyntaxErr("syntax error near unexpected token `" + op + "'");
      if (op === '>' || op === '>>' || op === '<' || op === '2>' || op === '2>>') {
        var nx = toks[i + 1];
        if (!nx || !nx.w) throw SyntaxErr("syntax error near unexpected token `newline'");
        if (!cmd) cmd = newCmd();
        cmd.redirs.push({ op: op, w: nx.w });
        i++; continue;
      }
      if (op === '2>&1') { if (!cmd) cmd = newCmd(); cmd.redirs.push({ op: op }); continue; }
      if (op === '|') {
        if (!cmd) throw SyntaxErr("syntax error near unexpected token `|'");
        endCmd(); continue;
      }
      endPipe(op);
    }
    if (cmd || pipe.length) endPipe(';');
    if (list.length) {
      var last = list[list.length - 1].next;
      if (last === '&&' || last === '||') throw SyntaxErr('unexpected end of file');
    }
    return list;
  }

  /* ======================= 実行 ======================= */
  function Shell(opt) {
    opt = opt || {};
    this.fs = opt.fs;
    this.env = opt.env || {
      HOME: '/root', PWD: '/root', OLDPWD: '/root', USER: 'root', LOGNAME: 'root',
      SHELL: '/bin/sh', PATH: '/usr/local/bin:/usr/bin:/bin', TERM: 'xterm-256color',
      LANG: 'C.UTF-8', HOSTNAME: 'localhost'
    };
    this.aliases = opt.aliases || { ll: 'ls -l', la: 'ls -a', l: 'ls -CF' };
    this.status = 0;
    this.args = opt.args || [];
    this.io = opt.io || {};          /* 端末とのつなぎ（pane / tab / exit など） */
    this.history = opt.history || [];
    this.depth = opt.depth || 0;
  }

  Shell.prototype.child = function (args) {
    var env = {};
    for (var k in this.env) env[k] = this.env[k];
    return new Shell({ fs: this.fs, env: env, aliases: this.aliases, io: this.io, args: args || this.args, history: this.history, depth: this.depth + 1 });
  };

  Shell.prototype.cwd = function () { return this.env.PWD || '/root'; };
  Shell.prototype.abs = function (p) { return this.fs.norm(p, this.cwd()); };

  Shell.prototype.getVar = function (name) {
    if (name === '?') return String(this.status);
    if (name === '$') return '4242';
    if (name === '!') return '';
    if (name === '#') return String(this.args.length);
    if (name === '@' || name === '*') return this.args.join(' ');
    if (/^[0-9]$/.test(name)) return name === '0' ? 'sh' : (this.args[+name - 1] || '');
    var v = this.env[name];
    return v == null ? '' : String(v);
  };

  Shell.prototype.capture = async function (src, sig) {
    var out = '';
    var sub = this.child();
    await sub.run(src, function (s) { out += s; }, this._err || function () {}, sig);
    this.status = sub.status;
    return out.replace(/\n+$/, '');
  };

  Shell.prototype.expandWord = async function (w, sig) {
    var fields = [''];
    var quoted = false;
    var glob = false;
    var produced = false;
    for (var i = 0; i < w.length; i++) {
      var s = w[i];
      if (s.q) quoted = true;
      if (s.k === 's') {
        var v = s.v;
        if (i === 0 && s.q === 0 && (v === '~' || v.indexOf('~/') === 0)) v = (this.env.HOME || '/root') + v.slice(1);
        if (s.q === 0 && /[*?]/.test(v)) glob = true;
        fields[fields.length - 1] += v;
        produced = true;
        continue;
      }
      var val = s.k === 'v' ? this.getVar(s.v) : await this.capture(s.v, sig);
      if (s.q) { fields[fields.length - 1] += val; produced = true; continue; }
      if (!val) continue;
      var parts = val.split(/[ \t\n]+/);
      for (var j = 0; j < parts.length; j++) {
        if (j > 0) fields.push('');
        fields[fields.length - 1] += parts[j];
      }
      produced = true;
    }
    fields = fields.filter(function (f, idx) { return f !== '' || (quoted && idx === 0); });
    if (!produced && !quoted) return [];
    if (glob && fields.length === 1 && this.fs) {
      var g = this.fs.glob(fields[0], this.cwd());
      if (g) return g;
    }
    return fields;
  };

  Shell.prototype.expandAll = async function (words, sig) {
    var out = [];
    for (var i = 0; i < words.length; i++) {
      var f = await this.expandWord(words[i], sig);
      for (var j = 0; j < f.length; j++) out.push(f[j]);
    }
    return out;
  };

  /* 文字列を実行する。out / err は文字列を受ける関数。 */
  Shell.prototype.run = async function (src, out, err, sig, stdin) {
    sig = sig || { aborted: false };
    var list;
    this._err = err;
    try { list = parse(lex(src)); }
    catch (e) {
      if (e.keyword) err('sh: ' + (Z.t ? Z.t('sh.nokw', e.keyword) : 'no ' + e.keyword) + '\n');
      else if (e.heredoc) err('sh: ' + (Z.t ? Z.t('sh.noheredoc') : 'no heredoc') + '\n');
      else err('sh: ' + e.message + '\n');
      this.status = 2;
      return 2;
    }
    for (var i = 0; i < list.length; i++) {
      if (sig.aborted) { this.status = 130; return 130; }
      if (i > 0) {
        var prev = list[i - 1].next;
        if (prev === '&&' && this.status !== 0) continue;
        if (prev === '||' && this.status === 0) continue;
      }
      this.status = await this.runPipe(list[i].pipe, out, err, sig, stdin);
      if (this.exiting) return this.status;
    }
    return this.status;
  };

  Shell.prototype.runPipe = async function (pipe, out, err, sig, stdin) {
    var input = stdin;
    var st = 0;
    for (var i = 0; i < pipe.length; i++) {
      if (sig.aborted) return 130;
      var last = i === pipe.length - 1;
      var buf = '';
      var o = last ? out : function (s) { buf += s; };
      st = await this.runSimple(pipe[i], input, o, err, sig);
      input = buf;
    }
    return st;
  };

  Shell.prototype.runSimple = async function (cmd, stdin, out, err, sig) {
    var self = this;
    var words = cmd.words;
    /* 別名 */
    var seen = {};
    while (words.length) {
      var p0 = plain(words[0]);
      if (p0 === null || !Object.prototype.hasOwnProperty.call(this.aliases, p0) || seen[p0]) break;
      seen[p0] = 1;
      var aw = lex(this.aliases[p0]).filter(function (t) { return t.w; }).map(function (t) { return t.w; });
      words = aw.concat(words.slice(1));
    }
    var argv;
    try { argv = await this.expandAll(words, sig); }
    catch (e) { err('sh: ' + e.message + '\n'); return 2; }

    var assigns = {};
    for (var a = 0; a < cmd.assigns.length; a++) {
      var aw2 = cmd.assigns[a];
      var first = aw2[0].v;
      var eq = first.indexOf('=');
      var name = first.slice(0, eq);
      var rest = [{ k: 's', v: first.slice(eq + 1), q: 1 }].concat(aw2.slice(1));
      var vals = await this.expandWord(rest, sig);
      assigns[name] = vals.join(' ');
    }
    if (!argv.length) {
      for (var k in assigns) this.env[k] = assigns[k];
      if (!cmd.redirs.length) return this.status = 0;
    }

    /* リダイレクト */
    var outFile = null, outAppend = false, errFile = null, errAppend = false, inFile = null, merge = false;
    for (var r = 0; r < cmd.redirs.length; r++) {
      var rd = cmd.redirs[r];
      if (rd.op === '2>&1') { merge = true; continue; }
      var target = (await this.expandWord(rd.w, sig))[0] || '';
      var path = target === '/dev/null' ? '/dev/null' : this.abs(target);
      if (rd.op === '>' || rd.op === '>>') { outFile = path; outAppend = rd.op === '>>'; }
      else if (rd.op === '2>' || rd.op === '2>>') { errFile = path; errAppend = rd.op === '2>>'; }
      else if (rd.op === '<') inFile = path;
    }
    var outBuf = '', errBuf = '';
    var o = out, e = err;
    try {
      if (outFile && outFile !== '/dev/null') this.fs.writeFile(outFile, '', outAppend);
      if (errFile && errFile !== '/dev/null') this.fs.writeFile(errFile, '', errAppend);
    } catch (ex) { err(Z.fsErr('sh', ex, outFile || errFile) + '\n'); return 1; }
    if (outFile) o = function (s) { outBuf += s; };
    if (errFile) e = function (s) { errBuf += s; };
    if (merge) e = o;
    if (inFile) {
      try { stdin = this.fs.readFile(inFile); }
      catch (ex2) { err(Z.fsErr('sh', ex2, inFile) + '\n'); return 1; }
    }

    var st = 0;
    if (argv.length) {
      var saved = {};
      for (var k2 in assigns) { saved[k2] = this.env[k2]; this.env[k2] = assigns[k2]; }
      st = await this.exec(argv, stdin, o, e, sig);
      for (var k3 in saved) { if (saved[k3] === undefined) delete this.env[k3]; else this.env[k3] = saved[k3]; }
    }
    try {
      if (outFile && outFile !== '/dev/null' && outBuf) this.fs.writeFile(outFile, outBuf, true);
      if (errFile && errFile !== '/dev/null' && errBuf) this.fs.writeFile(errFile, errBuf, true);
    } catch (ex3) { err(Z.fsErr('sh', ex3, outFile) + '\n'); return 1; }
    return st;
  };

  Shell.prototype.exec = async function (argv, stdin, out, err, sig) {
    var name = argv[0];
    var fn = Object.prototype.hasOwnProperty.call(Z.cmds, name) ? Z.cmds[name] : null;
    var self = this;
    if (!fn && name.indexOf('/') >= 0) {
      var p = this.abs(name);
      var node = this.fs.stat(p);
      if (node && node.t === 'f') {
        fn = function (c) { return self.runScript(p, c.args, c); };
      } else if (node && node.t === 'd') {
        err('sh: ' + name + ': Permission denied\n'); return 126;
      }
    }
    if (!fn) {
      err('sh: ' + name + ': not found\n');
      if (Z.bus) Z.bus.emit('notfound', name);
      return 127;
    }
    var c = {
      name: name, argv: argv, args: argv.slice(1), sh: this, fs: this.fs, env: this.env,
      stdin: stdin, out: out, err: err, sig: sig, io: this.io,
      abs: function (q) { return self.abs(q); },
      outln: function (s) { out(s + '\n'); },
      errln: function (s) { err(name + ': ' + s + '\n'); }
    };
    try {
      var r = await fn(c);
      return typeof r === 'number' ? r : 0;
    } catch (ex) {
      if (ex && ex.exit !== undefined) { this.exiting = true; return ex.exit; }
      err(name + ': ' + (ex && ex.message || ex) + '\n');
      return 1;
    }
  };

  Shell.prototype.runScript = async function (path, args, c, same) {
    var src;
    try { src = this.fs.readFile(path); }
    catch (e) { c.err(Z.fsErr('sh', e, path) + '\n'); return 1; }
    if (this.depth > 8) { c.err('sh: too deep\n'); return 2; }
    var sh = same ? this : this.child(args);
    if (same && args && args.length) sh.args = args;
    var st = await sh.run(src, c.out, c.err, c.sig, c.stdin);
    if (!same) sh.exiting = false;
    return st;
  };

  Z.Shell = Shell;
  Z.lex = lex;
  Z.parse = parse;
})(typeof window !== 'undefined' ? window : globalThis);
