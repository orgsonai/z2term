/* Z2Term サイトの動きだけを担当するスクリプト。
   外部依存なし。JS が動かない環境でも中身は HTML 側に静的に置いてあるので読める。

   ページ側は次の 2 つを用意する:
     <div class="term-demo" data-scenes="ID">      … 再生先（.win-tabs / .win-body を内包）
     <script type="application/json" id="ID">[...] … 台本（言語ごとに文面を変える）

   台本 = シーンの配列。シーンは { "tab": ラベル, "title": ウィンドウ名, "steps": [...] }。
   step の種類:
     {"t":"cmd","x":"..."}                コマンドを 1 文字ずつ打つ
     {"t":"out","x":"...","cls":"t-ok"}   出力行を出す
     {"t":"wait","ms":600}                待つ
     {"t":"clear"}                        端末を消す
     — ここから下は端末の隣にある擬似スマホ画面 (.phone-stage) 用 —
     {"t":"noti","title":"","body":"","app":"","actions":["ラベル"]}
     {"t":"toast","x":"..."}
     {"t":"ask","title":"","reply":"打ち込まれる答え"}
     {"t":"torch"}                        ライトが光る
     {"t":"tiles","items":["🔦 ライト", ...]}
     {"t":"widget","title":"","lines":["",""]}
     {"t":"wipe"}                          スマホ画面を空にする
*/
(function () {
  'use strict';

  var REDUCE = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  function $(s, r) { return (r || document).querySelector(s); }
  function $$(s, r) { return Array.prototype.slice.call((r || document).querySelectorAll(s)); }
  function sleep(ms) { return new Promise(function (r) { setTimeout(r, REDUCE ? Math.min(ms, 16) : ms); }); }
  function esc(s) {
    return String(s).replace(/&/g, '&amp;').replace(/</g, '&lt;')
      .replace(/>/g, '&gt;').replace(/"/g, '&quot;');
  }

  /* シェルらしく最低限だけ色を付ける。凝った構文解析はしない。 */
  function shell(text) {
    var out = esc(text);
    out = out.replace(/(#[^\n]*)$/, '<span class="t-c">$1</span>');
    /* esc() の後なので " は &quot; になっている */
    out = out.replace(/(&quot;[\s\S]*?&quot;|'[^']*')/g, '<span class="t-str">$1</span>');
    out = out.replace(/^(\s*)([\w./-]+)/, function (m, sp, w) {
      return sp + '<span class="t-cmd">' + w + '</span>';
    });
    out = out.replace(/\b(z2-[a-z]+|z2[a-z]+)\b/g, '<span class="t-key">$1</span>');
    return out;
  }

  /* ============================ 端末の再生 ============================ */

  function Term(root) {
    this.root = root;
    this.body = $('.win-body', root);
    this.title = $('.win-bar .title', root);
    this.stage = $('.phone-stage', root.parentNode) || $('.phone-stage', root);
    this.token = 0;
  }

  Term.prototype.clear = function () { this.body.innerHTML = ''; };

  Term.prototype.line = function (html, cls) {
    var d = document.createElement('div');
    if (cls) d.className = cls;
    d.innerHTML = html;
    this.body.appendChild(d);
    this.body.scrollTop = this.body.scrollHeight;
    return d;
  };

  Term.prototype.prompt = function () {
    return '<span class="t-prompt">~</span> <span class="t-path">$</span> ';
  };

  Term.prototype.type = async function (text, tok) {
    var d = this.line(this.prompt(), '');
    var p = this.prompt();
    for (var i = 1; i <= text.length; i++) {
      if (tok !== this.token) return;
      d.innerHTML = p + shell(text.slice(0, i)) + '<span class="caret"></span>';
      this.body.scrollTop = this.body.scrollHeight;
      await sleep(text.charCodeAt(i - 1) === 32 ? 34 : 26);
    }
    d.innerHTML = p + shell(text);
  };

  /* ============================ 擬似スマホ画面 ============================ */

  function phoneEl(cls, html) {
    var d = document.createElement('div');
    d.className = cls;
    d.innerHTML = html;
    return d;
  }

  Term.prototype.wipe = function () {
    if (!this.stage) return;
    var kids = $$('.noti,.toast,.tiles,.widget', this.stage);
    kids.forEach(function (k) {
      k.classList.add('out');
      setTimeout(function () { if (k.parentNode) k.parentNode.removeChild(k); }, 320);
    });
  };

  Term.prototype.noti = function (s) {
    if (!this.stage) return null;
    var acts = (s.actions || []).map(function (a) { return '<span>' + esc(a) + '</span>'; }).join('');
    var el = phoneEl('noti',
      '<div class="noti-app">' + esc(s.app || 'z2term') + ' · ' + esc(s.when || 'now') + '</div>' +
      '<div class="noti-title">' + esc(s.title || '') + '</div>' +
      (s.body ? '<div class="noti-body">' + esc(s.body) + '</div>' : '') +
      (acts ? '<div class="noti-actions">' + acts + '</div>' : '') +
      (s.reply !== undefined ? '<div class="noti-reply"><i></i><span class="rt"></span><span class="caret"></span></div>' : '')
    );
    this.stage.appendChild(el);
    requestAnimationFrame(function () { el.classList.add('in'); });
    return el;
  };

  Term.prototype.toast = async function (text, tok) {
    if (!this.stage) return;
    var el = phoneEl('toast', esc(text));
    this.stage.appendChild(el);
    requestAnimationFrame(function () { el.classList.add('in'); });
    await sleep(1800);
    if (tok !== this.token) { if (el.parentNode) el.parentNode.removeChild(el); return; }
    el.classList.remove('in');
    setTimeout(function () { if (el.parentNode) el.parentNode.removeChild(el); }, 320);
  };

  Term.prototype.tiles = function (items) {
    if (!this.stage) return;
    var html = (items || []).map(function (t, i) {
      return '<span style="transition-delay:' + (i * 70) + 'ms">' + esc(t) + '</span>';
    }).join('');
    var el = phoneEl('tiles', html);
    this.stage.appendChild(el);
    requestAnimationFrame(function () { el.classList.add('in'); });
  };

  Term.prototype.widget = function (s) {
    if (!this.stage) return;
    var lines = (s.lines || []).map(function (l) { return '<div>' + esc(l) + '</div>'; }).join('');
    var el = phoneEl('widget',
      '<div class="widget-h">' + esc(s.title || '') + '</div>' + lines);
    this.stage.appendChild(el);
    requestAnimationFrame(function () { el.classList.add('in'); });
  };

  Term.prototype.torch = async function () {
    if (!this.stage) return;
    this.stage.classList.add('flash');
    await sleep(900);
    this.stage.classList.remove('flash');
  };

  /* 端末の中に画像を「描く」。上からスキャンラインが下りて絵が現れる。 */
  Term.prototype.images = async function (items, tok) {
    var box = document.createElement('div');
    box.className = 'term-imgs';
    this.body.appendChild(box);

    for (var i = 0; i < items.length; i++) {
      if (tok !== this.token) return;
      var it = items[i];
      var fig = document.createElement('figure');
      fig.className = 'term-img' + (it.wide ? ' wide' : '');
      fig.innerHTML =
        '<span class="scan"></span>' +
        '<img src="' + esc(it.src) + '" alt="' + esc(it.alt || '') + '" loading="lazy">' +
        (it.cap ? '<figcaption>' + esc(it.cap) + '</figcaption>' : '');
      box.appendChild(fig);
      requestAnimationFrame(function (f) {
        return function () { f.classList.add('drawn'); };
      }(fig));
      this.body.scrollTop = this.body.scrollHeight;
      await sleep(420);
    }
    await sleep(700);
  };

  /* ============================ ステップ実行 ============================ */

  Term.prototype.run = async function (scene, tok) {
    this.token = tok;
    if (this.title && scene.title) this.title.textContent = scene.title;
    this.clear();
    this.wipe();
    await sleep(160);

    for (var i = 0; i < scene.steps.length; i++) {
      if (tok !== this.token) return;
      var s = scene.steps[i];

      if (s.t === 'cmd') {
        await this.type(s.x, tok);
        await sleep(240);

      } else if (s.t === 'out') {
        this.line(s.x === '' ? '&nbsp;' : esc(s.x), s.cls || 't-out');
        await sleep(s.ms || 90);

      } else if (s.t === 'wait') {
        await sleep(s.ms || 500);

      } else if (s.t === 'clear') {
        this.clear();

      } else if (s.t === 'wipe') {
        this.wipe();
        await sleep(280);

      } else if (s.t === 'noti') {
        this.noti(s);
        await sleep(s.ms || 700);

      } else if (s.t === 'toast') {
        await this.toast(s.x, tok);

      } else if (s.t === 'ask') {
        var el = this.noti({ app: s.app, when: s.when, title: s.title, body: s.body, reply: '' });
        await sleep(700);
        if (!el) continue;
        var rt = $('.rt', el);
        for (var k = 1; k <= s.reply.length; k++) {
          if (tok !== this.token) return;
          rt.textContent = s.reply.slice(0, k);
          await sleep(58);
        }
        await sleep(500);
        el.classList.add('sent');
        await sleep(320);
        el.classList.add('out');

      } else if (s.t === 'img') {
        await this.images(s.items || [], tok);

      } else if (s.t === 'torch') {
        await this.torch();

      } else if (s.t === 'tiles') {
        this.tiles(s.items);
        await sleep(s.ms || 800);

      } else if (s.t === 'widget') {
        this.widget(s);
        await sleep(s.ms || 800);
      }
    }
    this.line(this.prompt() + '<span class="caret"></span>');
  };

  /* ============================ 組み立て ============================ */

  function setupDemo(root) {
    var data;
    try { data = JSON.parse(document.getElementById(root.dataset.scenes).textContent); }
    catch (e) { return; }
    if (!data || !data.length) return;

    var term = new Term(root);
    var tabsBox = $('.win-tabs', root);
    var tok = 0, timer = null, auto = true, cur = -1;

    function select(n, byUser) {
      if (byUser) auto = false;
      cur = n;
      if (tabsBox) {
        $$('button', tabsBox).forEach(function (b, i) {
          b.setAttribute('aria-selected', i === n ? 'true' : 'false');
        });
      }
      tok++;
      var mine = tok;
      /* 次の場面の予約。⚠ 転んだときも通す — 1 つ失敗しただけで巡回が止まると、
         同じ場面が出たままになり「タブが自動で切り替わらない」ように見える。 */
      function next() {
        if (!auto || mine !== tok || data.length < 2) return;
        clearTimeout(timer);
        timer = setTimeout(function () {
          if (auto && mine === tok) select((n + 1) % data.length, false);
        }, 2600);
      }
      term.run(data[n], mine).then(next, next);
    }

    if (tabsBox && data.length > 1) {
      data.forEach(function (sc, i) {
        var b = document.createElement('button');
        b.type = 'button';
        b.textContent = sc.tab;
        b.setAttribute('aria-selected', 'false');
        b.addEventListener('click', function () { select(i, true); });
        tabsBox.appendChild(b);
      });
    } else if (tabsBox) {
      tabsBox.remove();
    }

    /* 画面に入るまで再生しない。
       ⚠ 判定は「少しでも見えているか」にする。割合 (threshold) で切ると、
         ページを読みながらスクロールしている間に何度も中断がかかり、
         戻るたび**同じ場面を頭からやり直す**ので、タブが一度も進まない。
       ⚠ 途中で切られたときは、戻ってきたら**次の場面**から始める。
         そうしないと、少しずつ読み進める人には 1 枚目しか見えない。 */
    var started = false, cutOff = false;
    var io = new IntersectionObserver(function (es) {
      es.forEach(function (e) {
        if (e.isIntersecting && !started) {
          started = true;
          var n = cur < 0 ? 0 : (cutOff ? (cur + 1) % data.length : cur);
          cutOff = false;
          select(n, false);
        } else if (!e.isIntersecting && started) {
          cutOff = true; tok++; clearTimeout(timer); started = false;
        }
      });
    }, { threshold: 0, rootMargin: '160px 0px 160px 0px' });
    io.observe(root);
  }

  /* セクション見出しの「$ …」を、見えたところで打ち直す */
  function setupCmdlines() {
    var io = new IntersectionObserver(function (es) {
      es.forEach(function (e) {
        if (!e.isIntersecting) return;
        io.unobserve(e.target);
        var el = e.target, txt = el.dataset.cmd || '', out = $('.typed', el);
        if (!out) return;
        if (REDUCE) { out.textContent = txt; return; }
        el.classList.add('typing');
        var i = 0;
        (function step() {
          out.textContent = txt.slice(0, ++i);
          if (i < txt.length) setTimeout(step, 30);
          else setTimeout(function () { el.classList.remove('typing'); }, 900);
        })();
      });
    }, { threshold: .6 });
    $$('.cmdline[data-cmd]').forEach(function (el) {
      if (!$('.typed', el)) return;
      $('.typed', el).textContent = '';
      io.observe(el);
    });
  }

  /* 狭い画面のメニュー開閉 */
  function setupMenu() {
    var btn = $('.menu-btn'), nav = $('header.site nav');
    if (!btn || !nav) return;
    function close() {
      nav.classList.remove('open');
      btn.setAttribute('aria-expanded', 'false');
      btn.textContent = 'menu';
    }
    btn.addEventListener('click', function () {
      var open = nav.classList.toggle('open');
      btn.setAttribute('aria-expanded', open ? 'true' : 'false');
      btn.textContent = open ? 'close' : 'menu';
    });
    $$('a', nav).forEach(function (a) { a.addEventListener('click', close); });
    addEventListener('resize', function () {
      if (innerWidth > 760) close();
    }, { passive: true });
  }

  function setupReveal() {
    var io = new IntersectionObserver(function (es) {
      es.forEach(function (e) {
        if (e.isIntersecting) { e.target.classList.add('in'); io.unobserve(e.target); }
      });
    }, { threshold: .12, rootMargin: '0px 0px -40px 0px' });
    $$('.reveal').forEach(function (el) { io.observe(el); });
  }

  function setupHeader() {
    var h = $('header.site');
    if (!h) return;
    var on = false;
    addEventListener('scroll', function () {
      var v = scrollY > 8;
      if (v !== on) { on = v; h.classList.toggle('stuck', v); }
    }, { passive: true });
  }

  document.addEventListener('DOMContentLoaded', function () {
    setupHeader();
    setupMenu();
    setupReveal();
    setupCmdlines();
    $$('.term-demo[data-scenes]').forEach(setupDemo);
  });
})();
