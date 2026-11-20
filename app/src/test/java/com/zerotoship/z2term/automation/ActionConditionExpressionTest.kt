package com.zerotoship.z2term.automation

import com.zerotoship.z2term.automation.ActionCondition.Term
import org.junit.Assert.*
import org.junit.Test

class ActionConditionExpressionTest {
    @Test fun commaBindsTighterThanOrAndShellTakesTheRest() {
        val expression = ActionCondition.parse("charging,ui text=OK|!color 10,20=red|command grep -q 'a,b|c' f # x")
        assertEquals(3, expression.groups.size)
        assertEquals(listOf(Term.State(false, "charging", "", ""), Term.Ui(false, ActionSelector("text", "OK"))), expression.groups[0])
        assertEquals(listOf(Term.Color(true, 10, 20, "red", null)), expression.groups[1])
        assertEquals(listOf(Term.Shell(false, "grep -q 'a,b|c' f # x")), expression.groups[2])
        assertTrue(expression.readsScreen)
        assertFalse(ActionCondition.parse("wifi|level<20").readsScreen)
        assertEquals(Term.Color(false, 1, 2, null, 0x12ab34), ActionCondition.parse("color 1,2=#12AB34").terms.single())
        assertEquals(ActionSelector("desc", "Open menu"), (ActionCondition.parse("ui desc=Open menu").terms.single() as Term.Ui).selector)
    }

    @Test fun malformedTermsAreRejected() {
        for (spec in listOf("", "wifi|", "|wifi", "wifi,,charging", "ui", "ui name=x", "color 1,2=mauve", "color 1=red",
            "color a,b=red", "command", "command   ", "!", "wifi|" + "wifi|".repeat(15) + "wifi")) {
            assertThrows(spec, IllegalArgumentException::class.java) { ActionCondition.validate(spec) }
        }
        ActionCondition.validate("wifi|".repeat(15) + "wifi")
    }

    @Test fun shellStartFindsOnlyACommandTermAtATermBoundary() {
        assertEquals(10, ActionCondition.shellStart("charging, command echo #"))
        assertEquals(1, ActionCondition.shellStart("!command test -f x"))
        assertNull(ActionCondition.shellStart("ui desc=a command b"))
        assertNull(ActionCondition.shellStart("commander"))
    }

    @Test fun colourNamesAndNearbyRgbMatch() {
        assertTrue(Term.Color(false, 0, 0, "red", null).matches(0xe02020))
        assertFalse(Term.Color(false, 0, 0, "red", null).matches(0x20e020))
        assertTrue(Term.Color(false, 0, 0, null, 0x808080).matches(0x989868))
        assertFalse(Term.Color(false, 0, 0, null, 0x808080).matches(0x999999))
    }

    private class Probes {
        val asked = mutableListOf<Term>()
        val pending = mutableListOf<(Boolean?, String?) -> Unit>()
        var cancelled = 0
        fun probe(term: Term, answer: (Boolean?, String?) -> Unit): () -> Unit {
            asked += term; pending += answer
            return { cancelled++ }
        }
    }

    @Test fun evaluationStopsAsSoonAsTheAnswerIsKnownAndAppliesNegation() {
        val probes = Probes()
        var reads = 0
        val results = mutableListOf<Pair<Boolean?, String?>>()
        ActionCondition.evaluate(ActionCondition.parse("charging,!ui text=A|wifi,ui text=B|ui text=C"),
            { reads++; mapOf("charging" to "true", "wifi" to "true") }, probes::probe) { m, e -> results += m to e }
        assertEquals(1, probes.asked.size)
        probes.pending[0](true, null)           // !ui text=A → 偽。次の組へ
        assertEquals(2, probes.asked.size)
        assertEquals(ActionSelector("text", "B"), (probes.asked[1] as Term.Ui).selector)
        probes.pending[1](true, null)           // wifi,ui text=B → 真。ui text=C は見ない
        assertEquals(listOf<Pair<Boolean?, String?>>(true to null), results)
        assertEquals(2, probes.asked.size)
        assertEquals(1, reads)                  // 端末の状態は 1 回だけ読む
    }

    @Test fun errorsAndMissingStateFailInsteadOfChoosingElse() {
        val results = mutableListOf<Pair<Boolean?, String?>>()
        ActionCondition.evaluate(ActionCondition.parse("level<20"), { mapOf("level" to "-1") },
            { _, _ -> error("unused") }) { m, e -> results += m to e }
        assertNull(results.single().first)
        assertNotNull(results.single().second)
        val probes = Probes()
        results.clear()
        ActionCondition.evaluate(ActionCondition.parse("command false"), { emptyMap() }, probes::probe) { m, e -> results += m to e }
        probes.pending[0](null, "Command timed out")
        assertEquals(listOf<Pair<Boolean?, String?>>(null to "Command timed out"), results)
    }

    @Test fun cancellingStopsTheProbeAndNeverAnswers() {
        val probes = Probes()
        val results = mutableListOf<Pair<Boolean?, String?>>()
        val cancel = ActionCondition.evaluate(ActionCondition.parse("ui text=A"), { emptyMap() }, probes::probe) { m, e -> results += m to e }
        cancel()
        assertEquals(1, probes.cancelled)
        probes.pending[0](true, null)
        assertTrue(results.isEmpty())
    }
}
