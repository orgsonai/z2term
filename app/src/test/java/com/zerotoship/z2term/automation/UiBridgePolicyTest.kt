package com.zerotoship.z2term.automation

import com.zerotoship.z2term.automation.UiBridgePolicy.Level
import com.zerotoship.z2term.automation.UiBridgePolicy.Target
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class UiBridgePolicyTest {
    private fun refusal(block: () -> Unit): String = try { block(); fail("no refusal"); "" } catch (e: Refusal) { e.code }

    @Test fun targetsAreNumbersSelectorsOrPoints() {
        assertEquals(Target.Ref(2), UiBridgePolicy.target("#2"))
        assertEquals(Target.Point(540, 1200), UiBridgePolicy.target("@540,1200"))
        assertEquals(ActionSelector("text", "次へ"), (UiBridgePolicy.target("text=次へ") as Target.Find).selector)
        assertEquals(ActionSelector("desc", "その他 の操作"), (UiBridgePolicy.target("desc=その他 の操作") as Target.Find).selector)
        for (bad in listOf("#0", "#x", "@1", "@-1,2", "@1,2,3", "label=次へ", "id=next"))
            assertEquals(bad, "bad_args", refusal { UiBridgePolicy.target(bad) })
    }

    @Test fun protectedAppsStayOffWhateverTheList() {
        val own = setOf("com.zerotoship.z2term", "com.android.settings")
        val levels = mapOf("com.android.settings" to Level.AUTO, "*" to Level.AUTO)
        assertEquals(Level.OFF, UiBridgePolicy.level("com.android.settings", own, levels))
        assertEquals(Level.OFF, UiBridgePolicy.level("com.zerotoship.z2term", own, levels))
        assertEquals(Level.AUTO, UiBridgePolicy.level("org.example.app", own, levels))
    }

    @Test fun unlistedAppsAreReadOnlyByDefault() {
        assertEquals(Level.LOOK, UiBridgePolicy.level("org.example.app", emptySet(), emptyMap()))
        assertEquals(Level.ASK, UiBridgePolicy.level("org.example.app", emptySet(), mapOf("org.example.app" to Level.ASK)))
        assertEquals(Level.OFF, UiBridgePolicy.level("org.example.other", emptySet(), mapOf("*" to Level.OFF)))
    }

    @Test fun onlyWideningNeedsApproval() {
        assertTrue(UiBridgePolicy.widens(Level.LOOK, Level.ASK))
        assertTrue(UiBridgePolicy.widens(Level.OFF, Level.AUTO))
        assertFalse(UiBridgePolicy.widens(Level.AUTO, Level.ASK))
        assertFalse(UiBridgePolicy.widens(Level.ASK, Level.ASK))
        assertEquals("bad_args", refusal { Level.parse("all") })
    }

    @Test fun grantMinutesKeepTheHiddenRange() {
        assertEquals(1, UiBridgePolicy.grantMinutes(0))
        assertEquals(60, UiBridgePolicy.grantMinutes(60))
        assertEquals(180, UiBridgePolicy.grantMinutes(180))
        assertEquals(1440, UiBridgePolicy.grantMinutes(5000))
    }

    @Test fun waitTimesAreBounded() {
        assertEquals(5000L, UiBridgePolicy.waitMs(null))
        assertEquals(30_000L, UiBridgePolicy.waitMs("30000"))
        for (bad in listOf("0", "30001", "5s")) assertEquals(bad, "bad_args", refusal { UiBridgePolicy.waitMs(bad) })
    }

    @Test fun rolesFoldIntoASmallVocabulary() {
        fun role(cls: String?, editable: Boolean = false, password: Boolean = false, checkable: Boolean = false,
            clickable: Boolean = false, scrollable: Boolean = false) =
            UiBridgePolicy.role(cls, editable, password, checkable, clickable, scrollable)
        assertEquals("password", role("android.widget.EditText", editable = true, password = true))
        assertEquals("input", role("android.widget.EditText", editable = true))
        assertEquals("switch", role("android.widget.Switch", checkable = true, clickable = true))
        assertEquals("check", role("android.widget.CheckBox", checkable = true, clickable = true))
        assertEquals("list", role("androidx.recyclerview.widget.RecyclerView", scrollable = true))
        assertEquals("button", role("android.widget.ImageButton"))
        assertEquals("button", role("android.view.ViewGroup", clickable = true))
        assertEquals("image", role("android.widget.ImageView"))
        assertEquals("text", role("android.widget.TextView"))
        assertEquals("other", role(null))
    }

    @Test fun textIsCleanedAndCut() {
        assertNull(UiBridgePolicy.clean("  "))
        assertEquals("a b", UiBridgePolicy.clean("a\nb"))
        assertEquals("abc…", UiBridgePolicy.clean("abcdef", limit = 3))
    }
}
