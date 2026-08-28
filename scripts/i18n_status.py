#!/usr/bin/env python3
"""多言語化の埋まり具合を数える（入口は scripts/i18n-status.sh）。

⚠ **対応言語の名簿を二重に持たない。** 名簿は AppLanguages.kt が正本で、ここはそれを
読み取る。ここに一覧を書くと、言語を増やしたとき片方だけ古くなる。
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
ROSTER = ROOT / "app/src/main/java/com/zerotoship/z2term/settings/AppLanguages.kt"

# 端末に出る文言を持っているファイル。⚠ 増やしたらここにも足すこと
# （足し忘れても数が減るだけで、壊れはしない）。
CLI_FILES = [
    "Z2ApiMessages.kt", "Z2MacroScript.kt", "Z2DoctorScript.kt", "Z2ScanScript.kt",
    "Z2AdbScript.kt", "Z2HelpScript.kt", "Z2RunScript.kt", "PacmanKeyringScript.kt",
]
# ja()/en() の組で持っているもの（数え方が違うので別扱い）。
CLI_BUNDLES = ["GuiScript.kt", "SshdScript.kt"]


def roster() -> list[str]:
    """AppLanguages.ALL に並んでいる言語コードを読む。"""
    text = ROSTER.read_text(encoding="utf-8")
    block = re.search(r"val ALL: List<Entry> = listOf\((.*?)\n    \)", text, re.S)
    if not block:
        sys.exit("AppLanguages.ALL を読めない。名簿の書き方を変えたなら、この正規表現も直すこと。")
    return re.findall(r'Entry\("([^"]+)"', block.group(1))


def res_dir(lang: str) -> Path:
    """言語コードから res のディレクトリ名。`zh-CN` は Android 流に `values-zh-rCN`。"""
    if lang == "en":
        return RES / "values"          # 既定 (values/) が英語
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


def count_cli(lang: str) -> tuple[int, int]:
    """(その言語の変わり値がある数, 文言の総数) を返す。"""
    have = total = 0
    for name in CLI_FILES:
        src = (PROOT / name).read_text(encoding="utf-8")
        # t( / t.lines( / t.of( の呼び出しを 1 件と数える
        calls = len(re.findall(r"\bt(?:\.lines|\.of)?\(\s*\n?\s*en\s*=", src))
        total += calls
        if lang in ("en", "ja"):
            have += calls          # en/ja は名前つき引数なので必ず埋まっている
        else:
            have += len(re.findall(r'"' + re.escape(lang) + r'"\s+to\b', src))
    for name in CLI_BUNDLES:
        src = (PROOT / name).read_text(encoding="utf-8")
        total += 1
        if re.search(r'"' + re.escape(lang) + r'"\s+to\s+::', src):
            have += 1
    return have, total


def pct(have: int, total: int) -> str:
    return "  --  " if total == 0 else f"{have * 100 // total:3d}%  "


def show_table(langs: list[str]) -> None:
    base = read_strings(res_dir("en"))
    print(f"対応言語: {', '.join(langs)}    (名簿: {ROSTER.relative_to(ROOT)})\n")
    print(f"{'言語':<8} {'アプリ画面 (res)':<24} {'端末に出る文言 (CLI)':<24}")
    print("-" * 60)
    for lang in langs:
        got = read_strings(res_dir(lang))
        r_have = len([k for k in base if k in got])
        c_have, c_total = count_cli(lang)
        print(
            f"{lang:<8} {pct(r_have, len(base))}{r_have:>5}/{len(base):<5}        "
            f"{pct(c_have, c_total)}{c_have:>5}/{c_total:<5}"
        )
    print()
    print("未訳のキーを出す:  bash scripts/i18n-status.sh --missing <言語>")
    print("⛔ 未訳のまま出しても壊れない（英語が出る）。⚠ 日本語には落ちない。")


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

    c_have, c_total = count_cli(lang)
    if c_have < c_total:
        print(
            f"\n{lang}: 端末に出る文言の未訳 {c_total - c_have} 件 / 全 {c_total} 件。"
            f"\n  対象: {', '.join(CLI_FILES + CLI_BUNDLES)}"
            f'\n  埋め方: t(en = "…", ja = "…") の後ろへ "{lang}" to "…" を足す (CliText.kt 参照)。'
        )


def main() -> None:
    ap = argparse.ArgumentParser(add_help=False)
    ap.add_argument("--missing", metavar="言語")
    ap.add_argument("--xml", action="store_true")
    ap.add_argument("-h", "--help", action="store_true")
    args = ap.parse_args()
    if args.help:
        print(__doc__)
        return
    langs = roster()
    if args.missing:
        if args.missing not in langs:
            sys.exit(f"{args.missing} は名簿にありません。まず AppLanguages.ALL に足すこと。")
        show_missing(args.missing, args.xml)
    else:
        show_table(langs)


if __name__ == "__main__":
    main()
