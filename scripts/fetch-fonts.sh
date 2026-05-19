#!/usr/bin/env bash
# プログラミング向けフォント (Regular weight) を app/src/main/assets/fonts/ に
# 配置するスクリプト。
#
# 配置するフォントは TerminalFontOptions.kt と対応:
#   - IBMPlexMono-Regular.ttf
#   - JetBrainsMono-Regular.ttf
#   - FiraCode-Regular.ttf
#
# 使い方:
#   bash scripts/fetch-fonts.sh             # 全部
#   FORCE=1 bash scripts/fetch-fonts.sh     # 既存ファイルを上書き

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEST="${PROJECT_ROOT}/app/src/main/assets/fonts"
FORCE="${FORCE:-0}"

mkdir -p "${DEST}"

fetch() {
    local out_name="$1" url="$2"
    local out="${DEST}/${out_name}"
    if [[ -f "${out}" && "${FORCE}" != "1" ]]; then
        echo "[skip] ${out_name} (FORCE=1 で再取得)"
        return 0
    fi
    echo "[info] fetching ${out_name} <- ${url}"
    curl -sL --fail -o "${out}" "${url}" || {
        echo "ERROR: 取得失敗 ${url}" >&2
        rm -f "${out}"
        return 1
    }
    # 簡易検証 (TTF / OTF magic number)
    local magic
    magic="$(head -c 4 "${out}" | od -An -tx1 | tr -d ' \n')"
    case "${magic}" in
        00010000|4f54544f|74727565|7474636f)
            : # OK: TTF / OpenType / TrueType collection
            ;;
        *)
            echo "ERROR: ${out_name} not a valid font (magic=${magic})" >&2
            rm -f "${out}"
            return 1
            ;;
    esac
    local sz
    sz="$(stat -c %s "${out}")"
    echo "       -> $(numfmt --to=iec-i --suffix=B "${sz}")"
}

# IBM Plex Mono (Regular) — 2024 リポジトリ再編後の packages/plex-mono パス
fetch "IBMPlexMono-Regular.ttf" \
    "https://github.com/IBM/plex/raw/master/packages/plex-mono/fonts/complete/ttf/IBMPlexMono-Regular.ttf"

# JetBrains Mono (Regular)
fetch "JetBrainsMono-Regular.ttf" \
    "https://github.com/JetBrains/JetBrainsMono/raw/master/fonts/ttf/JetBrainsMono-Regular.ttf"

# Fira Code (Regular) — リポジトリ master からは取得不可、release zip 経由で抽出
fetch_fira_code() {
    local out="${DEST}/FiraCode-Regular.ttf"
    if [[ -f "${out}" && "${FORCE}" != "1" ]]; then
        echo "[skip] FiraCode-Regular.ttf (FORCE=1 で再取得)"
        return 0
    fi
    local tmp
    tmp="$(mktemp -d)"
    local zip="${tmp}/firacode.zip"
    local latest_url
    latest_url="$(curl -sL --max-time 10 \
        "https://api.github.com/repos/tonsky/FiraCode/releases/latest" \
        | grep -oE '"browser_download_url":[[:space:]]*"[^"]+\.zip"' \
        | sed -E 's/.*"([^"]+)".*/\1/' | head -n1)"
    if [[ -z "${latest_url}" ]]; then
        echo "ERROR: Fira Code release URL 取得失敗" >&2
        rm -rf "${tmp}"; return 1
    fi
    echo "[info] fetching FiraCode-Regular.ttf <- ${latest_url}"
    curl -sL --fail -o "${zip}" "${latest_url}" || {
        echo "ERROR: zip 取得失敗" >&2; rm -rf "${tmp}"; return 1
    }
    (cd "${tmp}" && unzip -q "${zip}")
    local found
    found="$(find "${tmp}" -name 'FiraCode-Regular.ttf' -type f | head -n1)"
    [[ -n "${found}" ]] || {
        echo "ERROR: zip 内に FiraCode-Regular.ttf 見つからず" >&2
        rm -rf "${tmp}"; return 1
    }
    cp "${found}" "${out}"
    rm -rf "${tmp}"
    local sz
    sz="$(stat -c %s "${out}")"
    echo "       -> $(numfmt --to=iec-i --suffix=B "${sz}")"
}
fetch_fira_code

echo ""
echo "[done] フォント:"
ls -lah "${DEST}"/*.ttf 2>/dev/null
