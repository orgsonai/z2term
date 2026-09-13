package com.zerotoship.z2term.edge

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.MotionEvent
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import com.zerotoship.z2term.R

internal object EdgePanelCommandsUi {
    fun add(context: Context, body: LinearLayout, store: EdgeStore, panelId: String) {
        val section = EdgeSettingsUi.section(context, body, context.getString(R.string.edge_recreate_commands))
        val snapshot = synchronized(store) { store.panels() }
        val commands = EdgePanelCommands.generate(snapshot, panelId)
        val panel = snapshot.first { it.id == panelId }
        val scope = when {
            snapshot.any { panelId in it.tabs } -> R.string.edge_recreate_tab_help
            panel.tabs.isNotEmpty() -> R.string.edge_recreate_group_help
            else -> R.string.edge_recreate_panel_help
        }
        section.addView(EdgeSettingsUi.caption(context, context.getString(scope)))
        section.addView(EdgeSettingsUi.button(context, context.getString(R.string.edge_copy_commands),
            EdgeSettingsUi.Kind.OUTLINE) {
            // The editor is a focusable overlay. Copy only on this explicit foreground interaction,
            // and verify it because Android can silently refuse clipboard access.
            val copied = runCatching {
                val clipboard = context.getSystemService(ClipboardManager::class.java)
                clipboard.setPrimaryClip(ClipData.newPlainText("z2-edge $panelId", commands))
                clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString() == commands
            }.getOrDefault(false)
            Toast.makeText(context, if (copied) R.string.clip_copied else R.string.clip_copy_failed,
                Toast.LENGTH_SHORT).show()
        }, LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = EdgeSettingsUi.dp(context, 8) })
        val code = EdgeSettingsUi.mono(context, commands).apply {
            setTextIsSelectable(true)
            contentDescription = context.getString(R.string.edge_recreate_commands)
            setPadding(EdgeSettingsUi.dp(context, 10), EdgeSettingsUi.dp(context, 10),
                EdgeSettingsUi.dp(context, 10), EdgeSettingsUi.dp(context, 10))
        }
        section.addView(object : ScrollView(context) {
            override fun onInterceptTouchEvent(event: MotionEvent): Boolean {
                parent?.requestDisallowInterceptTouchEvent(event.actionMasked != MotionEvent.ACTION_UP &&
                    event.actionMasked != MotionEvent.ACTION_CANCEL)
                return super.onInterceptTouchEvent(event)
            }
        }.apply {
            isFillViewport = true
            background = EdgeSettingsUi.frame(context)
            addView(code)
        }, LinearLayout.LayoutParams(-1, EdgeSettingsUi.dp(context, 220)))
        section.addView(EdgeSettingsUi.caption(context, context.getString(R.string.edge_recreate_files_help)).apply {
            setPadding(0, EdgeSettingsUi.dp(context, 10), 0, 0)
        })
    }
}
