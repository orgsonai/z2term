package com.zerotoship.z2term.storage

import android.content.Context
import java.io.File

/**
 * 外部 SD カードなど /storage/XXXX-XXXX 形式の物理ボリュームを列挙するヘルパー。
 *
 * 端末標準の共有ストレージ (/storage/emulated/0 ≒ /sdcard) はここでは扱わない
 * (ProotLauncher が既に /sdcard へ別途バインドしているため)。
 *
 * 検出は API 19+ で使える [Context.getExternalFilesDirs] を経由し、
 * 端末側に物理マウントされている外部ボリュームの **アプリ専用ディレクトリ** を
 * 得て、そこから親のボリュームルート (`/storage/XXXX-XXXX`) を逆算する。
 * StorageManager.storageVolumes より権限要件が緩く、minSdk=29 でも安全。
 */
object ExternalStorageDetector {

    /** 物理 SD のボリューム名パターン (FAT UUID 形式: 4桁-4桁の hex)。 */
    private val VOLUME_NAME = Regex("[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}")
    private const val ANDROID_DATA_ANCHOR = "/Android/data/"

    /**
     * 検出された外部ボリュームのルートパス (例: "/storage/1A2B-3C4D") を返す。
     * 物理 SD が無い・取り外し中のときは空リスト。
     */
    fun detect(context: Context): List<String> {
        val dirs = context.getExternalFilesDirs(null) ?: return emptyList()
        val seen = LinkedHashSet<String>()
        for (dir in dirs) {
            if (dir == null) continue
            val volume = volumeRoot(dir.absolutePath) ?: continue
            if (volume == "/storage/emulated/0") continue
            // 物理 SD は普通 /storage/XXXX-XXXX/。USB OTG 等で違う命名も来うるが、
            // ここでは「/storage/ 直下にあって FAT UUID 形式」のみ拾う (誤検出回避)。
            if (!volume.startsWith("/storage/")) continue
            val name = volume.substringAfterLast('/')
            if (!VOLUME_NAME.matches(name)) continue
            // 実体が読めるか軽くチェック (取り外し直後の path 残りを弾く)。
            if (!File(volume).exists()) continue
            seen += volume
        }
        return seen.toList()
    }

    private fun volumeRoot(path: String): String? {
        val idx = path.indexOf(ANDROID_DATA_ANCHOR)
        return if (idx > 0) path.substring(0, idx) else null
    }
}
