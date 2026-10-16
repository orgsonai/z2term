package com.zerotoship.z2term.edge

import android.app.AlertDialog
import android.content.Context
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import com.zerotoship.z2term.R

/** Tracks unsaved drafts, including invalid input and collapsed controls. */
class EdgeEditorSession(private val context: Context) {
    /**
     * [prepare] checks a draft and hands back what writes it, or throws with the message to show.
     * Drafts without one (a tab or panel name typed but not yet added) cannot be saved from Done.
     */
    private class Draft(val dirty: () -> Boolean, val prepare: (() -> () -> Unit)?)
    private val drafts = linkedMapOf<View, Draft>()
    private var confirmation: AlertDialog? = null

    fun track(view: View, prepare: (() -> () -> Unit)? = null, dirty: () -> Boolean) { drafts[view] = Draft(dirty, prepare) }

    /** A draft that was closed on purpose; its view is gone and must not be asked about again. */
    fun untrack(view: View) { drafts.remove(view) }

    fun hasUnsavedChanges() = drafts.any { (view, draft) -> view.isAttachedToWindow && draft.dirty() }

    fun leave(except: View? = null, action: () -> Unit) {
        confirm(drafts.any { (view, draft) -> view !== except && view.isAttachedToWindow && draft.dirty() }, action)
    }

    fun discard(view: View, action: () -> Unit) {
        confirm(drafts[view]?.dirty?.invoke() == true, action)
    }

    /**
     * Done and the panel picker: with unsaved changes, ask Save / Discard / Keep editing. Save checks
     * every draft first and writes only when all pass, so a bad field never leaves half the edits
     * saved; then [saved] (reload what was written) and [action] run. A failure stays here with its message.
     */
    fun finish(saved: () -> Unit, action: () -> Unit) {
        if (confirmation != null) return
        val dirty = drafts.filter { (view, draft) -> view.isAttachedToWindow && draft.dirty() }.values
        if (dirty.isEmpty()) { action(); return }
        val dialog = AlertDialog.Builder(context)
            .setTitle(R.string.edge_finish_title)
            .setMessage(R.string.edge_finish_message)
            .setPositiveButton(R.string.edge_save) { _, _ ->
                runCatching { dirty.mapNotNull { it.prepare?.invoke() }.forEach { write -> write() } }
                    .onSuccess { drafts.clear(); saved(); action() }
                    .onFailure { Toast.makeText(context, it.message, Toast.LENGTH_LONG).show() }
            }
            .setNegativeButton(R.string.edge_discard) { _, _ -> action() }
            .setNeutralButton(R.string.edge_keep_editing, null)
            .create()
        show(dialog)
    }

    private fun confirm(dirty: Boolean, action: () -> Unit) {
        if (confirmation != null) return
        if (!dirty) { action(); return }
        val dialog = AlertDialog.Builder(context)
            .setTitle(R.string.edge_discard_title)
            .setMessage(R.string.edge_discard_message)
            .setPositiveButton(R.string.edge_discard) { _, _ -> action() }
            .setNegativeButton(R.string.edge_keep_editing, null)
            .create()
        show(dialog)
    }

    private fun show(dialog: AlertDialog) {
        dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        dialog.setOnDismissListener { confirmation = null }
        confirmation = dialog
        try { dialog.show() } catch (e: Exception) { confirmation = null; throw e }
    }

    fun dispose() {
        confirmation?.dismiss()
        confirmation = null
        drafts.clear()
    }
}
