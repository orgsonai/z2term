package com.zerotoship.z2term.viewer

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.zerotoship.z2term.R
import com.zerotoship.z2term.edge.EdgeToolRow
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ViewerPresentationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private fun descendants(view: View): List<View> = listOf(view) +
        if (view is ViewGroup) (0 until view.childCount).flatMap { descendants(view.getChildAt(it)) } else emptyList()

    @Test fun hidingBuiltInButtonsKeepsMacroActionsAndCachedContent() = instrumentation.runOnMainSync {
        val controls = ViewerControls.parse("""{"handler":"example.sh","actions":[{"id":"add","label":"Add item","args":["add"],"toolbar":true,"fields":[{"label":"Text","type":"text"}]}]}""")
        for (refresh in listOf(true, false)) for (expand in listOf(true, false)) {
            val key = "page-test-${UUID.randomUUID()}"
            val seed = ViewerStore.attach(key) { }
            val page = ViewerPage("<p>Saved page</p>", "Test", controls)
            ViewerStore.publish(context, page, "", seed)
            ViewerStore.detach(seed)
            val pane = ViewerPane(context, key, target = "test:page", expand = {},
                options = ViewerOptions(showRefresh = refresh, showExpand = expand))
            try {
                fun hasLabel(label: String) = descendants(pane).filterIsInstance<TextView>().any { it.text.toString() == label }
                assertEquals(refresh, hasLabel(context.getString(R.string.edge_refresh)))
                assertEquals(expand, hasLabel(context.getString(R.string.viewer_expand)))
                assertTrue(hasLabel("Add item"))
                assertEquals(page, ViewerStore.read(context, key))
                descendants(pane).filterIsInstance<TextView>().first { it.text.toString() == "Add item" }.performClick()
                assertTrue("A form remains accessible with both buttons hidden", pane.hasDraft)
            } finally {
                pane.dispose()
                File(context.cacheDir, "viewer-pages/$key.page").delete()
            }
        }
    }

    @Test fun hiddenEmptyToolbarDoesNotLeaveAnEmptyRow() = instrumentation.runOnMainSync {
        val key = "page-test-${UUID.randomUUID()}"
        val pane = ViewerPane(context, key, target = "test:page", expand = {},
            options = ViewerOptions(showRefresh = false, showExpand = false))
        try {
            assertEquals(View.GONE, descendants(pane).filterIsInstance<EdgeToolRow>().single().visibility)
            assertTrue(descendants(pane).filterIsInstance<TextView>().any {
                it.text.toString() == context.getString(R.string.viewer_empty_hidden)
            })
        } finally { pane.dispose() }
    }
}
