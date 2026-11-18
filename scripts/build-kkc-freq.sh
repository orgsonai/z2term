#!/bin/sh
# かな漢字変換の頻度補正データ app/src/main/assets/kkc_freq.tsv と、
# 文の中で使う地名・人名 app/src/main/assets/kkc_names.tsv、
# 打ちかけの補完に使う語の表 app/src/main/assets/kkc_predict.tsv、
# 確定した語の次に出す語の表 app/src/main/assets/kkc_next.tsv を作る。
#
# 日本語版ウィキペディアの文章を先頭から CHAR_LIMIT 文字だけ取り込み、MeCab (IPADIC) で単語に
# 分けて数え、同じ読み・同じ品詞の語のうち少ないほうを後ろへ回す補正を出す。
# 決め方の中身は scripts/build_kkc_freq.py の先頭に書いてある。
#
# 使い方:  sh scripts/build-kkc-freq.sh
# 主な環境変数:
#   DUMP_URL   : 入力ダンプ (既定 jawiki latest pages-articles。全体は落とさず、途中で打ち切る)
#   CHAR_LIMIT : 取り込む文字数 (既定 6000 万)
#   WORK       : 作業用の置き場 (既定 /tmp/z2-kkc-freq。文章と venv を置く。終わったら消してよい)
#   WORKERS    : 数えるときの並列数 (既定 コア数の半分)
#   TEXT       : 取り込み済みの文章ファイル。指定するとダウンロードを飛ばす
#   OUT        : 頻度補正の出力 (既定 app/src/main/assets/kkc_freq.tsv)
#   OUT_NAMES  : 地名・人名の出力 (既定 app/src/main/assets/kkc_names.tsv)
#   OUT_PREDICT: 補完用の語の表の出力 (既定 app/src/main/assets/kkc_predict.tsv)
#   OUT_NEXT   : 次の語の表の出力 (既定 app/src/main/assets/kkc_next.tsv)
#
# 道具: python3・curl・bzcat。mecab-python3 と ipadic は WORK の venv へ pip で入れる (アプリには入らない)。
# ⚠ ダンプは日々変わるので、作り直すと数字は少し変わる。
set -eu

DUMP_URL="${DUMP_URL:-https://dumps.wikimedia.org/jawiki/latest/jawiki-latest-pages-articles.xml.bz2}"
CHAR_LIMIT="${CHAR_LIMIT:-60000000}"
WORK="${WORK:-/tmp/z2-kkc-freq}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ASSETS="$SCRIPT_DIR/../app/src/main/assets"
OUT="${OUT:-$ASSETS/kkc_freq.tsv}"
OUT_NAMES="${OUT_NAMES:-$ASSETS/kkc_names.tsv}"
OUT_PREDICT="${OUT_PREDICT:-$ASSETS/kkc_predict.tsv}"
OUT_NEXT="${OUT_NEXT:-$ASSETS/kkc_next.tsv}"
WORKERS="${WORKERS:-$(( $(nproc) / 2 ))}"
[ "$WORKERS" -ge 1 ] || WORKERS=1
PY="$SCRIPT_DIR/build_kkc_freq.py"

mkdir -p "$WORK"
TEXT="${TEXT:-$WORK/text.txt}"
if [ ! -s "$TEXT" ]; then
    echo "[build-kkc-freq] 文章を取り込みます: $DUMP_URL ($CHAR_LIMIT 文字まで)"
    # 上限に達すると python が先に終わり、curl と bzcat は書き込み先を失って止まる (正常)。
    curl -sL "$DUMP_URL" | bzcat 2>/dev/null | python3 "$PY" extract "$CHAR_LIMIT" "$TEXT" || true
    [ -s "$TEXT" ] || { echo "文章を取り込めませんでした" >&2; exit 1; }
fi

if [ ! -x "$WORK/venv/bin/python" ]; then
    python3 -m venv "$WORK/venv"
    "$WORK/venv/bin/pip" install -q mecab-python3 ipadic
fi

echo "[build-kkc-freq] 数えます (並列 $WORKERS)"
"$WORK/venv/bin/python" "$PY" count "$TEXT" "$WORK/count.tsv" "$WORKERS" "$WORK/names.tsv" "$WORK/words.tsv" "$WORK/pairs.tsv"
python3 "$PY" build "$WORK/count.tsv" "$ASSETS/kkc_lex.tsv" "$OUT"
python3 "$PY" names "$WORK/names.tsv" "$ASSETS/kkc_lex.tsv" "$OUT_NAMES"
python3 "$PY" predict "$WORK/words.tsv" "$WORK/names.tsv" "$OUT_PREDICT"
python3 "$PY" next "$WORK/pairs.tsv" "$OUT_NEXT"
echo "[build-kkc-freq] 出力: $OUT / $OUT_NAMES / $OUT_PREDICT / $OUT_NEXT"
