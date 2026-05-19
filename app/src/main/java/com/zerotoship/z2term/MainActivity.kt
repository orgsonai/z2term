package com.zerotoship.z2term

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.zerotoship.z2term.core.SessionManager
import com.zerotoship.z2term.service.TerminalService
import com.zerotoship.z2term.ui.terminal.TerminalScreen
import com.zerotoship.z2term.ui.theme.Z2TermTheme
import com.zerotoship.z2term.ui.theme.ZtsBgPrimary

/**
 * エントリ Activity。
 *
 * - `SessionManager.ensureFirst` で最初のセッションを確保し、画面に表示する。
 * - `TerminalService` を起動して、Activity 破棄後も PTY を維持。
 * - Android 13+ では `POST_NOTIFICATIONS` をリクエスト (失敗しても起動継続)。
 */
class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted/denied どちらでもアプリは継続。永続通知が出ないだけ。 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        requestNotificationPermissionIfNeeded()
        TerminalService.start(applicationContext)

        // 最初のセッションを必ず確保 (TerminalScreen は SessionManager を直接観測)
        SessionManager.ensureFirst(applicationContext)

        setContent {
            Z2TermTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = ZtsBgPrimary
                ) {
                    TerminalScreen()
                }
            }
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
