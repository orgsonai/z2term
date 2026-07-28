package com.zerotoship.z2term.proot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * 常駐サーバー supervisor スクリプトの回帰テスト。
 *
 * このスクリプトは**アプリからは中身が見えないまま rootfs で実行される**ので、壊れていても
 * 「サーバーが起動しない」という形でしか現れず、原因の特定が遅れる (0.8.165 の事故)。
 * 生成物を実際の `sh -n` に通して構文を検証し、無停止リロードの要になっている部分が
 * 消えていないことも合わせて固定する。
 */
class ServerSupervisorScriptTest {

    private val script = ServerSupervisorScript.generate()

    /**
     * 行頭に `trimMargin` のマージン `|` が残っていないこと。
     * POSIX sh では行頭 `|` は常に構文エラーで、スクリプトごと起動不能になる (0.8.187 の事故)。
     */
    @Test
    fun hasNoMarginLeak() {
        val bad = script.lines().withIndex().filter { (_, line) -> line.startsWith("|") }
        assertTrue(
            "行頭に trimMargin のマージン `|` が残っている: " +
                bad.joinToString { "(line ${it.index + 1}) ${it.value}" },
            bad.isEmpty()
        )
    }

    /** 実際の `sh` に構文チェックさせる (sh が無い環境ではスキップ)。 */
    @Test
    fun isValidPosixShell() {
        val sh = listOf("/bin/sh", "/usr/bin/sh").firstOrNull { File(it).canExecute() }
        assumeTrue("sh が無い環境なのでスキップ", sh != null)
        val tmp = File.createTempFile("supervisor", ".sh")
        try {
            tmp.writeText(script)
            val proc = ProcessBuilder(sh!!, "-n", tmp.absolutePath)
                .redirectErrorStream(true)
                .start()
            val output = proc.inputStream.bufferedReader().readText()
            val rc = proc.waitFor()
            assertEquals("sh -n が構文エラーを報告した:\n$output", 0, rc)
        } finally {
            tmp.delete()
        }
    }

    /**
     * **サーバーの定義を焼き込まない**こと (A3)。焼き込みに戻ると、起動後に追加した
     * エントリを反映するのに supervisor 全体の再起動 = 他サーバーの巻き添え停止が必要になる。
     */
    @Test
    fun doesNotBakeInEntries() {
        assertTrue("ジョブファイルを走査していない", script.contains("*.job"))
        assertTrue("run ループを起こす仕掛けが無い", script.contains("run_server \"\$name\" &"))
    }

    /** 削除・編集の無停止反映 (job の有無と中身の変化を見ている) が残っていること。 */
    @Test
    fun watchesJobFileLifecycle() {
        assertTrue("job が消えたら抜ける条件が無い", script.contains("while [ -f \"\$jobf\" ]; do"))
        assertTrue("コマンド変更を検知していない", script.contains("[ \"\$newcmd\" != \"\$cmd\" ]"))
    }

    /** ログ・再起動回数・終了コード履歴 (A3 で足した観測手段) が残っていること。 */
    @Test
    fun recordsLogsAndExitHistory() {
        assertTrue("標準出力をログへ落としていない", script.contains(">> \"\$logf\" 2>&1 &"))
        assertTrue("再起動回数を数えていない", script.contains("restarts=`expr \$restarts + 1`"))
        assertTrue("終了コード履歴を残していない", script.contains(">> \"\$exitf\""))
        assertTrue("ログの切り詰めが無い", script.contains("trim_log"))
    }

    /**
     * `wait` を 1 回しか呼ばないこと。kill 後にもう一度 `wait` すると「そんな子は居ない」で
     * 無関係な終了コードを拾い、`last_exit` が嘘になる。
     */
    @Test
    fun waitsForTheChildExactlyOnce() {
        val waits = script.lines().count { it.trim().startsWith("wait \"\$spid\"") }
        assertEquals("子プロセスの wait は 1 回だけであるべき", 1, waits)
    }

    /** 起動時に `.claimed` を掃除すること (残骸があると run ループが二度と起こされない)。 */
    @Test
    fun clearsStaleClaimMarkers() {
        assertTrue(
            "起動時に .claimed を掃除していない",
            script.contains("rm -f \"\$STATUS_DIR\"/*.status \"\$STATUS_DIR\"/*.claimed")
        )
    }

    /** 常駐サーバーであることを子へ伝える環境変数 (sshd wrapper が前景常駐に切り替える印)。 */
    @Test
    fun exportsSupervisedFlag() {
        assertTrue(script.contains("Z2_SUPERVISED=1"))
        assertTrue(script.contains("export Z2_SUPERVISED"))
    }

    /**
     * 見張りの間隔をハードコードしないこと (0.8.268)。
     *
     * このスクリプトはエンジン (proot/z2root) の中で動き、外部コマンドを 1 回起こすだけで
     * ptrace 越しに数千 syscall になる。`sleep 1` に戻すと**常駐しているだけで端末が温まり
     * 電池が減る**ので、間隔は必ず `$POLL` 経由にして 1 か所で決める。
     */
    @Test
    fun pollsThroughASingleInterval() {
        assertTrue("POLL が定義されていない", script.contains("POLL=${ServerSupervisorScript.POLL_SECONDS}"))
        val hardcoded = script.lines().map { it.trim() }.filter { it.startsWith("sleep ") && it != "sleep \"\$POLL\"" }
        // 再起動前の待ち (sleep 3) だけは間隔と無関係なので許す。
        assertEquals("見張りの sleep が POLL を経由していない: $hardcoded", listOf("sleep 3"), hardcoded)
    }

    /**
     * `.want` をシェル組み込みの `read` で読むこと (0.8.268)。`cat` に戻すと 1 周期ごとに
     * プロセスが 1 つ増え、サーバーの本数だけ倍になる。
     */
    @Test
    fun readsWantFlagWithoutSpawningAProcess() {
        assertTrue("want を read で読んでいない", script.contains("read want < \"\$wantf\""))
        assertFalse("want を cat で読んでいる (プロセスが増える)", script.contains("cat \"\$wantf\""))
    }

    /**
     * 停止中のサーバーが `.status` を毎周期書き直さないこと (0.8.268)。動いていないサーバーの
     * 分までディスクとエンジンを回し続けるのを防ぐ。
     */
    @Test
    fun doesNotRewriteStatusWhileStopped() {
        assertTrue(
            "停止中の status 書き込みに変化ガードが無い",
            script.contains("[ \"\$laststop\" != \"\$restarts:\$cmd\" ]")
        )
    }

    /** シェバンで始まり、末尾は改行で終わること。 */
    @Test
    fun startsWithShebangAndEndsWithNewline() {
        assertTrue(script.startsWith("#!/bin/sh\n"))
        assertTrue(script.endsWith("\n"))
        assertFalse(script.endsWith("\n\n"))
    }
}
