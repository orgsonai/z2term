package com.zerotoship.z2term.proot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **端末に出る文言 (`z2-*` CLI) の訳が、言語を足した後に静かに腐るのを止める。**
 *
 * ⚠ **lint の `MissingTranslation` が守るのは res だけ。** 端末に出る文言は未訳でも
 * 英語が出て**何も壊れない**ので、新しい機能を足すたびに訳した言語の `z2-*` の表示だけが
 * 少しずつ英語へ戻っていく（画面は中国語なのに `z2-notify --help` は英語、という形）。
 * res と違って CI も緑のまま通るため、**気付く手段が実機しか無い**。
 *
 * そこで `AppLanguages` の `cliComplete` に「この言語は訳しきった」という印を持たせ、
 * 印のある言語の埋まり具合が 100% でなければ**ここで落とす**。
 *
 * ⭐ **数え上げは `scripts/i18n_status.py` に任せて、ここでは判定しない。**
 * `t(en = …, ja = …, "<言語>" to …)` を数えるやり方を Kotlin 側にもう 1 つ書くと、
 * 2 つの数え方がいずれ食い違い、**「表では 98% なのにテストは通る」**という
 * 一番たちの悪い形になる。ここは同じ道具を呼んで終了コードを見るだけにする。
 *
 * ⚠ **`python3` が無い環境ではスキップする** ([GuiScriptSyntaxTest] が `sh` に対して
 * しているのと同じ扱い)。python3 はビルドの必須要件ではないため、入っていないことを
 * 理由に開発機のテストを落とすことはしない。CI (ubuntu-latest) には入っているので、
 * **push すれば必ず検査される**。
 */
class CliTranslationCheckTest {

    @Test
    fun `languages marked as complete have every cli string translated`() {
        val root = repoRoot() ?: return
        val script = File(root, SCRIPT)
        assertTrue(
            "$SCRIPT が見つからない。移動・改名したならこのテストの参照も直すこと。",
            script.isFile
        )

        val python = python3() ?: return
        val p = ProcessBuilder(python, script.absolutePath, "--check")
            .directory(root)
            .redirectErrorStream(true)
            .start()
        val out = p.inputStream.bufferedReader().readText()
        val rc = p.waitFor()

        assertEquals(
            "端末に出る文言に未訳が残っている（詳しくは bash scripts/i18n-status.sh --check）:\n$out",
            0,
            rc
        )
    }

    private companion object {
        const val SCRIPT = "scripts/i18n_status.py"

        /** リポジトリの根の目印。⚠ スクリプト自身を目印にしないこと（改名すると黙って通る）。 */
        const val MARKER = "settings.gradle.kts"

        /**
         * リポジトリの根を探す。⚠ **作業ディレクトリを決め打ちしない** — Gradle の unit test は
         * モジュール (`app/`) で走るが、IDE から 1 件だけ走らせるとリポジトリの根になることがある。
         */
        fun repoRoot(): File? {
            var dir: File? = File(System.getProperty("user.dir") ?: return null).absoluteFile
            while (dir != null) {
                if (File(dir, MARKER).isFile) return dir
                dir = dir.parentFile
            }
            return null
        }

        fun python3(): String? =
            listOf("/usr/bin/python3", "/usr/local/bin/python3", "/bin/python3")
                .firstOrNull { File(it).canExecute() }
    }
}
