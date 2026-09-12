package com.zerotoship.z2term.service

import java.net.InetAddress
import java.net.ServerSocket
import java.util.UUID

/** A terminal owns its outputs; each command has a separate PulseAudio server and lease. */
class TerminalAudioOutputs {
    private val outputs = mutableMapOf<String, AudioBridge>()

    @Synchronized
    fun open(): String {
        val port = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { it.localPort }
        val token = UUID.randomUUID().toString()
        // The guest binds this port next. If another process wins the race, startup fails
        // and the wrapper releases this lease without launching the requested command.
        outputs[token] = AudioBridge(port, lowLatency = true).also { it.start() }
        return "$token $port"
    }

    @Synchronized
    fun ready(token: String): Boolean = outputs[token]?.connected == true

    @Synchronized
    fun close(token: String) { outputs.remove(token)?.stop() }

    @Synchronized
    fun closeAll() {
        outputs.values.forEach { it.stop() }
        outputs.clear()
    }
}
