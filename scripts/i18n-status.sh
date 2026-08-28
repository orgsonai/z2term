#!/usr/bin/env bash
# 多言語化の埋まり具合を数える。
#
# z2term の文言は **2 系統**ある。片方だけ訳すと「画面は中国語なのに z2-notify --help は
# 英語」になるので、両方を 1 つの表で見られるようにする。
#
#   1. アプリ画面    app/src/main/res/values[-<言語>]/strings.xml   (Android 標準)
#   2. 端末に出る文言 app/src/main/java/.../proot/*.kt の t(en = …, ja = …)
#                     (rootfs へ書き出すシェルスクリプトの中身なので res では持てない)
#
# 対応言語の名簿は app/src/main/java/.../settings/AppLanguages.kt が正本。
#
# 使い方:
#   bash scripts/i18n-status.sh                 # 全言語の埋まり具合を表で出す
#   bash scripts/i18n-status.sh --missing ja    # その言語で未訳の res キーを出す
#   bash scripts/i18n-status.sh --missing ja --xml
#                                               # 未訳キーを strings.xml に貼れる形で出す
#   bash scripts/i18n-status.sh --check         # 完訳を宣言した言語を検査する (落ちる)
#
# 終了コード: --check 以外は、未訳があっても 0。
# ここは「あとどれだけか」を見るための道具で、作業を止めるためのものではない。
#
# ⭐ **--check だけは落とすためにある。** AppLanguages.kt で cliComplete = true を付けた
# 言語（= 端末に出る文言まで訳しきったと宣言した言語）に未訳があれば終了コード 1 を返す。
# ⚠ res は見ない — そちらは lint の MissingTranslation が error で守っている。
# CI ではユニットテスト CliTranslationCheckTest がこれを呼ぶので、訳を足さずに
# 新しい文言を書くと testDebugUnitTest が落ちる。

set -euo pipefail

cd "$(dirname "$0")/.."
exec python3 scripts/i18n_status.py "$@"
