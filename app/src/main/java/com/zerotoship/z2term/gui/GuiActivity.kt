package com.zerotoship.z2term.gui

import android.os.Bundle
import android.util.DisplayMetrics
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.zerotoship.z2term.BuildConfig
import com.zerotoship.z2term.ui.theme.Z2TermTheme

/**
 * M8-2 検証用の単独 GUI Activity（**表示パイプラインの確認専用**）。
 *
 * `adb shell am start -n <appId>/com.zerotoship.z2term.gui.GuiActivity` で起動でき、
 * 端末 UI に手を入れずに「リモート画面が映る」ところまで確認できる。解像度は intent extra
 * `width`/`height`（無指定なら画面サイズ）。
 *
 * 正式なタブ統合・入力は M8-3/M8-4 で行う。それまでの暫定入口なので **debug ビルドのみ有効**
 * （release では即終了）。
 */
class GuiActivity : ComponentActivity() {

    private var session: GuiSession? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!BuildConfig.DEBUG) {
            finish()
            return
        }
        enableEdgeToEdge()

        val metrics = DisplayMetrics().also {
            @Suppress("DEPRECATION") windowManager.defaultDisplay.getRealMetrics(it)
        }
        val w = intent.getIntExtra("width", metrics.widthPixels.coerceIn(320, 4096))
        val h = intent.getIntExtra("height", metrics.heightPixels.coerceIn(320, 4096))

        val s = GuiSession(applicationContext)
        session = s
        s.start(w, h)

        setContent {
            Z2TermTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                    GuiScreen(s)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        session?.stop()
    }
}
