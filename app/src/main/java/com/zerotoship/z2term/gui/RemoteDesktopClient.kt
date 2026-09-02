package com.zerotoship.z2term.gui

import android.graphics.Bitmap
import kotlinx.coroutines.flow.StateFlow

/**
 * GUI タブが使うリモートデスクトップクライアントの共通境界。
 *
 * 描画側は [frame] / [redraw]、入力側は [sendPointerEvent] / [sendKeyEvent] だけを使い、
 * RFB や RDP の接続手順・画面更新形式には触れない。実装は受信した画面を ARGB_8888 の
 * [Bitmap] に反映し、変更のたびに [redraw] を更新する。
 *
 * キー番号は既存 GUI 入力経路との互換のため X11 keysym を共通表現にする。RFB 実装は
 * そのまま送信し、RDP 実装は内部で scancode / Unicode input へ変換する。
 */
interface RemoteDesktopClient {
    val width: Int
    val height: Int
    val desktopName: String

    /** 描画中の更新と競合しないよう [frameLock] の内側で読む。 */
    val frame: Bitmap?
    val frameLock: Any
    val redraw: StateFlow<Int>

    /** リモート側でコピーされたテキスト。対応しないプロトコルでは呼ばれない。 */
    var onRemoteClipboardText: ((String) -> Unit)?

    /** 同期接続とプロトコル初期化。IO スレッドで呼ぶ。 */
    fun connect(timeoutMs: Int = DEFAULT_CONNECT_TIMEOUT_MS)

    /** 受信ループ。[close] または接続断までブロックするため IO スレッドで呼ぶ。 */
    fun run()

    /** 正規化したボタン状態とフレームバッファ絶対座標を送る。UI スレッドから呼んでよい。 */
    fun sendPointerEvent(buttonMask: Int, x: Int, y: Int)

    /** X11 keysym の押下・解放を送る。UI スレッドから呼んでよい。 */
    fun sendKeyEvent(keysym: Int, down: Boolean)

    /** Android 側でコピーされたテキストを相手のクリップボードへ送る。非対応なら何もしない。 */
    fun sendClipboardText(text: String) = Unit

    /**
     * 相手がコピーしたファイルの置き場を渡す。⚠ **接続する前に渡すこと** (対応を宣言するかどうかが
     * これで決まる)。非対応のプロトコルでは何もしない。
     */
    fun setClipboardFileSink(sink: ClipboardFiles.Sink?) = Unit

    /** Android 側でコピーされたファイルを相手へ差し出す。null で取り下げる。非対応なら何もしない。 */
    fun offerClipboardFiles(source: ClipboardFiles.Source?) = Unit

    fun tapKey(keysym: Int) {
        sendKeyEvent(keysym, down = true)
        sendKeyEvent(keysym, down = false)
    }

    /**
     * 端末の枠に合わせてデスクトップの大きさを変えてよい相手か。
     *
     * ⚠ **決めるのはプロトコルではなく「その画面が誰のものか」。** RDP は接続のたびに
     * こちら専用のセッションを作らせるので変えてよい。RFB で覗きに行く先は**もう立っている
     * 実画面**なので、こちらの枠に合わせると相手の画面まで変えてしまう。
     */
    val ownsDesktopSize: Boolean get() = false

    /** 対応する相手へデスクトップサイズ変更を要求する。非対応なら何もしない。 */
    fun requestDesktopSize(width: Int, height: Int) = Unit

    fun close()

    companion object {
        const val DEFAULT_CONNECT_TIMEOUT_MS = 8_000

        // プロトコル非依存のポインターボタン状態。各実装が wire 上のフラグへ変換する。
        const val BUTTON_LEFT = 1 shl 0
        const val BUTTON_MIDDLE = 1 shl 1
        const val BUTTON_RIGHT = 1 shl 2
        const val BUTTON_WHEEL_UP = 1 shl 3
        const val BUTTON_WHEEL_DOWN = 1 shl 4
    }
}
