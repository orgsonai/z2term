package com.zerotoship.z2term.ui.snippets

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerotoship.z2term.R
import com.zerotoship.z2term.share.SharedIntake
import com.zerotoship.z2term.snippets.Snippet
import com.zerotoship.z2term.snippets.SnippetTemplate
import com.zerotoship.z2term.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Shared by the snippet list and the explicit choice following an Android share. */
@Composable
fun SnippetInputDialog(snippet: Snippet, onInsert: (String) -> Unit, onCancel: () -> Unit,
                       sharedFiles: List<String> = emptyList()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val fields = remember(snippet) { runCatching { SnippetTemplate.fields(snippet.command) }.getOrNull() }
    var values by rememberSaveable(snippet.id) { mutableStateOf(fields.orEmpty().associate {
        it.name to if (it.kind == SnippetTemplate.Kind.FILE && sharedFiles.size == 1) "~/" + sharedFiles.single() else it.initial
    }.let { HashMap(it) }) }
    var picking by rememberSaveable { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val key = picking
        picking = null
        if (uri != null && key != null) scope.launch {
            busy = true
            try {
                val relative = withContext(Dispatchers.IO) { SharedIntake.importDocument(context.applicationContext, uri) }
                values = HashMap(values).apply { put(key, "~/$relative") }
            } catch (_: Exception) { failed = true }
            finally { busy = false }
        }
    }
    val rendered = remember(snippet, values) {
        runCatching { SnippetTemplate.render(snippet.command, values) }.getOrNull()
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onCancel() }, containerColor = ZtsBgCard,
        title = { Text(snippet.label.ifBlank { stringResource(R.string.snippet_inputs_title) },
            color = ZtsGreen, fontFamily = FontFamily.Monospace, fontSize = 16.sp) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                fields.orEmpty().forEach { field ->
                    val value = values[field.name].orEmpty()
                    if (field.kind == SnippetTemplate.Kind.CHOICE) {
                        var expanded by remember { mutableStateOf(false) }
                        Box {
                            TextButton(onClick = { expanded = true }) { Text("${field.name}: $value ▾", color = ZtsTextPrimary) }
                            DropdownMenu(expanded, { expanded = false }) {
                                field.choices.forEach { choice ->
                                    DropdownMenuItem(text = { Text(choice) }, onClick = {
                                        values = HashMap(values).apply { put(field.name, choice) }; expanded = false
                                    })
                                }
                            }
                        }
                    } else {
                        OutlinedTextField(value, { values = HashMap(values).apply { put(field.name, it) } },
                            label = { Text(field.name) }, modifier = Modifier.fillMaxWidth(),
                            singleLine = field.kind != SnippetTemplate.Kind.TEXT,
                            isError = !SnippetTemplate.valid(field, value), enabled = !busy,
                            keyboardOptions = KeyboardOptions(keyboardType = if (field.kind == SnippetTemplate.Kind.NUMBER)
                                KeyboardType.Decimal else KeyboardType.Text))
                        if (field.kind == SnippetTemplate.Kind.FILE) {
                            Row(Modifier.fillMaxWidth().wrapContentHeight()) {
                                TextButton(enabled = !busy, onClick = { picking = field.name; pickFile.launch(arrayOf("*/*")) }) {
                                    Text(stringResource(R.string.snippet_inputs_file), color = ZtsGreen)
                                }
                            }
                            if (sharedFiles.size > 1) sharedFiles.forEach { path ->
                                TextButton(onClick = { values = HashMap(values).apply { put(field.name, "~/$path") } }) {
                                    Text(path.substringAfterLast('/'), color = ZtsTextPrimary)
                                }
                            }
                        }
                    }
                }
                if (failed || fields == null || rendered == null) Text(stringResource(R.string.snippet_inputs_invalid), color = ZtsError)
                Text(stringResource(R.string.snippet_inputs_preview), color = ZtsTextSecondary)
                Text(rendered.orEmpty(), color = ZtsTextPrimary, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                Text(stringResource(R.string.snippet_inputs_hint), color = ZtsTextSecondary, fontSize = 12.sp)
            }
        },
        confirmButton = { TextButton(enabled = rendered != null && !busy, onClick = { rendered?.let(onInsert) }) {
            Text(stringResource(R.string.share_intake_insert), color = ZtsGreen)
        } },
        dismissButton = { TextButton(enabled = !busy, onClick = onCancel) { Text(stringResource(R.string.action_cancel), color = ZtsTextSecondary) } }
    )
}
