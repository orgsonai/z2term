package com.zerotoship.z2term.automation

import android.content.Context

internal object ActionCommands {
    fun command(context: Context, args: List<String>): String {
        fun count(n: Int) { require(args.size == n) { "Wrong arguments; see z2-action --help" } }
        val store = ActionRuntime.store(context)
        return when (args.firstOrNull()) {
            "list" -> { count(1); store.names().joinToString("\n") }
            "show" -> { count(2); store.read(args[1]).text }
            "save" -> { count(3); store.save(args[1], args[2], ActionRuntime.screen(context)); args[1] }
            "delete" -> { count(2); store.delete(args[1]); "" }
            "inspect" -> {
                count(2)
                val entries = kotlinx.coroutines.runBlocking { com.zerotoship.z2term.edge.AndroidActions.inspectUi(args[1]) }
                org.json.JSONArray().apply { entries.forEach { entry ->
                    put(org.json.JSONObject().put("id", entry.id ?: org.json.JSONObject.NULL)
                        .put("text", entry.text ?: org.json.JSONObject.NULL)
                        .put("desc", entry.description ?: org.json.JSONObject.NULL))
                } }.toString()
            }
            "screen" -> { count(1); "screen=${ActionRuntime.screen(context)}" }
            "start" -> { count(2); ActionRuntime.start(context, args[1]) }
            "status", "stop", "pause", "resume" -> {
                require(args.size in 1..2) { "status|stop|pause|resume [RUN_ID]" }
                val id = args.getOrNull(1)
                when (args[0]) {
                    "stop" -> ActionRuntime.stop(id)
                    "pause" -> ActionRuntime.pause(id)
                    // From the shell, z2term is not moved away; the run waits for another screen as at the start.
                    "resume" -> ActionRuntime.resume(id)
                    else -> ActionRuntime.status(id)
                }.toString()
            }
            "history" -> { count(1); ActionRuntime.history(context) }
            else -> throw IllegalArgumentException("See z2-action --help")
        }
    }
}
