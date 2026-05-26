package com.zerotoship.z2term.gui

import android.content.Context
import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.zerotoship.z2term.core.SessionManager
import java.io.File
import java.io.RandomAccessFile

/**
 * 端末タブ内で実行された `z2run <gui-app>` から飛んでくる「GUI タブを開いてくれ」通知を監視する
 * シングルトン (P3 = CUI⇄GUI 連動)。
 *
 * **流れ**:
 *  1. 端末 (proot) 内で `z2run python gui.py` 等が実行される
 *  2. `z2run` (Z2RunScript) が `/storage/app/z2gui.events` に `OPEN <display>` を append する
 *  3. proot バインドにより、このファイルは Android 側の
 *     `getExternalFilesDir(null) + "/z2gui.events"` と同じ実体
 *  4. ここの [FileObserver] が MODIFY を受け取り、新規追記行をパースして
 *     [SessionManager.openGuiForDisplay] で対応する GUI タブを開く / 前面化
 *
 * **設計上の注意**:
 *  - **追記専用**: ファイルを切り詰めない。`offset` を内部で進めて新規分だけ読む。
 *    `start()` 時にいきなり「アプリ起動前に書かれた古い行」を全部消化すると望まない GUI タブが
 *    自動で開いてしまうので、起動時は **ファイル末尾までシーク** して過去分を捨てる。
 *  - 1 プロセス 1 インスタンス想定 (Application.onCreate から start)。サービス停止時に
 *    [stop] を呼ぶ必要性は薄い (FileObserver はプロセス終了で自然に解放される) が、
 *    `BroadcastReceiver` 等から再初期化したい場合に備えて [stop]/[start] を分けている。
 *  - FileObserver は **存在しないファイル** を監視できないため、起動時に空ファイルを `touch` で
 *    作っておく (`/storage/app` ディレクトリも事前に mkdirs)。
 *  - スレッド: FileObserver のコールバックは内部ハンドラスレッドで呼ばれる。
 *    UI への影響は [SessionManager] が StateFlow 経由で吸収するので Main へ post 不要だが、
 *    Activity が無いと「タブを作っても表示先が無い」状態。これは MainActivity 側が
 *    `activeId` を監視しているので、次回 Activity 起動時に自然と最新のアクティブが選ばれる。
 */
object GuiEventWatcher {

    private const val TAG = "GuiEventWatcher"
    private const val FILENAME = "z2gui.events"

    private var observer: FileObserver? = null
    private var watchedFile: File? = null
    @Volatile private var offset: Long = 0L
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Application.onCreate から呼ぶ。既に動いていれば何もしない (idempotent)。 */
    fun start(context: Context) {
        if (observer != null) return
        val appCtx = context.applicationContext
        // /storage/app は proot 内のバインド先。Android 側実体は getExternalFilesDir(null)。
        val dir = appCtx.getExternalFilesDir(null)
        if (dir == null) {
            Log.w(TAG, "external files dir unavailable — cannot start Watcher")
            return
        }
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, FILENAME)
        try {
            if (!file.exists()) file.createNewFile()
        } catch (e: Exception) {
            Log.w(TAG, "cannot create $FILENAME", e); return
        }
        watchedFile = file
        // 過去分を消化しないよう、起動時はファイル末尾までシーク (アプリ再起動跨ぎで OPEN が
        // 自動再生されるのを防ぐ)。
        offset = file.length()

        val obs = object : FileObserver(file, MODIFY or CREATE) {
            override fun onEvent(event: Int, path: String?) {
                if ((event and MODIFY) == 0 && (event and CREATE) == 0) return
                drainNewLines(appCtx)
            }
        }
        obs.startWatching()
        observer = obs
        Log.i(TAG, "watching ${file.absolutePath} (start offset=$offset)")
    }

    fun stop() {
        observer?.stopWatching()
        observer = null
        watchedFile = null
        offset = 0L
    }

    /** 末尾追記分を読み、行ごとに [handleLine] へ渡す。読み終わった位置を [offset] に保存。 */
    private fun drainNewLines(context: Context) {
        val file = watchedFile ?: return
        try {
            RandomAccessFile(file, "r").use { raf ->
                val end = raf.length()
                if (end <= offset) {
                    // 切り詰められたケース (めったに無いが安全策): 末尾基準にリセット。
                    if (end < offset) offset = end
                    return
                }
                raf.seek(offset)
                while (raf.filePointer < end) {
                    val line = raf.readLine() ?: break
                    val trimmed = line.trim()
                    if (trimmed.isNotEmpty()) handleLine(context, trimmed)
                }
                offset = raf.filePointer
            }
        } catch (e: Exception) {
            Log.w(TAG, "events read failed", e)
        }
    }

    /** "OPEN N" を解釈して GUI タブを開かせる。未知の行は無視。 */
    private fun handleLine(context: Context, line: String) {
        // 形式: "OPEN <display>" のみサポート。空白区切りで素朴に解析する。
        val parts = line.split(Regex("\\s+"))
        if (parts.size < 2 || parts[0] != "OPEN") {
            Log.d(TAG, "unknown event: $line"); return
        }
        val display = parts[1].toIntOrNull()
        if (display == null || display <= 0) {
            Log.w(TAG, "invalid display number: $line"); return
        }
        Log.i(TAG, "OPEN display=$display")
        // SessionManager 操作 (StateFlow 更新) はメインで実行。Compose 側は collectAsState で
        // 拾うので、Activity 不在でも内部状態は正しく整う (起動時に最新の active が選ばれる)。
        mainHandler.post {
            try {
                SessionManager.openGuiForDisplay(context, display)
            } catch (e: Exception) {
                Log.w(TAG, "GUI tab creation failed (display=$display)", e)
            }
        }
    }
}
