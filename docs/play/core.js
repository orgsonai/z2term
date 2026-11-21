/* z2term play — 共通の土台: 名前空間・出来事の通知・保存（localStorage）・文言・小さな道具。
   DOM に触る道具（h）以外は node でも読める。 */
(function (G) {
  'use strict';
  var Z = G.Z2P = G.Z2P || {};

  /* ---------------- 出来事の通知 ---------------- */
  var handlers = {};
  Z.bus = {
    on: function (name, fn) { (handlers[name] = handlers[name] || []).push(fn); return fn; },
    off: function (name, fn) {
      var a = handlers[name]; if (!a) return;
      var i = a.indexOf(fn); if (i >= 0) a.splice(i, 1);
    },
    emit: function (name, data) {
      var a = (handlers[name] || []).slice();
      for (var i = 0; i < a.length; i++) {
        try { a[i](data); } catch (e) { if (G.console) console.error(e); }
      }
      if (name !== '*') {
        var all = (handlers['*'] || []).slice();
        for (var j = 0; j < all.length; j++) { try { all[j](name, data); } catch (e2) { /* 無視 */ } }
      }
    },
    reset: function () { handlers = {}; }
  };

  /* ---------------- 保存（localStorage） ----------------
     鍵は z2mock: から始める。中身は { schema, v }。
     書くのは変化から 0.5 秒後にまとめて。読めない中身は消さずに z2mock:broken:<日時> へ退避する。 */
  var SCHEMA = 1;
  var PREFIX = 'z2mock:';
  var cache = {};
  var dirty = {};
  var timer = null;
  var ls = null;
  try { ls = G.localStorage; ls.getItem('z2mock:probe'); } catch (e) { ls = null; }

  function stamp() {
    var d = new Date();
    function p(n) { return (n < 10 ? '0' : '') + n; }
    return d.getFullYear() + p(d.getMonth() + 1) + p(d.getDate()) + '-' + p(d.getHours()) + p(d.getMinutes()) + p(d.getSeconds());
  }

  function flush() {
    timer = null;
    if (!ls) { dirty = {}; return; }
    for (var k in dirty) {
      if (!Object.prototype.hasOwnProperty.call(dirty, k)) continue;
      try {
        ls.setItem(PREFIX + k, JSON.stringify({ schema: SCHEMA, v: cache[k] }));
      } catch (e) {
        Z.bus.emit('quota', { key: k, error: e });
      }
    }
    dirty = {};
  }

  Z.store = {
    available: !!ls,
    get: function (key, def) {
      if (Object.prototype.hasOwnProperty.call(cache, key)) return cache[key];
      var v = def;
      if (ls) {
        var raw = null;
        try { raw = ls.getItem(PREFIX + key); } catch (e) { raw = null; }
        if (raw != null) {
          try {
            var o = JSON.parse(raw);
            if (!o || typeof o !== 'object' || !('v' in o)) throw new Error('shape');
            /* 形を変えたらここで読み替える（今は schema 1 だけ）。 */
            v = o.v;
          } catch (e2) {
            try { ls.setItem(PREFIX + 'broken:' + stamp() + ':' + key, raw); } catch (e3) { /* 退避も失敗 */ }
            try { ls.removeItem(PREFIX + key); } catch (e4) { /* 無視 */ }
            v = def;
          }
        }
      }
      cache[key] = v;
      return v;
    },
    set: function (key, val) {
      cache[key] = val;
      dirty[key] = 1;
      if (!timer) timer = setTimeout(flush, 500);
    },
    touch: function (key) { dirty[key] = 1; if (!timer) timer = setTimeout(flush, 500); },
    flush: function () { if (timer) clearTimeout(timer); flush(); },
    /* ブラウザの上限は「約 500 万文字」なので、文字数で数える */
    usage: function () {
      var n = 0;
      if (!ls) return 0;
      try {
        for (var i = 0; i < ls.length; i++) {
          var k = ls.key(i);
          if (k && k.indexOf(PREFIX) === 0) n += k.length + (ls.getItem(k) || '').length;
        }
      } catch (e) { /* 無視 */ }
      return n;
    },
    clearAll: function () {
      if (timer) { clearTimeout(timer); timer = null; }
      dirty = {}; cache = {};
      if (!ls) return;
      var ks = [];
      try {
        for (var i = 0; i < ls.length; i++) { var k = ls.key(i); if (k && k.indexOf(PREFIX) === 0) ks.push(k); }
        ks.forEach(function (x) { ls.removeItem(x); });
      } catch (e) { /* 無視 */ }
    },
    dropCache: function () { cache = {}; }
  };
  Z.LIMIT = { store: 5 * 1024 * 1024, logFile: 256 * 1024, logTotal: 2 * 1024 * 1024, lines: 2000 };

  /* ---------------- 文言 ---------------- */
  Z.S = Z.S || {};
  Z.lang = 'ja';
  Z.t = function (key) {
    var tbl = Z.S[Z.lang] || {};
    var s = tbl[key];
    if (s == null) s = (Z.S.en || {})[key];
    if (s == null) s = key;
    for (var i = 1; i < arguments.length; i++) s = s.split('{' + (i - 1) + '}').join(String(arguments[i]));
    return s;
  };

  /* ---------------- 小さな道具 ---------------- */
  Z.pad = function (n, w) { n = String(n); while (n.length < (w || 2)) n = '0' + n; return n; };
  Z.uid = function () { return Math.random().toString(36).slice(2, 8); };
  Z.locale = function () {
    try { return (G.navigator && (G.navigator.languages && G.navigator.languages[0] || G.navigator.language)) || 'en-US'; }
    catch (e) { return 'en-US'; }
  };
  Z.fmtTime = function (d, sec) {
    try {
      var o = { hour: '2-digit', minute: '2-digit' };
      if (sec) o.second = '2-digit';
      return new Intl.DateTimeFormat(Z.locale(), o).format(d);
    } catch (e) { return Z.pad(d.getHours()) + ':' + Z.pad(d.getMinutes()); }
  };
  Z.fmtDate = function (d, opt) {
    try { return new Intl.DateTimeFormat(Z.locale(), opt || { month: 'long', day: 'numeric', weekday: 'short' }).format(d); }
    catch (e) { return d.toDateString(); }
  };
  Z.hms = function (d) { return Z.pad(d.getHours()) + ':' + Z.pad(d.getMinutes()) + ':' + Z.pad(d.getSeconds()); };
  Z.sleep = function (ms, sig) {
    return new Promise(function (ok) {
      var t = setTimeout(done, ms);
      var iv = null;
      function done() { clearTimeout(t); if (iv) clearInterval(iv); ok(); }
      if (sig) iv = setInterval(function () { if (sig.aborted) done(); }, 50);
    });
  };
  Z.bytes = function (n) {
    if (n < 1024) return n + ' B';
    if (n < 1024 * 1024) return (n / 1024).toFixed(1) + ' KB';
    return (n / 1024 / 1024).toFixed(2) + ' MB';
  };

  /* DOM 部品を作る。文字は必ず textContent で入れる（innerHTML は使わない）。 */
  Z.h = function (tag, attrs) {
    var parts = tag.split('.');
    var e = G.document.createElement(parts[0] || 'div');
    if (parts.length > 1) e.className = parts.slice(1).join(' ');
    if (attrs) {
      for (var k in attrs) {
        if (!Object.prototype.hasOwnProperty.call(attrs, k)) continue;
        var v = attrs[k];
        if (v == null || v === false) continue;
        if (k === 'text') e.textContent = v;
        else if (k === 'on') { for (var ev in v) e.addEventListener(ev, v[ev]); }
        else if (k === 'style' && typeof v === 'object') { for (var s in v) e.style.setProperty(s, v[s]); }
        else if (k === 'cls') e.className += ' ' + v;
        else if (k in e && k !== 'list' && typeof v !== 'string') e[k] = v;
        else e.setAttribute(k, v === true ? '' : v);
      }
    }
    for (var i = 2; i < arguments.length; i++) {
      var c = arguments[i];
      if (c == null || c === false) continue;
      if (Array.isArray(c)) c.forEach(function (x) { if (x != null && x !== false) e.appendChild(typeof x === 'string' ? G.document.createTextNode(x) : x); });
      else e.appendChild(typeof c === 'string' ? G.document.createTextNode(c) : c);
    }
    return e;
  };

  /* 長押し（PC では右クリックも）。短く押したときは何もしない。 */
  Z.longPress = function (el, fn, ms) {
    var t = null, sx = 0, sy = 0, fired = false;
    el.addEventListener('pointerdown', function (e) {
      if (e.button !== 0) return;
      fired = false; sx = e.clientX; sy = e.clientY;
      t = setTimeout(function () { fired = true; t = null; fn(e); }, ms || 480);
    });
    function cancel() { if (t) { clearTimeout(t); t = null; } }
    el.addEventListener('pointerup', cancel);
    el.addEventListener('pointerleave', cancel);
    el.addEventListener('pointermove', function (e) { if (Math.abs(e.clientX - sx) + Math.abs(e.clientY - sy) > 10) cancel(); });
    el.addEventListener('contextmenu', function (e) { e.preventDefault(); cancel(); if (!fired) fn(e); });
    el.addEventListener('click', function (e) { if (fired) { e.stopImmediatePropagation(); e.preventDefault(); fired = false; } }, true);
  };

  /* 指を滑らせ終えた直後のクリックを 1 回だけ捨てる。
     クリックが起きなかったときに次の本物のクリックを捨てないよう、すぐ外す。 */
  Z.eatClick = function (el) {
    function stop(ev) { ev.stopImmediatePropagation(); ev.preventDefault(); }
    el.addEventListener('click', stop, true);
    setTimeout(function () { el.removeEventListener('click', stop, true); }, 0);
  };

  /* シングルとダブルを分ける（ダブルのときはシングルを呼ばない）。 */
  Z.clicks = function (el, single, dbl) {
    var t = null;
    el.addEventListener('click', function (e) {
      if (t) { clearTimeout(t); t = null; dbl(e); return; }
      t = setTimeout(function () { t = null; single(e); }, 260);
    });
  };

  /* 伏せ字（端末ログ）。本物と同じく形のはっきりしたものだけ。 */
  Z.mask = function (line) {
    return line
      .replace(/\b([A-Za-z0-9_]*(?:TOKEN|SECRET|PASSWORD|PASSWD|API_KEY|APIKEY|KEY)[A-Za-z0-9_]*)=(\S+)/gi, '$1=[z2term:masked]')
      .replace(/\b(ghp|gho|ghs|github_pat)_[A-Za-z0-9_]{10,}/g, '[z2term:masked]')
      .replace(/\bAKIA[0-9A-Z]{12,}/g, '[z2term:masked]')
      .replace(/\bsk-[A-Za-z0-9_-]{16,}/g, '[z2term:masked]');
  };
})(typeof window !== 'undefined' ? window : globalThis);
