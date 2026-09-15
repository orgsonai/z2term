package com.zerotoship.z2term.automation

/** Wait for the editor to leave and the next foreground app to settle before dispatching actions. */
internal class ActionForegroundWait(private val schedule: (Long, () -> Unit) -> (() -> Unit)) {
    fun start(ownPackage: String, focusedPackage: () -> String?, timeoutError: String,
        done: (String?) -> Unit): () -> Unit {
        var candidate: String? = null
        var observations = 0
        return ActionUiWait(schedule).start(5000, { callback ->
            val focused = focusedPackage()?.takeUnless { it == ownPackage }
            observations = if (focused == null) 0 else if (focused == candidate) observations + 1 else 1
            candidate = focused
            // Three observations at 250 ms intervals cover a half-second of stable focus.
            val noWork: () -> Unit = {}
            callback(observations >= 3, null)
            noWork
        }, done, timeoutError)
    }
}
