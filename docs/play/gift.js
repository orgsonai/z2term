/* z2term play — おまけ: hack と matrix。
   ホームの最後のページの「hack」を押すと、マクロが z2term を開いて `hack` と打って実行する。
   hack は配布する gift/hack（bash）と同じ画面を canvas で描く: 上は文字の雨、下はそれらしいログ（ずっと流れる）
   30 秒見る、または q / Ctrl+C で抜けると、おまけ（gift/hack と gift/matrix.sh）をダウンロードできる。
   どちらも見た目だけの演出で、何にも接続しない。 */
(function (G) {
  'use strict';
  var Z = G.Z2P = G.Z2P || {};
  var h = function () { return Z.h.apply(null, arguments); };
  var T = function () { return Z.t.apply(null, arguments); };
  var GIFT = Z.gift = {};

  var UNLOCK_AFTER = 30000;
  var C256 = { 0: '#000000', 22: '#005f00', 28: '#008700', 33: '#0087ff', 40: '#00d700', 46: '#00ff00', 51: '#00ffff',
    118: '#87ff00', 196: '#ff0000', 244: '#808080', 250: '#bcbcbc', 255: '#eeeeee' };
  var CHARS = '0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ@#$%&*';
  function rnd(n) { return Math.floor(Math.random() * n); }
  function ch() { return CHARS[rnd(CHARS.length)]; }
  function ip() { return (rnd(126) + 1) + '.' + (rnd(254) + 1) + '.' + (rnd(254) + 1) + '.' + (rnd(254) + 1); }
  function token(n) { var a = 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789', s = ''; for (var i = 0; i < n; i++) s += a[rnd(a.length)]; return s; }

  /* ---------------- 共通: canvas の画面（桁 × 行のマス） ---------------- */
  function Screen(cv, fs) {
    this.cv = cv;
    this.ctx = cv.getContext('2d');
    this.fs = fs;
    this.resize();
  }
  Screen.prototype.resize = function () {
    var r = this.cv.parentNode.getBoundingClientRect();
    var dpr = G.devicePixelRatio || 1;
    this.cv.width = Math.max(1, Math.floor(r.width * dpr));
    this.cv.height = Math.max(1, Math.floor(r.height * dpr));
    this.cv.style.width = r.width + 'px';
    this.cv.style.height = r.height + 'px';
    this.ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    this.ctx.font = this.fs + 'px ui-monospace, SFMono-Regular, Menlo, Consolas, "DejaVu Sans Mono", monospace';
    this.ctx.textBaseline = 'top';
    this.cw = Math.max(5, this.ctx.measureText('M').width);
    this.ch = Math.round(this.fs * 1.25);
    this.cols = Math.max(24, Math.floor(r.width / this.cw));
    this.rows = Math.max(12, Math.floor(r.height / this.ch));
    this.w = r.width; this.h = r.height;
    this.grid = [];
    for (var y = 0; y <= this.rows; y++) this.grid.push(new Array(this.cols + 1));
  };
  Screen.prototype.set = function (row, col, color, c) {
    if (row < 1 || row > this.rows || col < 1 || col > this.cols) return;
    this.grid[row][col] = c === ' ' ? null : [c, color];
  };
  Screen.prototype.clearRows = function (from, to) {
    for (var y = from; y <= to; y++) this.grid[y] = new Array(this.cols + 1);
  };
  Screen.prototype.text = function (row, col, color, s, bold) {
    var ctx = this.ctx;
    ctx.font = (bold ? 'bold ' : '') + this.fs + 'px ui-monospace, SFMono-Regular, Menlo, Consolas, "DejaVu Sans Mono", monospace';
    ctx.fillStyle = C256[color] || color;
    ctx.fillText(s, (col - 1) * this.cw, (row - 1) * this.ch);
    if (bold) ctx.font = this.fs + 'px ui-monospace, SFMono-Regular, Menlo, Consolas, "DejaVu Sans Mono", monospace';
  };
  Screen.prototype.paintGrid = function (rowsTo) {
    var ctx = this.ctx;
    for (var y = 1; y <= rowsTo; y++) {
      var line = this.grid[y];
      for (var x = 1; x <= this.cols; x++) {
        var c = line[x];
        if (!c) continue;
        ctx.fillStyle = C256[c[1]] || '#00ff00';
        ctx.fillText(c[0], (x - 1) * this.cw, (y - 1) * this.ch);
      }
    }
  };

  /* ---------------- hack（gift/hack と同じ: 上は文字の雨、下はそれらしいログ。ずっと流れる） ---------------- */
  function Hack(scr) {
    this.s = scr;
    this.frame = 0;
    this.logs = [];
    this.t0 = Date.now() / 1000;
    this.layout();
    this.log(46, '[BOOT] matrix renderer online; differential output enabled');
    this.log(51, '[CLUSTER] discovery mesh initialized; dynamic nodes enabled');
  }
  Hack.prototype.layout = function () {
    var s = this.s;
    this.border = Math.min(Math.max(7, Math.floor(s.rows * 58 / 100)), s.rows - 4);
    this.topLast = this.border - 1;
    this.logRows = s.rows - this.border;
    this.spacing = s.cols <= 100 ? 3 : 4;
    this.streams = [];
    for (var c = 1; c <= s.cols; c++) {
      this.streams[c] = { y: 1 - rnd(this.topLast * 2), sp: rnd(3) + 1, len: rnd(8) + 8, on: c % this.spacing === 0 };
    }
  };
  Hack.prototype.log = function (color, text) {
    var t = Math.floor(Date.now() / 1000 - this.t0);
    this.logs.push([color, '[' + Z.pad(Math.floor(t / 60)) + ':' + Z.pad(t % 60) + '] ' + text]);
    if (this.logs.length > 60) this.logs.shift();
  };
  Hack.prototype.demoLog = function () {
    var node = ip(), a = ip(), tk = token(rnd(6) + 7), pr = rnd(10000), sp = rnd(31) + 10;
    var n = [[196, '[FAILED] ' + a + ':22 user=root pass=' + tk], [196, '[FAILED] node=' + node + ' digest mismatch candidate=' + tk],
      [51, '[CLUSTER] dispatch range=' + pr + '-' + (pr + rnd(900) + 100) + ' node=' + node], [244, '[TRY] node=' + node + ' algo=yescrypt candidate=' + tk],
      [33, '[SCAN] ' + a + ' ports=22,80,443 latency=' + (rnd(70) + 4) + 'ms'], [196, '[FAILED] ' + a + ':8022 authentication rejected'],
      [51, '[QUEUE] worker=' + node + ' lease=' + rnd(32768) + ' queue=' + (rnd(90) + 10)], [244, '[TRY] rate=' + sp + 'w/s salt=' + token(6) + ' block=' + pr]][rnd(8)];
    this.log(n[0], n[1]);
  };
  Hack.prototype.step = function () {
    var s = this.s;
    this.frame++;
    for (var c = 1; c <= s.cols; c++) {
      var st = this.streams[c];
      if (!st.on || this.frame % st.sp) continue;
      var y = st.y + 1;
      st.y = y;
      s.set(y - st.len, c, 0, ' ');
      s.set(y - 7, c, 22, ch());
      s.set(y - 3, c, 28, ch());
      s.set(y - 1, c, 40, ch());
      if (y <= this.topLast) s.set(y, c, 255, ch());
      if (y - st.len > this.topLast) { st.y = 1 - rnd(this.topLast + 8); st.sp = rnd(3) + 1; st.len = rnd(8) + 8; }
    }
    if (this.frame % 3 === 0) this.demoLog();
  };
  Hack.prototype.draw = function () {
    var s = this.s, ctx = s.ctx;
    ctx.fillStyle = '#000';
    ctx.fillRect(0, 0, s.w, s.h);
    s.paintGrid(this.topLast);
    var label = ' MATRIX // DISTRIBUTED ANALYSIS ';
    var left = Math.max(0, Math.floor((s.cols - label.length) / 2));
    var line = new Array(left + 1).join('-') + label;
    line += new Array(Math.max(0, s.cols - line.length) + 1).join('-');
    s.text(this.border, 1, 46, line.slice(0, s.cols), true);
    var show = this.logs.slice(-this.logRows);
    for (var k = 0; k < show.length; k++) s.text(this.border + 1 + k, 1, show[k][0], show[k][1].slice(0, s.cols - 1));
  };

  /* ---------------- matrix（gift/matrix.sh と同じ雨だけ） ---------------- */
  var GRAD = [46, 40, 34, 28, 22, 236, 235, 234, 233, 232];
  var GRADHEX = { 46: '#00ff00', 40: '#00d700', 34: '#00af00', 28: '#008700', 22: '#005f00', 236: '#303030', 235: '#262626', 234: '#1c1c1c', 233: '#121212', 232: '#080808' };
  function Rain(scr) {
    this.s = scr;
    this.frame = 0;
    this.drops = [];
    for (var c = 0; c < scr.cols; c++) this.drops[c] = { y: -rnd(scr.rows * 2), sp: rnd(4) + 1, len: rnd(21) + 10 };
  }
  Rain.prototype.step = function () {
    var s = this.s;
    this.frame++;
    for (var c = 0; c < s.cols; c++) {
      var d = this.drops[c];
      if (this.frame % d.sp) continue;
      for (var i = 0; i < d.len; i++) {
        var y = d.y - i;
        var col = GRAD[Math.min(GRAD.length - 1, Math.floor(i / 4))];
        if (y > 0 && y <= s.rows) s.set(y, c + 1, col, 'abcdefghijklmnopqrstuvwxyz0123456789'[rnd(36)]);
      }
      s.set(d.y - d.len, c + 1, 0, ' ');
      d.y++;
      if (d.y - d.len > s.rows) { d.y = -rnd(20); d.sp = rnd(4) + 1; d.len = rnd(21) + 10; }
    }
  };
  Rain.prototype.draw = function () {
    var s = this.s;
    s.ctx.fillStyle = '#000';
    s.ctx.fillRect(0, 0, s.w, s.h);
    for (var y = 1; y <= s.rows; y++) {
      var line = s.grid[y];
      for (var x = 1; x <= s.cols; x++) {
        var c = line[x];
        if (!c) continue;
        s.ctx.fillStyle = GRADHEX[c[1]] || '#00ff00';
        s.ctx.fillText(c[0], (x - 1) * s.cw, (y - 1) * s.ch);
      }
    }
  };

  /* ---------------- 端末のコマンド ---------------- */
  function runShow(c, Kind, giftable) {
    var pane = c.io.pane;
    if (!pane || !pane.canvasMode) { c.err(c.name + ': ' + T('gift.noterm') + '\n'); return 1; }
    var fs = (Z.zt && Z.zt.cfg && Z.zt.cfg().font) || 13;
    var started = Date.now(), unlocked = false;
    var p = pane.canvasMode(function (k) {
      if (k.key === 'q' || k.key === 'Q') return 'quit';
      return null;
    }, c.sig);
    var cv = pane.altReady;
    var scr = new Screen(cv, Math.max(10, Math.min(16, fs)));
    var anim = new Kind(scr);
    var timer = setInterval(function () {
      if (!cv.isConnected) return;
      var r = cv.parentNode.getBoundingClientRect();
      if (Math.abs(r.width - scr.w) > 2 || Math.abs(r.height - scr.h) > 2) { scr.resize(); anim = new Kind(scr); }
      anim.step();
      anim.draw();
      if (giftable && !unlocked && Date.now() - started >= UNLOCK_AFTER) {
        unlocked = true;
        GIFT.unlock();
        Z.phone.notify({ title: T('gift.ready'), body: T('gift.readysub'), app: 'z2term', heads: true });
      }
    }, 40);
    return p.then(function (why) {
      clearInterval(timer);
      if (giftable) {
        GIFT.unlock();
        c.out(Z.color(T('gift.thanks'), '32') + '\n');
        setTimeout(GIFT.open, 250);
      }
      return why === 'ctrlc' ? 130 : 0;
    });
  }
  Z.cmds.hack = function (c) {
    if (c.args[0] === '--help' || c.args[0] === '-h') { c.out(T('gift.hackhelp') + '\n'); return 0; }
    return runShow(c, Hack, true);
  };
  Z.cmds.matrix = function (c) { return runShow(c, Rain, false); };
  Z.cmds['matrix.sh'] = Z.cmds.matrix;

  /* ---------------- おまけ ---------------- */
  GIFT.unlocked = function () { return !!(Z.phone.st && Z.phone.st.gift); };
  GIFT.unlock = function () {
    if (GIFT.unlocked()) return;
    Z.phone.st.gift = Date.now();
    Z.phone.save();
    Z.bus.emit('gift');
  };
  GIFT.open = function () {
    if (!GIFT.unlocked()) return;
    var base = Z.base || 'play/';
    function link(file, desc) {
      return h('a.z2p-gift-dl', { href: base + 'gift/' + file, download: file, 'data-id': 'gift_' + file.replace(/\W/g, '_') },
        h('span.z2p-gift-name', { text: '⬇ ' + file }), h('span.z2p-gift-desc', { text: desc }));
    }
    var box = h('div.z2p-dialog.z2p-gift', { role: 'dialog', 'aria-label': T('gift.title') },
      h('p.z2p-dialog-t', { text: T('gift.title') }),
      h('p', { text: T('gift.lead') }),
      link('hack', T('gift.hackdesc')),
      link('matrix.sh', T('gift.matrixdesc')),
      h('p.z2p-gift-how', { text: T('gift.how') }),
      h('code.z2p-gift-code', { text: 'bash hack\nbash matrix.sh' }),
      h('p.z2p-dim-note', { text: T('gift.note') }),
      h('div.z2p-dialog-b', null, h('button.z2p-btn', { type: 'button', text: 'OK', on: { click: Z.phone.closeModal } })));
    Z.phone.modal(box);
  };

  /* ホームの「hack」: マクロが z2term を開いて hack と打って実行する */
  GIFT.launch = function () {
    if (Z.action.running()) { Z.phone.toast(T('act.busy', Z.action.running().name)); return; }
    var t = Z.zt.current();
    if (!t || t.pane.running || t.pane.alt) Z.zt.newTab({ name: 'hack', show: true });
    var prog = ['version=2', 'human=on', 'timeout=60', 'launch com.zerotoship.z2term', 'wait 900', 'type hack', 'enter'].join('\n');
    Z.action.start('hack', { aborted: false }, null, 'hack-icon', prog).then(function (r) {
      if (!r.ok && !r.stopped) Z.phone.toast('hack: ' + r.error);
    });
  };
})(typeof window !== 'undefined' ? window : globalThis);
