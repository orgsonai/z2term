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
#
# 終了コード: 未訳があっても 0。⚠ **落とすのは lint の仕事** (MissingTranslation)。
# ここは「あとどれだけか」を見るための道具で、作業を止めるためのものではない。

set -euo pipefail

cd "$(dirname "$0")/.."
exec python3 scripts/i18n_status.py "$@"
