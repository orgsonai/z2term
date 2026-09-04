package com.zerotoship.z2term.gui

import android.content.Context
import android.util.Log
import com.zerotoship.z2term.proot.ProotLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

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

    /** 一覧を取る。取れなければ空を返す（例外は投げない: ☰ を押しただけで落ちないように）。 */
    suspend fun load(context: Context, distroId: String): List<GuiApp> =
        withContext(Dispatchers.IO) {
            withTimeoutOrNull(TIMEOUT_MS) { read(context, distroId) } ?: run {
                Log.w(TAG, "z2menu list が ${TIMEOUT_MS}ms で返らなかった")
                emptyList()
            }
        }

    private fun read(context: Context, distroId: String): List<GuiApp> {
        val p = runCatching {
            ProotLauncher(context).launch(
                distroId = distroId,
                command = "/usr/local/bin/z2menu",
                extraArgs = listOf("list"),
            )
        }.getOrElse {
            // rootfs 未展開・z2menu 未配置。どちらも「まだ使えない」だけなので空で返す。
            Log.w(TAG, "z2menu を起こせなかった", it)
            return emptyList()
        }
        val raw = try {
            // z2menu が終われば proot も終わり PTY が閉じる = EOF。
            p.reader.readBytes().toString(Charsets.UTF_8)
        } catch (e: Exception) {
            Log.w(TAG, "z2menu の出力を読めなかった", e)
            ""
        } finally {
            runCatching { p.close() }
        }
        return parse(raw)
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
