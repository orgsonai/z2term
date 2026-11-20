package com.zerotoship.z2term.automation

import org.junit.Assert.*
import org.junit.Test

class ActionLoopTest {
    private class Clock {
        private data class Task(val time: Long, val order: Int, val run: () -> Unit, var cancelled: Boolean = false)
        private val tasks = mutableListOf<Task>()
        var time = 0L
            private set
        private var order = 0
        fun schedule(delay: Long, block: () -> Unit): () -> Unit {
            val task = Task(time + delay, order++, block); tasks += task
            return { task.cancelled = true }
        }
        fun advance(to: Long) {
            while (true) {
                val task = tasks.filter { !it.cancelled && it.time <= to }
                    .minWithOrNull(compareBy<Task> { it.time }.thenBy { it.order }) ?: break
                tasks.remove(task); time = task.time; task.run()
            }
            time = to
        }
    }
    private fun parse(body: String) = ActionDefinition.parse("version=2\ntimeout=0\n$body")

    @Test fun repeatUntilAndBreakParse() {
        val loop = parse("target org.example.app\nrepeat until ui text~=完了 # 終わるまで\n  wait 1\nend").steps.single() as ActionDefinition.Step.Repeat
        assertNull(loop.count)
        assertEquals("ui text~=完了", loop.until)
        assertEquals("org.example.app", loop.target)
        assertEquals(5, (parse("repeat 5 until charging\n  wait 1\nend").steps.single() as ActionDefinition.Step.Repeat).count)
        assertEquals("charging", (parse("repeat forever until charging\n  wait 1\nend").steps.single() as ActionDefinition.Step.Repeat).until)
        assertNull((parse("repeat 3\n  wait 1\nend").steps.single() as ActionDefinition.Step.Repeat).until)
        val nested = parse("repeat 3\n  if charging\n    break\n  end\n  wait 1\nend").steps.single() as ActionDefinition.Step.Repeat
        assertEquals(ActionDefinition.Step.Break, (nested.body[0] as ActionDefinition.Step.Branch).yes.single())
        assertNull(ActionDefinition.commentStart("repeat until command echo # x"))
        for (body in listOf("break", "if charging\n  break\nend", "repeat until\n  wait 1\nend", "repeat 0 until wifi\n  wait 1\nend",
            "repeat until bogus\n  wait 1\nend", "repeat 3\n  break now\nend")) {
            assertThrows(body, IllegalArgumentException::class.java) { parse(body) }
        }
    }

    @Test fun partialSelectors() {
        val part = ActionSelector.parse("text~=残り")
        assertTrue(part.contains)
        assertTrue(part.matches(null, " 残り 3 回 ", null))
        assertFalse(part.matches(null, "あと 3 回", null))
        assertEquals("text~=残り", part.toString())
        assertTrue(ActionSelector.parse("desc~=More").matches(null, null, "More options"))
        assertFalse(ActionSelector.parse("text=残り").matches(null, "残り 3 回", null))
        assertThrows(IllegalArgumentException::class.java) { ActionSelector.parse("id~=org.example:id/a") }
        assertThrows(IllegalArgumentException::class.java) { ActionSelector.parse("text~=") }
        assertEquals(ActionSelector("text", "OK", true), (ActionCondition.parse("ui text~=OK").terms.single() as ActionCondition.Term.Ui).selector)
    }

    @Test fun repeatUntilChecksBeforeEachPassAndPauseChecksAgain() {
        val clock = Clock(); val engine = ActionExecution(clock::schedule)
        val ran = mutableListOf<Long>()
        val answers = mutableListOf<(Boolean?, String?) -> Unit>()
        val results = mutableListOf<ActionExecution.Result>()
        engine.start(parse("repeat until ui text=Done\n  wait 1\nend\nwait 9"),
            { step, done -> ran += (step as ActionDefinition.Step.Wait).ms; done(null); null }, { _, _ -> }, results::add,
            test = { _, _, answer -> answers += answer; null })
        clock.advance(200)
        assertEquals(1, answers.size)          // 最初の回の前に確かめる
        answers[0](false, null)
        clock.advance(400)
        assertEquals(listOf(1L), ran)
        assertEquals(2, answers.size)          // 2 回目の前
        assertTrue(engine.pause())
        answers[1](true, null)                 // 一時停止で捨てた答えは使わない
        assertTrue(engine.resume())
        clock.advance(600)
        assertEquals(3, answers.size)          // 再開で確かめ直す
        answers[2](true, null)
        clock.advance(1000)
        assertEquals(listOf(1L, 9L), ran)
        assertEquals("completed", results.single().state)
        assertEquals(3, results.single().step)  // repeat・wait 1・wait 9 (条件の確かめは命令に数えない)
    }

    @Test fun breakLeavesTheInnermostLoopThroughBranches() {
        val clock = Clock(); val engine = ActionExecution(clock::schedule)
        val ran = mutableListOf<Long>()
        val results = mutableListOf<ActionExecution.Result>()
        var passes = 0
        engine.start(parse("repeat 3\n  repeat forever\n    wait 1\n    if charging\n      break\n    end\n    wait 2\n  end\n  wait 5\nend"),
            { step, done -> ran += (step as ActionDefinition.Step.Wait).ms; done(null); null }, { _, _ -> }, results::add,
            condition = { passes++ % 2 == 1 })
        clock.advance(5000)
        assertEquals("completed", results.single().state)
        // 内側は 1 回目の if で抜けず (wait 2 まで)、2 回目で抜ける。外側は 3 回。
        assertEquals(listOf(1L, 2L, 1L, 5L, 1L, 2L, 1L, 5L, 1L, 2L, 1L, 5L), ran)
    }
}
