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

# release zip 内から指定 TTF を抽出して配置する汎用関数。
# master に個別 weight の TTF を置かなくなったフォント (JetBrains Mono / Fira Code 等)
# はこちらで取得する。
#   $1 out_name : 配置するファイル名 (= zip 内で探す TTF 名)
#   $2 repo     : "owner/name" 形式の GitHub リポジトリ
fetch_from_release_zip() {
    local out_name="$1" repo="$2"
    local out="${DEST}/${out_name}"
    if [[ -f "${out}" && "${FORCE}" != "1" ]]; then
        echo "[skip] ${out_name} (FORCE=1 で再取得)"
        return 0
    fi
    local tmp
    tmp="$(mktemp -d)"
    local zip="${tmp}/font.zip"
    local latest_url
    # 未認証の api.github.com は 60 req/h・**IP 単位**。CI ランナーは IP を共有するため
    # ここは他人の消費で 403 になりやすい。GITHUB_TOKEN があれば付けて上限を上げる。
    local auth=()
    [[ -n "${GITHUB_TOKEN:-}" ]] && auth=(-H "Authorization: Bearer ${GITHUB_TOKEN}")
    # `set -o pipefail` 下では grep が 0 件ヒットしただけでこの代入が失敗し、スクリプトが
    # **何も出力せずに即死**する (実際に CI で「原因不明の exit 1」として現れた)。
    # `|| true` で握って、下の空チェック＝意味のあるエラーメッセージに到達させる。
    latest_url="$(curl -sL --max-time 15 "${auth[@]}" \
        "https://api.github.com/repos/${repo}/releases/latest" \
        | grep -oE '"browser_download_url":[[:space:]]*"[^"]+\.zip"' \
        | sed -E 's/.*"([^"]+)".*/\1/' | head -n1 || true)"
    if [[ -z "${latest_url}" ]]; then
        echo "ERROR: ${repo} の release zip URL 取得失敗 (API レート制限や応答形式の変化を疑う)" >&2
        rm -rf "${tmp}"; return 1
    fi
    echo "[info] fetching ${out_name} <- ${latest_url}"
    curl -sL --fail -o "${zip}" "${latest_url}" || {
        echo "ERROR: zip 取得失敗 ${latest_url}" >&2; rm -rf "${tmp}"; return 1
    }
    (cd "${tmp}" && unzip -q "${zip}")
    local found
    found="$(find "${tmp}" -name "${out_name}" -type f | head -n1)"
    [[ -n "${found}" ]] || {
        echo "ERROR: zip 内に ${out_name} 見つからず" >&2
        rm -rf "${tmp}"; return 1
    }
    cp "${found}" "${out}"
    rm -rf "${tmp}"
    local sz
    sz="$(stat -c %s "${out}")"
    echo "       -> $(numfmt --to=iec-i --suffix=B "${sz}")"
}

# JetBrains Mono (Regular) — variable font 化により master に個別 weight TTF が
# 無いため release zip から抽出
fetch_from_release_zip "JetBrainsMono-Regular.ttf" "JetBrains/JetBrainsMono"

# Fira Code (Regular) — リポジトリ master からは取得不可、release zip 経由で抽出
fetch_from_release_zip "FiraCode-Regular.ttf" "tonsky/FiraCode"

echo ""
echo "[done] フォント:"
ls -lah "${DEST}"/*.ttf 2>/dev/null
