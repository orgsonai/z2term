package com.zerotoship.z2term.automation

import android.content.ContextWrapper
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

/** Save only the draft, never the activity or its views, across recreation and app lock. */
internal class ActionMacrosState(private var saved: Bundle? = null) {
    var editor: ActionMacrosEditor? = null
        private set

    private fun snapshot(): Bundle = editor?.saveState() ?: saved ?: Bundle()

    fun requestLeave(after: () -> Unit) {
        val current = editor
        if (current == null) after() else current.requestLeave(after)
    }

    fun create(activity: ComponentActivity, onClose: () -> Unit) =
        ActionMacrosEditor(activity, saved, embedded = true, onClose = onClose)

    fun attach(current: ActionMacrosEditor) { editor = current; current.start() }

    fun detach(current: ActionMacrosEditor) {
        if (editor === current) {
            saved = current.saveState()
            editor = null
        }
        current.dispose()
    }

    companion object {
        val saver = Saver<ActionMacrosState, Bundle>(
            save = { it.snapshot() }, restore = { ActionMacrosState(it) }
        )
    }
}

@Composable
internal fun ActionMacrosBody(state: ActionMacrosState, modifier: Modifier = Modifier, onClose: () -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) {
        generateSequence(context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<ComponentActivity>().first()
    }
    val close by rememberUpdatedState(onClose)
    val editor = remember(activity, state) { state.create(activity) { close() } }
    DisposableEffect(editor) {
        state.attach(editor)
        onDispose { state.detach(editor) }
    }
    AndroidView(factory = { editor.view }, modifier = modifier)
}
