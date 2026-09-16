package com.zerotoship.z2term.share

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zerotoship.z2term.R
import com.zerotoship.z2term.ui.snippets.SnippetInputDialog
import com.zerotoship.z2term.ui.theme.*

@Composable
fun SharedIntakeDialog(choice: SharedIntakeModel.Choice, onInsert: (String) -> Unit, onDismiss: () -> Unit) {
    val intake = choice.intake
    var selected by rememberSaveable(intake.manifest) { mutableStateOf<String?>(null) }
    val snippet = choice.actions.firstOrNull { it.id == selected }
    if (snippet != null) {
        SnippetInputDialog(snippet, onInsert = {
            onInsert(SharedPayload.command(it, intake.body, intake.manifest))
        }, onCancel = { selected = null }, sharedFiles = intake.files)
        return
    }
    AlertDialog(onDismissRequest = onDismiss, containerColor = ZtsBgCard,
        title = { Text(stringResource(R.string.share_intake_title), color = ZtsGreen, fontFamily = FontFamily.Monospace) },
        text = {
            Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (intake.body.isNotEmpty()) Text(intake.body.take(2000), color = ZtsTextPrimary, fontSize = 13.sp)
                intake.fileNames.forEach { Text(it, color = ZtsTextSecondary, fontSize = 13.sp) }
                Text(stringResource(R.string.share_intake_hint), color = ZtsTextSecondary, fontSize = 12.sp)
                choice.actions.forEach { action ->
                    TextButton(onClick = {
                        if (action.inputForm) selected = action.id
                        else onInsert(SharedPayload.command(action.command, intake.body, intake.manifest))
                    }) { Text(action.label.ifBlank { action.command }, color = ZtsGreen) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onInsert(intake.text) }) { Text(stringResource(R.string.share_intake_insert), color = ZtsGreen) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.share_intake_close), color = ZtsTextSecondary) } }
    )
}
