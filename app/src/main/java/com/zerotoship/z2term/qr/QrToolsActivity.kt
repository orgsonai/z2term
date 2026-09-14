package com.zerotoship.z2term.qr

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.CameraPreview as ScannerPreview
import com.journeyapps.barcodescanner.DecoratedBarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import com.zerotoship.z2term.R
import com.zerotoship.z2term.channel.SshProfile
import com.zerotoship.z2term.channel.SshProfileStore
import com.zerotoship.z2term.core.SessionManager
import com.zerotoship.z2term.edge.EdgeRuntime
import com.zerotoship.z2term.snippets.Snippet
import com.zerotoship.z2term.snippets.SnippetStore
import com.zerotoship.z2term.ui.theme.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

internal open class QrToolsActivity : QrActivityBase() {
    protected open val receivesExternalContent = false
    private var entryVersion by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        entryVersion = savedInstanceState?.getInt("qr.entryVersion") ?: 0
        setContent {
            Z2TermTheme {
                Surface(color = ZtsBgPrimary, modifier = Modifier.fillMaxSize()) {
                    Unlocked { key(entryVersion) { ToolsScreen() } }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        entryVersion++
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("qr.entryVersion", entryVersion)
        super.onSaveInstanceState(outState)
    }

    @Composable private fun ToolsScreen() {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val initialText = remember {
            if (receivesExternalContent) "" else intent.getStringExtra("text").orEmpty()
                .takeIf { it.length <= QrContent.MAX_TEXT }.orEmpty()
        }
        var text by rememberSaveable { mutableStateOf(initialText) }
        var incomingHandled by rememberSaveable { mutableStateOf(false) }
        var incomingFailed by rememberSaveable { mutableStateOf(false) }
        var name by rememberSaveable { mutableStateOf(runCatching { QrContent.parse(text).name }.getOrDefault("")) }
        var camera by remember { mutableStateOf(false) }
        var busy by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf(false) }
        var choices by rememberSaveable { mutableStateOf(emptyList<String>()) }
        var qrText by rememberSaveable { mutableStateOf(if (!receivesExternalContent && intent.getBooleanExtra("show", false)) text else "") }
        val content = remember(text) { runCatching { QrContent.parse(text) }.getOrNull() }
        val read: (String) -> Unit = { value ->
            camera = false; qrText = ""; choices = emptyList(); incomingFailed = false
            error = value.length > QrContent.MAX_TEXT || value.isBlank()
            text = if (error) "" else value
            name = runCatching { QrContent.parse(text).name }.getOrDefault("")
        }
        LaunchedEffect(Unit) {
            if (receivesExternalContent && !incomingHandled) {
                busy = true
                try {
                    val incoming = QrIncoming.from(intent)
                    if (incoming.text.isNotEmpty()) {
                        read(incoming.text)
                    } else {
                        require(incoming.images.isNotEmpty())
                        val found = withContext(Dispatchers.IO) {
                            val values = LinkedHashSet<String>()
                            for (uri in incoming.images) {
                                ensureActive()
                                val decoded = try { QrImages.decode(context, uri) }
                                    catch (e: CancellationException) { throw e }
                                    catch (_: Exception) { emptyList() }
                                values.addAll(decoded)
                                require(values.size <= 32 && values.sumOf { it.length } <= 32_768)
                            }
                            values.toList()
                        }
                        if (found.size == 1) read(found.single())
                        else if (found.isEmpty()) incomingFailed = true
                        else choices = found
                    }
                    incomingHandled = true
                } catch (e: CancellationException) { throw e }
                catch (_: Exception) { incomingFailed = true; incomingHandled = true }
                finally { busy = false }
            }
        }
        val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            camera = granted; error = !granted
        }
        val images = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) scope.launch {
                busy = true; error = false; incomingFailed = false; camera = false
                try {
                    val found = withContext(Dispatchers.IO) { QrImages.decode(context, uri) }
                    if (found.size == 1) read(found.single())
                    else if (found.isEmpty()) error = true
                    else choices = found
                } catch (_: Exception) { error = true }
                finally { busy = false }
            }
        }
        val action: (suspend () -> Unit) -> Unit = { work ->
            scope.launch {
                busy = true; error = false
                try { work(); Toast.makeText(context, R.string.qr_tools_done, Toast.LENGTH_SHORT).show() }
                catch (_: Exception) { error = true }
                finally { busy = false }
            }
        }
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).imePadding()
            .verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.qr_tools_title), color = ZtsGreen, style = MaterialTheme.typography.titleLarge)
                TextButton(onClick = { finish() }) { Text(stringResource(R.string.qr_tools_close)) }
            }
            Text(stringResource(R.string.qr_tools_intro), color = ZtsTextSecondary)
            OutlinedButton(shape = RectangleShape, enabled = !busy, onClick = {
                error = false; incomingFailed = false
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) camera = true
                else permission.launch(Manifest.permission.CAMERA)
            }) { Text(stringResource(R.string.qr_tools_camera)) }
            OutlinedButton(shape = RectangleShape, enabled = !busy, onClick = { images.launch(arrayOf("image/*")) }) {
                Text(stringResource(R.string.qr_tools_image))
            }
            if (camera) {
                CameraPreview(read) { error = true; camera = false }
                TextButton(onClick = { camera = false }) { Text(stringResource(R.string.qr_tools_cancel)) }
            }
            choices.forEach { value ->
                OutlinedButton(shape = RectangleShape, enabled = !busy, onClick = { read(value) }) { Text(value.take(160)) }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (incomingFailed) Text(stringResource(R.string.qr_tools_incoming_failed), color = MaterialTheme.colorScheme.error)
            if (error) Text(stringResource(R.string.qr_tools_failed), color = MaterialTheme.colorScheme.error)
            OutlinedTextField(value = text, enabled = !busy, onValueChange = { if (it.length <= QrContent.MAX_TEXT) { text = it; qrText = "" } },
                label = { Text(stringResource(R.string.qr_tools_content)) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            if (text.isNotBlank()) {
                OutlinedButton(shape = RectangleShape, enabled = !busy, onClick = {
                    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("QR", text))
                }) { Text(stringResource(R.string.qr_tools_copy)) }
                OutlinedButton(shape = RectangleShape, enabled = !busy, onClick = { qrText = text }) { Text(stringResource(R.string.qr_tools_show)) }
            }
            if (content?.kind == QrContent.Kind.URL) {
                Button(shape = RectangleShape, enabled = !busy, onClick = {
                    runCatching { startActivity(Intent(Intent.ACTION_VIEW, content.raw.toUri()).addCategory(Intent.CATEGORY_BROWSABLE)) }
                        .onFailure { error = true }
                }) { Text(stringResource(R.string.qr_tools_open_url)) }
            }
            if (content?.kind == QrContent.Kind.SSH) {
                Text(content.user + "@" + content.host + ":" + content.port, color = ZtsTextPrimary)
                Text(stringResource(R.string.qr_tools_ssh_note), color = ZtsTextSecondary)
                Button(shape = RectangleShape, enabled = !busy, onClick = {
                    action {
                        SshProfileStore(applicationContext).upsert(SshProfile(UUID.randomUUID().toString(),
                            content.host, host = content.host, port = content.port, user = content.user))
                    }
                }) { Text(stringResource(R.string.qr_tools_save_ssh)) }
            }
            if (content != null && content.kind in setOf(QrContent.Kind.TEXT, QrContent.Kind.COMMAND) &&
                QrContent.singleLine(content.text)) {
                if (content.kind == QrContent.Kind.COMMAND) SelectionContainer { Text(content.text, color = ZtsTextPrimary) }
                OutlinedTextField(value = name, onValueChange = { if (it.length <= 80) name = it },
                    label = { Text(stringResource(R.string.qr_tools_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text(stringResource(R.string.qr_tools_command_note), color = ZtsTextSecondary)
                Button(shape = RectangleShape, enabled = !busy, onClick = {
                    error = !SessionManager.insertText(content.text)
                    if (!error) finish()
                }) { Text(stringResource(R.string.qr_tools_insert)) }
                OutlinedButton(shape = RectangleShape, enabled = !busy, onClick = {
                    action { SnippetStore(applicationContext).upsert(Snippet(UUID.randomUUID().toString(), name, content.text)) }
                }) { Text(stringResource(R.string.qr_tools_save_command)) }
                OutlinedButton(shape = RectangleShape, enabled = !busy, onClick = {
                    action {
                        withContext(Dispatchers.IO) {
                            val store = EdgeRuntime.store(applicationContext)
                            val id = "qr-" + UUID.randomUUID().toString().take(12)
                            // New disabled panel: never overwrite existing panels or start imported commands.
                            store.setPanel(id, mapOf("label" to name.ifBlank { "QR" }, "handle" to "off"))
                            try {
                                store.setItem(id + ":command", mapOf("type" to "run", "label" to name.ifBlank { "QR" },
                                    "run" to content.text, "order" to "0"))
                            } catch (e: Exception) { store.removePanel(id); throw e }
                        }
                        EdgeRuntime.reload(applicationContext)
                    }
                }) { Text(stringResource(R.string.qr_tools_save_edge)) }
                OutlinedButton(shape = RectangleShape, enabled = !busy, onClick = {
                    runCatching { qrText = QrContent.command(name, content.text) }.onFailure { error = true }
                }) { Text(stringResource(R.string.qr_tools_command_qr)) }
            } else if (text.isNotEmpty() && !QrContent.singleLine(text)) {
                Text(stringResource(R.string.qr_tools_multiline), color = ZtsTextSecondary)
            }
            if (qrText.isNotEmpty()) QrDisplay(qrText)
        }
    }

    @Composable private fun CameraPreview(onRead: (String) -> Unit, onError: () -> Unit) {
        val context = LocalContext.current
        val latest by rememberUpdatedState(onRead)
        val latestError by rememberUpdatedState(onError)
        val view = remember { DecoratedBarcodeView(context) }
        DisposableEffect(view) {
            view.barcodeView.decoderFactory = DefaultDecoderFactory(listOf(BarcodeFormat.QR_CODE))
            view.setStatusText(getString(R.string.qr_tools_camera))
            view.barcodeView.addStateListener(object : ScannerPreview.StateListener {
                override fun previewSized() = Unit
                override fun previewStarted() = Unit
                override fun previewStopped() = Unit
                override fun cameraClosed() = Unit
                override fun cameraError(error: Exception) { latestError() }
            })
            view.decodeSingle { result -> latest(result.text) }
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) view.resume()
                if (event == Lifecycle.Event.ON_PAUSE) view.pause()
            }
            lifecycle.addObserver(observer)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) view.resume()
            onDispose { lifecycle.removeObserver(observer); view.pause() }
        }
        AndroidView(factory = { view }, modifier = Modifier.fillMaxWidth().height(320.dp))
    }

    companion object {
        fun showCommand(context: Context, name: String, command: String) {
            runCatching { open(context, QrContent.command(name, command), show = true) }
                .onFailure { Toast.makeText(context, R.string.qr_tools_failed, Toast.LENGTH_LONG).show() }
        }
        fun showSsh(context: Context, host: String, port: Int, user: String) {
            runCatching { open(context, QrContent.ssh(host, port, user), show = true) }
                .onFailure { Toast.makeText(context, R.string.qr_tools_failed, Toast.LENGTH_LONG).show() }
        }
        fun open(context: Context, text: String = "", show: Boolean = false) {
            context.startActivity(Intent(context, QrToolsActivity::class.java).putExtra("text", text).putExtra("show", show)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

@Composable internal fun QrDisplay(text: String) {
    val bitmap by produceState<android.graphics.Bitmap?>(null, text) {
        value = null
        value = withContext(Dispatchers.Default) { runCatching { QrImages.encode(text) }.getOrNull() }
    }
    bitmap?.let {
        Image(it.asImageBitmap(), stringResource(R.string.qr_tools_show),
            Modifier.widthIn(max = 360.dp).fillMaxWidth().aspectRatio(1f))
    } ?: Text(stringResource(R.string.qr_tools_qr_limit))
}
