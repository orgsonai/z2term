package com.zerotoship.z2term.editor

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.zerotoship.z2term.R
import com.zerotoship.z2term.security.AppLock
import com.zerotoship.z2term.settings.AppSettings
import com.zerotoship.z2term.settings.CustomThemeStore
import com.zerotoship.z2term.settings.LocaleHelper
import com.zerotoship.z2term.service.Z2ApiBridge
import com.zerotoship.z2term.ui.theme.AppColors
import kotlinx.coroutines.launch
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

/**
 * `z2-edit <ファイル>` (1.0.67) の編集画面。端末の文字ファイルを、ふつうの入力欄で書き換える。
 * nano や vi はスマホのキーボードでは保存・終了の操作が重い、という利用者の声から。
 *
 * ⚠ **ファイルそのものには触らない**。`z2-edit` が受付口の置き場 (`<z2api>/<stage>/text.txt`) へ写し、
 * この画面は置き場の名前だけを受け取る ([com.zerotoship.z2term.service.Z2ApiBridge] の `view` と同じ理由:
 * パスを受けると、端末側から任意のファイルをアプリに読み書きさせる口になる)。
 * 保存は `text.txt` を書き換えて印 `saved` を置くだけで、元のファイルへ書き戻すのは待っている `z2-edit`。
 * 閉じたら印 `closed` を置き、`z2-edit` はそれを見て終わる。
 */
class EditorActivity : ComponentActivity() {
    private lateinit var stage: File
    private lateinit var field: EditText
    private var savedText = ""
    private var textSizeSp = DEFAULT_TEXT_SP

    override fun attachBaseContext(newBase: Context) { super.attachBaseContext(LocaleHelper.applyLocale(newBase)) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        actionBar?.hide()
        CustomThemeStore.ensureLoaded(applicationContext)
        val dir = stageDir(this, intent.getStringExtra(EXTRA_STAGE).orEmpty())
        val text = dir?.let { runCatching { read(File(it, TEXT)) }.getOrNull() }
        if (dir == null || text == null) {
            Toast.makeText(this, R.string.editor_unreadable, Toast.LENGTH_LONG).show()
            dir?.let { mark(it, CLOSED) }
            finish(); return
        }
        stage = dir
        savedText = text
        textSizeSp = getSharedPreferences(PREFS, MODE_PRIVATE).getFloat(TEXT_SIZE, DEFAULT_TEXT_SP).coerceIn(MIN_TEXT_SP, MAX_TEXT_SP)

        val density = resources.displayMetrics.density
        val pad = (12 * density).toInt()
        val accent = AppColors.accent.toArgb()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setBackgroundColor(AppColors.bgPrimary.toArgb())
        }
        // 上の帯は z2-view の画面 (ViewerActivity) と同じ: 緑の ← と等幅の題名、押すと戻る。右端に「保存」。
        root.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            background = android.graphics.drawable.GradientDrawable().apply {
                setColor(AppColors.bgPrimary.toArgb()); setStroke(density.toInt().coerceAtLeast(1), AppColors.border.toArgb())
            }
            isClickable = true; contentDescription = getString(R.string.viewer_close)
            setOnClickListener { onBackPressedDispatcher.onBackPressed() }
            addView(TextView(this@EditorActivity).apply {
                this.text = "←"; textSize = 15f; maxLines = 1; setTextColor(accent)
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
                importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO
                setPadding(pad, pad / 2, pad / 2, pad / 2)
            }, LinearLayout.LayoutParams(-2, -2))
            addView(TextView(this@EditorActivity).apply {
                this.text = intent.getStringExtra(EXTRA_TITLE).orEmpty(); textSize = 15f; maxLines = 1; setTextColor(accent)
                ellipsize = android.text.TextUtils.TruncateAt.START
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, 600, false)
                setPadding(pad / 2, pad / 2, pad / 2, pad / 2)
            }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(TextView(this@EditorActivity).apply {
                this.text = getString(R.string.editor_save); textSize = 15f; setTextColor(accent)
                typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                setPadding(pad, pad / 2, pad, pad / 2)
                isClickable = true; isFocusable = true
                setOnClickListener { save() }
            }, LinearLayout.LayoutParams(-2, -2))
        }, LinearLayout.LayoutParams(-1, -2))

        field = EditText(this).apply {
            // 決まった id があれば、回転で作り直しても書きかけの文字が残る。
            id = android.R.id.edit
            setText(text)
            textSize = textSizeSp
            typeface = android.graphics.Typeface.MONOSPACE
            gravity = Gravity.TOP or Gravity.START
            setTextColor(AppColors.textPrimary.toArgb())
            background = null
            setPadding(pad, pad, pad, pad)
            // 予測変換や自動修正で、設定の綴りが勝手に変わらないようにする。
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI
            isVerticalScrollBarEnabled = true
        }
        // 2 本指のピンチで文字の大きさを変え、次に開くときも同じ大きさで開く。指が 2 本になった時点で
        // 入力欄へ取り消しを送り、離し終えるまで入力欄には渡さない (範囲選択やカーソル移動に化けない)。
        val pinch = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                textSizeSp = (textSizeSp * detector.scaleFactor).coerceIn(MIN_TEXT_SP, MAX_TEXT_SP)
                field.textSize = textSizeSp
                return true
            }
            override fun onScaleEnd(detector: ScaleGestureDetector) {
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putFloat(TEXT_SIZE, textSizeSp).apply()
            }
        })
        var pinching = false
        @Suppress("ClickableViewAccessibility")
        field.setOnTouchListener { view, event ->
            pinch.onTouchEvent(event)
            if (event.actionMasked == MotionEvent.ACTION_POINTER_DOWN && !pinching) {
                pinching = true
                val cancel = MotionEvent.obtain(event).apply { action = MotionEvent.ACTION_CANCEL }
                view.onTouchEvent(cancel); cancel.recycle()
            }
            val consumed = pinching
            if (event.actionMasked == MotionEvent.ACTION_UP || event.actionMasked == MotionEvent.ACTION_CANCEL) pinching = false
            consumed
        }
        root.addView(field, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom); insets
        }
        ViewCompat.requestApplyInsets(root)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (field.text.toString() == savedText) finish()
                else android.app.AlertDialog.Builder(this@EditorActivity)
                    .setTitle(R.string.edge_discard_title).setMessage(R.string.edge_discard_message)
                    .setPositiveButton(R.string.editor_save_and_close) { _, _ -> if (save()) finish() }
                    .setNeutralButton(R.string.edge_discard) { _, _ -> finish() }
                    .setNegativeButton(R.string.edge_keep_editing, null).show()
            }
        })
        lifecycleScope.launch {
            AppSettings(applicationContext).flow.collect { AppLock.applyPolicy(it.appLockEnabled, it.appLockGraceSec) }
        }
    }

    /** 置き場の `text.txt` を書き換え、`z2-edit` へ「保存した」と知らせる。置き場が消えていれば (z2-edit が止められた) 断る。 */
    private fun save(): Boolean {
        val content = field.text.toString()
        val ok = stage.isDirectory && runCatching {
            val tmp = File(stage, ".$TEXT.tmp")
            tmp.writeText(content, Charsets.UTF_8)
            check(tmp.renameTo(File(stage, TEXT)))
            mark(stage, SAVED)
        }.isSuccess
        if (ok) savedText = content
        Toast.makeText(this, if (ok) R.string.editor_saved else R.string.editor_gone, Toast.LENGTH_SHORT).show()
        return ok
    }

    override fun onStart() { super.onStart(); AppLock.onEnterForeground() }
    override fun onStop() {
        if (!isChangingConfigurations) AppLock.onLeaveForeground()
        super.onStop()
    }
    override fun onDestroy() {
        if (isFinishing && ::stage.isInitialized) mark(stage, CLOSED)
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_STAGE = "stage"
        private const val EXTRA_TITLE = "title"
        private const val TEXT = "text.txt"
        private const val SAVED = "saved"
        private const val CLOSED = "closed"
        private const val PREFS = "editor"
        private const val TEXT_SIZE = "text-size"
        private const val DEFAULT_TEXT_SP = 14f
        private const val MIN_TEXT_SP = 8f
        private const val MAX_TEXT_SP = 40f
        /** EditText が重くならない大きさまで。z2-edit 側も同じ値で断る。 */
        private const val LIMIT = 1024 * 1024

        /** `z2api edit STAGE TITLE` から。置き場の名前を確かめてから開く。 */
        fun open(context: Context, stage: String, title: String) {
            require(stageDir(context, stage) != null) { "edit: bad stage" }
            context.startActivity(Intent(context, EditorActivity::class.java)
                .putExtra(EXTRA_STAGE, stage).putExtra(EXTRA_TITLE, title)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK))
        }

        private fun stageDir(context: Context, stage: String): File? {
            if (stage.isBlank() || stage.contains('/') || stage.contains("..")) return null
            val root = Z2ApiBridge.dir(context).canonicalFile
            val dir = File(root, stage).canonicalFile
            return dir.takeIf { it.parentFile == root && it.isDirectory }
        }

        /** UTF-8 として読めない・大きすぎるものは開かない (保存で中身を壊さないため)。 */
        private fun read(file: File): String {
            val bytes = file.readBytes()
            require(bytes.size <= LIMIT && bytes.none { it == 0.toByte() })
            return Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
        }

        private fun mark(dir: File, name: String) { runCatching { File(dir, name).writeText("") } }
    }
}
