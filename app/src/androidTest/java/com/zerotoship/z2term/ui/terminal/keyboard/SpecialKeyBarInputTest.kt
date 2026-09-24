package com.zerotoship.z2term.ui.terminal.keyboard

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.KeyguardManager
import android.graphics.Rect
import android.os.PowerManager
import android.os.SystemClock
import android.view.MotionEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zerotoship.z2term.MainActivity
import com.zerotoship.z2term.ui.terminal.components.SpecialKeyBar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.Collections

/** 実際のタッチで、編集済みの補助バーとスクロールの競合を確認する。 */
@RunWith(AndroidJUnit4::class)
class SpecialKeyBarInputTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()

    @Test fun savedLayoutSurvivesJsonAndInvalidDataUsesDefaultKeys() {
        val layout = specialKeyLayout().copy(rows = listOf(KeyRow(listOf(
            KeySlot.of(KeyDef.text("TEXT", "日本語")),
            KeySlot.of(KeyDef.named("F5", NamedKey.F5)),
        ))))
        assertEquals(layout, specialKeyLayoutFromJson(KeyLayoutJson.toJsonString(layout)))
        assertEquals(specialKeyLayout(), specialKeyLayoutFromJson("invalid JSON"))
        assertEquals(specialKeyLayout(), specialKeyLayoutFromJson(""))
    }

    @Test fun editedKeysSendTextChordsAndGesturesWithoutTypingDuringScroll() {
        val context = instrumentation.targetContext
        assumeTrue("Touch test requires an awake, unlocked device",
            context.getSystemService(PowerManager::class.java).isInteractive &&
                !context.getSystemService(KeyguardManager::class.java).isKeyguardLocked)
        instrumentation.uiAutomation.serviceInfo = instrumentation.uiAutomation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        val sent = Collections.synchronizedList(mutableListOf<String>())
        val ctrl = mutableStateOf(false)
        val gesture = KeyDef(label = "GEST", bindings = mapOf(
            KeyGesture.TAP to listOf(KeyAction.Text("tap")),
            KeyGesture.LEFT to listOf(KeyAction.Text("left")),
            KeyGesture.LONG_PRESS to listOf(KeyAction.Text("hold")),
            KeyGesture.DOUBLE_TAP to listOf(KeyAction.Text("double")),
        ))
        val keys = listOf(
            KeyDef.text("TEXT", "日本語"), KeyDef.named("F5", NamedKey.F5),
            KeyDef.modifier("CTRL", ModKey.CTRL), gesture,
        ) + (1..12).map { KeyDef.text("K$it") }
        val layout = specialKeyLayout().copy(rows = listOf(KeyRow(keys.map { KeySlot.of(it) })))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            SystemClock.sleep(600)
            instrumentation.uiAutomation.rootInActiveWindow?.let(::dismissNotificationDialog)
            instrumentation.waitForIdleSync()
            scenario.onActivity { activity ->
                activity.setContent {
                    Column(Modifier.windowInsetsPadding(WindowInsets.systemBars)) {
                        SpecialKeyBar(
                            layoutJson = KeyLayoutJson.toJsonString(layout),
                            composing = ComposingState { sent.add("text:$it") },
                            ctrlState = ctrl,
                            onBytes = { sent.add("text:${it.toString(Charsets.UTF_8)}") },
                            onKey = { key, mods -> sent.add("key:${key.id}:${mods.ctrl}") },
                        )
                    }
                }
            }
            tap(bounds("TEXT"))
            tap(bounds("CTRL"))
            tap(bounds("F5"))
            instrumentation.waitForIdleSync()
            assertEquals(listOf("text:日本語", "key:f5:true"), sent.toList())
            assertFalse(ctrl.value)
            sent.clear()

            val gestureBounds = bounds("GEST")
            tap(gestureBounds)
            SystemClock.sleep(80)
            tap(gestureBounds)
            SystemClock.sleep(400)
            assertEquals(listOf("text:double"), sent.toList())
            sent.clear()
            tap(gestureBounds, holdMs = android.view.ViewConfiguration.getLongPressTimeout().toLong() + 150)
            assertEquals(listOf("text:hold"), sent.toList())
            sent.clear()
            swipeLeft(gestureBounds)
            SystemClock.sleep(400)
            assertEquals(listOf("text:left"), sent.toList())
            sent.clear()
            // 未割り当ての横ドラッグはバーを動かし、押し始めたキーを送らない。
            swipeLeft(bounds("F5"))
            SystemClock.sleep(400)
            assertEquals(emptyList<String>(), sent.toList())
        }
    }

    private fun bounds(text: String): Rect {
        repeat(30) {
            fun find(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
                if (node.text?.toString() == text) return node
                for (i in 0 until node.childCount) node.getChild(i)?.let(::find)?.let { return it }
                return null
            }
            instrumentation.uiAutomation.rootInActiveWindow?.let(::find)?.let { node ->
                return Rect().also(node::getBoundsInScreen)
            }
            SystemClock.sleep(100)
        }
        error("Key missing: $text; foreground=" + instrumentation.uiAutomation.rootInActiveWindow?.packageName)
    }

    private fun dismissNotificationDialog(node: AccessibilityNodeInfo) {
        if (node.viewIdResourceName?.endsWith(":id/permission_deny_button") == true &&
            node.packageName?.toString()?.endsWith("permissioncontroller") == true) {
            node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            return
        }
        for (i in 0 until node.childCount) node.getChild(i)?.let(::dismissNotificationDialog)
    }

    private fun tap(rect: Rect, holdMs: Long = 40) {
        val start = SystemClock.uptimeMillis()
        pointer(start, MotionEvent.ACTION_DOWN, rect.exactCenterX(), rect.exactCenterY())
        SystemClock.sleep(holdMs)
        pointer(start, MotionEvent.ACTION_UP, rect.exactCenterX(), rect.exactCenterY())
        instrumentation.waitForIdleSync()
    }

    private fun swipeLeft(rect: Rect) {
        val start = SystemClock.uptimeMillis()
        val x = rect.exactCenterX()
        val y = rect.exactCenterY()
        pointer(start, MotionEvent.ACTION_DOWN, x, y)
        for (step in 1..5) {
            SystemClock.sleep(20)
            pointer(start, MotionEvent.ACTION_MOVE, x - rect.width() * step / 5f, y)
        }
        pointer(start, MotionEvent.ACTION_UP, x - rect.width(), y)
        instrumentation.waitForIdleSync()
    }

    private fun pointer(start: Long, action: Int, x: Float, y: Float) {
        val event = MotionEvent.obtain(start, SystemClock.uptimeMillis(), action, x, y, 0)
        instrumentation.sendPointerSync(event)
        event.recycle()
    }
}
