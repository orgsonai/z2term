package com.zerotoship.z2term.backup

import com.zerotoship.z2term.settings.WhenRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** 取り込んだ自動化ルールは止めた形で書き戻す (1.0.49)。ほかの行は変えない。 */
class BackupStopAutomationTest {
    private fun parse(text: String) = checkNotNull(WhenRule.parse("r", text))

    @Test fun enabledRuleIsStoppedAndKeepsItsOtherLines() {
        val text = "trigger=charge:start\nrun=z2-notify charged\nenabled=1\nname=charge\n"
        val stopped = BackupManager.stoppedRule(text)
        assertEquals("trigger=charge:start\nrun=z2-notify charged\nname=charge\nenabled=0\n", stopped)
        assertFalse(parse(stopped).enabled)
        assertEquals("z2-notify charged", parse(stopped).run)
    }

    @Test fun ruleWithoutEnabledLineIsStopped() {
        // enabled 行が無いルールは「有効」として読まれる。止めるには必ず行を足す。
        assertFalse(parse(BackupManager.stoppedRule("trigger=boot\nrun=echo hi")).enabled)
    }

    @Test fun everyEnabledSpellingIsReplaced() {
        val stopped = BackupManager.stoppedRule("trigger=boot\n enabled = 1\nrun=x\nenabled=yes\n\n")
        assertEquals("trigger=boot\nrun=x\nenabled=0\n", stopped)
        assertFalse(parse(stopped).enabled)
    }
}
