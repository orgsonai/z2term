package com.zerotoship.z2term.edge

import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EdgeItemGeometryTest {
    @Test fun freeCoordinatesUseTheMeasuredItemSizeAndKeepUnpositionedItemsInOrder() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val canvas = EdgeItemCanvas(context, 400)
            val first = canvas.cell(EdgeStore.Item("first", mapOf("width" to "50%", "at" to "100%,100%")))
            first.addView(View(context), LinearLayout.LayoutParams(-1, 120))
            val next = canvas.cell(EdgeStore.Item("next", emptyMap()))
            next.addView(View(context), LinearLayout.LayoutParams(-1, 50))
            canvas.measure(View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            canvas.layout(0, 0, canvas.measuredWidth, canvas.measuredHeight)
            assertEquals(150, first.width)
            assertEquals(150, first.left)
            assertEquals(280, first.top)
            assertEquals(400, next.top)
            assertEquals(450, canvas.height)
        }
    }
    @Test fun fixedHeightKeepsOversizedContentsScrollable() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val context = instrumentation.targetContext
            val content = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                addView(View(context), LinearLayout.LayoutParams(-1, 500))
            }
            val frame = EdgeItemFrame(context, EdgeStore.Item("input", mapOf("width" to "50%", "height" to "50%")), 400, content)
            frame.measure(View.MeasureSpec.makeMeasureSpec(320, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            frame.layout(0, 0, frame.measuredWidth, frame.measuredHeight)
            assertEquals(160, frame.width)
            assertEquals(200, frame.height)
            val scroll = frame.getChildAt(0) as ScrollView
            assertEquals(500, scroll.getChildAt(0).height)
            assertTrue(scroll.canScrollVertically(1))
        }
    }
}
