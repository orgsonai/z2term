package com.zerotoship.z2term.edge

/** Main-thread action leases prevent late completions from changing a newer run. */
internal class EdgeButtonRuns {
    data class Run(val panel: String, val item: EdgeStore.Item, val revision: Int)
    private val runs = mutableMapOf<String, Run>()

    fun start(token: String, run: Run) {
        cancel(run.panel, run.item.id)
        runs[token] = run
    }
    fun get(token: String): Run? = runs[token]
    fun finish(token: String) { runs.remove(token) }
    fun running(panel: String, item: EdgeStore.Item): Boolean = runs.values.any { it.panel == panel && it.item == item }
    fun cancel(panel: String, id: String) { runs.entries.removeAll { it.value.panel == panel && it.value.item.id == id } }
    fun clear() { runs.clear() }
}
