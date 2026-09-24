package com.zerotoship.z2term.proot

/** Subscription changes use the same public viewer actions as other macros. */
internal fun rssFeedsLabel(t: CliText): String = t(
    en = "Subscriptions", ja = "購読先",
    "zh-CN" to "订阅源", "zh-TW" to "訂閱源", "es" to "Suscripciones", "ko" to "구독",
)

internal fun rssFeedsScript(d: String, t: CliText): String {
    // Keep the label order in sync with the Python tuple below.
    val labels = t.lines(
        en = listOf(
            "Subscriptions", "Add", "Articles", "Change", "Remove", "Feed URL",
            "Remove this subscription? Collected articles will be kept.",
            "Changes apply on the next poll. Collected articles are kept.",
            "No subscriptions. Use Add to enter an RSS or Atom URL.",
            "Enter an HTTP(S) URL without spaces or credentials (up to 2048 characters).",
            "This subscription already exists.",
            "This subscription has changed. Refresh the list and try again.",
            "Another change is being saved. Try again.",
            "Previous", "Next",
        ),
        ja = listOf(
            "購読先", "追加", "記事一覧", "変更", "削除", "フィード URL",
            "この購読先を削除しますか？ 取得済みの記事は残ります。",
            "変更は次の巡回から反映します。取得済みの記事は残ります。",
            "購読先がありません。「追加」から RSS / Atom の URL を入力してください。",
            "空白や認証情報を含まない HTTP(S) の URL を入力してください（2048文字以内）。",
            "この購読先は登録済みです。",
            "この購読先は変更されています。一覧を更新してやり直してください。",
            "別の変更を保存中です。もう一度お試しください。",
            "前へ", "次へ",
        ),
        "zh-CN" to listOf(
            "订阅源", "添加", "文章列表", "修改", "删除", "订阅源 URL",
            "删除此订阅源？已获取的文章将保留。",
            "更改将在下次抓取时生效。已获取的文章将保留。",
            "没有订阅源。请通过“添加”输入 RSS / Atom URL。",
            "请输入不含空白或认证信息的 HTTP(S) URL（最多 2048 个字符）。",
            "此订阅源已存在。",
            "此订阅源已更改。请刷新列表后重试。",
            "正在保存其他更改。请重试。",
            "上一页", "下一页",
        ),
        "zh-TW" to listOf(
            "訂閱源", "新增", "文章列表", "修改", "刪除", "訂閱源 URL",
            "刪除此訂閱源？已取得的文章將保留。",
            "變更將在下次擷取時生效。已取得的文章將保留。",
            "沒有訂閱源。請透過「新增」輸入 RSS / Atom URL。",
            "請輸入不含空白或認證資訊的 HTTP(S) URL（最多 2048 個字元）。",
            "此訂閱源已存在。",
            "此訂閱源已變更。請重新整理列表後重試。",
            "正在儲存其他變更。請重試。",
            "上一頁", "下一頁",
        ),
        "es" to listOf(
            "Suscripciones", "Añadir", "Artículos", "Cambiar", "Eliminar", "URL del feed",
            "¿Eliminar esta suscripción? Los artículos guardados se conservarán.",
            "Los cambios se aplican en la próxima consulta. Se conservan los artículos guardados.",
            "No hay suscripciones. Usa Añadir para introducir una URL RSS / Atom.",
            "Introduce una URL HTTP(S) sin espacios ni credenciales (máximo 2048 caracteres).",
            "Esta suscripción ya existe.",
            "Esta suscripción ha cambiado. Actualiza la lista e inténtalo de nuevo.",
            "Se está guardando otro cambio. Inténtalo de nuevo.",
            "Anterior", "Siguiente",
        ),
        "ko" to listOf(
            "구독", "추가", "글 목록", "변경", "삭제", "피드 URL",
            "이 구독을 삭제할까요? 수집한 글은 유지됩니다.",
            "변경은 다음 수집부터 반영됩니다. 수집한 글은 유지됩니다.",
            "구독이 없습니다. 추가를 눌러 RSS / Atom URL을 입력하세요.",
            "공백이나 인증 정보가 없는 HTTP(S) URL을 입력하세요 (2048자 이내).",
            "이미 등록된 구독입니다.",
            "이 구독이 변경되었습니다. 목록을 새로 고친 후 다시 시도하세요.",
            "다른 변경을 저장 중입니다. 다시 시도하세요.",
            "이전", "다음",
        ),
    ).joinToString(" ") { "'" + it.replace("'", "'\"'\"'") + "'" }
    val quotedTitle = "'" + rssFeedsLabel(t).replace("'", "'\"'\"'") + "'"
    return """
manage_feeds() (
  mkdir -p "${d}DIR" || exit 1
  feed_work=${d}(mktemp -d "${d}DIR/.feeds-XXXXXXXX") || exit 1
  trap 'rm -rf "${d}feed_work"' 0
  python3 - "${d}FEEDS" "${d}feed_work" $labels "${d}@" <<'Z2RSS_FEEDS'
import fcntl, hashlib, html, json, os, sys, tempfile
from pathlib import Path
from urllib.parse import urlsplit

feed_file, work = map(Path, sys.argv[1:3])
(title, add, articles, change, remove, url_label, confirm, hint, empty,
 invalid, duplicate, stale, busy, previous, next_label) = sys.argv[3:18]
command, *args = sys.argv[18:]

def fail(message):
    print(message, file=sys.stderr)
    sys.exit(1)

def identity(url):
    # A stale page must never modify a different row after an insertion/deletion.
    return hashlib.sha256(url.encode("utf-8")).hexdigest()

def feed_url(line):
    value = line.strip()
    return value if value and not value.startswith("#") else None

def validate(value):
    if (not value or len(value) > 2048
            or any(c.isspace() or ord(c) < 32 or ord(c) == 127 for c in value)):
        fail(invalid)
    try:
        parsed = urlsplit(value)
        if (parsed.scheme not in ("http", "https") or not parsed.hostname
                or parsed.username is not None or parsed.password is not None
                or "\\" in parsed.netloc):
            fail(invalid)
        parsed.port  # Reject invalid port syntax.
    except ValueError:
        fail(invalid)
    # The polling script recognizes lowercase http(s).
    return parsed.scheme + value[value.index(":"):]

def read_lines():
    try:
        return feed_file.read_text(encoding="utf-8").splitlines(keepends=True)
    except FileNotFoundError:
        return []

page = 0
if command == "feeds":
    if len(args) > 1 or (args and (not args[0].isascii() or not args[0].isdigit() or len(args[0]) > 8)):
        fail(invalid)
    page = int(args[0]) if args else 0
    lines = read_lines()
else:
    expected = {"feed-add": 1, "feed-edit": 2, "feed-remove": 1}
    if command not in expected or len(args) != expected[command]:
        fail(invalid)
    # Serialize writers without blocking a viewer action behind a hung process.
    with open(feed_file.parent / ".feeds.lock", "a") as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            fail(busy)
        lines = read_lines()
        urls = [u for line in lines if (u := feed_url(line))]
        old = None
        if command != "feed-add":
            old = next((u for u in urls if identity(u) == args[0]), None)
            if old is None:
                fail(stale)
        new = None if command == "feed-remove" else validate(args[-1])
        if new is not None and new != old and new in urls:
            fail(duplicate)
        if command == "feed-add":
            if lines and not lines[-1].endswith("\n"):
                lines[-1] += "\n"
            lines.append(new + "\n")
        else:
            # Keep comments, blank lines and unrelated subscriptions.
            updated = []
            replaced = False
            for line in lines:
                if feed_url(line) != old:
                    updated.append(line)
                elif new is not None and not replaced:
                    updated.append(new + "\n")
                    replaced = True
            lines = updated
        fd, pending = tempfile.mkstemp(dir=feed_file.parent, prefix=".feeds-save-")
        try:
            with os.fdopen(fd, "w", encoding="utf-8") as out:
                out.writelines(lines)
                out.flush()
                os.fsync(out.fileno())
            os.replace(pending, feed_file)
        finally:
            if os.path.exists(pending):
                os.unlink(pending)

urls = list(dict.fromkeys(u for line in lines if (u := feed_url(line))))
# Bound the controls payload even for long non-ASCII URLs.
page_size = 20
page_count = max(1, (len(urls) + page_size - 1) // page_size)
page = min(page, page_count - 1)
actions = [
    {"id": "add", "label": add, "args": ["feed-add"], "toolbar": True,
     "fields": [{"id": "url", "label": url_label, "type": "text", "required": True}]},
    {"id": "articles", "label": articles, "args": ["view"], "toolbar": True},
]
def link(action_id, label):
    return '<a href="z2-action:' + action_id + '">' + html.escape(label) + '</a>'

style = ('<style>'
    ':root{color-scheme:light dark}'
    'body{margin:0;background:var(--z2-bg,#fff);color:var(--z2-fg,#111);'
    'font:16px/1.55 sans-serif}'
    'header,main{padding:14px 16px}header{border-bottom:1px solid var(--z2-line,#ddd)}'
    'h1{font-size:18px;margin:0}header p{margin:6px 0 0;color:var(--z2-dim,#666);font-size:14px}'
    'ul{padding:0;margin:0;list-style:none}li{padding:12px 0;border-bottom:1px solid var(--z2-line,#ddd)}'
    'p.url{overflow-wrap:anywhere;margin:0 0 8px}'
    'nav{display:flex;gap:20px;align-items:center;flex-wrap:wrap}'
    'a{color:var(--z2-accent,#087d40);padding:8px 0}'
    '.pages{margin-top:18px}'
'</style>')
out = ['<!doctype html><html><head><meta charset="utf-8">',
       '<meta name="viewport" content="width=device-width,initial-scale=1">',
       '<title>' + html.escape(title) + '</title>', style, '</head><body><header><h1>',
       html.escape(title) + ' (' + str(len(urls)) + ')</h1><p>' + html.escape(hint),
       '</p></header><main>']
if not urls:
    out.append('<p>' + html.escape(empty) + '</p>')
out.append('<ul>')
for i, url in enumerate(urls[page * page_size:(page + 1) * page_size]):
    edit_id, remove_id = "edit-" + str(i), "remove-" + str(i)
    key = identity(url)
    actions.extend([
        {"id": edit_id, "label": change, "args": ["feed-edit", key],
         "fields": [{"id": "url", "label": url_label, "type": "text",
                     "default": url[:2048], "required": True}]},
        {"id": remove_id, "label": remove, "args": ["feed-remove", key], "confirm": confirm},
    ])
    out.append('<li><p class="url">' + html.escape(url) + '</p><nav>'
               + link(edit_id, change) + link(remove_id, remove) + '</nav></li>')
out.append('</ul><nav class="pages">')
for action_id, label, target in (("previous", previous, page - 1), ("next", next_label, page + 1)):
    if 0 <= target < page_count:
        actions.append({"id": action_id, "label": label, "args": ["feeds", str(target)]})
        out.append(link(action_id, label))
out.append('<span>' + str(page + 1) + ' / ' + str(page_count) + '</span></nav></main></body></html>')
(work / "page.html").write_text("\n".join(out), encoding="utf-8")
(work / "controls.json").write_text(json.dumps({
    "handler": "rss.sh", "refresh": ["feeds", str(page)], "actions": actions,
}, ensure_ascii=False), encoding="utf-8")
Z2RSS_FEEDS
  [ ${d}? -eq 0 ] || exit 1
  z2-view --controls "${d}feed_work/controls.json" "${d}feed_work/page.html" $quotedTitle
)
""".trimIndent()
}
