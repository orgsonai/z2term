package com.zerotoship.z2term.channel

/**
 * シリアルのタブの口 (1.0.209)。USB ([SerialChannel]) とネットワーク越し ([TcpSerialChannel]) で共通の顔。
 * AI の `serial_*` 道具はこれだけを見る (どちらの口でも同じように読み書きできるように)。
 */
interface SerialLink : ProcessChannel {
    /** 受け取った分の控え。 */
    val transcript: SerialTranscript

    /** いま相手とつながっているか (ケーブルが抜けている・ネットワークが切れている間は false)。 */
    val connected: Boolean

    /** つながっている相手の名前 (つながっていなければ null)。 */
    val deviceLabel: String?
}
