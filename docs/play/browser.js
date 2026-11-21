/* z2term play — モックのブラウザ。
   検索: 2 つから選べる（最後に選んだ方を覚える。最初は Bing）。
     Bing … 検索結果のページをそのまま iframe で表示する（主な検索エンジンで埋め込みを断らないのは Bing だけだった）
     Wikipedia ＋ z2term … 言葉を Wikipedia の検索 API へ送り、z2term の手引きの見出しと並べる
   表示: 埋め込みを許している相手（許可リスト）だけ iframe で本当に表示する。
         それ以外は iframe に入れず「新しいタブで開く」を出す（中継サーバーは使わない）。
   サイトの外へ通信するのはここだけ。 */
(function (G) {
  'use strict';
  var Z = G.Z2P = G.Z2P || {};
  var h = function () { return Z.h.apply(null, arguments); };
  var T = function () { return Z.t.apply(null, arguments); };

  var SITE = 'https://orgsonai.github.io/z2term/';
  var ALLOW = {
    'ja.m.wikipedia.org': 1, 'en.m.wikipedia.org': 1, 'orgsonai.github.io': '/z2term/', 'www.bing.com': 1
  };

  /* 手引きの見出し（サイトと HANDBOOK）。検索結果の先頭に混ぜる。 */
  var DOCS = {
    ja: [
      ['できること', 'index.ja.html#what', 'linux z2 コマンド 機能'],
      ['マクロ', 'index.ja.html#macros', 'macro 自動化 シェル スクリプト'],
      ['タブの中のデスクトップ', 'index.ja.html#desktop', 'gui vnc rdp デスクトップ'],
      ['入手する', 'index.ja.html#download', 'apk ダウンロード f-droid インストール'],
      ['使いこなしガイド', 'guide.ja.html', 'ガイド 使い方'],
      ['ターミナルの使い方', 'guide-terminal.ja.html', 'ターミナル タブ キーボード ログ'],
      ['z2-when（自動化）', 'guide-when.ja.html', 'z2-when 自動化 トリガー 機内モード 充電'],
      ['タイル', 'guide-tile.ja.html', 'z2-tile タイル クイック設定'],
      ['マクロの書き方', 'guide-macro.ja.html', 'マクロ z2-action 画面操作'],
      ['使い方の手引き（HANDBOOK）', 'https://github.com/orgsonai/z2term/blob/main/docs/ja/HANDBOOK.md', 'handbook 手引き z2term'],
      ['Android操作マクロ（z2-action）', 'https://github.com/orgsonai/z2term/blob/main/docs/ja/ACTION-MACROS.md', 'z2-action human tap ui 軌跡']
    ],
    en: [
      ['What it does', 'index.html#what', 'linux z2 commands features'],
      ['Macros', 'index.html#macros', 'macro automation shell script'],
      ['Desktop in a tab', 'index.html#desktop', 'gui vnc rdp desktop'],
      ['Get it', 'index.html#download', 'apk download f-droid install'],
      ['User handbook', 'https://github.com/orgsonai/z2term/blob/main/docs/en/HANDBOOK.md', 'handbook guide z2term'],
      ['Android action macros (z2-action)', 'https://github.com/orgsonai/z2term/blob/main/docs/en/ACTION-MACROS.md', 'z2-action human tap ui trail'],
      ['Automation (z2-when)', 'https://github.com/orgsonai/z2term/blob/main/docs/en/HANDBOOK.md', 'z2-when automation trigger airplane charge']
    ]
  };

  var app = { id: 'browser', pkg: 'z2.mock.browser', icon: '◎', name: function () { return T('app.browser'); } };
  var b = {};
  var hist = [], pos = -1;

  app.build = function (el) {
    b.addr = h('input.z2p-br-addr', { type: 'text', inputmode: 'url', autocomplete: 'off', spellcheck: false, placeholder: T('br.addr'), 'aria-label': T('br.addrlabel'), 'data-id': 'browser_address' });
    b.addr.addEventListener('focus', function () { b.addr.select(); });
    var form = h('form.z2p-br-bar', { on: { submit: function (e) { e.preventDefault(); go(b.addr.value); b.addr.blur(); } } },
      b.backB = h('button.z2p-br-b', { type: 'button', 'aria-label': T('br.back'), text: '←', on: { click: back } }),
      b.fwdB = h('button.z2p-br-b', { type: 'button', 'aria-label': T('br.fwd'), text: '→', on: { click: fwd } }),
      b.addr,
      h('button.z2p-br-b', { type: 'button', 'aria-label': T('br.reload'), text: '↻', on: { click: function () { render(true); } } }),
      h('button.z2p-br-b', { type: 'button', 'aria-label': T('br.newtab'), text: '⧉', on: { click: openOutside } }));
    b.view = h('div.z2p-br-view');
    el.appendChild(h('div.z2p-browser', null, form, b.view));
    hist = [{ type: 'start' }]; pos = 0;
    render();
  };
  app.show = function (arg) {
    if (arg && arg.url) go(arg.url);
    else if (arg && arg.q) search(arg.q);
  };
  app.back = function () { if (pos > 0) { back(); return true; } return false; };
  app.reset = function () { hist = [{ type: 'start' }]; pos = 0; render(); };

  function push(e) { hist = hist.slice(0, pos + 1); hist.push(e); pos = hist.length - 1; render(); }
  function back() { if (pos > 0) { pos--; render(); } }
  function fwd() { if (pos < hist.length - 1) { pos++; render(); } }
  function cur() { return hist[pos]; }
  function online() { var s = Z.phone.state; return !s.airplane && s.wifi; }

  function go(text) {
    text = String(text || '').trim();
    if (!text) return;
    if (/^https?:\/\//i.test(text)) return push({ type: 'url', url: text });
    if (/^[\w-]+(\.[\w-]+)+(\/\S*)?$/.test(text) && !/\s/.test(text)) return push({ type: 'url', url: 'https://' + text });
    search(text);
  }
  function search(q) { push({ type: 'search', q: q }); Z.bus.emit('search', q); }
  app.search = search;
  app.go = go;

  function openOutside() {
    var e = cur();
    var u = e.type === 'url' ? e.url : e.type === 'search' ? wikiSearchUrl(e.q) : SITE;
    G.open(u, '_blank', 'noopener');
  }

  function wikiLang() { return Z.lang === 'ja' ? 'ja' : 'en'; }
  function wikiSearchUrl(q) { return 'https://' + wikiLang() + '.m.wikipedia.org/w/index.php?search=' + encodeURIComponent(q); }

  /* 許可リストの判定。wikipedia.org はモバイル版へ寄せる。返り値: 表示に使う URL か null */
  function embeddable(u) {
    var url;
    try { url = new URL(u); } catch (e) { return null; }
    if (url.protocol !== 'https:' && url.protocol !== 'http:') return null;
    var host = url.hostname;
    var m = /^(ja|en)\.wikipedia\.org$/.exec(host);
    if (m) { url.hostname = m[1] + '.m.wikipedia.org'; host = url.hostname; }
    var rule = ALLOW[host];
    if (!rule) return null;
    if (typeof rule === 'string' && url.pathname.indexOf(rule) !== 0) return null;
    url.protocol = 'https:';
    return url.href;
  }

  function render() {
    var e = cur();
    b.backB.disabled = pos <= 0;
    b.fwdB.disabled = pos >= hist.length - 1;
    b.view.textContent = '';
    if (e.type === 'start') { b.addr.value = ''; renderStart(); return; }
    if (e.type === 'search') { b.addr.value = e.q; renderSearch(e.q); return; }
    if (e.type === 'url') { b.addr.value = e.url; renderUrl(e); return; }
    if (e.type === 'text') { b.addr.value = e.url; renderText(e); }
  }

  function searchBox(q) {
    var inp = h('input.z2p-br-q', { type: 'search', placeholder: T('br.search'), 'aria-label': T('br.search'), value: q || '', autocomplete: 'off', 'data-id': 'browser_search' });
    return h('form.z2p-br-sform', { role: 'search', on: { submit: function (ev) { ev.preventDefault(); if (inp.value.trim()) search(inp.value.trim()); } } },
      inp, h('button.z2p-br-go', { type: 'submit', 'aria-label': T('br.dosearch'), text: '→' }));
  }

  function renderStart() {
    b.view.appendChild(h('div.z2p-br-start', null,
      h('p.z2p-br-logo', { text: T('br.logo') }),
      searchBox(''),
      h('p.z2p-br-note', { text: T('br.privacy') }),
      h('div.z2p-br-links', null,
        link(T('br.site'), Z.lang === 'ja' ? SITE + 'index.ja.html' : SITE),
        link('Wikipedia', 'https://' + wikiLang() + '.m.wikipedia.org/'))));
  }
  function link(label, url) {
    return h('button.z2p-br-link', { type: 'button', text: label, on: { click: function () { go(url); } } });
  }

  function result(title, url, snippet, onClick, tag) {
    return h('div.z2p-br-r', null,
      tag ? h('span.z2p-br-tag', { text: tag }) : null,
      h('button.z2p-br-rt', { type: 'button', text: title, on: { click: onClick } }),
      h('div.z2p-br-ru', { text: url }),
      snippet ? h('p.z2p-br-rs', { text: snippet }) : null);
  }

  function engine() { return (Z.phone.st && Z.phone.st.engine) || 'bing'; }
  function engineTabs(q) {
    var seg = h('div.z2p-seg.small.z2p-br-eng', { role: 'tablist', 'aria-label': T('br.engine') });
    [['bing', 'Bing'], ['wiki', T('br.wiki')]].forEach(function (o) {
      seg.appendChild(h('button', { type: 'button', role: 'tab', 'aria-selected': engine() === o[0] ? 'true' : 'false', 'data-id': 'engine_' + o[0], text: o[1],
        on: { click: function () { Z.phone.st.engine = o[0]; Z.phone.save(); render(); } } }));
    });
    return seg;
  }
  function bingUrl(q) {
    return 'https://www.bing.com/search?q=' + encodeURIComponent(q) + '&setlang=' + (Z.lang === 'ja' ? 'ja' : 'en') + '&cc=' + (Z.lang === 'ja' ? 'JP' : 'US');
  }
  function renderSearch(q) {
    b.view.appendChild(h('div.z2p-br-engbar', null, engineTabs(q)));
    if (engine() === 'bing') {
      if (!online()) { b.view.appendChild(offline()); return; }
      b.view.appendChild(h('iframe.z2p-br-frame', { src: bingUrl(q), title: 'Bing: ' + q, referrerpolicy: 'no-referrer',
        sandbox: 'allow-scripts allow-same-origin allow-popups allow-forms' }));
      return;
    }
    var box = h('div.z2p-br-results');
    box.appendChild(searchBox(q));
    box.appendChild(result(T('br.here'), 'z2term://play', T('br.heresub'), function () { Z.phone.open('z2term'); Z.phone.toast(T('br.already')); }, '★'));
    var ql = q.toLowerCase();
    (DOCS[Z.lang] || DOCS.en).forEach(function (d) {
      var hay = (d[0] + ' ' + d[2]).toLowerCase();
      if (ql.split(/\s+/).some(function (w) { return w && hay.indexOf(w) >= 0; })) {
        var u = /^https?:/.test(d[1]) ? d[1] : SITE + d[1];
        box.appendChild(result(d[0], u, null, function () { go(u); }, 'z2term'));
      }
    });
    var wbox = h('div.z2p-br-wiki', null, h('p.z2p-br-wait', { text: T('br.loading') }));
    box.appendChild(wbox);
    b.view.appendChild(box);
    if (!online()) { wbox.textContent = ''; wbox.appendChild(offline()); return; }
    var lang = wikiLang();
    var url = 'https://' + lang + '.wikipedia.org/w/api.php?action=query&list=search&format=json&origin=*&srlimit=8&srsearch=' + encodeURIComponent(q);
    var ctl = G.AbortController ? new AbortController() : null;
    var to = setTimeout(function () { if (ctl) ctl.abort(); }, 9000);
    var myPos = pos;
    G.fetch(url, { signal: ctl ? ctl.signal : undefined, referrerPolicy: 'no-referrer', credentials: 'omit' })
      .then(function (r) { return r.json(); })
      .then(function (j) {
        clearTimeout(to);
        if (myPos !== pos) return;
        wbox.textContent = '';
        var list = (j && j.query && j.query.search) || [];
        if (!list.length) { wbox.appendChild(h('p.z2p-br-wait', { text: T('br.none') })); return; }
        list.forEach(function (it) {
          var title = it.title;
          /* 抜粋は HTML で来るので、文字だけ取り出す（DOMParser は script を実行しない） */
          var snip = '';
          try { snip = new DOMParser().parseFromString(it.snippet || '', 'text/html').body.textContent; } catch (e) { snip = ''; }
          var u = 'https://' + lang + '.m.wikipedia.org/wiki/' + encodeURIComponent(title.replace(/ /g, '_'));
          wbox.appendChild(result(title, lang + '.wikipedia.org', snip, function () { go(u); }, 'Wikipedia'));
        });
      })
      .catch(function () {
        clearTimeout(to);
        if (myPos !== pos) return;
        wbox.textContent = '';
        wbox.appendChild(h('p.z2p-br-wait', { text: T('br.fail') }));
      });
  }

  function offline() {
    return h('div.z2p-br-off', null,
      h('p.z2p-br-off-t', { text: T('br.offline') }),
      h('p', { text: Z.phone.state.airplane ? T('br.offair') : T('br.offwifi') }));
  }

  function renderUrl(e) {
    var u = embeddable(e.url);
    if (!online()) { b.view.appendChild(offline()); return; }
    if (!u) {
      b.view.appendChild(h('div.z2p-br-block', null,
        h('p.z2p-br-off-t', { text: T('br.toobig') }),
        h('p.z2p-br-ru', { text: e.url }),
        h('p', { text: T('br.toobigsub') }),
        safeLink(e.url)));
      return;
    }
    var frame = h('iframe.z2p-br-frame', {
      src: u, title: e.url, referrerpolicy: 'no-referrer', loading: 'lazy',
      sandbox: 'allow-scripts allow-same-origin allow-popups allow-forms'
    });
    var tools = h('div.z2p-br-tools', null);
    if (/\.m\.wikipedia\.org$/.test(new URL(u).hostname)) {
      tools.appendChild(h('button.z2p-link', { type: 'button', text: T('br.textonly'), on: { click: function () { push({ type: 'text', url: u }); } } }));
    }
    tools.appendChild(safeLink(u));
    b.view.appendChild(tools);
    b.view.appendChild(frame);
    Z.bus.emit('page', u);
  }

  function safeLink(u) {
    var a = h('a.z2p-link', { href: u, target: '_blank', rel: 'noopener noreferrer', text: T('br.opentab') });
    return a;
  }

  /* iframe がうまく出ないときのための「文字だけで読む」（Wikipedia の本文を文字で取る） */
  function renderText(e) {
    var url = new URL(e.url);
    var lang = url.hostname.split('.')[0];
    var title = decodeURIComponent(url.pathname.replace(/^\/wiki\//, ''));
    var box = h('article.z2p-br-text', null, h('h1', { text: title.replace(/_/g, ' ') }), h('p.z2p-br-wait', { text: T('br.loading') }));
    b.view.appendChild(box);
    var api = 'https://' + lang + '.wikipedia.org/w/api.php?action=query&prop=extracts&explaintext=1&exsectionformat=plain&redirects=1&format=json&origin=*&titles=' + encodeURIComponent(title);
    G.fetch(api, { referrerPolicy: 'no-referrer', credentials: 'omit' }).then(function (r) { return r.json(); }).then(function (j) {
      var pages = (j.query && j.query.pages) || {};
      var p = pages[Object.keys(pages)[0]] || {};
      box.textContent = '';
      box.appendChild(h('h1', { text: p.title || title }));
      String(p.extract || T('br.none')).split(/\n+/).slice(0, 80).forEach(function (para) {
        if (para.trim()) box.appendChild(h('p', { text: para }));
      });
      box.appendChild(safeLink(e.url));
    }).catch(function () { box.textContent = ''; box.appendChild(h('p', { text: T('br.fail') })); });
  }

  Z.browser = app;
  Z.registerApp(app);
})(typeof window !== 'undefined' ? window : globalThis);
