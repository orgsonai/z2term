package com.zerotoship.z2term.edge

import android.app.AlertDialog
import android.content.Context
import android.view.View
import android.view.WindowManager
import com.zerotoship.z2term.R

/** Tracks unsaved drafts, including invalid input and collapsed controls. */
class EdgeEditorSession(private val context: Context) {
    private val drafts = linkedMapOf<View, () -> Boolean>()
    private var confirmation: AlertDialog? = null

    fun track(view: View, dirty: () -> Boolean) { drafts[view] = dirty }

    /** A draft that was closed on purpose; its view is gone and must not be asked about again. */
    fun untrack(view: View) { drafts.remove(view) }

    fun hasUnsavedChanges() = drafts.any { (view, dirty) -> view.isAttachedToWindow && dirty() }

    fun leave(except: View? = null, action: () -> Unit) {
        confirm(drafts.any { (view, dirty) -> view !== except && view.isAttachedToWindow && dirty() }, action)
    }

    fun discard(view: View, action: () -> Unit) {
        confirm(drafts[view]?.invoke() == true, action)
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
