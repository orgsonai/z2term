package com.zerotoship.z2term.automation

import android.app.Dialog
import android.content.Context
import android.widget.Toast
import com.zerotoship.z2term.R
import com.zerotoship.z2term.edge.EdgeRuntime
import com.zerotoship.z2term.tile.TileStore
import com.zerotoship.z2term.tile.Z2TileService
import java.util.UUID

/** Only an exact standalone reference takes the native playback route; shell expressions stay shell. */
internal object ActionMacroReference {
    fun name(command: String): String? = Regex("z2-action[ \t]+start[ \t]+([A-Za-z0-9_-]{1,64})")
        .matchEntire(command.trim())?.groupValues?.get(1)
    fun command(name: String) = "z2-action start " + ActionDefinition.macroName(name)
}

internal object ActionMacroPlacement {
    fun show(context: Context, name: String, show: (Dialog) -> Unit) {
        fun message(text: String) = Toast.makeText(context, text, Toast.LENGTH_LONG).show()
        fun assign(label: Int = R.string.action_place_done, action: () -> Unit) {
            try { action(); message(context.getString(label)) }
            catch (e: Exception) { message(e.message ?: context.getString(R.string.action_edit_error)) }
        }
        val command = ActionMacroReference.command(name)
        show(ActionUi.choices(context, context.getString(R.string.action_place_title, name),
            listOf(context.getString(R.string.action_place_tile), context.getString(R.string.action_place_edge))) { destination ->
            if (destination == 0) {
                val slots = (1..TileStore.COUNT).map { TileStore.get(context, it) }
                show(ActionUi.choices(context, context.getString(R.string.action_place_tile), slots.mapIndexed { index, slot ->
                    context.getString(R.string.action_place_slot, index + 1, slot?.label ?: context.getString(R.string.action_place_empty))
                }) { selected ->
                    val slot = selected + 1
                    fun save() = assign(R.string.action_place_tile_done) {
                        check(TileStore.get(context, slot) == slots[selected]) { context.getString(R.string.action_edit_conflict) }
                        TileStore.set(context, slot, command, name)
                        Z2TileService.requestUpdate(context, slot)
                        Z2TileService.requestAdd(context, slot)
                    }
                    if (slots[selected] == null) save()
                    else show(ActionUi.modal(context, context.getString(R.string.action_place_tile),
                        message = context.getString(R.string.action_place_replace, slots[selected]!!.label, name),
                        confirm = context.getString(R.string.action_edit_apply)) { dialog -> save(); dialog.dismiss() })
                })
            } else {
                val store = EdgeRuntime.store(context)
                val panels = runCatching { store.panels() }.getOrElse { message(it.message.orEmpty()); return@choices }
                if (panels.isEmpty()) { message(context.getString(R.string.action_place_no_panels)); return@choices }
                show(ActionUi.choices(context, context.getString(R.string.action_place_edge), panels.map {
                    it.fields["title"]?.let { title -> title + " (" + it.id + ")" } ?: it.id
                }) { selected ->
                    assign {
                        val panel = store.panel(panels[selected].id)
                        val id = "action_" + UUID.randomUUID().toString().replace("-", "")
                        store.saveItemDraft(panel.id + ":" + id, mapOf("type" to "run", "label" to name,
                            "run" to command, "out" to "none", "order" to ((panel.items.maxOfOrNull { it.order } ?: 0) + 1).toString()), null)
                        EdgeRuntime.reload(context)
                    }
                })
            }
        })
    }
}
