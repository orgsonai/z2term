package com.zerotoship.z2term.gui.rdp

/** NTLM connection-oriented sealing用の状態を保持するRC4。 */
internal class Rc4(key: ByteArray) {
    private val state = IntArray(256) { it }
    private var i = 0
    private var j = 0

    init {
        require(key.isNotEmpty())
        var keyIndex = 0
        var swapIndex = 0
        for (index in state.indices) {
            swapIndex = (swapIndex + state[index] + (key[keyIndex].toInt() and 0xFF)) and 0xFF
            val tmp = state[index]
            state[index] = state[swapIndex]
            state[swapIndex] = tmp
            keyIndex = (keyIndex + 1) % key.size
        }
    }

    fun process(input: ByteArray): ByteArray = ByteArray(input.size) { index ->
        i = (i + 1) and 0xFF
        j = (j + state[i]) and 0xFF
        val tmp = state[i]
        state[i] = state[j]
        state[j] = tmp
        val keyByte = state[(state[i] + state[j]) and 0xFF]
        (input[index].toInt() xor keyByte).toByte()
    }
}
