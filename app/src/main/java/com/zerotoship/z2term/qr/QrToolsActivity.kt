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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
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
import com.zerotoship.z2term.ui.settings.Field
import com.zerotoship.z2term.ui.settings.PillButton
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
        val scroll = rememberScrollState()
        val history = remember { QrHistoryStore(applicationContext) }
        val historyEntries by history.entries.collectAsState(initial = emptyList())
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
        val qrAction = remember(text) { QrAction.parse(text) }
        var notice by remember { mutableStateOf<Int?>(null) }
        var cameraRequested by rememberSaveable { mutableStateOf(false) }
        val show: (String) -> Unit = { value ->
            camera = false; qrText = ""; choices = emptyList(); incomingFailed = false; notice = null
            error = value.length > QrContent.MAX_TEXT || value.isBlank()
            text = if (error) "" else value
            name = runCatching { QrContent.parse(text).name }.getOrDefault("")
        }
        // 履歴に残すのは読み取った内容だけ (0.8.603・利用者の指定)。履歴から表示し直すときは show だけ。
        val read: (String) -> Unit = { value ->
            show(value)
            if (!error) scope.launch { runCatching { history.add(value) } }
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
        // z2-qr はカメラで読み取る状態で始める (0.8.602)。回転では繰り返さない。
        LaunchedEffect(Unit) {
            if (!receivesExternalContent && !cameraRequested && intent.getBooleanExtra("camera", false)) {
                cameraRequested = true
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) camera = true
                else permission.launch(Manifest.permission.CAMERA)
            }
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
            .verticalScroll(scroll).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            ToolScreenHeader(stringResource(R.string.qr_tools_title)) { finish() }
            ToolNote(stringResource(R.string.qr_tools_intro))
            PillButton(label = stringResource(R.string.qr_tools_camera), accent = true, fill = true, enabled = !busy) {
                error = false; incomingFailed = false
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) camera = true
                else permission.launch(Manifest.permission.CAMERA)
            }
            PillButton(label = stringResource(R.string.qr_tools_image), fill = true, enabled = !busy) {
                images.launch(arrayOf("image/*"))
            }
            if (camera) {
                CameraPreview(read) { error = true; camera = false }
                PillButton(label = stringResource(R.string.qr_tools_cancel), fill = true) { camera = false }
            }
            choices.forEach { value ->
                PillButton(label = value.take(160), fill = true, enabled = !busy) { read(value) }
            }
            if (busy) ToolProgress()
            if (incomingFailed) ToolError(stringResource(R.string.qr_tools_incoming_failed))
            if (error) ToolError(stringResource(R.string.qr_tools_failed))
            // 中身に合った「開く」を 1 つだけ出す。読み取っただけでは開かない (0.8.602・利用者の判断)。
            // LINE のログイン URL などは、押すとそのアプリの確認画面が開く。
            qrAction?.let { found ->
                actionDetail(found).takeIf { it.isNotBlank() }?.let { ToolLabel(it) }
                PillButton(label = stringResource(actionLabel(found)), accent = true, fill = true, enabled = !busy) {
                    notice = when (QrActionLauncher.open(this@QrToolsActivity, found)) {
                        QrActionLauncher.Outcome.OPENED -> null
                        QrActionLauncher.Outcome.NO_APP -> R.string.qr_action_no_app
                        QrActionLauncher.Outcome.WIFI_PASSWORD_COPIED -> R.string.qr_action_wifi_copied
                    }
                }
                notice?.let { ToolNote(stringResource(it)) }
            }
            Field(label = stringResource(R.string.qr_tools_content), value = text, multiline = true,
                onChange = { if (!busy && it.length <= QrContent.MAX_TEXT) { text = it; qrText = "" } })
            if (text.isNotBlank()) {
                PillButton(label = stringResource(R.string.qr_tools_copy), fill = true, enabled = !busy) {
                    context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("QR", text))
                }
                PillButton(label = stringResource(R.string.qr_tools_show), fill = true, enabled = !busy) { qrText = text }
            }
            if (content?.kind == QrContent.Kind.SSH) {
                ToolLabel(content.user + "@" + content.host + ":" + content.port)
                ToolNote(stringResource(R.string.qr_tools_ssh_note))
                PillButton(label = stringResource(R.string.qr_tools_save_ssh), accent = true, fill = true, enabled = !busy) {
                    action {
                        SshProfileStore(applicationContext).upsert(SshProfile(UUID.randomUUID().toString(),
                            content.host, host = content.host, port = content.port, user = content.user))
                    }
                }
            }
            if (qrAction == null && content != null && content.kind in setOf(QrContent.Kind.TEXT, QrContent.Kind.COMMAND) &&
                QrContent.singleLine(content.text)) {
                if (content.kind == QrContent.Kind.COMMAND) SelectionContainer {
                    Text(content.text, color = ZtsGreen, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
                Field(label = stringResource(R.string.qr_tools_name), value = name,
                    onChange = { if (it.length <= 80) name = it })
                ToolNote(stringResource(R.string.qr_tools_command_note))
                PillButton(label = stringResource(R.string.qr_tools_insert), accent = true, fill = true, enabled = !busy) {
                    error = !SessionManager.insertText(content.text)
                    if (!error) finish()
                }
                PillButton(label = stringResource(R.string.qr_tools_save_command), fill = true, enabled = !busy) {
                    action { SnippetStore(applicationContext).upsert(Snippet(UUID.randomUUID().toString(), name, content.text)) }
                }
                PillButton(label = stringResource(R.string.qr_tools_save_edge), fill = true, enabled = !busy) {
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
                }
                PillButton(label = stringResource(R.string.qr_tools_command_qr), fill = true, enabled = !busy) {
                    runCatching { qrText = QrContent.command(name, content.text) }.onFailure { error = true }
                }
            } else if (qrAction == null && text.isNotEmpty() && !QrContent.singleLine(text)) {
                ToolNote(stringResource(R.string.qr_tools_multiline))
            }
            if (qrText.isNotEmpty()) QrDisplay(qrText)
            if (historyEntries.isNotEmpty()) {
                QrHistorySection(
                    entries = QrHistory.ordered(historyEntries),
                    enabled = !busy,
                    // 内容欄は上にあるので、表示し直したら先頭へ戻す。
                    onOpen = { entry -> show(entry.text); scope.launch { scroll.animateScrollTo(0) } },
                    onPin = { entry -> scope.launch { runCatching { history.setPinned(entry.text, !entry.pinned) } } },
                    onDelete = { entry -> scope.launch { runCatching { history.remove(entry.text) } } },
                    onClear = { scope.launch { runCatching { history.clearUnpinned() } } },
                )
            }
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
        /** `z2-qr`: open with the camera already scanning. */
        fun scan(context: Context) {
            context.startActivity(Intent(context, QrToolsActivity::class.java).putExtra("camera", true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        fun open(context: Context, text: String = "", show: Boolean = false) {
            context.startActivity(Intent(context, QrToolsActivity::class.java).putExtra("text", text).putExtra("show", show)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

/** QR 画像。画面幅の中央に置く (白い余白は画像側に含まれる)。 */
@Composable internal fun QrDisplay(text: String) {
    val bitmap by produceState<android.graphics.Bitmap?>(null, text) {
        value = null
        value = withContext(Dispatchers.Default) { runCatching { QrImages.encode(text) }.getOrNull() }
    }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        bitmap?.let {
            Image(it.asImageBitmap(), stringResource(R.string.qr_tools_show),
                Modifier.widthIn(max = 360.dp).fillMaxWidth().aspectRatio(1f))
        } ?: ToolNote(stringResource(R.string.qr_tools_qr_limit))
    }
}

private fun actionLabel(action: QrAction): Int = when (action) {
    is QrAction.Web, is QrAction.Link -> R.string.qr_action_open
    is QrAction.Dial -> R.string.qr_action_dial
    is QrAction.Email -> R.string.qr_action_email
    is QrAction.Sms -> R.string.qr_action_sms
    is QrAction.Wifi -> R.string.qr_action_wifi
    is QrAction.Contact -> R.string.qr_action_contact
    is QrAction.Event -> R.string.qr_action_event
}

/** 押す前に「何が開くか」を 1 行で見せる。 */
private fun actionDetail(action: QrAction): String = when (action) {
    is QrAction.Web -> action.host
    is QrAction.Link -> action.uri.take(80)
    is QrAction.Dial -> action.number
    is QrAction.Email -> action.to
    is QrAction.Sms -> action.number
    is QrAction.Wifi -> action.ssid
    is QrAction.Contact -> action.name.ifBlank { (action.phones + action.emails).firstOrNull().orEmpty() }
    is QrAction.Event -> action.title
}
