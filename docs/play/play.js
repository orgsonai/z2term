/* z2term play — ブラウザで触れる z2term（スマホのモック）の入口。
   紹介ページ（index.html / index.ja.html）がこのファイルだけを defer で読む。
   ここでは「Enter the z2term world」ボタンを見せて、押されたときに残りの部品を
   順に読み込むだけ。JS が動かない環境ではボタンは hidden のまま（本文は今まで通り読める）。

   外部ライブラリ・ビルド工程なし。部品は素の <script> を順に差し込む（file:// でも動く）。 */
(function () {
  'use strict';

  var VER = '4';
  var PARTS = [
    'strings.ja.js', 'strings.en.js', 'help.js',
    'core.js', 'fs.js', 'shell.js', 'cmds.js', 'z2cmds.js',
    'phone.js', 'apps.js', 'browser.js', 'term.js', 'z2term.js',
    'when.js', 'edge.js', 'action.js', 'guide.js', 'main.js'
  ];

  var me = document.currentScript;
  var base = me ? me.src.replace(/[^/]*(\?.*)?$/, '') : 'play/';
  var loading = null;

  function addCss() {
    if (document.getElementById('z2p-css')) return;
    var l = document.createElement('link');
    l.id = 'z2p-css';
    l.rel = 'stylesheet';
    l.href = base + 'play.css?v=' + VER;
    document.head.appendChild(l);
  }

  function loadOne(name) {
    return new Promise(function (ok, ng) {
      var s = document.createElement('script');
      s.src = base + name + '?v=' + VER;
      s.async = false;
      s.onload = ok;
      s.onerror = function () { ng(new Error(name)); };
      document.head.appendChild(s);
    });
  }

  function loadAll() {
    if (loading) return loading;
    addCss();
    loading = PARTS.reduce(function (p, n) {
      return p.then(function () { return loadOne(n); });
    }, Promise.resolve());
    return loading;
  }

  function enter(btn) {
    btn.setAttribute('aria-busy', 'true');
    btn.disabled = true;
    loadAll().then(function () {
      btn.disabled = false;
      btn.removeAttribute('aria-busy');
      window.Z2P.open();
    }, function (e) {
      btn.disabled = false;
      btn.removeAttribute('aria-busy');
      var note = document.getElementById('z2p-enter-note');
      if (note) note.textContent = (document.documentElement.lang === 'ja'
        ? '読み込めませんでした: ' : 'Could not load: ') + e.message;
      loading = null;
    });
  }

  function init() {
    var btns = document.querySelectorAll('[data-z2p-enter]');
    for (var i = 0; i < btns.length; i++) {
      var b = btns[i];
      b.hidden = false;
      var box = b.closest('[data-z2p-box]');
      if (box) box.hidden = false;
      b.addEventListener('click', function (ev) { enter(ev.currentTarget); });
    }
    /* 前回モックを開いたまま閉じたページなら、ボタンを押すまで開かない（勝手に覆わない）。 */
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init);
  else init();
})();
