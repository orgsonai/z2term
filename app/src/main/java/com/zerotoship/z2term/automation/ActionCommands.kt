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
            "screen" -> { count(1); "screen=${ActionRuntime.screen(context)}" }
            "start" -> { count(2); ActionRuntime.start(context, args[1]) }
            "status", "stop" -> {
                require(args.size in 1..2) { "status|stop [RUN_ID]" }
                (if (args[0] == "stop") ActionRuntime.stop(args.getOrNull(1)) else ActionRuntime.status(args.getOrNull(1))).toString()
            }
            "history" -> { count(1); ActionRuntime.history(context) }
            else -> throw IllegalArgumentException("See z2-action --help")
        }
    }
}
