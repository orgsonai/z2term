package com.zerotoship.z2term.gui

/**
 * GUI タブが繋ぎに行く**リモートの画面 1 件**。
 *
 * [com.zerotoship.z2term.gui.GuiSession] にこれを渡すと **z2gui も Linux 側も起動せず**、
 * ここに書かれた相手へ繋ぐだけのタブになる。プロトコルの違い (RFB / RDP) は
 * [createClient] の中だけに閉じ込め、タブの側は [RemoteDesktopClient] しか見ない。
 *
 * 保存は SSH の接続先 ([com.zerotoship.z2term.channel.SshProfile]) にぶら下がる
 * [com.zerotoship.z2term.channel.RemoteService] に相乗りしていて、実装クラスは
 * その 1 回の接続に必要な値だけを運ぶ入れ物。
 */
interface RemoteTarget {
    val host: String
    val port: Int

    /** タブに出す名前。 */
    val label: String

    /** この接続 1 回ぶんのクライアント。[GuiSession] の生成時に 1 度だけ呼ばれる。 */
    fun createClient(): RemoteDesktopClient

    /** SSH 一時転送など、このタブと同じ寿命を持つ通信経路を閉じる。2 度呼ばれてよい。 */
    fun closeTransport()
}
