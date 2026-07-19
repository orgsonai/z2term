package com.zerotoship.z2term.service

import java.io.File

/**
 * 通知検知 / システムイベント検知のログ書き込み。2 モードを扱う:
 *  - **末尾追記** (既定): `appendText` で末尾に足すだけ (安い・新着が下)。
 *  - **先頭追記**: 新しい行をファイル先頭に置く (新着が上)。ファイルは先頭に 1 行差し込む
 *    OS 機能が無いため、既存内容を読んで「新しい行 + 既存」で書き直す。
 *
 * **ローテーションはしない**: ログは 1 ファイルに追記し続け、サイズ上限で分割・退避しない
 * (ユーザー要望)。以前は 1 MiB で `<名前>.1` へ退避していたが、マクロが「過去に遡って
 * 集計する」用途では途中でファイルが切り替わると解析が面倒になるため、全履歴を 1 本に残す。
 * 掃除が要るときはユーザーがターミナル側で truncate/rotate する (例: `: > ~/.z2term/events.jsonl`)。
 *
 * 注意: **先頭追記モードは 1 行ごとにファイル全体を読み書きする**ため、上限を外したことで
 * ファイルが肥大すると 1 件あたりのコストが線形に増える。大量イベントを長期常用する場合は
 * 末尾追記 (既定) の利用を推奨する (追記のみで肥大に影響されない)。
 */
internal object LogWriter {

    /** [line] (末尾改行なし) を [f] へ書く。[prepend] が true なら先頭追記 (新着が上)。 */
    fun write(f: File, line: String, prepend: Boolean) {
        f.parentFile?.mkdirs()
        if (!prepend) {
            f.appendText(line + "\n")
            return
        }
        val existing = if (f.exists()) f.readText() else ""
        f.writeText(line + "\n" + existing)
    }
}
