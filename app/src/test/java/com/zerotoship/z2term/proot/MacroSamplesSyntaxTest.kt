package com.zerotoship.z2term.proot

import com.zerotoship.z2term.settings.AppLanguages
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 同梱のサンプルマクロと `z2-macro` 本体を、**全対応言語で** `sh -n` (構文チェックのみ) に通す (1.0.121)。
 *
 * 訳した文言はシェルスクリプトの中にそのまま入る。引用符の外に置かれた語 (case の型・
 * 引数) に空白や記号が混じると、その言語のスクリプトだけが壊れる。英語と日本語では
 * 起きないので、言語ごとに確かめないと実機まで気付けない
 * (ベトナム語の延期ボタン「+10 phút」が case の型で構文エラーになっていた)。
 * `sh` が無い環境ではスキップする。
 */
class MacroSamplesSyntaxTest {

    @Test
    fun `bundled macros parse as posix sh in every language`() {
        val sh = listOf("/bin/sh", "/usr/bin/sh").firstOrNull { File(it).canExecute() } ?: return
        val tmp = File(System.getProperty("java.io.tmpdir"), "z2macro-syntax-test").apply { mkdirs() }
        val broken = mutableListOf<String>()
        var checked = 0
        for (lang in AppLanguages.CODES) {
            val scripts = z2MacroSamples(lang) + ("z2-macro" to z2MacroScript(lang))
            for ((name, body) in scripts) {
                checked++
                val f = File(tmp, "$lang-$name").apply { writeText(body) }
                val p = ProcessBuilder(sh, "-n", f.absolutePath).redirectErrorStream(true).start()
                val out = p.inputStream.bufferedReader().readText()
                if (p.waitFor() != 0) broken += "$lang $name: $out"
            }
        }
        assertTrue("checked nothing", checked > AppLanguages.CODES.size)
        assertTrue("sh -n failed:\n" + broken.joinToString("\n"), broken.isEmpty())
    }
}
