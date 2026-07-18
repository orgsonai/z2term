package com.zerotoship.z2term.service

import java.io.File

/**
 * 通知検知 / システムイベント検知のログ書き込み。2 モードを扱う:
 *  - **末尾追記** (既定): `appendText` で末尾に足すだけ (安い・新着が下)。
 *  - **先頭追記**: 新しい行をファイル先頭に置く (新着が上)。ファイルは先頭に 1 行差し込む
 *    OS 機能が無いため、既存内容を読んで「新しい行 + 既存」で書き直す。
 *
 * **ローテーション**: どちらのモードでもログは放置すると無限に増える (マクロを常用するほど速い)。
 * [MAX_BYTES] を超えたら `<名前>.1` へ退避して新しいファイルを作る。**消さずに 1 世代残す**ので、
 * 直前のぶんは `~/.z2term/events.jsonl.1` で追える (合計サイズは上限のおよそ 2 倍で頭打ち)。
 * 先頭追記モードは毎回ファイル全体を読み書きするため、上限があること自体が速度面でも効く。
 */
internal object LogWriter {

    /** 1 ファイルの上限。JSONL 1 行 150 バイト前後として概ね 7000 行。 */
    private const val MAX_BYTES = 1L * 1024 * 1024

    /** [line] (末尾改行なし) を [f] へ書く。[prepend] が true なら先頭追記 (新着が上)。 */
    fun write(f: File, line: String, prepend: Boolean) {
        f.parentFile?.mkdirs()
        rotateIfNeeded(f)
        if (!prepend) {
            f.appendText(line + "\n")
            return
        }
        val existing = if (f.exists()) f.readText() else ""
        f.writeText(line + "\n" + existing)
    }

    /**
     * 上限超過なら `<名前>.1` へ退避する (既存の `.1` は上書き = 保持は 1 世代)。
     * 失敗しても書き込み自体は続ける (ログのために本体を止めない)。
     */
    private fun rotateIfNeeded(f: File) {
        runCatching {
            if (!f.exists() || f.length() < MAX_BYTES) return
            val backup = File(f.parentFile, f.name + ".1")
            if (backup.exists()) backup.delete()
            if (!f.renameTo(backup)) {
                // rename できない環境では、せめて肥大を止めるために切り詰める。
                f.writeText("")
            }
        }
    }
}
