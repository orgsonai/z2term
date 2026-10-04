package com.zerotoship.z2term.edge

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Local-only surfaces for measuring the accessibility scroll controller in device tests. */
@SuppressLint("SetTextI18n", "ClickableViewAccessibility")
class EdgeScrollProbeActivity : Activity() {
    lateinit var surface: View
    lateinit var tabs: HorizontalScrollView
    var pageReady = false
    var downs = 0
    var ups = 0
    val positions = mutableListOf<Pair<Long, Int>>()

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val width = resources.displayMetrics.widthPixels
        tabs = HorizontalScrollView(this)
        val pages = LinearLayout(this)
        surface = if (intent.getBooleanExtra("web", false)) {
            WebView(this).apply {
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) { pageReady = true }
                }
                loadDataWithBaseURL(null, "<meta name='viewport' content='width=device-width,initial-scale=1'><body style='margin:0'>" + "<div style='height:100px;border-bottom:1px solid gray'>Scroll probe</div>".repeat(300) + "</body>", "text/html", "UTF-8", null)
            }
        } else {
            pageReady = true
            ScrollView(this).apply {
                addView(TextView(this@EdgeScrollProbeActivity).apply {
                    text = "Scroll probe"; minimumHeight = 60000
                }, android.widget.FrameLayout.LayoutParams(-1, -2))
            }
        }
        // Observe only; returning false leaves click handling with the original view.
        surface.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> downs++
                MotionEvent.ACTION_UP -> ups++
            }
            false
        }
        surface.setOnScrollChangeListener { _: View, _: Int, y: Int, _: Int, _: Int ->
            positions += android.os.SystemClock.uptimeMillis() to y
        }
        pages.addView(surface, LinearLayout.LayoutParams(width, -1))
        pages.addView(View(this), LinearLayout.LayoutParams(width, -1))
        tabs.addView(pages)
        setContentView(tabs)
    }

    override fun onDestroy() {
        (surface as? WebView)?.destroy()
        super.onDestroy()
    }
}
