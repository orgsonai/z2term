/* z2term play — 端末 1 枚ぶん（Pane）。
   本物の VT ではなく「行の端末」: 出力は行の配列で持ち、色は SGR（ESC[…m）の一部だけ読む。
   行 = [[文字, 種類], …]。種類: p=プロンプト d=淡い c=コマンド e=エラー aNN=SGR の色
   入力行の編集・履歴・補完・Ctrl+C・less の全画面表示を受け持つ。 */
(function (G) {
  'use strict';
  var Z = G.Z2P = G.Z2P || {};
  var h = function () { return Z.h.apply(null, arguments); };

  function Pane(opt) {
    this.sh = opt.sh;
    this.name = opt.name || 'shell';
    this.lines = opt.lines || [];
    this.partial = null;
    this.sgr = '';
    this.buf = '';
    this.cur = 0;
    this.hpos = -1;
    this.hsaved = '';
    this.running = false;
    this.sig = null;
    this.alt = null;
    this.compose = '';
    this.marks = null;
    this.onLine = opt.onLine || function () {};
    this.onExit = opt.onExit || function () {};
    this.onChange = opt.onChange || function () {};
    this.onActivity = opt.onActivity || function () {};
    this.wrap = true;
    this.el = h('div.z2p-term', { role: 'log', 'aria-live': 'off', tabindex: '-1' });
    this.linesEl = h('div.z2p-lines');
    this.inEl = h('div.z2p-in');
    this.altEl = h('div.z2p-alt', { hidden: true });
    this.el.appendChild(this.linesEl);
    this.el.appendChild(this.inEl);
    this.el.appendChild(this.altEl);
    var self = this;
    this.el.addEventListener('wheel', function (e) {
      if (!self.alt) return;
      e.preventDefault();
      self.altScroll(e.deltaY > 0 ? 3 : -3);
    }, { passive: false });
    this.renderAll();
    if (!opt.noPrompt) this.prompt();
  }

  /* ---------------- 出力 ---------------- */
  var SGRCLS = { '1': 'b', '2': 'd', '31': 'a31', '32': 'a32', '33': 'a33', '34': 'a34', '35': 'a35', '36': 'a36', '90': 'd', '91': 'a31', '92': 'a32', '93': 'a33', '94': 'a34', '95': 'a35', '96': 'a36' };
  function sgrToCls(codes) {
    var out = [];
    codes.split(';').forEach(function (c) { if (SGRCLS[c]) out.push(SGRCLS[c]); });
    return out.join(' ');
  }

  Pane.prototype.write = function (text, cls) {
    text = String(text);
    var parts = text.split(/\x1b\[([0-9;]*)m/);
    for (var i = 0; i < parts.length; i++) {
      if (i % 2 === 1) { this.sgr = parts[i] === '0' || parts[i] === '' ? '' : sgrToCls(parts[i]); continue; }
      var chunk = parts[i].replace(/\r(?!\n)/g, '').replace(/\x1b\[[0-9;?]*[A-Za-z]/g, '');
      if (!chunk) continue;
      var c = [cls || '', this.sgr].filter(Boolean).join(' ');
      var ls = chunk.split('\n');
      for (var j = 0; j < ls.length; j++) {
        if (j > 0) this.commit();
        if (ls[j]) {
          if (!this.partial) this.partial = [];
          this.partial.push([ls[j], c]);
        }
      }
    }
    this.renderInput();
    this.onActivity();
  };
  Pane.prototype.commit = function (segs) {
    var line = segs || this.partial || [];
    if (!segs) this.partial = null;
    this.lines.push(line);
    var max = Z.LIMIT.lines;
    var stick = this.atBottom();
    if (this.lines.length > max) {
      var cut = this.lines.length - max;
      this.lines.splice(0, cut);
      for (var k = 0; k < cut && this.linesEl.firstChild; k++) this.linesEl.removeChild(this.linesEl.firstChild);
    }
    this.linesEl.appendChild(this.lineEl(line));
    this.onLine(plain(line));
    this.onChange();
    if (stick) this.scrollBottom();
  };
  function plain(line) { return line.map(function (s) { return s[0]; }).join(''); }
  Pane.plain = plain;

  Pane.prototype.lineEl = function (line, re) {
    var d = h('div.z2p-ln');
    line.forEach(function (s) {
      var sp = h('span', { cls: s[1] || null });
      if (re) {
        var t = s[0], last = 0, m;
        re.lastIndex = 0;
        while ((m = re.exec(t)) && m[0]) {
          if (m.index > last) sp.appendChild(document.createTextNode(t.slice(last, m.index)));
          sp.appendChild(h('mark', { text: m[0] }));
          last = m.index + m[0].length;
        }
        if (last < t.length) sp.appendChild(document.createTextNode(t.slice(last)));
      } else sp.textContent = s[0];
      d.appendChild(sp);
    });
    if (!line.length) d.appendChild(document.createTextNode('​'));
    return d;
  };

  Pane.prototype.renderAll = function (re) {
    this.linesEl.textContent = '';
    var frag = document.createDocumentFragment();
    var self = this;
    this.lines.forEach(function (l) { frag.appendChild(self.lineEl(l, re)); });
    this.linesEl.appendChild(frag);
    this.renderInput();
  };

  Pane.prototype.atBottom = function () {
    var e = this.el;
    return e.scrollHeight - e.scrollTop - e.clientHeight < 40;
  };
  Pane.prototype.scrollBottom = function () {
    var e = this.el;
    e.scrollTop = e.scrollHeight;
  };

  Pane.prototype.clear = function () {
    this.lines = [];
    this.partial = null;
    this.linesEl.textContent = '';
    this.renderInput();
    this.onChange();
  };

  Pane.prototype.capture = function () {
    return this.lines.slice(-60).map(plain).join('\n');
  };

  /* ---------------- プロンプトと入力行 ---------------- */
  Pane.prototype.prompt = function () {
    var d = new Date();
    this.commit([['[' + Z.hms(d) + '] ', 'd'], [this.sh.fs.pretty(this.sh.cwd()), 'd']]);
    this.buf = ''; this.cur = 0; this.hpos = -1;
    this.renderInput();
    this.scrollBottom();
  };

  Pane.prototype.renderInput = function () {
    var e = this.inEl;
    e.textContent = '';
    if (this.partial) this.partial.forEach(function (s) { e.appendChild(h('span', { cls: s[1] || null, text: s[0] })); });
    if (this.running) {
      if (this.focused) e.appendChild(h('span.z2p-cur', { text: ' ' }));
      return;
    }
    e.appendChild(h('span.p', { text: '❯ ' }));
    var b = this.buf, c = this.cur;
    e.appendChild(h('span.c', { text: b.slice(0, c) }));
    if (this.compose) e.appendChild(h('span.z2p-compose', { text: this.compose }));
    e.appendChild(h('span.z2p-cur', { text: b.charAt(c) || ' ' }));
    e.appendChild(h('span.c', { text: b.slice(c + 1) }));
  };

  Pane.prototype.setFocused = function (f) {
    this.focused = f;
    this.el.classList.toggle('focused', !!f);
    this.renderInput();
  };

  Pane.prototype.insert = function (s) {
    this.buf = this.buf.slice(0, this.cur) + s + this.buf.slice(this.cur);
    this.cur += s.length;
    this.renderInput();
    this.scrollBottom();
  };

  /* 文字の入力（貼り付けの改行は Enter として扱う） */
  Pane.prototype.input = function (text) {
    if (this.alt) { for (var i = 0; i < text.length; i++) this.altKey({ key: text[i] }); return; }
    if (this.running) return;
    var ls = String(text).replace(/\r\n?/g, '\n').split('\n');
    for (var j = 0; j < ls.length; j++) {
      if (j > 0) { this.enter(); if (this.running) { this.queue = (this.queue || []).concat(ls.slice(j)); return; } }
      if (ls[j]) this.insert(ls[j].replace(/\t/g, '  '));
    }
  };

  Pane.prototype.key = function (k) {
    if (this.alt) { this.altKey(k); return; }
    var key = k.key;
    if (k.ctrl && !k.alt) {
      var lk = key.length === 1 ? key.toLowerCase() : key;
      if (lk === 'c') { this.interrupt(); return; }
      if (this.running) return;
      if (lk === 'a') { this.cur = 0; }
      else if (lk === 'e') { this.cur = this.buf.length; }
      else if (lk === 'u') { this.buf = this.buf.slice(this.cur); this.cur = 0; }
      else if (lk === 'k') { this.buf = this.buf.slice(0, this.cur); }
      else if (lk === 'w') { var m = /\S+\s*$/.exec(this.buf.slice(0, this.cur)); if (m) { this.buf = this.buf.slice(0, m.index) + this.buf.slice(this.cur); this.cur = m.index; } }
      else if (lk === 'l') { this.clear(); this.prompt(); return; }
      else if (lk === 'd') { if (!this.buf) { this.commit([['❯ ', 'p'], ['exit', 'c']]); this.onExit(); } return; }
      else if (lk === 'b') { this.cur = Math.max(0, this.cur - 1); }
      else if (lk === 'f') { this.cur = Math.min(this.buf.length, this.cur + 1); }
      else return;
      this.renderInput();
      return;
    }
    if (this.running) { return; }
    if (k.alt && key === 'b') { var mb = /\S+\s*$/.exec(this.buf.slice(0, this.cur)); this.cur = mb ? mb.index : 0; this.renderInput(); return; }
    if (k.alt && key === 'f') { var mf = /^\s*\S+/.exec(this.buf.slice(this.cur)); this.cur = mf ? this.cur + mf[0].length : this.buf.length; this.renderInput(); return; }
    switch (key) {
      case 'Enter': this.enter(); return;
      case 'Backspace': if (this.cur > 0) { this.buf = this.buf.slice(0, this.cur - 1) + this.buf.slice(this.cur); this.cur--; } break;
      case 'Delete': this.buf = this.buf.slice(0, this.cur) + this.buf.slice(this.cur + 1); break;
      case 'ArrowLeft': this.cur = Math.max(0, this.cur - 1); break;
      case 'ArrowRight': this.cur = Math.min(this.buf.length, this.cur + 1); break;
      case 'Home': this.cur = 0; break;
      case 'End': this.cur = this.buf.length; break;
      case 'ArrowUp': this.histMove(-1); return;
      case 'ArrowDown': this.histMove(1); return;
      case 'Tab': this.complete(); return;
      case 'Escape': return;
      default:
        if (key && key.length === 1) { this.insert(key); return; }
        return;
    }
    this.renderInput();
  };

  Pane.prototype.histMove = function (d) {
    var hs = this.sh.history;
    if (!hs.length) return;
    if (this.hpos === -1) { if (d > 0) return; this.hsaved = this.buf; this.hpos = hs.length; }
    var np = this.hpos + d;
    if (np < 0) return;
    if (np >= hs.length) { this.hpos = -1; this.buf = this.hsaved; }
    else { this.hpos = np; this.buf = hs[np]; }
    this.cur = this.buf.length;
    this.renderInput();
  };

  Pane.prototype.interrupt = function () {
    if (this.running && this.sig) {
      this.sig.aborted = true;
      this.write('^C\n', 'd');
      this.queue = null;
      return;
    }
    this.commit([['❯ ', 'p'], [this.buf, 'c'], ['^C', 'd']]);
    this.prompt();
  };

  Pane.prototype.enter = function () {
    var line = this.buf;
    this.commit([['❯ ', 'p'], [line, 'c']]);
    this.buf = ''; this.cur = 0; this.hpos = -1;
    if (!line.trim()) { this.prompt(); return; }
    var hs = this.sh.history;
    if (hs[hs.length - 1] !== line) hs.push(line);
    if (hs.length > 500) hs.splice(0, hs.length - 500);
    this.run(line);
  };

  Pane.prototype.run = function (line) {
    var self = this;
    this.running = true;
    this.sig = { aborted: false };
    this.renderInput();
    Z.bus.emit('command', { pane: this, line: line });
    var out = function (s) { self.write(s); };
    var err = function (s) { self.write(s, 'e'); };
    return this.sh.run(line, out, err, this.sig).then(function (st) {
      if (self.partial) self.commit();
      self.running = false;
      self.sig = null;
      Z.bus.emit('command-done', { pane: self, line: line, status: st });
      if (self.sh.exiting) { self.sh.exiting = false; self.onExit(); return; }
      if (self.dead) return;
      self.prompt();
      self.onChange();
      if (self.queue && self.queue.length) {
        var q = self.queue; self.queue = null;
        self.input(q.join('\n'));
      }
    });
  };

  /* ---------------- 補完 ---------------- */
  Pane.prototype.complete = function () {
    var before = this.buf.slice(0, this.cur);
    var m = /(\S*)$/.exec(before);
    var word = m[1];
    var isCmd = !/\S\s/.test(before.replace(/^\s+/, '')) && before.trim() === word;
    var cands = [];
    var fs = this.sh.fs;
    if (isCmd && word.indexOf('/') < 0) {
      cands = Object.keys(Z.cmds).concat(Object.keys(this.sh.aliases))
        .filter(function (n) { return n.indexOf(word) === 0 && /^[\w.\-[:]+$/.test(n); })
        .filter(function (n, i, a) { return a.indexOf(n) === i; }).sort()
        .map(function (n) { return { v: n, dir: false }; });
    } else {
      var slash = word.lastIndexOf('/');
      var dirPart = slash >= 0 ? word.slice(0, slash + 1) : '';
      var base = slash >= 0 ? word.slice(slash + 1) : word;
      var dp = fs.norm(dirPart || '.', this.sh.cwd());
      var names = [];
      try { names = fs.readdir(dp); } catch (e) { names = []; }
      cands = names.filter(function (n) { return n.indexOf(base) === 0 && (base[0] === '.' || n[0] !== '.'); })
        .map(function (n) { var node = fs.stat(dp.replace(/\/$/, '') + '/' + n); return { v: dirPart + n, dir: node && node.t === 'd' }; });
    }
    if (!cands.length) return;
    if (cands.length === 1) {
      var c = cands[0];
      this.insert(c.v.slice(word.length) + (c.dir ? '/' : ' '));
      Z.bus.emit('complete', c.v);
      return;
    }
    var pre = cands[0].v;
    cands.forEach(function (x) { while (x.v.indexOf(pre) !== 0) pre = pre.slice(0, -1); });
    if (pre.length > word.length) { this.insert(pre.slice(word.length)); Z.bus.emit('complete', pre); return; }
    this.commit([['❯ ', 'p'], [this.buf, 'c']]);
    var names2 = cands.map(function (x) { return x.v.split('/').pop() + (x.dir ? '/' : ''); });
    this.commit([[names2.join('  '), '']]);
    this.renderInput();
    this.scrollBottom();
  };

  /* ---------------- less（全画面） ---------------- */
  Pane.prototype.pager = function (text, name, sig) {
    var self = this;
    return new Promise(function (ok) {
      var ls = String(text).replace(/\n$/, '').split('\n');
      self.alt = { lines: ls, top: 0, name: name, done: ok, sig: sig };
      self.linesEl.hidden = true; self.inEl.hidden = true; self.altEl.hidden = false;
      self.altRender();
      self._altIv = setInterval(function () { if (sig && sig.aborted) self.altQuit(); }, 100);
    });
  };
  Pane.prototype.altRows = function () {
    var lh = parseFloat(getComputedStyle(this.el).lineHeight) || 18;
    return Math.max(3, Math.floor(this.el.clientHeight / lh) - 1);
  };
  Pane.prototype.altRender = function () {
    var a = this.alt;
    if (!a) return;
    var rows = this.altRows();
    var e = this.altEl;
    e.textContent = '';
    var self = this;
    a.lines.slice(a.top, a.top + rows).forEach(function (l) { e.appendChild(self.lineEl([[l, '']])); });
    for (var i = a.lines.length - a.top; i < rows; i++) e.appendChild(h('div.z2p-ln.d', { text: '~' }));
    var end = a.top + rows >= a.lines.length;
    e.appendChild(h('div.z2p-ln.z2p-alt-st', { text: end ? '(END)  q: ' + Z.t('term.quit') : a.name + '  ' + Math.round((a.top + rows) / a.lines.length * 100) + '%  q: ' + Z.t('term.quit') }));
  };
  Pane.prototype.altScroll = function (d) {
    var a = this.alt;
    if (!a) return;
    var max = Math.max(0, a.lines.length - this.altRows());
    a.top = Math.max(0, Math.min(max, a.top + d));
    this.altRender();
  };
  Pane.prototype.altKey = function (k) {
    var r = this.altRows();
    var key = k.key;
    if (k.ctrl && (key === 'c' || key === 'C')) { this.altQuit(); return; }
    if (key === 'q' || key === 'Q' || key === 'Escape') this.altQuit();
    else if (key === 'j' || key === 'ArrowDown' || key === 'Enter' || key === 'e') this.altScroll(1);
    else if (key === 'k' || key === 'ArrowUp' || key === 'y') this.altScroll(-1);
    else if (key === ' ' || key === 'f' || key === 'PageDown') this.altScroll(r - 1);
    else if (key === 'b' || key === 'PageUp') this.altScroll(-(r - 1));
    else if (key === 'g' || key === 'Home' || key === '<') this.altScroll(-1e9);
    else if (key === 'G' || key === 'End' || key === '>') this.altScroll(1e9);
  };
  Pane.prototype.altQuit = function () {
    var a = this.alt;
    if (!a) return;
    clearInterval(this._altIv);
    this.alt = null;
    this.altEl.hidden = true; this.altEl.textContent = '';
    this.linesEl.hidden = false; this.inEl.hidden = false;
    this.scrollBottom();
    a.done();
  };

  /* ---------------- 検索の印 ---------------- */
  Pane.prototype.highlight = function (re) {
    this.renderAll(re || null);
    if (!re) { this.marks = null; return 0; }
    this.marks = Array.prototype.slice.call(this.linesEl.querySelectorAll('mark'));
    return this.marks.length;
  };

  Pane.prototype.toJSON = function () {
    return { lines: this.lines.slice(-Z.LIMIT.lines) };
  };

  Z.Pane = Pane;
})(typeof window !== 'undefined' ? window : globalThis);
