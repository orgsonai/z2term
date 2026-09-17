package com.zerotoship.z2term.edge

import android.content.Context
import com.zerotoship.z2term.R

/** Guide examples use ordinary panel definitions and never replace an existing item. */
internal object EdgeSamplePanels {
    enum class Kind(val id: String, val title: Int) {
        NOTE("sample-tools", R.string.guide_sample_note),
        TRANSLATION("sample-tools-translation", R.string.guide_sample_translation),
        TERMINAL("sample-tools-terminal", R.string.guide_sample_terminal)
    }

    fun panel(kind: Kind, label: (Int) -> String, targetLanguage: String = "ja"): EdgeStore.Panel {
        val fields = linkedMapOf("label" to label(kind.title), "handle" to "bar", "side" to "right",
            "offset" to "48", "size" to "6", "length" to "6", "alpha" to "0.3",
            "width" to "90%", "height" to "65%", "fit" to "fixed", "flow" to "vertical",
            "title" to "off", "tabbar" to "on", "close" to "on", "labels" to "on", "settings" to "on", "add" to "off")
        if (kind == Kind.NOTE) fields["tabs"] = "${Kind.TRANSLATION.id},${Kind.TERMINAL.id}"
        else fields["handle"] = "off"
        val items = when (kind) {
            Kind.NOTE -> listOf(EdgeStore.Item("note", mapOf("type" to "note", "label" to "", "note-lines" to "on")))
            Kind.TERMINAL -> listOf(EdgeStore.Item("terminal", mapOf("type" to "terminal", "label" to "")))
            Kind.TRANSLATION -> listOf(
                EdgeStore.Item("source", mapOf("type" to "argument", "label" to label(R.string.guide_sample_source),
                    "rows" to "3", "required" to "on", "order" to "10")),
                EdgeStore.Item("language", mapOf("type" to "argument", "label" to label(R.string.guide_sample_language),
                    "argument-kind" to "choice", "choices" to "ja|en|zh-CN|zh-TW|es|ko",
                    "default" to targetLanguage, "order" to "20")),
                EdgeStore.Item("translate", mapOf("type" to "run", "label" to label(R.string.guide_sample_translate),
                    "run" to "sh \"\$HOME/.z2term/macros/translate.sh\" --", "args" to "source,language",
                    "result" to "result", "timeout" to "60", "order" to "30")),
                EdgeStore.Item("result", mapOf("type" to "result", "label" to label(R.string.guide_sample_result),
                    "rows" to "6", "result-controls" to "off", "order" to "40"))
            )
        }
        EdgeStore.validatePanel(fields)
        items.forEach { EdgeStore.validateItem(it.fields) }
        return EdgeStore.Panel(kind.id, fields, items)
    }

    fun command(context: Context): String {
        val locale = context.resources.configuration.locales[0]
        val language = when (locale.language) {
            "ja", "en", "es", "ko" -> locale.language
            "zh" -> if (locale.country in setOf("TW", "HK", "MO")) "zh-TW" else "zh-CN"
            else -> "en"
        }
        val create = command({ context.getString(it) }, language)
        val missing = EdgeDefaultPanel.shellWord(context.getString(R.string.guide_sample_translation_missing))
        return "if command -v trans >/dev/null 2>&1 && test -f \"\$HOME/.z2term/macros/translate.sh\"; then $create; else printf '%s\\n' $missing >&2; false; fi"
    }

    fun command(label: (Int) -> String, language: String = "ja"): String =
        // Children must exist before the parent can refer to them as tabs.
        listOf(Kind.TRANSLATION, Kind.TERMINAL, Kind.NOTE).joinToString(" && ") {
            command(panel(it, label, language))
        }

    private fun command(panel: EdgeStore.Panel): String {
        fun create(verb: String, target: String, fields: Map<String, String>): String {
            val words = listOf("z2-edge", verb, target) + fields.map { (key, value) -> "$key=$value" }
            return "(z2-edge get ${EdgeDefaultPanel.shellWord(target)} >/dev/null 2>&1 || " +
                words.joinToString(" ", transform = EdgeDefaultPanel::shellWord) + ")"
        }
        return (listOf(create("panel", panel.id, panel.fields)) + panel.items.map {
            create("set", "${panel.id}:${it.id}", it.fields)
        }).joinToString(" && ")
    }
}
