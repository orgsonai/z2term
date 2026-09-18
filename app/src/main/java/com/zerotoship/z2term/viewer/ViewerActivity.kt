package com.zerotoship.z2term.viewer

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.zerotoship.z2term.R
import com.zerotoship.z2term.security.AppLock
import com.zerotoship.z2term.settings.AppSettings
import com.zerotoship.z2term.settings.CustomThemeStore
import com.zerotoship.z2term.settings.LocaleHelper
import com.zerotoship.z2term.ui.theme.AppColors
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.io.File

/**
 * `z2-view <ファイル>` — 端末で作った HTML を**アプリの中で読む** (0.8.628)。
 *
 * **なぜ要るか**: 通知は「気付く」ためのもので、**並べて読むのには向かない** (利用者の指摘:
 * 「rss ですが通知だと購読しにくいですね」)。読み物にするなら HTML が素直だが、⚠ **`file://` は
 * 他アプリへ渡せない** (`FileUriExposedException`) ので、端末で書いた HTML を既定のブラウザへ
 * 渡す道は塞がっている。⚠ **HTTP サーバーを立てて読むのは常駐が 1 つ増える** — 読み物 1 枚の
 * ために常駐は増やさない。⇒ **自前で開く**。
 *
 * ⚠ **中身は文字列として読み込む** (`loadDataWithBaseURL(null, …)`)。WebView にファイルを
 * 開かせない (`allowFileAccess = false`) ので、ここから置き場をたどる道が無い。
 * ⚠ **JavaScript も外部通信も止める**。書くのは端末側とはいえ、**フィードから来た文字がそのまま
 * 載る**ので、読み物側には何もさせない (外部画像の取得も止める = 開いただけで読んだことが
 * 相手に伝わらない)。
 * ⚠ **`http(s)` のリンクは外のブラウザへ出す**。中で開けるようにするとここがミニブラウザになり、
 * 通信を止めてある意味が無くなる。
 *
 * ⚠ **配色は端末のテーマから渡す** ([THEME_STYLE])。読み物ごとに色を決め打ちさせると、
 * 明るいテーマの人に真っ黒な頁が出る。`var(--z2-bg)` などを使って書けば、テーマに追従する。
 */
class ViewerActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) { super.attachBaseContext(LocaleHelper.applyLocale(newBase)) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()
        CustomThemeStore.ensureLoaded(applicationContext)
        val path = intent.getStringExtra(EXTRA_PATH)
        val page = path?.let { staged(this, it) }?.takeIf { it.isFile }
        if (page == null) {
            Toast.makeText(this, R.string.viewer_missing, Toast.LENGTH_LONG).show(); finish(); return
        }
        val html = runCatching { page.readText() }.getOrNull()
        if (html == null) {
            Toast.makeText(this, R.string.viewer_missing, Toast.LENGTH_LONG).show(); finish(); return
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(AppColors.bgPrimary.toArgb())
        }
        val heading = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { page.nameWithoutExtension }
        root.addView(TextView(this).apply {
            text = heading
            textSize = 15f
            gravity = Gravity.CENTER_VERTICAL
            setTextColor(AppColors.textSecondary.toArgb())
            setBackgroundColor(AppColors.bgSecondary.toArgb())
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            val pad = (12 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, pad / 2)
        }, LinearLayout.LayoutParams(-1, -2))

        val web = WebView(this).apply {
            setBackgroundColor(AppColors.bgPrimary.toArgb())
            isVerticalScrollBarEnabled = true
            settings.javaScriptEnabled = false
            settings.allowFileAccess = false
            settings.allowContentAccess = false
            settings.domStorageEnabled = false
            settings.blockNetworkLoads = true
            settings.builtInZoomControls = true
            settings.displayZoomControls = false
            settings.setSupportZoom(true)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val uri = request?.url ?: return true
                    if (uri.scheme in setOf("http", "https")) open(uri)
                    return true
                }
            }
        }
        root.addView(web, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        ViewCompat.requestApplyInsets(root)
        web.loadDataWithBaseURL(null, themed(html), "text/html", "UTF-8", null)
        lifecycleScope.launch {
            AppSettings(applicationContext).flow.collect { AppLock.applyPolicy(it.appLockEnabled, it.appLockGraceSec) }
        }
    }

    override fun onStart() { super.onStart(); AppLock.onEnterForeground() }
    override fun onStop() {
        if (!isChangingConfigurations) AppLock.onLeaveForeground()
        super.onStop()
    }

    private fun open(uri: Uri) {
        runCatching {
            startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.onFailure { Toast.makeText(this, R.string.viewer_no_browser, Toast.LENGTH_LONG).show() }
    }

    /**
     * 端末のテーマを CSS 変数として先頭に差し込む。
     *
     * ⚠ **頁の側の指定を上書きしない** — 変数を定義するだけで、使うかどうかは書いた人が決める
     * (`var(--z2-bg, white)` のように既定値を書ける)。`<head>` があればその直後、無ければ先頭。
     */
    private fun themed(html: String): String {
        fun hex(color: androidx.compose.ui.graphics.Color) = "#%06X".format(color.toArgb() and 0xFFFFFF)
        val style = THEME_STYLE.format(hex(AppColors.bgPrimary), hex(AppColors.bgSecondary),
            hex(AppColors.textPrimary), hex(AppColors.textSecondary), hex(AppColors.border), hex(AppColors.accent))
        val head = Regex("<head[^>]*>", RegexOption.IGNORE_CASE).find(html)
        return if (head != null) html.substring(0, head.range.last + 1) + style + html.substring(head.range.last + 1)
        else style + html
    }

    companion object {
        private const val EXTRA_PATH = "path"
        private const val EXTRA_TITLE = "title"

        /** ⚠ 置き場は 1 か所に決め打つ。任意のパスを開けるようにすると、端末の外から読ませる道になる。 */
        private const val DIRECTORY = "viewer"

        /** 読み込む上限。⚠ 大きな 1 枚は WebView ごと落ちるので、開く前に断る。 */
        private const val MAX_BYTES = 4L * 1024 * 1024

        private val THEME_STYLE =
            "<style>:root{--z2-bg:%s;--z2-bg2:%s;--z2-fg:%s;--z2-dim:%s;--z2-line:%s;--z2-accent:%s}</style>"

        private fun home(context: Context) = File(context.cacheDir, DIRECTORY)

        private fun staged(context: Context, name: String): File? {
            val file = File(home(context), name)
            val base = home(context).canonicalPath + File.separator
            return if (file.canonicalPath.startsWith(base)) file else null
        }

        /**
         * 端末が渡した 1 枚を取り込んで開く。
         *
         * @param source `/storage/app/z2api/…` に置かれた実体 (端末側が書き、ここで写し取る)
         * @throws IllegalArgumentException 読めない・大きすぎる
         */
        fun show(context: Context, source: File, title: String) {
            require(source.isFile && source.canRead()) { context.getString(R.string.viewer_missing) }
            require(source.length() <= MAX_BYTES) { context.getString(R.string.viewer_too_large) }
            val dir = home(context).apply { mkdirs() }
            // ⚠ 開くたびに溜めない。前に開いた分は消す (読み終わったものを残す意味が無い)。
            dir.listFiles()?.forEach { it.delete() }
            val page = File(dir, "page-${System.currentTimeMillis()}.html")
            source.copyTo(page, overwrite = true)
            context.startActivity(Intent(context, ViewerActivity::class.java)
                .putExtra(EXTRA_PATH, page.name)
                .putExtra(EXTRA_TITLE, title)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        }
    }
}
