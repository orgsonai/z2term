package com.zerotoship.z2term.edge

import org.junit.Assert.*
import org.junit.Test

class EdgeNodeScrollPolicyTest {
    private val generic = EdgeNodeScrollPolicy.Actions(false, false, false, false, false, true, true)

    @Test fun horizontalPagerIsNeverAForwardBackwardScrollTarget() {
        assertNull(EdgeNodeScrollPolicy.action(generic, "androidx.viewpager.widget.ViewPager", null, null, -600f))
        assertNull(EdgeNodeScrollPolicy.action(generic.copy(right = true), "android.view.View", 30, 1, -600f))
        assertNull(EdgeNodeScrollPolicy.action(generic.copy(page = true), "android.view.View", 30, 1, -600f))
    }

    @Test fun timelineUsesVerticalActionsEvenInsideABidirectionalContainer() {
        val vertical = generic.copy(up = true, down = true, left = true, right = true)
        assertEquals(EdgeNodeScrollPolicy.Action.DOWN,
            EdgeNodeScrollPolicy.action(vertical, "android.view.View", null, null, -600f))
        assertEquals(EdgeNodeScrollPolicy.Action.UP,
            EdgeNodeScrollPolicy.action(vertical, "android.view.View", null, null, 600f))
    }

    @Test fun reachingTheBottomMustNotSwitchToTheNextHorizontalPage() {
        assertNull(EdgeNodeScrollPolicy.action(generic.copy(up = true, right = true),
            "android.view.View", null, null, -600f))
    }

    @Test fun legacyVerticalListsRemainUsableButUnknownGenericActionsAreNotAssumedVertical() {
        assertEquals(EdgeNodeScrollPolicy.Action.FORWARD,
            EdgeNodeScrollPolicy.action(generic, "android.widget.ScrollView", null, null, -600f))
        assertEquals(EdgeNodeScrollPolicy.Action.BACKWARD,
            EdgeNodeScrollPolicy.action(generic, "androidx.recyclerview.widget.RecyclerView", 30, 1, 600f))
        assertNull(EdgeNodeScrollPolicy.action(generic, "android.view.View", null, null, -600f))
    }

    @Test fun unsupportedFractionalScrollingUsesAFullPageIntervalIncludingAtLowSpeed() {
        assertEquals(1334L, EdgeNodeScrollPolicy.periodMs(600f, 800f, false))
        assertEquals(16000L, EdgeNodeScrollPolicy.periodMs(50f, 800f, false))
        assertEquals(250L, EdgeNodeScrollPolicy.periodMs(40000f, 800f, false))
        assertEquals(40L, EdgeNodeScrollPolicy.periodMs(50f, 800f, true))
    }
}
