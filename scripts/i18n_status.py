#!/usr/bin/env python3
"""多言語化の埋まり具合を数える（入口は scripts/i18n-status.sh）。

⚠ **対応言語の名簿を二重に持たない。** 名簿は AppLanguages.kt が正本で、ここはそれを
読み取る。ここに一覧を書くと、言語を増やしたとき片方だけ古くなる。

⭐ `--check` は「端末に出る文言を訳しきった」印 (AppLanguages の `cliComplete`) が付いた
言語だけを検査し、欠けていれば終了コード 1 で落ちる。CI ではユニットテスト
(CliTranslationCheckTest) がこれを呼ぶので、訳を足さずに新しい文言を書くと落ちる。

端末に出る文言の数え方 (1.0.84 から):
  英語と日本語は文言を使う場所に、それ以外の言語は proot/lang/Cli<言語>.kt に置く。
  - スクリプトの文言 … t("名前", en = …) / t.lines("名前", …) / t.of("名前", …) の名前と、
    言語ファイルの texts / lineSets / values に置いた名前を突き合わせる
  - z2-* のメッセージ集 … Z2ApiMessages.kt の `open val|fun` と、言語ファイルで
    それを上書きした `override val|fun`
  - GUI / sshd の組・声で操作の言葉の表 … 言語ファイルの `override fun gui()` /
    `override fun sshd()` / `override val voiceWords`
"""
from __future__ import annotations

import argparse
import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app/src/main/res"
PROOT = ROOT / "app/src/main/java/com/zerotoship/z2term/proot"
LANG_DIR = PROOT / "lang"
API_FILE = PROOT / "Z2ApiMessages.kt"
ROSTER = ROOT / "app/src/main/java/com/zerotoship/z2term/settings/AppLanguages.kt"

# 言語ごとの組 (英語・日本語は使う側のファイル、他は言語ファイルの関数・値)。名前 -> 英語の置き場
BUNDLES = {
    "gui": "GuiScript.kt の GuiScriptStrings.en()",
    "sshd": "SshdScript.kt の SshdScriptStrings.en()",
    "voiceWords": "Z2VoiceMacro.kt の VOICE_WORDS_EN (言い方の表。訳ではなくその言語の言い方を並べる)",
}
# 名前で引く文言の置き場 (CliText の呼び方 → 言語ファイルの欄)
KIND_FIELD = {"": "texts", "lines": "lineSets", "of": "values"}


def roster() -> list[tuple[str, bool]]:
    """AppLanguages.ALL を (言語コード, CLI 完訳の印) の並びで読む。

    印は `Entry("zh-CN", "简体中文", cliComplete = true)` の形。⚠ 引数の中に丸括弧を
    書くとこの読み取りが壊れる（名簿は文字列と真偽値だけにしておくこと）。
    """
    text = ROSTER.read_text(encoding="utf-8")
    block = re.search(r"val ALL: List<Entry> = listOf\((.*?)\n    \)", text, re.S)
    if not block:
        sys.exit("AppLanguages.ALL を読めない。名簿の書き方を変えたなら、この正規表現も直すこと。")
    out = []
    for args in re.findall(r"Entry\((.*?)\)", block.group(1), re.S):
        code = re.search(r'"([^"]+)"', args)
        if code:
            out.append((code.group(1), re.search(r"cliComplete\s*=\s*true", args) is not None))
    return out


# 名簿の言語コード -> res のフォルダ名に使う古いコード (Java / Android の決まり)
LEGACY_RES = {"id": "in", "he": "iw", "yi": "ji"}


def res_dir(lang: str) -> Path:
    """言語コードから res のディレクトリ名。`zh-CN` は Android 流に `values-zh-rCN`。"""
    if lang == "en":
        return RES / "values"          # 既定 (values/) が英語
    # ⚠ Android の res は古い言語コードで書く (インドネシア語は values-in。values-id は古い端末で引けない)
    lang = LEGACY_RES.get(lang, lang)
    if "-" in lang:
        base, region = lang.split("-", 1)
        return RES / f"values-{base}-r{region}"
    return RES / f"values-{lang}"


def read_strings(path: Path) -> dict[str, str]:
    f = path / "strings.xml"
    if not f.exists():
        return {}
    root = ET.parse(f).getroot()
    out = {}
    for el in root:
        name = el.get("name")
        if name is None:
            continue
        if el.tag == "string":
            if el.get("translatable") == "false":
                continue
            out[name] = "".join(el.itertext())
        elif el.tag in ("plurals", "string-array"):
            out[name] = f"<{el.tag}>"
    return out


# --- Kotlin を読む小道具 -------------------------------------------------------
#
# ⛔ **文字列とコメントを飛ばさないと、説明の注記や文言の中身まで数えてしまう。**
# 実際、以前は `// 3 言語目は t(en = …, ja = …) の後ろへ` のような注記を 8 件多く数えていた
# (訳しても 100% に届かない = --check が永遠に通らない)。raw string の中の `${…}` に
# 入れ子の文字列があっても崩れないよう、テンプレートの括弧まで追う。

def _skip_string(src: str, i: int) -> int:
    raw = src.startswith('"""', i)
    j, n = i + (3 if raw else 1), len(src)
    while j < n:
        if raw:
            if src.startswith('"""', j):
                k = j + 3
                while k < n and src[k] == '"':
                    k += 1
                return k
        else:
            if src[j] == "\\":
                j += 2
                continue
            if src[j] == '"':
                return j + 1
        if src.startswith("${", j):
            j = _skip_group(src, j + 1)
            continue
        j += 1
    return n


def _skip_char(src: str, i: int) -> int:
    j = i + 1
    if j < len(src) and src[j] == "\\":
        j += 2
        while j < len(src) and src[j] != "'":
            j += 1
        return j + 1
    return j + 2


def _skip_group(src: str, i: int) -> int:
    """src[i] が開き括弧のとき、対応する閉じ括弧の次の位置。"""
    depth, j, n = 0, i, len(src)
    while j < n:
        c = src[j]
        if c == '"':
            j = _skip_string(src, j)
            continue
        if c == "'":
            j = _skip_char(src, j)
            continue
        if src.startswith("//", j):
            j = src.find("\n", j)
            j = n if j == -1 else j
            continue
        if src.startswith("/*", j):
            e = src.find("*/", j + 2)
            j = n if e == -1 else e + 2
            continue
        if c in "([{":
            depth += 1
        elif c in ")]}":
            depth -= 1
            if depth == 0:
                return j + 1
        j += 1
    return n


def code_positions(src: str, start: int = 0, end: int | None = None, out: set[int] | None = None) -> set[int]:
    """文字列とコメントの外にある位置。⚠ 文字列の中の `${…}` の中身はコードとして数える
    (`"…${t("doctor.title", en = …)}…"` のように文言を埋め込んでいる箇所があるため)。"""
    out = set() if out is None else out
    i, n = start, len(src) if end is None else end
    while i < n:
        c = src[i]
        if c == '"':
            j = _skip_string(src, i)
            k = i
            while True:
                k = src.find("${", k, j)
                if k == -1:
                    break
                e = _skip_group(src, k + 1)
                code_positions(src, k + 2, e - 1, out)
                k = e
            i = j
            continue
        if c == "'":
            i = _skip_char(src, i)
            continue
        if src.startswith("//", i):
            i = src.find("\n", i)
            i = n if i == -1 else i
            continue
        if src.startswith("/*", i):
            e = src.find("*/", i + 2)
            i = n if e == -1 else e + 2
            continue
        out.add(i)
        i += 1
    return out


def line_of(src: str, pos: int) -> int:
    return src.count("\n", 0, pos) + 1


# --- 文言を使う側 (英語・日本語) ------------------------------------------------

KEYED_CALL = re.compile(r'\bt(?:\.(lines|of))?\(\s*"([^"]+)"\s*,')
UNKEYED_CALL = re.compile(r'\bt(?:\.lines|\.of)?\(\s*en\s*=')
EN_ARG = re.compile(r"\ben\s*=\s*")
OPEN_MEMBER = re.compile(r"^    open (?:val|fun) ([A-Za-z_][A-Za-z0-9_]*)", re.M)


class Source:
    """文言を使う側から読んだもの。"""

    def __init__(self) -> None:
        self.keys: dict[str, tuple[str, str, int, str]] = {}   # 名前 -> (欄, ファイル, 行, 英語の原文)
        self.problems: list[str] = []
        for f in sorted(PROOT.glob("*.kt")):
            src = f.read_text(encoding="utf-8")
            pos = code_positions(src)
            for m in KEYED_CALL.finditer(src):
                if m.start() not in pos or (m.start() > 0 and src[m.start() - 1] == "."):
                    continue
                key, field = m.group(2), KIND_FIELD[m.group(1) or ""]
                where = f"{f.name}:{line_of(src, m.start())}"
                if key in self.keys:
                    self.problems.append(f"名前が重なっている: {key} ({self.keys[key][1]}:{self.keys[key][2]} と {where})")
                    continue
                self.keys[key] = (field, f.name, line_of(src, m.start()), self._english(src, m.end()))
            # Z2ApiMsg は英語・日本語だけを選ぶ t(en, ja) を持つ (他の言語は上書きで持つ) ので除く
            for m in ([] if f == API_FILE else UNKEYED_CALL.finditer(src)):
                if m.start() in pos and not (m.start() > 0 and src[m.start() - 1] == "."):
                    self.problems.append(
                        f"名前の無い t(en = …) がある: {f.name}:{line_of(src, m.start())} "
                        '(t("<名前>", en = …, ja = …) と書く)'
                    )
        api = API_FILE.read_text(encoding="utf-8")
        api_pos = code_positions(api)
        self.api = [m.group(1) for m in OPEN_MEMBER.finditer(api) if m.start() in api_pos]
        self.api_en = {m.group(1): self._english(api, m.end()) for m in OPEN_MEMBER.finditer(api)}

    @staticmethod
    def _english(src: str, start: int) -> str:
        """呼び出しの中の `en = …` の式 (原文のまま)。"""
        m = EN_ARG.search(src, start)
        if not m:
            return ""
        j = m.end()
        depth, n = 0, len(src)
        k = j
        while k < n:
            c = src[k]
            if c == '"':
                k = _skip_string(src, k)
                continue
            if c in "([{":
                depth += 1
            elif c in ")]}":
                if depth == 0:
                    break
                depth -= 1
            elif c == "," and depth == 0:
                break
            k += 1
        return src[j:k].strip()

    @property
    def total(self) -> int:
        return len(self.keys) + len(self.api) + len(BUNDLES)


# --- 言語ファイル (英語・日本語以外) ---------------------------------------------

MAP_FIELD = re.compile(r"override val (texts|lineSets|values)\b[^\n]*")
MAP_KEY = re.compile(r'"([^"]+)"\s+to\b')
OVERRIDE_MEMBER = re.compile(r"^    override (?:val|fun) ([A-Za-z_][A-Za-z0-9_]*)", re.M)
CODE_DECL = re.compile(r'override val code: String = "([^"]+)"')


def lang_files() -> dict[str, Path]:
    """言語コード -> 言語ファイル。"""
    out = {}
    for f in sorted(LANG_DIR.glob("*.kt")):
        m = CODE_DECL.search(f.read_text(encoding="utf-8"))
        if m:
            out[m.group(1)] = f
    return out


class Translation:
    """1 言語ぶんの言語ファイルから読んだもの。"""

    def __init__(self, path: Path | None) -> None:
        self.path = path
        self.keys: dict[str, str] = {}      # 名前 -> 欄
        self.api: set[str] = set()
        self.bundles: set[str] = set()
        self.problems: list[str] = []
        if path is None:
            return
        src = path.read_text(encoding="utf-8")
        pos = code_positions(src)
        for m in MAP_FIELD.finditer(src):
            if m.start() not in pos:
                continue
            field = m.group(1)
            open_ = src.find("mapOf(", m.end())
            if open_ == -1:
                continue
            open_ += len("mapOf")
            close = _skip_group(src, open_)
            for a, b in self._entries(src, open_ + 1, close - 1):
                km = MAP_KEY.match(src[a:b].strip())
                if not km:
                    self.problems.append(f"{path.name}:{line_of(src, a)}: \"名前\" to … の形でない項目がある")
                    continue
                key = km.group(1)
                if key in self.keys:
                    self.problems.append(f"{path.name}: 同じ名前が 2 回ある: {key}")
                self.keys[key] = field
        cls = re.search(r"class \w+\([^)]*\)\s*:\s*Z2ApiMsg\(", src)
        if cls:
            body_open = src.find("{", cls.end())
            body_close = _skip_group(src, body_open)
            for om in OVERRIDE_MEMBER.finditer(src, body_open, body_close):
                if om.start() in pos:
                    self.api.add(om.group(1))
        for b in BUNDLES:
            if re.search(rf"override (?:fun {b}\(\)|val {b}\b)", src):
                self.bundles.add(b)

    @staticmethod
    def _entries(src: str, start: int, end: int) -> list[tuple[int, int]]:
        """mapOf( … ) の中身を最上位のカンマで区切る (入れ子の listOf や文字列の中は区切らない)。"""
        out, depth, j, seg = [], 0, start, start
        while j < end:
            c = src[j]
            if c == '"':
                j = _skip_string(src, j)
                continue
            if c == "'":
                j = _skip_char(src, j)
                continue
            if src.startswith("//", j):
                j = src.find("\n", j)
                continue
            if src.startswith("/*", j):
                j = src.find("*/", j + 2) + 2
                continue
            if c in "([{":
                depth += 1
            elif c in ")]}":
                depth -= 1
            elif c == "," and depth == 0:
                out.append((seg, j))
                seg = j + 1
            j += 1
        if src[seg:end].strip():
            out.append((seg, end))
        return out


def count_cli(lang: str, source: Source, files: dict[str, Path]) -> tuple[int, int, list[str], list[str]]:
    """(訳のある数, 文言の総数, 未訳の一覧, 問題の一覧) を返す。"""
    total = source.total
    if lang in ("en", "ja"):
        return total, total, [], []            # 英語・日本語は使う場所に必ず書いてある
    tr = Translation(files.get(lang))
    missing = []
    for key, (field, *_rest) in source.keys.items():
        if tr.keys.get(key) != field:
            missing.append(key)
    missing += [f"Z2ApiMsg.{n}" for n in source.api if n not in tr.api]
    missing += [f"{b}()" for b in BUNDLES if b not in tr.bundles]
    problems = list(tr.problems)
    stale = [k for k in tr.keys if k not in source.keys]
    for k in stale:
        problems.append(f"{lang}: 使われていない名前 (文言を消したか名前を変えた): {k}")
    for k, field in tr.keys.items():
        if k in source.keys and source.keys[k][0] != field:
            problems.append(f"{lang}: {k} は {source.keys[k][0]} に置くこと (いまは {field})")
    for n in sorted(tr.api - set(source.api)):
        problems.append(f"{lang}: Z2ApiMsg に無い (または open でない) 文言を上書きしている: {n}")
    return total - len(missing), total, missing, problems


def pct(have: int, total: int) -> str:
    return "  --  " if total == 0 else f"{have * 100 // total:3d}%  "


def show_table(langs: list[tuple[str, bool]]) -> None:
    base = read_strings(res_dir("en"))
    source, files = Source(), lang_files()
    codes = ", ".join(c for c, _ in langs)
    print(f"対応言語: {codes}    (名簿: {ROSTER.relative_to(ROOT)})\n")
    print(f"{'言語':<8} {'アプリ画面 (res)':<24} {'端末に出る文言 (CLI)':<22} 印")
    print("-" * 64)
    problems = list(source.problems)
    for lang, cli_complete in langs:
        got = read_strings(res_dir(lang))
        r_have = len([k for k in base if k in got])
        c_have, c_total, _, probs = count_cli(lang, source, files)
        problems += probs
        print(
            f"{lang:<8} {pct(r_have, len(base))}{r_have:>5}/{len(base):<5}        "
            f"{pct(c_have, c_total)}{c_have:>5}/{c_total:<5}  "
            f"{'✔' if cli_complete else '-'}"
        )
    print()
    print("未訳のキーを出す:  bash scripts/i18n-status.sh --missing <言語>")
    print("⛔ 未訳のまま出しても壊れない（英語が出る）。⚠ 日本語には落ちない。")
    print()
    print("印 (✔) = 端末に出る文言を訳しきったという宣言 (AppLanguages の cliComplete)。")
    print("  印のある言語は `--check` が 100% を要求する ⇒ 訳を足さずに文言を増やすと落ちる。")
    if problems:
        print("\n⚠ 直すところ (--check で落ちる):")
        for p in problems:
            print(f"  - {p}")


def run_check(langs: list[tuple[str, bool]]) -> int:
    """印の付いた言語の CLI 文言が 100% かを検査する。落ちるなら 1 を返す。

    ⚠ **res はここで見ない。** そちらは lint の MissingTranslation が error で守っている
    （二重に判断すると、どちらの言い分を直せばよいのか分からなくなる）。
    ⚠ 名前の重なり・使われていない名前は、印の有無にかかわらず落とす
    （どちらも訳が黙って外れる原因になる）。
    """
    source, files = Source(), lang_files()
    problems = list(source.problems)
    marked = [c for c, done in langs if done]

    failed = []
    for lang in marked:
        have, total, _, probs = count_cli(lang, source, files)
        problems += probs
        status = "OK" if have == total else f"未訳 {total - have} 件"
        print(f"  {lang:<8} 端末に出る文言 {have}/{total}   {status}")
        if have < total:
            failed.append((lang, total - have))

    # 印は無いが 100% に届いた言語は、印を付ければ以後守られる。落とさずに知らせるだけ。
    for lang, done in langs:
        if not done:
            have, total, _, probs = count_cli(lang, source, files)
            problems += probs
            if total and have == total:
                print(f"  {lang:<8} 端末に出る文言 {have}/{total}   "
                      f"⭐ 100% です。AppLanguages の {lang} に cliComplete = true を付けられます")

    if problems:
        print("\n⛔ 直すところがあります:")
        for p in problems:
            print(f"  - {p}")
    if not failed and not problems:
        print("\n✅ 印の付いた言語はすべて端末に出る文言まで訳せています。")
        return 0
    if failed:
        print("\n⛔ 端末に出る文言の未訳があります:")
        for lang, missing in failed:
            print(f"  - {lang}: {missing} 件")
        print(
            "\n直し方は 2 つのどちらか:\n"
            "  a) 訳を足す … bash scripts/i18n-status.sh --missing <言語> で残りと英語の原文を出し、\n"
            "     その言語のファイル (proot/lang/Cli<言語>.kt) に同じ名前で足す (CliLanguage.kt 参照)\n"
            "  b) 印を外す … その言語を完訳として出さないと決めたなら、AppLanguages.ALL の\n"
            "     cliComplete を false にする (未訳の文言は英語で出る)\n"
            "⛔ 印を付けたまま未訳を残さないこと。画面はその言語なのに z2-* だけ英語、になる。"
        )
    return 1


def show_missing(lang: str, as_xml: bool) -> None:
    base = read_strings(res_dir("en"))
    got = read_strings(res_dir(lang))
    missing = [k for k in base if k not in got]
    if not missing:
        print(f"{lang}: res の未訳はありません。")
    elif as_xml:
        print(f'<!-- {lang}: 未訳 {len(missing)} 件。訳して values-… /strings.xml へ移すこと -->')
        for k in missing:
            body = base[k].replace("&", "&amp;").replace("<", "&lt;")
            print(f'    <string name="{k}">{body}</string>')
    else:
        print(f"{lang}: res の未訳 {len(missing)} 件 (英語の原文つき)\n")
        for k in missing:
            print(f"  {k}\n      {base[k]}")

    source, files = Source(), lang_files()
    c_have, c_total, c_missing, problems = count_cli(lang, source, files)
    if not c_missing:
        print(f"\n{lang}: 端末に出る文言の未訳はありません。")
    else:
        dest = files.get(lang)
        where = dest.relative_to(ROOT) if dest else f"proot/lang/Cli<言語>.kt (code = \"{lang}\") を作る"
        print(f"\n{lang}: 端末に出る文言の未訳 {len(c_missing)} 件 / 全 {c_total} 件。置き場: {where}\n")
        for key in c_missing:
            if key in source.keys:
                field, fname, line, en = source.keys[key]
                print(f"  [{field}] \"{key}\"   ({fname}:{line})\n      en = {en}\n")
            elif key.startswith("Z2ApiMsg."):
                name = key.split(".", 1)[1]
                print(f"  [Z2ApiMsg を上書き] {name}\n      en = {source.api_en.get(name, '')}\n")
            else:
                print(f"  [{key}] {BUNDLES[key.rstrip('()')]} を元に置く\n")
    for p in problems:
        print(f"  ⚠ {p}")


def main() -> None:
    ap = argparse.ArgumentParser(add_help=False)
    ap.add_argument("--missing", metavar="言語")
    ap.add_argument("--xml", action="store_true")
    ap.add_argument("--check", action="store_true")
    ap.add_argument("-h", "--help", action="store_true")
    args = ap.parse_args()
    if args.help:
        print(__doc__)
        return
    langs = roster()
    if args.check:
        sys.exit(run_check(langs))
    elif args.missing:
        if args.missing not in [c for c, _ in langs]:
            sys.exit(f"{args.missing} は名簿にありません。まず AppLanguages.ALL に足すこと。")
        show_missing(args.missing, args.xml)
    else:
        show_table(langs)


if __name__ == "__main__":
    main()
