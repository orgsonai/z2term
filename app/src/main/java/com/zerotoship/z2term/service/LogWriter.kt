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
 * 通知と SMS は、設定で選んだ日数より古い記録を同じファイルから切り落とせる ([LogRetention]・1.0.50)。
 *
 * 注意: **先頭追記モードは 1 行ごとにファイル全体を読み書きする**ため、上限を外したことで
 * ファイルが肥大すると 1 件あたりのコストが線形に増える。大量イベントを長期常用する場合は
 * 末尾追記 (既定) の利用を推奨する (追記のみで肥大に影響されない)。
 */
internal object LogWriter {

    /**
     * [line] (末尾改行なし) を [f] へ書く。[prepend] が true なら先頭追記 (新着が上)。
     * [keepDays] を渡すと [LogRetention] の控えを付け、0 より大きければその日数より古い記録を消す
     * (1.0.50・通知と SMS だけ。0 は無制限)。
     */
    fun write(f: File, line: String, prepend: Boolean, keepDays: Int? = null,
              now: Long = System.currentTimeMillis()) {
        f.parentFile?.mkdirs()
        val text = line + "\n"
        if (!prepend) {
            f.appendText(text)
        } else {
            val existing = if (f.exists()) f.readText() else ""
            f.writeText(text + existing)
        }
        // 記録は書けている。古い分を消せなくても、書き込みの失敗にはしない (次の機会にやり直す)。
        if (keepDays != null) runCatching {
            LogRetention.recorded(f, text.toByteArray(Charsets.UTF_8).size.toLong(), prepend, keepDays, now)
        }
    }
}
