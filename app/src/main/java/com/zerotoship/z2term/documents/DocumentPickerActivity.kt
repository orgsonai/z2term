package com.zerotoship.z2term.documents

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zerotoship.z2term.R
import com.zerotoship.z2term.security.ToolActivity
import com.zerotoship.z2term.ui.theme.*
import kotlinx.coroutines.delay

internal class DocumentPickerActivity : ToolActivity() {
    private var launched = false
    private val id get() = intent.getStringExtra("request").orEmpty()
    private val picker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        DocumentRequests.selected(applicationContext, id, if (result.resultCode == Activity.RESULT_OK) result.data?.data else null)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        launched = savedInstanceState?.getBoolean("launched") ?: false
        val request = DocumentRequests.get(id) ?: run { finish(); return }
        setContent { Z2TermTheme { Surface(Modifier.fillMaxSize(), color = ZtsBgPrimary) {
            LaunchedEffect(id) {
                while (!request.result.isDone) delay(100)
                finish()
            }
            Unlocked {
                LaunchedEffect(id) {
                    if (!launched) {
                        launched = true
                        try {
                            picker.launch(Intent(if (request.save == null) Intent.ACTION_OPEN_DOCUMENT else Intent.ACTION_CREATE_DOCUMENT)
                                .addCategory(Intent.CATEGORY_OPENABLE).setType(request.mime).apply {
                                    if (request.save != null) putExtra(Intent.EXTRA_TITLE, request.name)
                                })
                        } catch (e: Exception) { request.result.completeExceptionally(e) }
                    }
                }
                Column(Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
                    verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.document_waiting))
                    TextButton(onClick = { DocumentRequests.cancel(id); finish() }) { Text(stringResource(android.R.string.cancel)) }
                }
            }
        } } }
    }
    override fun onSaveInstanceState(outState: Bundle) { outState.putBoolean("launched", launched); super.onSaveInstanceState(outState) }
    override fun onDestroy() { if (isFinishing) DocumentRequests.cancel(id); super.onDestroy() }
}
