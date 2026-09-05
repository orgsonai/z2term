package com.zerotoship.z2term.proot

import com.zerotoship.z2term.settings.AppLanguages
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * `z2gui` は **生成されたシェルスクリプト**なので、Kotlin 側がコンパイルできても
 * シェルとして壊れていることがある。壊れると GUI が一切立たない (しかも実機でしか気付けない)。
 *
 * ここでは全対応言語で生成し、`sh -n` (構文チェックのみ・実行しない) に通す。
 * `sh` が無い環境ではスキップする (CI/開発機のどちらでも落とさない)。
 */
class GuiScriptSyntaxTest {

    @Test
    fun `generated z2gui parses as posix sh`() {
        val sh = listOf("/bin/sh", "/usr/bin/sh").firstOrNull { File(it).canExecute() } ?: return
        val tmp = File(System.getProperty("java.io.tmpdir"), "z2gui-syntax-test").apply { mkdirs() }
        for (lang in AppLanguages.CODES) {
            val script = z2guiScript(strings = GuiScriptStrings.forLang(lang))
            val f = File(tmp, "z2gui-$lang.sh").apply { writeText(script) }
            val p = ProcessBuilder(sh, "-n", f.absolutePath)
                .redirectErrorStream(true)
                .start()
            val out = p.inputStream.bufferedReader().readText()
            val rc = p.waitFor()
            assertTrue("sh -n failed for $lang:\n$out", rc == 0)
        }
    }

    /**
     * `z2menu` / `z2run` も同じ理由で構文チェックする (0.8.498)。
     *
     * ⚠ **`z2menu` の本体は awk プログラムなので、ここは中身まで見ていない。**
     * `sh -n` が見るのはシェルとしての構文だけで、awk の中は「ただの文字列」でしかない。
     * awk 側を変えたときは実機で `z2menu list` を叩いて確かめること。
     */
    @Test
    fun `generated z2menu and z2run parse as posix sh`() {
        val sh = listOf("/bin/sh", "/usr/bin/sh").firstOrNull { File(it).canExecute() } ?: return
        val tmp = File(System.getProperty("java.io.tmpdir"), "z2gui-syntax-test").apply { mkdirs() }
        for (lang in AppLanguages.CODES) {
            for ((name, script) in listOf("z2menu" to z2menuScript(lang), "z2run" to z2runScript(lang))) {
                val f = File(tmp, "$name-$lang.sh").apply { writeText(script) }
                val p = ProcessBuilder(sh, "-n", f.absolutePath)
                    .redirectErrorStream(true)
                    .start()
                val out = p.inputStream.bufferedReader().readText()
                assertTrue("sh -n failed for $name/$lang:\n$out", p.waitFor() == 0)
            }
        }
    }
}
