package com.zerotoship.z2term.workspace

import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zerotoship.z2term.MainActivity
import com.zerotoship.z2term.R
import com.zerotoship.z2term.core.SessionManager
import com.zerotoship.z2term.gui.GuiInputView
import com.zerotoship.z2term.gui.RemoteDesktopClient
import com.zerotoship.z2term.gui.RemoteTarget
import com.zerotoship.z2term.settings.AppSettings
import com.zerotoship.z2term.ui.terminal.input.TerminalInputView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import kotlin.math.abs

/** Real activity/layout regression; the desktop transport stays entirely in memory. */
@RunWith(AndroidJUnit4::class)
class WorkspaceUiTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun focusKeepsPaneGeometryAndGuiLongPressOpensPlacement() {
        val settings = AppSettings(context)
        val saved = runBlocking { settings.flow.first() }
        runBlocking { settings.setKeyboardMode("custom"); settings.setKeyboardToggleBar(true); settings.setLandscapeKeyboardHeightDp(200f) }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var terminalId = ""
            var guiId = ""
            var previous: String? = null
            var previousLayout = SessionLayout()
            var orientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            try {
                scenario.onActivity { activity ->
                    orientation = activity.requestedOrientation
                    previous = SessionManager.activeId.value
                    previousLayout = Workspace.layout
                    terminalId = SessionManager.openNew(activity).id
                    val gui = SessionManager.openRemoteDesktop(activity, object : RemoteTarget {
                        override val host = "test.invalid"
                        override val port = 0
                        override val label = "Layout test"
                        override fun createClient() = MemoryDesktop()
                        override fun closeTransport() = Unit
                    })
                    guiId = gui.id
                    gui.start(800, 600)
                }
                for (mode in listOf("custom", "system")) {
                    runBlocking { settings.setKeyboardMode(mode) }
                    for ((rotation, position) in listOf(
                        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT to AppSettings.LANDSCAPE_KB_BOTTOM,
                        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE to AppSettings.LANDSCAPE_KB_BOTTOM,
                        ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE to AppSettings.LANDSCAPE_KB_LEFT,
                    )) {
                        runBlocking { settings.setLandscapeKeyboardPosition(position) }
                        scenario.onActivity { it.requestedOrientation = rotation }
                        SystemClock.sleep(700)
                        for (horizontal in listOf(false, true)) {
                            android.util.Log.i("WorkspaceUiTest", "mode=$mode rotation=$rotation position=$position horizontal=$horizontal")
                            scenario.onActivity {
                                Workspace.layout = SessionLayout(terminalId, guiId, horizontal = horizontal)
                                SessionManager.setActive(terminalId)
                            }
                            val first = bounds(scenario, gui = false)
                            val second = switchAndRecord(scenario, guiId, gui = true)
                            assertTrue("GUI should receive focus", second.isNotEmpty())
                            second.forEach { rect ->
                                assertTrue("Width changed: $first -> $rect", abs(first.width() - rect.width()) <= 1)
                                assertTrue("Height changed: $first -> $rect", abs(first.height() - rect.height()) <= 1)
                                if (horizontal) assertEquals(first.top, rect.top) else assertEquals(first.left, rect.left)
                            }
                            switchAndRecord(scenario, terminalId, gui = false).forEach { assertEquals(first, it) }
                        }
                    }
                }
                runBlocking { settings.setKeyboardMode("custom") }
                // Folding the keyboard must remain in effect when changing the input pane.
                scenario.onActivity { it.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
                SystemClock.sleep(700)
                val expanded = bounds(scenario, gui = false)
                tap(findText(activityText(scenario, R.string.keyboard_hide_button)))
                val folded = bounds(scenario, gui = false)
                assertTrue("Keyboard should fold", folded.height() > expanded.height())
                switchAndRecord(scenario, guiId, gui = true).forEach {
                    assertTrue("Focus reopened the keyboard: $folded -> $it", abs(folded.height() - it.height()) <= 1)
                }
                val sessionCount = SessionManager.sessions.value.size
                val guiButton = findText("🖥")
                tap(guiButton, longPress = true)
                findText(activityText(scenario, R.string.workspace_title))
                assertEquals("Long press must not create a GUI", sessionCount, SessionManager.sessions.value.size)
                instrumentation.uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            } finally {
                scenario.onActivity { activity ->
                    Workspace.layout = previousLayout
                    if (guiId.isNotEmpty()) SessionManager.close(guiId)
                    if (terminalId.isNotEmpty()) SessionManager.close(terminalId)
                    previous?.let(SessionManager::setActive)
                    activity.requestedOrientation = orientation
                }
                runBlocking {
                    settings.setKeyboardMode(saved.keyboardMode)
                    settings.setKeyboardToggleBar(saved.keyboardToggleBar)
                    settings.setLandscapeKeyboardPosition(saved.landscapeKeyboardPosition)
                    settings.setLandscapeKeyboardHeightDp(saved.landscapeKeyboardHeightDp)
                }
            }
        }
    }

    private fun bounds(scenario: ActivityScenario<MainActivity>, gui: Boolean): Rect {
        SystemClock.sleep(500)
        var rect: Rect? = null
        var tree = ""
        scenario.onActivity {
            rect = inputBounds(it.window.decorView, gui)
            fun describe(view: View): String = "${view.javaClass.simpleName}(${view.width}x${view.height})" +
                if (view is ViewGroup) (0 until view.childCount).joinToString(prefix = "[", postfix = "]") { i -> describe(view.getChildAt(i)) } else ""
            if (rect == null) tree = describe(it.window.decorView)
        }
        return requireNotNull(rect) { "Input view missing (gui=$gui): $tree" }
    }

    private fun switchAndRecord(scenario: ActivityScenario<MainActivity>, id: String, gui: Boolean): List<Rect> {
        val observed = mutableListOf<Rect>()
        lateinit var root: View
        val listener = android.view.ViewTreeObserver.OnPreDrawListener {
            inputBounds(root, gui)?.let {
                if (observed.lastOrNull() != it) android.util.Log.i("WorkspaceUiTest", "gui=$gui bounds=$it ime=${androidx.core.view.ViewCompat.getRootWindowInsets(root)?.getInsets(androidx.core.view.WindowInsetsCompat.Type.ime())?.bottom}")
                observed.add(it)
            }
            true
        }
        scenario.onActivity {
            root = it.window.decorView
            root.viewTreeObserver.addOnPreDrawListener(listener)
            SessionManager.setActive(id)
        }
        SystemClock.sleep(650)
        scenario.onActivity { root.viewTreeObserver.removeOnPreDrawListener(listener) }
        assertTrue("No frames for selected pane", observed.isNotEmpty())
        return observed.distinct()
    }

    private fun inputBounds(view: View, gui: Boolean): Rect? {
        if ((gui && view is GuiInputView || !gui && view is TerminalInputView) && view.width > 0 && view.height > 0) {
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            return Rect(location[0], location[1], location[0] + view.width, location[1] + view.height)
        }
        if (view is ViewGroup) for (i in 0 until view.childCount) inputBounds(view.getChildAt(i), gui)?.let { return it }
        return null
    }

    private fun activityText(scenario: ActivityScenario<MainActivity>, id: Int): String {
        var text = ""
        scenario.onActivity { text = it.getString(id) }
        return text
    }

    private fun findText(text: String): AccessibilityNodeInfo {
        repeat(30) {
            fun search(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
                if (node.text?.toString() == text) return node
                for (i in 0 until node.childCount) node.getChild(i)?.let { search(it) }?.let { return it }
                return null
            }
            instrumentation.uiAutomation.rootInActiveWindow?.let(::search)?.let { return it }
            SystemClock.sleep(100)
        }
        error("UI text missing: $text; visible=" + instrumentation.uiAutomation.rootInActiveWindow?.let { root ->
            fun texts(node: AccessibilityNodeInfo): List<String> = listOfNotNull(node.text?.toString()) +
                (0 until node.childCount).flatMap { node.getChild(it)?.let(::texts).orEmpty() }
            texts(root)
        })
    }

    private fun tap(node: AccessibilityNodeInfo, longPress: Boolean = false) {
        val rect = Rect().also(node::getBoundsInScreen)
        val time = SystemClock.uptimeMillis()
        for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
            val event = MotionEvent.obtain(time, SystemClock.uptimeMillis(), action, rect.exactCenterX(), rect.exactCenterY(), 0)
            instrumentation.sendPointerSync(event)
            event.recycle()
            if (longPress && action == MotionEvent.ACTION_DOWN) {
                SystemClock.sleep(android.view.ViewConfiguration.getLongPressTimeout().toLong() + 150)
            }
        }
    }

    private class MemoryDesktop : RemoteDesktopClient {
        override val width = 800
        override val height = 600
        override val desktopName = "Layout test"
        override val frame = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        override val frameLock = Any()
        override val redraw = MutableStateFlow(0)
        override var onRemoteClipboardText: ((String) -> Unit)? = null
        private val stopped = CountDownLatch(1)
        override fun connect(timeoutMs: Int) = Unit
        override fun run() { stopped.await() }
        override fun close() { stopped.countDown() }
        override fun sendPointerEvent(buttonMask: Int, x: Int, y: Int) = Unit
        override fun sendKeyEvent(keysym: Int, down: Boolean) = Unit
    }
}
