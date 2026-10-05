package com.zerotoship.z2term.edge

import android.app.UiAutomation
import android.content.Intent
import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityWindowInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class EdgeContinuousScrollTest {
    @Test fun nativeListKeepsSpeedAndVerticalDirection() = measure(false, 600f)
    @Test fun browserKeepsSpeedAndVerticalDirection() = measure(true, 600f)
    @Test fun browserSlowScrollKeepsMoving() = measure(true, 100f)
    @Test fun nativeListScrollsBackUp() = measure(false, 600f, up = true)
    @Test fun browserScrollsBackUp() = measure(true, 600f, up = true)

    private fun measure(web: Boolean, speed: Float, up: Boolean = false) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext
        check(app.packageName.endsWith(".debug2"))
        // Launch first: instrumentation force-stops the target package, and Android does not
        // rebind a service in a stopped package until its activity has been launched.
        val activity = instrumentation.startActivitySync(Intent(app, EdgeScrollProbeActivity::class.java)
            .putExtra("web", web).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as EdgeScrollProbeActivity
        val error = AtomicReference<String?>()
        try {
            // Activity startup in the test runner may reconnect UiAutomation with default flags.
            // Keep the real accessibility services enabled after startup, before measuring.
            instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
            val connectionDeadline = SystemClock.uptimeMillis() + 5000L
            while (!AndroidActions.connected() && SystemClock.uptimeMillis() < connectionDeadline) SystemClock.sleep(40)
            check(AndroidActions.connected()) { "Enable the debug Android actions service for this test" }
            val field = AndroidActions::class.java.getDeclaredField("active").apply { isAccessible = true }
            val service = field.get(null) as AndroidActions
            val readyDeadline = SystemClock.uptimeMillis() + 5000L
            while (SystemClock.uptimeMillis() < readyDeadline) {
                var ready = false
                instrumentation.runOnMainSync { val windows = service.windows
                    ready = activity.pageReady && activity.surface.height > 0 && activity.hasWindowFocus() &&
                        windows.any { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && (it.isActive || it.isFocused) }
                    @Suppress("DEPRECATION") windows.forEach { it.recycle() } }
                if (ready) break
                SystemClock.sleep(40)
            }
            // Wait for the new window's insets and delayed accessibility events to settle.
            SystemClock.sleep(300)
            val bounds = Rect()
            instrumentation.runOnMainSync {
                assertTrue(activity.pageReady)
                assertTrue("The test surface must have vertical overflow", activity.surface.canScrollVertically(1))
                activity.surface.getGlobalVisibleRect(bounds)
                if (up) activity.surface.scrollTo(0, (bounds.height() * 20f).toInt())
            }
            if (up) SystemClock.sleep(300)
            instrumentation.runOnMainSync {
                AndroidActions.startAutoScroll(if (up) speed else -speed, bounds, how = "auto") { error.set(it) }
            }
            val samples = mutableListOf<Pair<Long, Int>>()
            repeat(45) {
                SystemClock.sleep(80)
                instrumentation.runOnMainSync { samples += SystemClock.uptimeMillis() to activity.surface.scrollY }
            }
            instrumentation.runOnMainSync {
                assertTrue("Auto scroll must remain active", AndroidActions.recentlyAutoScrolling())
                AndroidActions.stopAutoScroll()
            }
            SystemClock.sleep(450)
            var stopped = 0
            var downs = 0
            var horizontal = 0
            instrumentation.runOnMainSync {
                stopped = activity.surface.scrollY; downs = activity.downs; horizontal = activity.tabs.scrollX
                assertEquals("Every held pointer must receive UP", activity.downs, activity.ups)
            }
            SystemClock.sleep(300)
            instrumentation.runOnMainSync { assertEquals("Stop must not leave a fling", stopped, activity.surface.scrollY) }
            assertNull(error.get())
            val density = app.resources.displayMetrics.density
            val first = samples[5]; val last = samples.last()
            val actual = (last.second - first.second) / density * 1000 / (last.first - first.first) * if (up) -1 else 1
            android.util.Log.i("EdgeScrollProbe", "web=$web up=$up requested=$speed actual=$actual downs=$downs horizontal=$horizontal")
            assertTrue("Requested $speed dp/s, actual $actual", abs(actual - speed) <= speed * 0.22f)
            assertEquals("Vertical reading must leave the horizontal pager in place", 0, horizontal)
            assertTrue("Auto must use continuous gestures on these surfaces", downs > 0)
            assertTrue("A continuous scroll must not lift every small movement", downs <= 8)
            var gap = 0L
            var previous = first
            for (sample in samples.drop(6)) {
                if (sample.second != previous.second) { gap = maxOf(gap, sample.first - previous.first); previous = sample }
            }
            assertTrue("Unexpected pause: $gap ms", gap < 400L)
        } finally {
            instrumentation.runOnMainSync { AndroidActions.stopAutoScroll(); activity.finish() }
        }
    }
}
