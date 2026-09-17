package com.zerotoship.z2term.edge

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.MediaStore
import android.provider.Settings
import com.zerotoship.z2term.R

/**
 * The sample app panel created by the edge-panel guide (0.8.601; a guide command from 0.8.603).
 *
 * It mirrors the app list panel used day to day: a thin bar on the right edge, one icon-only
 * column, add and settings buttons, and variable-speed scrolling on vertical swipes (accessibility).
 *
 * Only role apps present on every device are listed: z2term, browser, camera, phone, messages and
 * settings. Personally installed apps are not guessed. Roles without a resolvable app are skipped.
 *
 * ⚠ Turning panels on never creates it (0.8.603, the user's decision); 0.8.601-0.8.602 did so when no
 * panel existed. Package names differ per device, so the guide builds its line when shown, from the
 * existing `z2-edge panel` and `z2-edge set` commands. Do not add a subcommand for it (the user's decision).
 */
internal object EdgeDefaultPanel {
    data class App(val packageName: String, val label: String)

    const val PANEL_ID = "main"

    /** The guide's line for this device. */
    fun command(context: Context): String = command(context.getString(R.string.edge_default_panel),
        items(resolve(context), AppLaunch.freeformSupported(context)))

    /** `z2-edge panel main …`, then one `z2-edge set main:ID …` per item, joined with `&&`. */
    fun command(label: String, items: Map<String, Map<String, String>>): String {
        fun words(values: Map<String, String>) = values.map { (key, value) -> "$key=$value" }
        val calls = listOf(listOf("z2-edge", "panel", PANEL_ID) + words(fields + ("label" to label))) +
            items.map { (id, values) -> listOf("z2-edge", "set", "$PANEL_ID:$id") + words(values) }
        return calls.joinToString(" && ") { call -> call.joinToString(" ", transform = ::shellWord) }
    }

    /** Plain words stay readable in the guide card; anything else is single-quoted for sh. */
    fun shellWord(word: String): String =
        if (word.isNotEmpty() && word.all { (it.isLetterOrDigit() && it.code < 128) || it in "_@%+=:,./-" }) word
        else "'" + word.replace("'", "'\\''") + "'"

    val fields: Map<String, String> = linkedMapOf(
        "handle" to "bar", "side" to "right", "offset" to "31.5", "size" to "8", "length" to "8", "alpha" to "0.3",
        "width" to "14%", "height" to "47%", "flow" to "vertical", "labels" to "off", "add" to "on", "settings" to "on",
        "actions-up" to "scroll-variable", "actions-down" to "scroll-variable",
        "gesture-speed" to "40000", "gesture-range" to "400",
    )

    /**
     * Item ID to definition. One app may serve two roles, so packages are deduplicated.
     * Freeform is added only where supported: most devices reject `--window freeform` launches.
     */
    fun items(apps: List<App>, freeform: Boolean): Map<String, Map<String, String>> =
        apps.distinctBy { it.packageName }.withIndex().associate { (index, app) ->
            val order = index + 1
            "app_$order" to linkedMapOf(
                "type" to "run",
                "run" to "z2-intent -p ${app.packageName}" + if (freeform) " --window freeform" else "",
                "label" to app.label.replace(Regex("\\s+"), " ").trim(),
                "icon" to "@app:${app.packageName}",
                "order" to order.toString(),
            )
        }.filterValues { runCatching { EdgeStore.validateItem(it) }.isSuccess }

    /** Apps in panel order. Launcher apps are visible through the manifest's LAUNCHER query. */
    fun resolve(context: Context): List<App> {
        val pm = context.packageManager
        val roles = listOf(
            null,
            Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).addCategory(Intent.CATEGORY_BROWSABLE),
            Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA),
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")),
            Intent(Settings.ACTION_SETTINGS),
        )
        return roles.mapNotNull { intent ->
            runCatching {
                val packageName = if (intent == null) context.packageName else defaultPackage(pm, intent)
                packageName?.takeIf { pm.getLaunchIntentForPackage(it) != null }?.let {
                    App(it, pm.getApplicationLabel(pm.getApplicationInfo(it, 0)).toString())
                }
            }.getOrNull()
        }.distinctBy { it.packageName }
    }

    /** The chosen default; with no choice yet, a single system candidate, otherwise nothing. */
    private fun defaultPackage(pm: PackageManager, intent: Intent): String? {
        val candidates = pm.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .map { it.activityInfo.applicationInfo }
        val preferred = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        if (preferred != null && candidates.any { it.packageName == preferred }) return preferred
        return candidates.filter { it.flags and ApplicationInfo.FLAG_SYSTEM != 0 }
            .map { it.packageName }.distinct().singleOrNull()
    }
}
