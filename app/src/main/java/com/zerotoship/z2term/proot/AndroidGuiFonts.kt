package com.zerotoship.z2term.proot

import java.io.File

/** Android にあるフォントを Linux GUI から参照する。フォントのコピー・同梱はしない。 */
internal object AndroidGuiFonts {
    const val GUEST_DIR = "/usr/local/share/fonts/z2term-android"
    private val sources = listOf("/system/fonts", "/product/fonts", "/system_ext/fonts")
    val guestDirectories: List<String> = sources.map { "$GUEST_DIR/${it.split('/')[1]}" }

    fun prepare(rootfs: File, fontDirectories: List<File> = sources.map(::File)): List<Pair<File, String>> {
        val binds = fontDirectories.filter { it.isDirectory && it.canRead() }.map { source ->
            source to "$GUEST_DIR/${source.parentFile!!.name}"
        }
        if (binds.isEmpty()) return emptyList()
        for ((_, target) in binds) File(rootfs, target.removePrefix("/")).mkdirs()
        // conf.d を使い、ユーザーの fonts.conf / local.conf は保持する。
        val config = File(rootfs, "etc/fonts/conf.d/99-z2term-android-fonts.conf")
        config.parentFile!!.mkdirs()
        val xml = """
            <?xml version="1.0"?>
            <!DOCTYPE fontconfig SYSTEM "urn:fontconfig:fonts.dtd">
            <fontconfig>
              <dir>$GUEST_DIR</dir>
            </fontconfig>
        """.trimIndent() + "\n"
        if (!config.isFile || config.readText() != xml) config.writeText(xml)
        return binds
    }
}
