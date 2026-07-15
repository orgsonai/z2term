package com.zerotoship.z2term.settings

import java.io.File
import java.nio.file.Files

/**
 * インストール済み OS (rootfs = `filesDir/distros/<id>`) 内に溜まる「再取得可能なキャッシュ」を
 * 直接ファイル操作で掃除するユーティリティ。
 *
 * Android の「キャッシュ削除」(cacheDir) は本アプリではほぼ空になる (ダウンロード一時ファイルは
 * インストール成功直後に消えるため)。実際に容量を食うのは **rootfs 内のパッケージマネージャ
 * キャッシュ / ビルドキャッシュ** で、これは Android のキャッシュ API では触れない (アプリから見ると
 * ただの「データ」)。ここではそれらを [DistroInstaller]/[TerminalSession.deleteDistroData] と
 * 同じ「Kotlin から直接削除」方式で掃除する。
 *
 * 対象は **消しても再取得できるダウンロード/ビルドキャッシュだけ**。稼働中セッションが握る恐れの
 * ある `/tmp` や、パッケージ本体・設定・ユーザファイルには一切触れない。停止中/稼働中どちらの
 * OS でも安全に消せるものだけを列挙している。
 */
object RootfsCacheCleaner {

    /** rootfs ルートからの相対パスと、確認ダイアログに出す表示名。 */
    private data class Target(val label: String, val relPath: String)

    // 各 OS の rootfs 直下 (filesDir/distros/<id>/) からの相対パス。存在するものだけ拾う。
    private val TARGETS = listOf(
        Target("パッケージキャッシュ (pacman)", "var/cache/pacman/pkg"),
        Target("パッケージキャッシュ (apt)", "var/cache/apt/archives"),
        Target("パッケージキャッシュ (apk)", "var/cache/apk"),
        Target("ビルド/ツールキャッシュ (~/.cache)", "root/.cache"),
    )

    /** 掃除対象 1 件。[dir] の **中身** を消す (dir 自体は残す)。 */
    data class Item(val label: String, val dir: File, val bytes: Long)

    /**
     * [distrosRoot] (= `filesDir/distros`) 配下の全 OS と、アプリの [appCacheDir] を走査し、
     * 中身が空でないキャッシュディレクトリを [Item] として返す (サイズ降順)。
     */
    fun scan(distrosRoot: File, appCacheDir: File?): List<Item> {
        val items = ArrayList<Item>()
        val distros = distrosRoot.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name } ?: emptyList()
        val multi = distros.size > 1
        for (osDir in distros) {
            val prefix = if (multi) "${osDir.name}: " else ""
            for (t in TARGETS) {
                val d = File(osDir, t.relPath)
                if (d.isDirectory && !Files.isSymbolicLink(d.toPath())) {
                    val sz = dirSize(d)
                    if (sz > 0) items.add(Item(prefix + t.label, d, sz))
                }
            }
            // home/<user>/.cache も拾う (一般ユーザを作っている場合)。
            File(osDir, "home").listFiles()?.filter { it.isDirectory }?.forEach { u ->
                val d = File(u, ".cache")
                if (d.isDirectory && !Files.isSymbolicLink(d.toPath())) {
                    val sz = dirSize(d)
                    if (sz > 0) items.add(Item("${prefix}ユーザキャッシュ (${u.name}/.cache)", d, sz))
                }
            }
        }
        // アプリ本体のダウンロード一時 (cacheDir 全体)。従来の「キャッシュ削除」相当。
        if (appCacheDir != null && appCacheDir.isDirectory) {
            val sz = dirSize(appCacheDir)
            if (sz > 0) items.add(Item("ダウンロード一時 (アプリ)", appCacheDir, sz))
        }
        return items.sortedByDescending { it.bytes }
    }

    /** [items] 各 [Item.dir] の中身を削除し、解放できたバイト数 (走査値の合計) を返す。 */
    fun clean(items: List<Item>): Long {
        var freed = 0L
        for (item in items) {
            val before = dirSize(item.dir)
            item.dir.listFiles()?.forEach { child -> runCatching { child.deleteRecursively() } }
            val after = dirSize(item.dir)
            freed += (before - after).coerceAtLeast(0L)
        }
        return freed
    }

    /** ディレクトリ配下の実ファイル合計サイズ。symlink は辿らない (循環・二重計上を防ぐ)。 */
    private fun dirSize(dir: File): Long {
        var total = 0L
        dir.walkTopDown()
            .onEnter { !Files.isSymbolicLink(it.toPath()) }
            .forEach { f ->
                if (f.isFile && !Files.isSymbolicLink(f.toPath())) total += f.length()
            }
        return total
    }
}
