package com.zerotoship.z2term.gui.rdp

import android.os.Environment
import com.zerotoship.z2term.clipboard.ClipboardFileTransfer
import java.io.File

/**
 * RDP のフォルダ共有 1 件。相手のデスクトップから `\\tsclient\[name]` として見える。
 *
 * ⚠ **渡すのはこの 1 フォルダだけ。** [RdpDrive] が [root] の外へ出る道を毎回弾く。
 */
data class RdpShare(
    val root: File,
    val name: String,
)

/**
 * フォルダ共有の既定値。
 *
 * ⭐ **既定は「クリップボードで受け取ったファイルと同じ置き場」**。相手からファイルを
 * 戻す先が 2 か所に分かれると、どちらに入ったのか探すことになる。**場所を毎回選ばせない**
 * (変えたい人だけ接続先の設定でパスを書く)。
 */
object RdpShareDefaults {

    /** 相手の一覧に出る既定の共有名。 */
    const val NAME = "z2term"

    /** 端末側の既定の置き場 (`/sdcard/Download/z2term` 相当)。 */
    val PATH: String
        get() = File(
            Environment.getExternalStorageDirectory(),
            ClipboardFileTransfer.FOLDER,
        ).absolutePath

    /**
     * 設定された共有を実際に使える形にする。**フォルダが無ければ作る。**
     *
     * @return 使えないとき (パスが空・作れない・フォルダでない) は null。
     *   ⚠ null でも RDP そのものは繋ぐ — 共有できないことと繋げないことは別。
     */
    fun resolve(path: String, name: String): RdpShare? {
        val root = File(path.ifBlank { PATH })
        if (!root.exists() && !root.mkdirs()) return null
        if (!root.isDirectory) return null
        return RdpShare(root = root, name = name.ifBlank { NAME })
    }
}
