package com.zerotoship.z2term.service

import java.io.File

/**
 * 通知検知 / システムイベント検知のログ書き込み。2 モードを扱う:
 *  - **末尾追記** (既定): `appendText` で末尾に足すだけ (安い・新着が下)。
 *  - **先頭追記**: 新しい行をファイル先頭に置く (新着が上)。ファイルは先頭に 1 行差し込む
 *    OS 機能が無いため、既存内容を読んで「新しい行 + 既存」で書き直す。
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
