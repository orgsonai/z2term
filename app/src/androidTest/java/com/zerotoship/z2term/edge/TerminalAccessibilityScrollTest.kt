package com.zerotoship.z2term.edge

import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zerotoship.z2term.core.CellMetrics
import com.zerotoship.z2term.core.TerminalSession
import com.zerotoship.z2term.ui.terminal.input.TerminalInputView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TerminalAccessibilityScrollTest {
    @Test fun fractionalVerticalActionsAccumulateWithoutInjectingTouchesOrTyping() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext
        check(app.packageName.endsWith(".debug2"))
        val session = TerminalSession(app)
        try {
            instrumentation.runOnMainSync {
                session.emulator.processBytes(("line\r\n".repeat(200)).toByteArray())
                session.updateCellMetrics(CellMetrics(cellW = 8f, lineHeight = 10f, canvasRows = 24, canvasCols = 80))
                val view = TerminalInputView(app).apply { this.session = session; layout(0, 0, 640, 240) }
                @Suppress("DEPRECATION")
                val info = AccessibilityNodeInfo.obtain()
                try {
                    view.onInitializeAccessibilityNodeInfo(info)
                    assertTrue(info.isScrollable)
                    assertTrue(AccessibilityNodeInfoCompat.wrap(info).isGranularScrollingSupported)
                    assertTrue(info.actionList.any { it.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.id })
                    assertFalse(info.actionList.any { it.id == AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT.id })
                } finally { @Suppress("DEPRECATION") info.recycle() }
                val small = Bundle().apply {
                    putFloat(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_SCROLL_AMOUNT_FLOAT, 0.01f)
                }
                repeat(5) {
                    assertTrue(view.performAccessibilityAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.id, small))
                }
                // 5 * 2.4px accumulates to one 10px line; no rounding loss on each action.
                assertEquals(1, session.scrollOffset.value)
                val page = Bundle().apply {
                    putFloat(AccessibilityNodeInfoCompat.ACTION_ARGUMENT_SCROLL_AMOUNT_FLOAT, 0.25f)
                }
                assertTrue(view.performAccessibilityAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_UP.id, page))
                assertEquals(7, session.scrollOffset.value)
                assertTrue(view.performAccessibilityAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_DOWN.id, page))
                assertEquals(2, session.scrollOffset.value)
                assertFalse(view.performAccessibilityAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_LEFT.id, small))
            }
        } finally { session.shutdown() }
    }
}
