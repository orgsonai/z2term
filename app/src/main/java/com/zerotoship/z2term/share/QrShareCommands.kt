package com.zerotoship.z2term.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import org.json.JSONObject

internal object QrShareCommands {
    fun command(context: Context, args: List<String>): String {
        when (args.firstOrNull()) {
            "show" -> { require(args.size == 1); show(context) }
            "start" -> {
                require(args.size == 4) { "qr-share start FILE HOME DISTRO" }
                val file = QrShareManager.cliFile(context, args[1], args[2], args[3])
                QrShareManager.start(context, Uri.fromFile(file), file.name)
                // A notification remains available if Android prevents a background activity launch.
                runCatching { show(context) }
            }
            "stop" -> { require(args.size == 1); QrShareManager.stop(context) }
            "status" -> require(args.size == 1)
            else -> error("qr-share show | start FILE HOME DISTRO | status | stop")
        }
        val state = QrShareManager.state.value
        return JSONObject().put("id", state.id).put("state", state.phase.name.lowercase(java.util.Locale.ROOT))
            .put("name", state.name).put("size", state.size).put("url", state.url)
            .put("expires", state.expires).put("sent", state.sent).put("error", state.error).toString()
    }

    private fun show(context: Context) {
        context.startActivity(Intent(context, QrShareActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
