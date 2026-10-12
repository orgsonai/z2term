package com.zerotoship.z2term.edge

/** Main-thread state for one panel item, independent of its current overlay view. */
internal class EdgeTerminalState(private val create: () -> EdgeTerminalSession) : AutoCloseable {
    private var session: EdgeTerminalSession? = null
    var draft = ""
    val history = EdgeTerminalHistory()

    fun state(): EdgeTerminalSession.State? = session?.state()

    /** Width of the result view in characters; kept here so a shell started later gets it too. */
    var columns = EdgeTerminalSession.COLUMNS
        set(value) { field = value; session?.resize(value) }

    fun execute(command: String): Boolean {
        val current = session ?: create().also { session = it; it.resize(columns) }
        return current.execute(command)
    }

    fun answer(text: String): Boolean = session?.answer(text) ?: false

    /**
     * While a command runs, ends that command and keeps the shell. Otherwise, or when the running
     * command cannot be signalled on its own, ends the session as before.
     */
    fun stop() {
        val current = session ?: return
        if (current.state().phase != EdgeTerminalSession.Phase.RUNNING || !current.interrupt()) current.close()
    }

    private fun end() { session?.close() }

    /** Reset only on an explicit refresh; a stopped shell is never silently replaced. */
    fun refresh() {
        end()
        session = null
        draft = ""
        history.load(emptyList())
    }

    override fun close() { end() }
}
