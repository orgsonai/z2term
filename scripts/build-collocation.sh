#!/bin/sh
# 共起 (コロケーション) rerank 用データ生成 — Phase 4 / ギャップ G3。
#
# mozc の CollocationRewriter 相当。「内容語 × (助詞をはさんだ直後の内容語)」の 2-gram を
# 日本語 Wikipedia から頻度カット抽出し、ExistenceFilter (Bloom フィルタ) バイナリにして
# app/src/main/assets/kkc_colloc.bloom を生成する。Kotlin 側 (CollocationReranker) が
# 同じ FNV-1a ハッシュで参照する。
#
# 形態素解析器 (mecab) を使わず、表層の文字種だけで内容語核を切り出す近似:
#   - 内容語核 = 漢字/カタカナ/々/ー の連続
#   - glue     = ひらがなの連続 (助詞・送り仮名・助動詞)。1〜4 文字までを「同一局所」とみなす
#   - それ以外 (約物/英数/空白) は文節境界として連鎖を断つ
# これにより「水を飲(む)」→ (水,飲)、「本を読(む)」→ (本,読) のような共起が取れる。
# 漢字核は送り仮名を落とした「核」で記録し、reranker 側も同じ核へ正規化して引く。
#
# 全量 (4.6GB) は端末資源的に重いので、bz2 をストリーム展開して先頭 CHAR_LIMIT 文字
# だけ食べる「部分ストリーム抽出」。再現性のためパラメータは環境変数で上書き可能。
#
# 使い方:  sh scripts/build-collocation.sh
# 主な環境変数:
#   DUMP_URL    : 入力ダンプ (既定 jawiki latest pages-articles)
#   CHAR_LIMIT  : 取り込む最大文字数 (既定 1.2 億 ≈ 360MB utf8)
#   MIN_COUNT   : ペア頻度カット閾値 (既定 4)
#   FP_RATE     : Bloom の目標誤検出率 (既定 0.02)
#   OUT         : 出力 (既定 app/src/main/assets/kkc_colloc.bloom)
set -eu

DUMP_URL="${DUMP_URL:-https://dumps.wikimedia.org/jawiki/latest/jawiki-latest-pages-articles.xml.bz2}"
CHAR_LIMIT="${CHAR_LIMIT:-120000000}"
MIN_COUNT="${MIN_COUNT:-4}"
FP_RATE="${FP_RATE:-0.02}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
OUT="${OUT:-$SCRIPT_DIR/../app/src/main/assets/kkc_colloc.bloom}"

echo "[build-collocation] dump=$DUMP_URL"
echo "[build-collocation] char_limit=$CHAR_LIMIT min_count=$MIN_COUNT fp_rate=$FP_RATE"
echo "[build-collocation] out=$OUT"

CHAR_LIMIT="$CHAR_LIMIT" MIN_COUNT="$MIN_COUNT" FP_RATE="$FP_RATE" OUT="$OUT" \
  sh -c 'curl -sL "$0" | bzcat | python3 "$1"' "$DUMP_URL" "$SCRIPT_DIR/build_collocation.py"

echo "[build-collocation] done."
