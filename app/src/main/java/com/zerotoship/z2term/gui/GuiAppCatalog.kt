package com.zerotoship.z2term.gui

import android.content.Context
import android.util.Log
import com.zerotoship.z2term.proot.ProotLauncher
import com.zerotoship.z2term.pty.PtyProcess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * GUI で起こせるアプリ 1 件（distro 側の `.desktop` 由来）。
 *
 * @param name     表示名。`Name[<言語>]` があればそちら。
 * @param exec     起動するコマンド。`.desktop` の `Exec` から**フィールドコードを外した**もの。
 * @param comment  1 行の説明（`Comment`）。無いことも多い。
 * @param terminal `Terminal=true` = 端末の中で開くアプリ（`vim` 等）。
 * @param category freedesktop の主分類に丸めたもの（`Utility` / `Other` …）。
 */
data class GuiApp(
    val name: String,
    val exec: String,
    val comment: String,
    val terminal: Boolean,
    val category: String,
)

/**
 * distro に入っている GUI アプリの一覧を **`z2menu list`** から取る（0.8.499）。
 *
 * ⭐ **`.desktop` のパースをここで書き直さないこと。** 何を出すか（`Type` / `NoDisplay` / `Hidden` /
 * `TryExec` / `Exec` の実体が PATH に在るか / フィールドコードの除去 / `Terminal=true`）は
 * `z2menu`（[com.zerotoship.z2term.proot.z2menuScript]）が全部決めている。同じ規則を Kotlin 側にも
 * 持つと、**必ずどちらか片方だけ直されて食い違う**（デスクトップの右クリックメニューと ☰ で
 * 並ぶものが違う、という形で出る）。ここがするのは TSV を読むことだけ。
 *
 * ⚠ **PTY 越しに読む**ので改行は CRLF になる（termios の `ONLCR`）。`\r` を落としてから割る。
 */
object GuiAppCatalog {

    private const val TAG = "GuiAppCatalog"

    /**
     * `z2menu list` の待ち時間。⚠ **無期限に待たない** — rootfs が壊れている等で z2menu が
     * 返らないと、☰ を押しただけでシートが永久に「読み込み中」になる。
     * 数十個の `.desktop` を読むだけなので、実測では 1 秒に満たない。
     */
    private const val TIMEOUT_MS = 20_000L

    /**
     * 読み取りの上限。`.desktop` 数百件でも数十 KB にしかならないので、これを超えるのは
     * z2menu 以外の何かが喋り続けているとき。⚠ 上限が無いと、そのときアプリのメモリを食い潰す。
     */
    private const val MAX_BYTES = 1 shl 20

    /**
     * 直近に読めた一覧（distro ごと）。⭐ **アプリが動いている間だけ持つ**（0.8.509・要望）。
     *
     * ☰ を開くたびに `z2menu list` を起こすと、proot の起動と `.desktop` の読み取りで毎回
     * 待たされ、その間シートは空のまま出る。初回に読めたらここから即返し、取り直しは
     * シートの「更新」を押したときだけにする。
     *
     * ⚠ **空はしまわない。** 空になるのは GUI 未導入 / rootfs 未展開 / 取得失敗のときで、
     * 導入し終えたあとも空を返し続けると「入れたのに一覧に無い」から抜け出せなくなる。
     */
    private val cache = ConcurrentHashMap<String, List<GuiApp>>()

    /** 取得済みの一覧。まだ 1 度も読めていなければ null（＝ [load] を呼ぶ番）。 */
    fun cached(distroId: String): List<GuiApp>? = cache[distroId]

    /** 一覧を取る。取れなければ空を返す（例外は投げない: ☰ を押しただけで落ちないように）。 */
    suspend fun load(context: Context, distroId: String): List<GuiApp> = coroutineScope {
        val p = runCatching {
            ProotLauncher(context).launch(
                distroId = distroId,
                command = "/usr/local/bin/z2menu",
                extraArgs = listOf("list"),
            )
        }.getOrElse {
            // rootfs 未展開・z2menu 未配置。どちらも「まだ使えない」だけなので空で返す。
            Log.w(TAG, "z2menu を起こせなかった", it)
            return@coroutineScope emptyList()
        }
        // ⛔ **打ち切りを `withTimeoutOrNull` で書かないこと。** 中身は PTY からの
        // **ブロッキング read** で、キャンセルを一切見ない。時間が来ても read が返るまで
        // 何も起きず、「20 秒で打ち切る」は効かない。fd を閉じれば read は必ず失敗して返るので、
        // 打ち切りは PTY を閉じることで行う。
        val watchdog = launch(Dispatchers.Default) {
            delay(TIMEOUT_MS)
            Log.w(TAG, "z2menu list が ${TIMEOUT_MS}ms で返らなかった")
            runCatching { p.close() }
        }
        val raw = withContext(Dispatchers.IO) { drain(p) }
        watchdog.cancel()
        runCatching { p.close() }
        parse(raw).also { if (it.isNotEmpty()) cache[distroId] = it }
    }

    /**
     * PTY の出力を最後まで読む。
     *
     * ⛔ **`readBytes()` で一気に読んではいけない。** PTY は**ゲスト側が終わると master の
     * read が `EIO` で落ちる**（EOF が戻り値ではなく例外として出る）。`readBytes()` はその例外を
     * そのまま投げるので、**それまでに読めていた TSV ごと捨てられ、一覧は必ず空になる**
     * （0.8.499〜0.8.501 で「☰ に何も出ない」となっていた原因）。読めた分を貯めながら進み、
     * 例外は正常終了として扱う（PTY を読む他の場所 — `GuiSession.drainPty` /
     * `HeadlessRun` — と同じ形）。
     */
    private fun drain(p: PtyProcess): String {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(4096)
        try {
            while (out.size() < MAX_BYTES) {
                val n = p.reader.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
            }
        } catch (e: Exception) {
            // z2menu が終わって PTY が閉じた = 正常終了。ここまでに読めた分をそのまま使う。
            Log.d(TAG, "z2menu の PTY が閉じた (${out.size()} bytes)", e)
        }
        return String(out.toByteArray(), Charsets.UTF_8)
    }

    /**
     * `名前 <TAB> コマンド <TAB> 説明 <TAB> 端末 <TAB> 分類` を読む。
     *
     * 列が足りない行は捨てる（proot やシェルが何か 1 行出しても、一覧が壊れないように）。
     */
    fun parse(raw: String): List<GuiApp> =
        raw.replace("\r", "")
            .lineSequence()
            .mapNotNull { line ->
                val f = line.split('\t')
                if (f.size < 5) return@mapNotNull null
                val name = f[0].trim()
                val exec = f[1].trim()
                if (name.isEmpty() || exec.isEmpty()) return@mapNotNull null
                GuiApp(
                    name = name,
                    exec = exec,
                    comment = f[2].trim(),
                    terminal = f[3].trim() == "1",
                    category = f[4].trim(),
                )
            }
            .distinctBy { it.name to it.exec }
            .toList()
}
