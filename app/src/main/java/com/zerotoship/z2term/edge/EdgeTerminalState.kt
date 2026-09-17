package com.zerotoship.z2term.edge

/** Main-thread state for one panel item, independent of its current overlay view. */
internal class EdgeTerminalState(private val create: () -> EdgeTerminalSession) : AutoCloseable {
    private var session: EdgeTerminalSession? = null
    var draft = ""
    val history = EdgeTerminalHistory()

    fun state(): EdgeTerminalSession.State? = session?.state()

    fun execute(command: String): Boolean {
        val current = session ?: create().also { session = it }
        return current.execute(command)
    }

    fun stop() { session?.close() }

    /** Reset only on an explicit refresh; a stopped shell is never silently replaced. */
    fun refresh() {
        stop()
        session = null
        draft = ""
        history.load(emptyList())
    }

    override fun close() { stop() }
}
