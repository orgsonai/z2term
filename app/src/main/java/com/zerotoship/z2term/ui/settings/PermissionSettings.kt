package com.zerotoship.z2term.ui.settings

import android.Manifest
import android.app.NotificationManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.zerotoship.z2term.R
import com.zerotoship.z2term.edge.AndroidActions
import com.zerotoship.z2term.service.NetGuard
import com.zerotoship.z2term.service.NotificationLogService
import com.zerotoship.z2term.service.PasswordWatchAdmin
import com.zerotoship.z2term.service.ScreenTimeout
import com.zerotoship.z2term.settings.BatteryGuard
import com.zerotoship.z2term.ui.theme.ZtsBorder
import com.zerotoship.z2term.ui.theme.ZtsGreen
import com.zerotoship.z2term.ui.theme.ZtsTextSecondary
import com.zerotoship.z2term.usb.UsbHostAccess
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** Invalidate OS-owned permission state whenever the settings page resumes. */
@Composable
internal fun rememberPermissionRefresh(): Int {
    var revision by remember { mutableIntStateOf(0) }
    val owner = LocalView.current.findViewTreeLifecycleOwner()
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) revision++
        }
        owner?.lifecycle?.addObserver(observer)
        onDispose { owner?.lifecycle?.removeObserver(observer) }
    }
    return revision
}

/** All user-managed app permissions; requests happen only after a specific button press. */
@Composable
internal fun PermissionsSection(
    rootAvailable: Boolean, rootUnlocked: Boolean, rootProbing: Boolean, onRootProbe: () -> Unit
) {
    val context = LocalContext.current
    val resume = rememberPermissionRefresh()
    var resultRevision by remember { mutableIntStateOf(0) }
    val packageUri = remember(context) { ("package:" + context.packageName).toUri() }
    val details = remember(context) { Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri) }
    val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        resultRevision++
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        resultRevision++
    }
    fun open(intent: Intent, fallback: Intent = details) {
        runCatching { settingsLauncher.launch(intent) }.recoverCatching { settingsLauncher.launch(fallback) }
            .onFailure { Toast.makeText(context, R.string.permissions_open_failed, Toast.LENGTH_LONG).show() }
    }
    fun request(vararg permissions: String) {
        runCatching { permissionLauncher.launch(arrayOf(*permissions)) }
            .onFailure { open(details) }
    }
    fun allowed(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    val grants = remember(context, resume, resultRevision) {
        PermissionGrants.read(context)
    }
    Text(stringResource(R.string.permissions_intro), color = ZtsTextSecondary, fontSize = 12.sp)
    PermissionRow(R.string.permissions_notifications, R.string.permissions_notifications_desc, grants.notifications,
        onRequest = if (Build.VERSION.SDK_INT >= 33 && !allowed(Manifest.permission.POST_NOTIFICATIONS))
            ({ request(Manifest.permission.POST_NOTIFICATIONS) }) else null,
        onSettings = { open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) })
    PermissionRow(R.string.permissions_listener, R.string.permissions_listener_desc, grants.listener,
        onSettings = { open(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) })
    PermissionRow(R.string.permissions_accessibility, R.string.permissions_accessibility_desc, grants.accessibility,
        onSettings = {
            val fallback = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            val intent = if (Build.VERSION.SDK_INT >= 30) Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
                .putExtra(Intent.EXTRA_COMPONENT_NAME, ComponentName(context, AndroidActions::class.java)) else fallback
            open(intent, fallback)
        })
    PermissionRow(R.string.permissions_overlay, R.string.permissions_overlay_desc, grants.overlay,
        onSettings = { open(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, packageUri)) })
    PermissionRow(R.string.permissions_storage, R.string.permissions_storage_desc, grants.storage,
        onRequest = if (Build.VERSION.SDK_INT < 30 && !grants.storage)
            ({ request(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE) }) else null,
        onSettings = {
            if (Build.VERSION.SDK_INT >= 30) open(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, packageUri),
                Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) else open(details)
        })
    PermissionRow(R.string.permissions_sms, R.string.permissions_sms_desc, grants.sms,
        onRequest = if (!grants.sms) ({ request(Manifest.permission.RECEIVE_SMS) }) else null,
        onSettings = { open(details) })
    PermissionRow(R.string.permissions_battery, R.string.permissions_battery_desc, grants.battery,
        onRequest = if (!grants.battery) ({ open(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, packageUri),
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }) else null,
        onSettings = { open(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) })
    PermissionRow(R.string.permissions_write_settings, R.string.permissions_write_settings_desc, grants.writeSettings,
        onSettings = { open(ScreenTimeout.manageIntent(context).apply { flags = 0 }) })
    PermissionRow(R.string.permissions_usage, R.string.permissions_usage_desc, grants.usage,
        onSettings = { open(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS, packageUri)) })
    PermissionRow(R.string.permissions_install, R.string.permissions_install_desc, grants.install,
        onSettings = { open(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, packageUri)) })
    PermissionRow(R.string.permissions_admin, R.string.permissions_admin_desc, grants.admin,
        onRequest = if (!grants.admin) ({ open(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, PasswordWatchAdmin.component(context))
            .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, context.getString(R.string.settings_unlock_watch_admin_explain))) }) else null,
        onSettings = { open(Intent(Settings.ACTION_SECURITY_SETTINGS)) })
    PermissionRow(R.string.permissions_ime, R.string.permissions_ime_desc, grants.ime,
        onSettings = { open(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) })
    ActionButton(stringResource(R.string.settings_ime_pick)) {
        context.getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
    }
    Section(stringResource(R.string.permissions_usb)) {
        Text(stringResource(R.string.permissions_usb_desc), color = ZtsTextSecondary, fontSize = 12.sp)
        var devices by remember { mutableStateOf(emptyList<UsbHostAccess.Device>()) }
        val owner = LocalView.current.findViewTreeLifecycleOwner()
        LaunchedEffect(context, owner) {
            owner?.lifecycle?.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (isActive) {
                    devices = runCatching { UsbHostAccess.devices(context) }.getOrDefault(emptyList())
                    delay(1500)
                }
            }
        }
        if (devices.isEmpty()) Text(stringResource(R.string.permissions_usb_empty), color = ZtsTextSecondary, fontSize = 12.sp)
        devices.forEach { device ->
            key(device.path) {
                Text(device.productName.ifBlank { device.path }, color = ZtsTextSecondary, fontSize = 12.sp)
                PermissionStatus(device.allowed)
                if (!device.allowed) ActionButton(stringResource(R.string.permissions_request)) {
                    runCatching { UsbHostAccess.allow(context, device.path) }.onSuccess { result ->
                        if (result is UsbHostAccess.AllowResult.NoDevices || result is UsbHostAccess.AllowResult.NotFound)
                            Toast.makeText(context, R.string.permissions_usb_empty, Toast.LENGTH_LONG).show()
                    }.onFailure { Toast.makeText(context, R.string.permissions_open_failed, Toast.LENGTH_LONG).show() }
                }
            }
        }
    }
    if (rootAvailable) Section(stringResource(R.string.permissions_root)) {
        Text(stringResource(if (rootUnlocked) R.string.permissions_root_checked else R.string.permissions_root_desc),
            color = ZtsTextSecondary, fontSize = 12.sp)
        ActionButton(stringResource(if (rootProbing) R.string.settings_root_retry_probing else R.string.settings_root_retry_button),
            onClick = onRootProbe)
    }
    Section(stringResource(R.string.permissions_app_details)) {
        Text(stringResource(R.string.permissions_app_details_desc), color = ZtsTextSecondary, fontSize = 12.sp)
        ActionButton(stringResource(R.string.permissions_manage)) { open(details) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PermissionRow(
    title: Int, description: Int, granted: Boolean,
    onRequest: (() -> Unit)? = null, onSettings: () -> Unit
) {
    Section(stringResource(title)) {
        Text(stringResource(description), color = ZtsTextSecondary, fontSize = 12.sp)
        PermissionStatus(granted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (onRequest != null) ActionButton(stringResource(R.string.permissions_request), onClick = onRequest)
            ActionButton(stringResource(R.string.permissions_manage), onClick = onSettings)
        }
    }
}

@Composable
private fun PermissionStatus(granted: Boolean) {
    Text(stringResource(if (granted) R.string.permissions_granted else R.string.permissions_missing),
        color = if (granted) ZtsGreen else ZtsTextSecondary, fontSize = 12.sp)
}

private data class PermissionGrants(
    val notifications: Boolean, val listener: Boolean, val accessibility: Boolean,
    val overlay: Boolean, val storage: Boolean, val sms: Boolean, val battery: Boolean,
    val writeSettings: Boolean, val usage: Boolean, val install: Boolean,
    val admin: Boolean, val ime: Boolean
) {
    companion object {
        fun read(context: android.content.Context): PermissionGrants {
            fun check(block: () -> Boolean) = runCatching(block).getOrDefault(false)
            fun allowed(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            return PermissionGrants(
                notifications = check { NotificationManagerCompat.from(context).areNotificationsEnabled() },
                listener = check { context.getSystemService(NotificationManager::class.java)
                    .isNotificationListenerAccessGranted(ComponentName(context, NotificationLogService::class.java)) },
                accessibility = check { AndroidActions.enabled(context) },
                overlay = check { Settings.canDrawOverlays(context) },
                storage = check { if (Build.VERSION.SDK_INT >= 30) Environment.isExternalStorageManager()
                    else allowed(Manifest.permission.READ_EXTERNAL_STORAGE) && allowed(Manifest.permission.WRITE_EXTERNAL_STORAGE) },
                sms = allowed(Manifest.permission.RECEIVE_SMS),
                battery = check { BatteryGuard.isIgnoring(context) },
                writeSettings = ScreenTimeout.canWrite(context),
                usage = NetGuard.hasUsageAccess(context),
                install = check { context.packageManager.canRequestPackageInstalls() },
                admin = PasswordWatchAdmin.isActive(context),
                ime = check { context.getSystemService(InputMethodManager::class.java).enabledInputMethodList
                    .any { it.packageName == context.packageName } }
            )
        }
    }
}

@Composable
internal fun PermissionSettingsTabs(notifications: Boolean, onSelect: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(false, true).forEach { logs ->
            Column(Modifier.weight(1f).selectable(selected = notifications == logs, role = Role.Tab, onClick = { onSelect(logs) })) {
                Text(stringResource(if (logs) R.string.permissions_tab_logs else R.string.permissions_tab_access),
                    modifier = Modifier.padding(vertical = 10.dp), color = if (notifications == logs) ZtsGreen else ZtsTextSecondary,
                    fontSize = 13.sp)
                Box(Modifier.fillMaxWidth().height(2.dp).background(if (notifications == logs) ZtsGreen else ZtsBorder))
            }
        }
    }
}

@Composable
internal fun NotificationPermissionHints(notifications: Boolean, sms: Boolean, admin: Boolean, onOpen: () -> Unit) {
    val context = LocalContext.current
    val revision = rememberPermissionRefresh()
    val grants = remember(context, revision) { PermissionGrants.read(context) }
    val missing = listOfNotNull(
        if (notifications && !grants.listener) stringResource(R.string.permissions_listener) else null,
        if (sms && !grants.sms) stringResource(R.string.permissions_sms) else null,
        if (admin && !grants.admin) stringResource(R.string.permissions_admin) else null
    )
    if (missing.isNotEmpty()) {
        Text(stringResource(R.string.permissions_log_missing, missing.joinToString(", ")),
            color = ZtsTextSecondary, fontSize = 12.sp)
        ActionButton(stringResource(R.string.permissions_tab_access), onClick = onOpen)
    }
}
