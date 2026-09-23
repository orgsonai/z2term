package com.zerotoship.z2term.edge

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.zerotoship.z2term.R
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/** The panel entry writes a definition; the CLI entry returns the selected package. */
class AppPickerActivity : Activity() {
    private val main = Handler(Looper.getMainLooper())
    private val expire = Runnable { finish() }
    private var selected = false
    private lateinit var search: android.widget.EditText

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val request = intent.getStringExtra("request")
        if (request != null && !pending.containsKey(request)) { finish(); return }
        val entries = packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0
        ).distinctBy { it.activityInfo.packageName }.sortedBy { it.loadLabel(packageManager).toString() }
        title = getString(R.string.edge_pick_app)
        fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
        val labels = entries.associateWith { it.loadLabel(packageManager).toString() }
        var visible = entries
        val icons = android.util.LruCache<String, android.graphics.drawable.Drawable>(64)
        val body = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        search = android.widget.EditText(this).apply {
            hint = getString(R.string.edge_search_apps)
            setSingleLine(true)
            setText(savedInstanceState?.getString("query").orEmpty())
        }
        body.addView(search)
        val empty = android.widget.TextView(this).apply { text = getString(R.string.edge_no_apps) }
        body.addView(empty)
        val list = android.widget.ListView(this)
        body.addView(list, android.widget.LinearLayout.LayoutParams(-1, 0, 1f))
        list.emptyView = empty
        val adapter = object : android.widget.BaseAdapter() {
            override fun getCount() = visible.size
            override fun getItem(position: Int) = visible[position]
            override fun getItemId(position: Int) = position.toLong()
            override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
                val row = convertView as? android.widget.LinearLayout ?: android.widget.LinearLayout(this@AppPickerActivity).apply {
                    gravity = android.view.Gravity.CENTER_VERTICAL
                    minimumHeight = dp(64)
                    setPadding(dp(8), dp(6), dp(8), dp(6))
                    addView(android.widget.ImageView(context), android.widget.LinearLayout.LayoutParams(dp(40), dp(40)))
                    addView(android.widget.LinearLayout(context).apply {
                        orientation = android.widget.LinearLayout.VERTICAL
                        setPadding(dp(12), 0, 0, 0)
                        addView(android.widget.TextView(context).apply { textSize = 17f })
                        addView(android.widget.TextView(context).apply { textSize = 12f; alpha = 0.7f })
                    }, android.widget.LinearLayout.LayoutParams(0, -2, 1f))
                }
                val entry = visible[position]
                val pkg = entry.activityInfo.packageName
                val icon = icons[pkg] ?: runCatching { entry.loadIcon(packageManager) }.getOrNull()?.also { icons.put(pkg, it) }
                (row.getChildAt(0) as android.widget.ImageView).setImageDrawable(icon)
                val text = row.getChildAt(1) as android.widget.LinearLayout
                (text.getChildAt(0) as android.widget.TextView).text = labels[entry]
                (text.getChildAt(1) as android.widget.TextView).text = pkg
                return row
            }
        }
        list.adapter = adapter
        fun filter() {
            val query = search.text.toString().trim()
            visible = entries.filter { labels[it].orEmpty().contains(query, ignoreCase = true) ||
                it.activityInfo.packageName.contains(query, ignoreCase = true) }
            adapter.notifyDataSetChanged()
        }
        search.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { filter() }
            override fun afterTextChanged(s: android.text.Editable?) = Unit
        })
        filter()
        body.addView(android.widget.Button(this).apply {
            setText(android.R.string.cancel); setOnClickListener { finish() }
        })
        setContentView(body)
        window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
            android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        if (android.os.Build.VERSION.SDK_INT >= 30) body.setOnApplyWindowInsetsListener { view, insets ->
            val bars = insets.getInsets(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout())
            view.setPadding(dp(12) + bars.left, dp(8) + bars.top, dp(12) + bars.right, dp(8) + bars.bottom)
            insets
        }
        list.setOnItemClickListener { _, _, index, _ ->
                val entry = visible[index]
                val pkg = entry.activityInfo.packageName
                fun save(windowMode: String) {
                runCatching {
                    val panel = intent.getStringExtra("panel")
                    if (panel != null) {
                        val store = EdgeRuntime.store(this)
                        val id = "app_" + UUID.randomUUID().toString().replace("-", "")
                        // In a tab arranged in rows, the app joins the last row of apps, after its items.
                        val existing = store.panel(panel).items
                        val placement = EdgeRows.rowForNewApp(existing)?.let { row ->
                            mapOf("row" to row.toString(), "order" to ((existing.maxOfOrNull { it.order } ?: -1) + 1).toString())
                        }.orEmpty()
                        store.setItem("$panel:$id", mapOf("type" to "run", "run" to "z2-intent -p $pkg --window $windowMode",
                            "label" to entry.loadLabel(packageManager).toString().replace('\n', ' ').replace('\r', ' '),
                            "icon" to "@app:$pkg") + placement)
                        EdgeRuntime.reload(this)
                        if (store.enabled()) EdgeRuntime.open(panel, settings = intent.getBooleanExtra("editPanel", false), page = 0)
                    }
                    request?.let { pending[it]?.complete(pkg) }
                    selected = true
                }.onFailure { Toast.makeText(this, it.message, Toast.LENGTH_LONG).show() }
                finish()
                }
                if (intent.hasExtra("panel")) AppLaunch.choose(this) { save(it) } else save("full")
        }
        val deadline = intent.getLongExtra("deadline", 0L).takeIf { it > 0 }
            ?: (android.os.SystemClock.elapsedRealtime() + 120_000).also { intent.putExtra("deadline", it) }
        main.postDelayed(expire, (deadline - android.os.SystemClock.elapsedRealtime()).coerceAtLeast(0))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        if (::search.isInitialized) outState.putString("query", search.text.toString())
        super.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        main.removeCallbacks(expire)
        if (isFinishing && !isChangingConfigurations && !selected && intent.hasExtra("panel")) {
            intent.getStringExtra("panel")?.let { panel ->
                runCatching {
                    if (EdgeRuntime.store(this).enabled()) EdgeRuntime.open(panel,
                        settings = intent.getBooleanExtra("editPanel", false), page = 0)
                }
            }
        }
        if (!isChangingConfigurations && !selected) intent.getStringExtra("request")?.let {
            pending[it]?.completeExceptionally(IllegalStateException("App selection cancelled"))
        }
        super.onDestroy()
    }

    companion object {
        private val pending = ConcurrentHashMap<String, CompletableFuture<String>>()
        fun pick(context: Context): String {
            check(Looper.myLooper() != Looper.getMainLooper()) { "App selection must wait off the main thread" }
            val id = UUID.randomUUID().toString()
            val result = CompletableFuture<String>()
            pending[id] = result
            try {
                context.startActivity(Intent(context, AppPickerActivity::class.java).putExtra("request", id)
                    .putExtra("deadline", android.os.SystemClock.elapsedRealtime() + 120_000)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return result.get(120, TimeUnit.SECONDS)
            } finally { pending.remove(id) }
        }
    }
}
