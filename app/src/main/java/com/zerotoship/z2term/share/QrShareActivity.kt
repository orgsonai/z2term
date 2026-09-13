package com.zerotoship.z2term.share

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Bundle
import android.provider.OpenableColumns
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.graphics.createBitmap
import androidx.core.net.toUri
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.zerotoship.z2term.R
import com.zerotoship.z2term.settings.CustomThemeStore
import com.zerotoship.z2term.settings.LocaleHelper
import com.zerotoship.z2term.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

internal class QrShareActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) { super.attachBaseContext(LocaleHelper.applyLocale(newBase)) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        CustomThemeStore.ensureLoaded(applicationContext)
        setContent { Z2TermTheme { Surface(color = ZtsBgPrimary) { QrShareScreen { finish() } } } }
    }
}

@Composable
private fun QrShareScreen(onClose: () -> Unit) {
    val context = LocalContext.current
    val qrSize = (LocalConfiguration.current.screenHeightDp - 64).coerceIn(160, 360).dp
    val scope = rememberCoroutineScope()
    val state by QrShareManager.state.collectAsState()
    var selected by rememberSaveable { mutableStateOf("") }
    var name by rememberSaveable { mutableStateOf("") }
    var selectionError by rememberSaveable { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                val displayName = withContext(Dispatchers.IO) {
                    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                        if (it.moveToFirst()) it.getString(0) else null
                    } ?: "download"
                }
                selected = uri.toString(); name = displayName; selectionError = ""
            }.onFailure { selectionError = context.getString(R.string.qr_share_file_failed) }
        }
    }
    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)
        .verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.qr_share_title), style = MaterialTheme.typography.titleLarge, color = ZtsGreen, modifier = Modifier.weight(1f))
            TextButton(onClick = onClose) { Text(stringResource(R.string.qr_share_close)) }
        }
        Text(stringResource(R.string.qr_share_experimental), color = ZtsTextSecondary)
        Text(stringResource(R.string.qr_share_explanation), color = ZtsTextPrimary)
        HorizontalDivider()
        OutlinedButton(shape = androidx.compose.ui.graphics.RectangleShape, onClick = { picker.launch(arrayOf("*/*")) }, enabled = !state.active) {
            Text(stringResource(R.string.qr_share_choose))
        }
        if (name.isNotEmpty() && !state.active) Text(name, color = ZtsTextPrimary)
        Button(shape = androidx.compose.ui.graphics.RectangleShape, onClick = {
            runCatching { QrShareManager.start(context, selected.toUri(), name) }
                .onFailure { selectionError = context.getString(R.string.qr_share_start_failed) }
        }, enabled = selected.isNotEmpty() && !state.active) { Text(stringResource(R.string.qr_share_start)) }
        if (selectionError.isNotEmpty()) Text(selectionError, color = MaterialTheme.colorScheme.error)
        if (state.phase != QrShareManager.Phase.IDLE) {
            HorizontalDivider()
            Text(state.name, color = ZtsTextPrimary)
            Text(stringResource(when (state.phase) {
                QrShareManager.Phase.PREPARING -> R.string.qr_share_preparing
                QrShareManager.Phase.CONNECTING -> R.string.qr_share_connecting
                QrShareManager.Phase.READY -> R.string.qr_share_ready
                QrShareManager.Phase.STOPPING -> R.string.qr_share_stopping
                QrShareManager.Phase.ERROR -> R.string.qr_share_failed
                else -> R.string.qr_share_stopped
            }), color = ZtsTextPrimary)
            Text(Formatter.formatFileSize(context, state.size), color = ZtsTextSecondary)
            if (state.active && state.phase != QrShareManager.Phase.READY) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (state.phase == QrShareManager.Phase.ERROR) Text(state.error, color = MaterialTheme.colorScheme.error)
            if (state.phase == QrShareManager.Phase.READY && state.url.isNotEmpty()) {
                // The async encoder must retain this URL even if stop/reconnect clears the live state.
                val qrUrl = state.url
                val bitmap by produceState<Bitmap?>(null, qrUrl) {
                    value = withContext(Dispatchers.Default) {
                        val matrix = QRCodeWriter().encode(qrUrl, BarcodeFormat.QR_CODE, 640, 640)
                        createBitmap(640, 640).apply {
                            val pixels = IntArray(640 * 640) { i -> if (matrix[i % 640, i / 640]) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
                            setPixels(pixels, 0, 640, 0, 0, 640, 640)
                        }
                    }
                }
                bitmap?.let { Image(it.asImageBitmap(), stringResource(R.string.qr_share_scan), Modifier.widthIn(max = qrSize).fillMaxWidth().aspectRatio(1f)) }
                Text(stringResource(R.string.qr_share_scan), color = ZtsTextPrimary)
                SelectionContainer { Text(qrUrl, fontFamily = FontFamily.Monospace, color = ZtsTextSecondary) }
                OutlinedButton(shape = androidx.compose.ui.graphics.RectangleShape, onClick = {
                    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("z2term", qrUrl))
                    Toast.makeText(context, R.string.qr_share_copied, Toast.LENGTH_SHORT).show()
                }) { Text(stringResource(R.string.qr_share_copy)) }
            }
            if (state.expires > 0 && state.active) Text(stringResource(R.string.qr_share_expires,
                DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(state.expires))), color = ZtsTextSecondary)
            if (state.sent > 0) Text(stringResource(R.string.qr_share_sent, Formatter.formatFileSize(context, state.sent)), color = ZtsTextSecondary)
            if (state.active) OutlinedButton(shape = androidx.compose.ui.graphics.RectangleShape, onClick = { QrShareManager.stop(context) }, enabled = state.phase != QrShareManager.Phase.STOPPING) {
                Text(stringResource(R.string.qr_share_stop))
            }
        }
        Text(stringResource(R.string.qr_share_background), color = ZtsTextSecondary)
    }
}
