package com.zerotoship.z2term.automation

import org.junit.Assert.*
import org.junit.Test

class ActionWatchRunOutputTest {
    private val screen = ActionDefinition.Screen(1080, 2400, 0)

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

    @Test fun watchAndRunOutputParse() {
        val steps = ActionDefinition.parse("version=2\nscreen=current\ntarget org.example.app\n" +
            "watch ui text=Yes|color 1,2=red call ask-user   # 確認\nrun-output python3 /root/plan.py # シェルの #", screen).steps
        assertEquals(ActionDefinition.Step.Watch("org.example.app", "ui text=Yes|color 1,2=red", "ask-user"), steps[0])
        assertEquals(ActionDefinition.Step.RunOutput("org.example.app", "python3 /root/plan.py # シェルの #"), steps[1])
        for (body in listOf("watch ui text=Yes", "watch call x", "watch ui text=Yes call", "watch command true call x",
            "watch ui text=Yes call ../x", "run-output")) {
            assertThrows(body, IllegalArgumentException::class.java) { ActionDefinition.parse("version=2\n$body") }
        }
        assertThrows(IllegalArgumentException::class.java) { ActionDefinition.parse("version=1\nrun-output echo") }
    }

    @Test fun fragmentsInheritScreenAndTargetAndRefuseDynamicCalls() {
        val steps = ActionDefinition.fragment("# 盤面から\ntap px 10 20\ndrag px 50 1,1 2,2\nrepeat 2\n  wait 1\nend\n", screen, "org.example.app")
        assertEquals("org.example.app", (steps[0] as ActionDefinition.Step.Stroke).target)
        assertEquals(3, steps.size)
        assertTrue(ActionDefinition.fragment("\n# 何もしない\n", screen, ActionDefinition.CURRENT_TARGET).isEmpty())
        for (output in listOf("call other", "watch ui text=A call other", "run-output echo", "timeout=5\nwait 1",
            "tap px 2000 10", "bogus")) {
            assertThrows(output, IllegalArgumentException::class.java) { ActionDefinition.fragment(output, screen, ActionDefinition.CURRENT_TARGET) }
        }
    }

    @Test fun watchedMacrosAreLoadedBeforeTheRun() {
        val sources = mapOf("root" to "version=2\nwatch ui text=A call handler\nwait 1", "handler" to "version=1\nwait 2")
        val program = ActionProgram.load("root") { ActionDefinition.parse(sources.getValue(it)) }
        assertEquals(setOf("root", "handler"), program.definitions.keys)
        assertThrows(NoSuchElementException::class.java) {
            ActionProgram.load("root") { ActionDefinition.parse(mapOf("root" to sources.getValue("root")).getValue(it)) }
        }
    }

    @Test fun aWatchCutsAWaitShortRunsTheHandlerAndSendsTheWaitAgain() {
        val clock = Clock(); val engine = ActionExecution(clock::schedule)
        var shown = false
        val ran = mutableListOf<Pair<Long, Long>>()   // (時刻, 待ちの長さ)
        val results = mutableListOf<ActionExecution.Result>()
        val handler = ActionDefinition.parse("version=1\nwait 7")
        engine.start(ActionDefinition.parse("version=2\nwatch ui text=Yes call handler\nwait 1000\nwait 5"),
            { step, done ->
                val ms = (step as ActionDefinition.Step.Wait).ms
                ran += clock.time to ms
                if (ms == 7L) shown = false        // 呼ばれたマクロが確認を閉じる
                clock.schedule(ms) { done(null) }
            }, { _, _ -> }, results::add, resolve = { handler }, name = "root",
            test = { _, _, answer -> answer(shown, null); null })
        clock.advance(400)
        shown = true                                // wait 1000 の途中で確認が出る
        clock.advance(5000)
        val waits = ran.map { it.second }
        assertEquals(listOf(1000L, 7L, 1000L, 5L), waits)
        assertEquals("completed", results.single().state)
    }

    @Test fun aWatchLetsAGestureFinishAndEndsWithItsBlock() {
        val clock = Clock(); val engine = ActionExecution(clock::schedule)
        var shown = false
        val order = mutableListOf<String>()
        val handler = ActionDefinition.parse("version=1\nkey back")
        val results = mutableListOf<ActionExecution.Result>()
        engine.start(ActionDefinition.parse("version=2\nrepeat 1\n  watch ui text=Yes call handler\n  command long\nend\ncommand after"),
            { step, done ->
                val label = when (step) { is ActionDefinition.Step.Command -> step.text; is ActionDefinition.Step.Key -> "key"; else -> "?" }
                order += label
                clock.schedule(if (label == "long") 1000 else 10) { done(null) }
            }, { _, _ -> }, results::add, resolve = { handler }, name = "root",
            test = { _, _, answer -> answer(shown, null); null })
        clock.advance(400)
        shown = true                                // 長いシェルの途中。打ち切らずに終わるのを待つ
        clock.advance(3000)
        assertEquals(listOf("long", "key", "after"), order)
        shown = true
        clock.advance(6000)                         // ブロックが終わったので、もう呼ばない
        assertEquals("completed", results.single().state)
        assertEquals(listOf("long", "key", "after"), order)
    }

    @Test fun runOutputExecutesTheProducedStepsInPlace() {
        val clock = Clock(); val engine = ActionExecution(clock::schedule)
        val ran = mutableListOf<Long>()
        val results = mutableListOf<ActionExecution.Result>()
        val asked = mutableListOf<String>()
        engine.start(ActionDefinition.parse("version=2\nrun-output plan\nwait 9"),
            { step, done -> ran += (step as ActionDefinition.Step.Wait).ms; done(null); null }, { _, _ -> }, results::add,
            expand = { step, produced -> asked += step.text; produced(ActionDefinition.fragment("wait 1\nwait 2", null, step.target), null); null })
        clock.advance(1000)
        assertEquals(listOf("plan"), asked)
        assertEquals(listOf(1L, 2L, 9L), ran)
        assertEquals("completed", results.single().state)

        val failed = mutableListOf<ActionExecution.Result>()
        ActionExecution(clock::schedule).start(ActionDefinition.parse("version=2\nrun-output plan"), { _, done -> done(null); null },
            { _, _ -> }, failed::add, expand = { _, produced -> produced(null, "Command exited: 1"); null })
        clock.advance(2000)
        assertEquals("failed", failed.single().state)
        assertEquals("Command exited: 1", failed.single().error)
    }
}
