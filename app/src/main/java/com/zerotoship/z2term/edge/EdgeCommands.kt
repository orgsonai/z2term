package com.zerotoship.z2term.edge

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import org.json.JSONObject
import androidx.core.net.toUri

object EdgeCommands {
    fun command(context: Context, args: List<String>): String {
        val store = EdgeRuntime.store(context)
        fun count(n: Int) { require(args.size == n) { "Wrong arguments; see z2-edge --help" } }
        fun reload() { EdgeRuntime.reload(context) }
        return when (args.firstOrNull()) {
            "permission" -> {
                count(1)
                context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                context.getString(com.zerotoship.z2term.R.string.edge_overlay_help)
            }
            "status" -> {
                count(1)
                JSONObject().put("enabled", store.enabled()).put("overlay_permission", Settings.canDrawOverlays(context))
                    .put("panels", store.panels().size).toString()
            }
            "on" -> { count(1); EdgeRuntime.on(context); "" }
            "off" -> { count(1); EdgeRuntime.off(context); "" }
            "toggle" -> { count(1); EdgeRuntime.onMain {
                if (store.enabled()) EdgeRuntime.off(context) else EdgeRuntime.on(context)
            }; "" }
            "reload" -> { count(1); reload(); "" }
            "open" -> {
                require(args.size == 2 || (args.size == 3 && args[2] == "--toggle")) { "open ID [--toggle]" }
                EdgeRuntime.open(args[1], toggle = args.size == 3); ""
            }
            "close" -> { count(1); EdgeRuntime.close(); "" }
            "list" -> {
                require(args.size in 1..2) { "list [panel]" }
                if (args.size == 1) {
                    val all = store.panels()
                    all.joinToString("\n") { p ->
                        val parent = all.firstOrNull { p.id in it.tabs }?.id
                        "${p.id}\t${if (parent != null) "tab:$parent" else p.handle}\t${p.fields["label"].orEmpty()}"
                    }
                }
                else store.panel(args[1]).items.joinToString("\n") { "${args[1]}:${it.id}\t${it.type}\t${it.fields["label"].orEmpty()}" }
            }
            "get" -> {
                count(2)
                val target = args[1]
                val values = if (':' in target) store.item(target).fields else store.panel(target).fields
                EdgeStore.encode(values)
            }
            "set" -> {
                require(args.size >= 3) { "set panel:item key=value ..." }
                store.setItem(args[1], fields(args.drop(2)))
                reload(); ""
            }
            "remove" -> { count(2); store.removeItem(args[1]); reload(); "" }
            "tab" -> {
                require(args.size in 3..4) { "tab PARENT ID [LABEL]" }
                store.addTab(args[1], args[2], args.getOrElse(3) { args[2] })
                reload(); args[2]
            }
            "delete" -> {
                count(2)
                store.directory(args[1]).also { require(it.isDirectory) { "No panel: ${args[1]}" } }
                EdgeRuntime.close() // Save active notes before deleting their owning directory.
                val removed = store.removePanel(args[1])
                reload()
                "Deleted ${args[1]} ($removed items)"
            }
            "panel" -> {
                require(args.size >= 3) { "panel ID key=value ..." }
                store.setPanel(args[1], fields(args.drop(2)))
                reload(); ""
            }
            "handle" -> {
                require(args.size >= 3 && args[2] in setOf("bar", "button", "off")) { "handle ID bar|button|off [options]" }
                val fields = linkedMapOf("handle" to args[2], "run" to "")
                var i = 3
                while (i < args.size) {
                    val option = args[i++]
                    require(i < args.size) { "Missing value for $option" }
                    val value = args[i++]
                    when (option) {
                        "--side" -> fields["side"] = value
                        "--offset", "--length" -> fields[option.removePrefix("--")] = value.removeSuffix("%")
                        "--size", "--run", "--label", "--alpha", "--open", "--bar-color" -> fields[option.removePrefix("--")] = value
                        "--at" -> {
                            val point = value.split(',')
                            require(point.size == 2) { "--at X%,Y%" }
                            fields["x"] = point[0].removeSuffix("%")
                            fields["y"] = point[1].removeSuffix("%")
                        }
                        else -> throw IllegalArgumentException("Unknown option: $option")
                    }
                }
                store.setPanel(args[1], fields)
                reload(); ""
            }
            "push", "state" -> {
                count(3); EdgeRuntime.push(context, args[1], args[2], state = args[0] == "state"); ""
            }
            "badge" -> {
                count(3); EdgeRuntime.badge(context, args[1], args[2]); ""
            }
            else -> throw IllegalArgumentException("See z2-edge --help")
        }
    }

    private fun fields(args: List<String>): Map<String, String> {
        require(args.all { '=' in it && '\n' !in it && '\r' !in it }) { "Use one key=value argument per field" }
        return EdgeStore.parse(args.joinToString("\n"))
    }
}
