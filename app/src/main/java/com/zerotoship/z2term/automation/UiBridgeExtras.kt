package com.zerotoship.z2term.automation

import android.app.PendingIntent
import android.content.Context
import android.os.SystemClock
import android.util.Base64
import com.zerotoship.z2term.BuildConfig
import com.zerotoship.z2term.R
import com.zerotoship.z2term.edge.AndroidUiLook
import com.zerotoship.z2term.service.NotificationLogService
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/*
 * z2-ui の第 3 段 (docs/ja/AI-BRIDGE.md §6・§7・§8)。可否の判定は UiBridge が済ませてから呼ぶ。
 *  - UiBridgeRecorder: 成功した操作を控え、`save` で z2-action の定義にする
 *  - UiBridgeNotes: アプリごとの覚え書き (~/.z2term/ui/notes/<パッケージ名>.md)
 *  - UiBridgeNotifications: 通知の一覧とボタン
 *  - UiBridgeMcp: MCP (AI とツールをつなぐ取り決め) の受け口
 */

/** `mark` から `save` までの操作の控え。メモリだけに持つ (再起動・停止で消える)。 */
internal object UiBridgeRecorder {
    private const val MAX_STEPS = 200
    private val steps = mutableListOf<Pair<UiBridgeReplay.Step, Long>>()

    @Synchronized fun record(verb: String, args: List<String>) {
        val step = runCatching {
            UiBridgeReplay.step(verb, args) { n ->
                AndroidUiLook.lastElement(n)?.let { UiBridgeReplay.Named(it.id, it.text, it.desc, it.box.centerX(), it.box.centerY()) }
            }
        }.getOrNull() ?: return
        if (step.line == null && step.unsupported == null) return
        steps += step to SystemClock.elapsedRealtime()
        if (steps.size > MAX_STEPS) steps.removeAt(0)
    }

    @Synchronized fun mark() = steps.clear()

    @Synchronized fun save(app: Context, name: String): JSONObject {
        if (steps.isEmpty()) throw Refusal("nothing_recorded", "No operations since the last mark")
        steps.indexOfFirst { it.first.unsupported != null }.takeIf { it >= 0 }?.let {
            throw Refusal("unsupported_step", "Operation ${it + 1} (${steps[it].first.unsupported}) cannot be replayed by z2-action; " +
                "run z2-ui mark and record again without it")
        }
        val store = ActionRuntime.store(app)
        // 利用者が作ったマクロを AI が上書きしないよう、同じ名前があれば断る。
        if (name in store.names()) throw Refusal("exists", "An action macro named $name already exists; choose another name")
        val text = UiBridgeReplay.definition(steps)
        try { store.save(name, text, ActionRuntime.screen(app)) } catch (e: IllegalArgumentException) {
            throw Refusal("bad_args", e.message ?: "Invalid macro")
        }
        val count = steps.size
        steps.clear()
        return UiBridge.success().put("name", name).put("path", "/root/.z2term/actions/$name.actions")
            .put("operations", count).put("definition", text)
    }
}

/**
 * アプリごとの覚え書き。中身は AI が書く平文で、人も読んで直せる。
 * 1 行目に書いた時点のアプリの版を残し、`look` で「いまの版のものか」を返す。
 */
internal object UiBridgeNotes {
    private const val MAX_BYTES = 65_536
    private val HEADER = Regex("^<!-- z2-ui app-version: (.*) -->$")

    private fun file(app: Context, pkg: String): File {
        try { ActionDefinition.packageName(pkg) } catch (_: IllegalArgumentException) { throw Refusal("bad_args", "Invalid package name") }
        return File(app.filesDir, "shared_home/.z2term/ui/notes/$pkg.md")
    }

    fun appVersion(app: Context, pkg: String): String? = runCatching {
        val info = app.packageManager.getPackageInfo(pkg, 0)
        "${info.versionName} (${info.longVersionCode})"
    }.getOrNull()

    private fun split(raw: String): Pair<String?, String> {
        val first = raw.substringBefore('\n')
        val noted = HEADER.matchEntire(first)?.groupValues?.get(1)
        return noted to if (noted != null) raw.substringAfter('\n', "") else raw
    }

    fun summary(app: Context, pkg: String): JSONObject {
        val f = runCatching { file(app, pkg) }.getOrNull()
        if (f == null || !f.isFile) return JSONObject().put("exists", false)
        val noted = runCatching { split(f.readText()).first }.getOrNull()
        return JSONObject().put("exists", true).put("current", noted != null && noted == appVersion(app, pkg))
    }

    fun read(app: Context, pkg: String): JSONObject {
        val f = file(app, pkg)
        val out = UiBridge.success().put("package", pkg).put("path", "/root/.z2term/ui/notes/$pkg.md")
            .put("app_version", appVersion(app, pkg) ?: JSONObject.NULL)
        if (!f.isFile) return out.put("exists", false)
        val (noted, text) = split(f.readText())
        return out.put("exists", true).put("noted_version", noted ?: JSONObject.NULL).put("text", text)
    }

    fun write(app: Context, pkg: String, text: String): JSONObject {
        if (text.toByteArray(Charsets.UTF_8).size > MAX_BYTES) throw Refusal("bad_args", "Notes must be at most 64 KiB")
        val f = file(app, pkg)
        val version = appVersion(app, pkg) ?: throw Refusal("not_found", "$pkg is not installed")
        f.parentFile?.mkdirs()
        val tmp = File(f.parentFile, ".${f.name}.tmp")
        tmp.writeText("<!-- z2-ui app-version: $version -->\n$text")
        if (!tmp.renameTo(f)) throw Refusal("error", "Could not write the notes")
        return UiBridge.success().put("package", pkg).put("path", "/root/.z2term/ui/notes/$pkg.md").put("app_version", version)
    }
}

/** 通知の一覧とボタン。読むのは段階が look 以上のアプリだけ、押すのは操作として扱う。 */
internal object UiBridgeNotifications {
    private fun active() = NotificationLogService.uiNotifications()
        ?: throw Refusal("no_notification_access", "Turn on notification access for z2term")

    fun list(app: Context, level: (String) -> UiBridgePolicy.Level): JSONObject {
        val items = active().filter { level(it.pkg).rank >= UiBridgePolicy.Level.LOOK.rank }
        return UiBridge.success().put("notifications", JSONArray(items.map { n ->
            JSONObject().put("key", n.key).put("package", n.pkg).put("app", UiBridge.label(app, n.pkg))
                .put("title", n.title).put("text", n.text)
                .put("actions", JSONArray(n.actions.indices.map { JSONObject().put("label", n.actions[it]).put("reply", n.replies[it]) }))
        }))
    }

    /** [gate] は通知を出したアプリと「何をするか」で判定する (承認待ちを含む)。 */
    fun press(app: Context, key: String, label: String, reply: String?, gate: (String, String) -> Unit): JSONObject {
        if (reply != null && reply.length > 2000) throw Refusal("bad_args", "Reply must be at most 2000 characters")
        val n = active().firstOrNull { it.key == key } ?: throw Refusal("not_found", "No notification with that key; list again")
        val matches = n.actions.indices.filter { n.actions[it] == label }
        val index = when (matches.size) {
            1 -> matches[0]
            0 -> throw Refusal("not_found", "The notification has no button named $label")
            else -> throw Refusal("ambiguous", "Several buttons are named $label")
        }
        if (n.replies[index] && reply == null) throw Refusal("bad_args", "This button needs reply text")
        if (!n.replies[index] && reply != null) throw Refusal("bad_args", "This button has no reply field")
        val name = UiBridge.label(app, n.pkg)
        gate(n.pkg, if (reply != null) app.getString(R.string.ui_bridge_op_noti_reply, name, label,
            if (reply.length > 80) reply.take(80) + "…" else reply) else app.getString(R.string.ui_bridge_op_noti, name, label))
        try {
            NotificationLogService.pressUiAction(key, index, label, reply)
        } catch (e: IllegalStateException) {
            throw Refusal("stale", e.message ?: "The notification changed; list it again")
        } catch (e: PendingIntent.CanceledException) {
            throw Refusal("rejected", "The app no longer accepts this button")
        }
        return UiBridge.success()
    }
}

/**
 * MCP の受け口 (`z2-ui mcp`)。JSON-RPC 2.0 を 1 行ずつ受け、1 行で返す (通知には何も返さない)。
 * 外部ライブラリは使わない (やり取りは JSON を 1 行ずつ受け渡すだけ)。道具の中身は [UiBridge.execute] で、
 * CLI と同じ判定・記録を通る。
 */
internal object UiBridgeMcp {
    private val VERSIONS = listOf("2025-06-18", "2025-03-26", "2024-11-05")
    /** 接続したときに AI へ渡す説明。CLI の `z2-ui guide` と同じ文面 ([UiBridgeGuide])。 */
    private val INSTRUCTIONS get() = UiBridgeGuide.TEXT

    fun handle(app: Context, line: String): String {
        val request = try { JSONObject(line) } catch (_: Exception) { return error(JSONObject.NULL, -32700, "Parse error") }
        // id の無いもの (notifications/initialized など) は通知。返事を書かない。
        if (!request.has("id")) return ""
        val id = request.get("id")
        val params = request.optJSONObject("params") ?: JSONObject()
        return when (val method = request.optString("method")) {
            "initialize" -> result(id, JSONObject()
                .put("protocolVersion", params.optString("protocolVersion").takeIf { it in VERSIONS } ?: VERSIONS[0])
                .put("capabilities", JSONObject().put("tools", JSONObject().put("listChanged", false)))
                .put("serverInfo", JSONObject().put("name", "z2term").put("version", BuildConfig.VERSION_NAME))
                .put("instructions", INSTRUCTIONS))
            "ping" -> result(id, JSONObject())
            "tools/list" -> result(id, JSONObject().put("tools", tools()))
            "tools/call" -> result(id, call(app, params.optString("name"), params.optJSONObject("arguments") ?: JSONObject()))
            else -> error(id, -32601, "Method not found: $method")
        }
    }

    private fun result(id: Any, value: JSONObject) = JSONObject().put("jsonrpc", "2.0").put("id", id).put("result", value).toString()
    private fun error(id: Any, code: Int, message: String) = JSONObject().put("jsonrpc", "2.0").put("id", id)
        .put("error", JSONObject().put("code", code).put("message", message)).toString()

    private fun call(app: Context, name: String, a: JSONObject): JSONObject {
        // ガイドは判定も記録も要らない (CLI では z2-ui --help がこれを出す)。
        if (name == "guide") return JSONObject().put("isError", false)
            .put("content", JSONArray().put(JSONObject().put("type", "text").put("text", UiBridgeGuide.TEXT)))
        fun text(key: String) = a.optString(key).takeIf { a.has(key) && it.isNotEmpty() }
            ?: throw Refusal("bad_args", "$name needs $key")
        fun optional(key: String) = if (a.has(key) && !a.isNull(key)) a.get(key).toString() else null
        val result = try {
            val (verb, args) = when (name) {
                "front", "status", "mark" -> name to emptyList()
                "look" -> "look" to buildList {
                    if (a.optBoolean("all")) add("--all")
                    if (a.optBoolean("tree")) add("--tree")
                    optional("in")?.let { add("--in"); add(it) }
                }
                // 画像は会話に入るので、既定は半分の大きさの JPEG にする (小さく、書き出しも速い)。
                "shot" -> "shot" to listOfNotNull("--scale", optional("scale") ?: "0.5", "--jpeg".takeIf { a.optString("format", "jpeg") != "png" })
                "drag" -> "drag" to buildList {
                    val points = a.optJSONArray("points") ?: throw Refusal("bad_args", "drag needs points")
                    for (i in 0 until points.length()) {
                        val xy = points.optJSONArray(i) ?: throw Refusal("bad_args", "Each point is [x, y]")
                        add("${xy.optInt(0, -1)},${xy.optInt(1, -1)}")
                    }
                    optional("step")?.let { add("--step"); add(it) }
                }
                "tap", "long", "enter" -> name to listOf(text("target"))
                "type" -> "type" to listOfNotNull(text("target"), a.optString("text"), "--append".takeIf { a.optBoolean("append") })
                "key" -> "key" to listOf(text("name"))
                "open" -> "open" to listOf(text("target"))
                "wait" -> "wait" to listOfNotNull(text("selector"), optional("ms"))
                "wait_change" -> "wait-change" to listOfNotNull(optional("ms"))
                "scroll" -> "scroll" to listOf(text("target"), text("direction"))
                "swipe" -> "swipe" to listOfNotNull(text("x1"), text("y1"), text("x2"), text("y2"), optional("ms"))
                "notes" -> "notes" to listOf(text("package"))
                "write_notes" -> "note" to listOf(text("package"), a.optString("text"))
                "save" -> "save" to listOf(text("name"))
                "notifications" -> "noti" to listOf("list")
                "press_notification" -> "noti" to listOfNotNull("press", text("key"), text("label"), optional("reply"))
                else -> throw Refusal("bad_args", "Unknown tool: $name")
            }
            UiBridge.execute(app, verb, args)
        } catch (e: Refusal) { UiBridge.failure(e.code, e.message.orEmpty()) }
        val content = JSONArray().put(JSONObject().put("type", "text").put("text", result.toString()))
        if (name == "shot" && result.optBoolean("ok")) runCatching {
            val file = result.getString("path").substringAfterLast('/')
            val bytes = File(app.filesDir, "shared_home/.z2term/ui/$file").readBytes()
            content.put(JSONObject().put("type", "image").put("mimeType", if (file.endsWith(".jpg")) "image/jpeg" else "image/png")
                .put("data", Base64.encodeToString(bytes, Base64.NO_WRAP)))
        }
        return JSONObject().put("content", content).put("isError", !result.optBoolean("ok"))
    }

    private fun tools(): JSONArray {
        fun prop(type: String, description: String, values: List<String>? = null) = JSONObject().put("type", type)
            .put("description", description).apply { values?.let { put("enum", JSONArray(it)) } }
        val target = prop("string", "Element: \"#N\" from the last look, \"text=…\", \"desc=…\", \"id=PACKAGE:id/NAME\" or \"@X,Y\" (pixels)")
        fun tool(name: String, description: String, props: Map<String, JSONObject> = emptyMap(), required: List<String> = emptyList()) =
            JSONObject().put("name", name).put("description", description).put("inputSchema", JSONObject().put("type", "object")
                .put("properties", JSONObject(props as Map<*, *>)).put("required", JSONArray(required)))
        return JSONArray(listOf(
            tool("front", "Foreground app: package, name, permission level, screen size, whether locked."),
            tool("look", "Read the foreground screen as numbered elements with role, text, position and available operations.",
                mapOf("all" to prop("boolean", "Include containers without text or operations"),
                    "tree" to prop("boolean", "Nest elements by parent/child"),
                    "in" to prop("integer", "Only inside element N of the last look"))),
            tool("shot", "Capture only the foreground app window as an image. Screen = box.x,y + image.x,y / scale.",
                mapOf("scale" to prop("number", "0.1..1, default 0.5"), "format" to prop("string", "jpeg (default) or png", listOf("jpeg", "png")))),
            tool("drag", "Touch the first point and move through all points without lifting, as one gesture (puzzle games and drawing). " +
                "Plan the whole path before calling; the move takes step ms per segment.",
                mapOf("points" to JSONObject().put("type", "array").put("description", "2..256 points [x, y] in screen pixels")
                    .put("items", JSONObject().put("type", "array").put("items", JSONObject().put("type", "integer"))),
                    "step" to prop("integer", "Milliseconds per segment, 20..1000, default 100")), listOf("points")),
            tool("tap", "Tap an element.", mapOf("target" to target), listOf("target")),
            tool("long", "Long-press an element.", mapOf("target" to target), listOf("target")),
            tool("type", "Replace (or append to) the text of an input field. Never works on password fields.",
                mapOf("target" to target, "text" to prop("string", "Text, up to 4096 characters"),
                    "append" to prop("boolean", "Add to the end instead of replacing")), listOf("target", "text")),
            tool("enter", "Press Enter (search, send…) in an input field.", mapOf("target" to target), listOf("target")),
            tool("key", "Android global action.", mapOf("name" to prop("string", "back, home or recents", listOf("back", "home", "recents"))), listOf("name")),
            tool("open", "Open an app by package name, or a URI.", mapOf("target" to prop("string", "Package name or URI")), listOf("target")),
            tool("wait", "Wait until an element appears.", mapOf("selector" to prop("string", "text=…, desc=… or id=…"),
                "ms" to prop("integer", "1..30000, default 5000")), listOf("selector")),
            tool("wait_change", "Wait until the screen changes from the last look.", mapOf("ms" to prop("integer", "1..30000, default 5000"))),
            tool("scroll", "Scroll a list by one screen.", mapOf("target" to target,
                "direction" to prop("string", "Direction", listOf("up", "down", "left", "right"))), listOf("target", "direction")),
            tool("swipe", "Swipe by pixel coordinates inside the foreground app window.", mapOf(
                "x1" to prop("integer", "Start x"), "y1" to prop("integer", "Start y"), "x2" to prop("integer", "End x"),
                "y2" to prop("integer", "End y"), "ms" to prop("integer", "50..3000, default 300")), listOf("x1", "y1", "x2", "y2")),
            tool("notes", "Read your notes for an app, with the app version they were written for.",
                mapOf("package" to prop("string", "Package name")), listOf("package")),
            tool("write_notes", "Replace your notes for an app (plain text, up to 64 KiB). Prefer resource IDs over labels.",
                mapOf("package" to prop("string", "Package name"), "text" to prop("string", "Notes")), listOf("package", "text")),
            tool("mark", "Start recording operations for save."),
            tool("save", "Save the operations since mark as a z2-action macro that replays without the AI. Fails if they include typing, scrolling or other steps z2-action cannot replay.",
                mapOf("name" to prop("string", "1..64 letters, digits, _ or -")), listOf("name")),
            tool("notifications", "List current notifications with their buttons."),
            tool("press_notification", "Press a notification button, with reply text if the button has a reply field.",
                mapOf("key" to prop("string", "Notification key from notifications"), "label" to prop("string", "Button label"),
                    "reply" to prop("string", "Reply text")), listOf("key", "label")),
            tool("status", "Permission state: enabled, paused, levels, approval grants."),
            tool("guide", "The full z2-ui guide for AI agents: rules, commands, error codes. Read it before operating."),
        ))
    }
}
