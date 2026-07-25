package com.zerotoship.z2term.proot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * `z2doctor` の生成物を検証する。
 *
 * 診断コマンドは**困っている人が最後に打つもの**なので、そこで落ちると打つ手が無くなる。
 * ブリッジ (`z2api`) が無い・応答が空、という**いちばん壊れている状況でも最後まで走り切る**
 * ことを、実際の `sh` で確かめる（テスト環境には `z2api` が無いので、その状況が再現できる）。
 */
class Z2DoctorScriptTest {

    private val sh = listOf("/bin/sh", "/usr/bin/sh").firstOrNull { File(it).canExecute() }

    private fun run(script: String, vararg args: String): Pair<Int, String> {
        val f = File.createTempFile("z2doctor", ".sh").apply { writeText(script) }
        try {
            val pb = ProcessBuilder(listOf(sh!!, f.absolutePath) + args).redirectErrorStream(true)
            pb.environment()["HOME"] = System.getProperty("java.io.tmpdir")
            val p = pb.start()
            val out = p.inputStream.bufferedReader().readText()
            return p.waitFor() to out
        } finally {
            f.delete()
        }
    }

    @Test
    fun bothLanguagesAreValidPosixShell() {
        assumeTrue("sh が無い環境なのでスキップ", sh != null)
        for (lang in listOf("ja", "en")) {
            val f = File.createTempFile("z2doctor", ".sh").apply { writeText(z2doctorScript(lang)) }
            try {
                val p = ProcessBuilder(sh!!, "-n", f.absolutePath).redirectErrorStream(true).start()
                val out = p.inputStream.bufferedReader().readText()
                assertEquals("z2doctor ($lang): sh -n が構文エラーを報告した:\n$out", 0, p.waitFor())
            } finally {
                f.delete()
            }
        }
    }

    @Test
    fun startsWithShebangAndEndsWithNewline() {
        for (lang in listOf("ja", "en")) {
            val body = z2doctorScript(lang)
            assertTrue("z2doctor ($lang): シェバンが無い", body.startsWith("#!/bin/sh\n"))
            assertTrue("z2doctor ($lang): 改行で終わっていない", body.endsWith("\n"))
        }
    }

    @Test
    fun noMarginLeak() {
        for (lang in listOf("ja", "en")) {
            val bad = z2doctorScript(lang).lines().filter { it.startsWith("|") }
            assertTrue("z2doctor ($lang): 行頭に `|` が残っている: $bad", bad.isEmpty())
        }
    }

    @Test
    fun survivesWithoutTheBridge() {
        // z2api が無い = アプリ側の情報がまったく取れない、という最悪の状況。
        // ここで落ちると「動かないので z2doctor を打つ」ができなくなる。
        assumeTrue("sh が無い環境なのでスキップ", sh != null)
        val (rc, out) = run(z2doctorScript("ja"))
        assertEquals("ブリッジ無しで z2doctor が落ちた:\n$out", 0, rc)
        assertTrue("見出しが出ていない:\n$out", out.contains("z2doctor"))
        // 取れなかった項目は NG ではなく `--` (不明) で出す。分からないことを異常として数えない。
        assertTrue("不明を示す `--` 行が無い:\n$out", out.contains("--  "))
    }

    @Test
    fun helpDoesNotRunTheDiagnosis() {
        assumeTrue("sh が無い環境なのでスキップ", sh != null)
        val (rc, out) = run(z2doctorScript("en"), "--help")
        assertEquals(0, rc)
        assertTrue("usage が出ていない:\n$out", out.contains("usage: z2doctor"))
        assertTrue("--help なのに診断が走っている:\n$out", !out.contains("-- permissions --"))
    }

    @Test
    fun redactionNoteIsAlwaysShown() {
        // 伏せ字は「後で付ける」と必ず漏れるので、報告文に必ず添えることを固定する。
        assumeTrue("sh が無い環境なのでスキップ", sh != null)
        val (_, ja) = run(z2doctorScript("ja"))
        assertTrue("伏せている旨の注記が無い:\n$ja", ja.contains("伏せ"))
        val (_, en) = run(z2doctorScript("en"))
        assertTrue("redaction note missing:\n$en", en.contains("left out"))
    }
}
