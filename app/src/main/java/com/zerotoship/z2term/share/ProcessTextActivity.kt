package com.zerotoship.z2term.share

import android.app.Activity
import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import com.zerotoship.z2term.R
import com.zerotoship.z2term.edge.EdgeRunner
import com.zerotoship.z2term.security.ToolActivity
import com.zerotoship.z2term.snippets.Snippet
import com.zerotoship.z2term.snippets.SnippetStore
import com.zerotoship.z2term.ui.snippets.SnippetInputDialog
import com.zerotoship.z2term.ui.theme.*

/** Explicitly selected local commands receive stdin and return stdout, including trailing newlines. */
internal class ProcessTextModel(app: Application) : AndroidViewModel(app) {
    private val runner = EdgeRunner(app)
    var busy by mutableStateOf(false)
        private set
    var output by mutableStateOf<String?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    fun run(command: String, text: String) {
        if (busy) return
        busy = true; output = null; error = null
        if (!runner.run("process-text", command, 60, input = text) { result ->
                busy = false; error = result.error
                if (result.error == null) output = result.output
            }) { busy = false; error = "Cannot start command" }
    }
    fun cancel() { runner.cancelAll(); busy = false }
    override fun onCleared() { runner.cancelAll() }
}

internal class ProcessTextActivity : ToolActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(Activity.RESULT_CANCELED)
        if (intent.action != Intent.ACTION_PROCESS_TEXT || intent.type != "text/plain") { finish(); return }
        val input = intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString() ?: run { finish(); return }
        val readOnly = intent.getBooleanExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
        val model = ViewModelProvider(this)[ProcessTextModel::class.java]
        val snippets = SnippetStore(applicationContext)
        setContent { Z2TermTheme { Surface(Modifier.fillMaxSize(), color = ZtsBgPrimary) {
            BackHandler { model.cancel(); finish() }
            Unlocked {
                val actions by snippets.snippets.collectAsState(initial = emptyList())
                var form by remember { mutableStateOf<Snippet?>(null) }
                Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
                    Text(stringResource(R.string.process_text_title), style = MaterialTheme.typography.titleMedium)
                    if (input.toByteArray(Charsets.UTF_8).size > 65536 || '\u0000' in input) {
                        Text(stringResource(R.string.process_text_too_large))
                    } else {
                        Text(stringResource(R.string.process_text_contract), color = ZtsTextSecondary)
                        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            SelectionContainer { Text(model.output ?: input) }
                            model.error?.let { Text(it, color = ZtsError) }
                            if (model.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                            val choices = actions.filter { it.processTextAction }
                            if (choices.isEmpty()) Text(stringResource(R.string.process_text_setup))
                            choices.forEach { action ->
                                OutlinedButton(enabled = !model.busy, onClick = {
                                    if (action.inputForm) form = action else model.run(action.command, input)
                                }) { Text(action.label.ifBlank { action.command }) }
                            }
                        }
                        model.output?.let { result ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (!readOnly) Button(onClick = {
                                    setResult(Activity.RESULT_OK, Intent().putExtra(Intent.EXTRA_PROCESS_TEXT, result)); finish()
                                }) { Text(stringResource(R.string.process_text_replace)) }
                                TextButton(onClick = {
                                    getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("", result))
                                }) { Text(stringResource(android.R.string.copy)) }
                            }
                        }
                    }
                    TextButton(onClick = { model.cancel(); finish() }) { Text(stringResource(android.R.string.cancel)) }
                }
                form?.let { action -> SnippetInputDialog(action,
                    onInsert = { form = null; model.run(it, input) }, onCancel = { form = null },
                    confirmLabel = stringResource(R.string.guide_ask_run),
                    hint = stringResource(R.string.process_text_contract)) }
            }
        } } }
    }
}
