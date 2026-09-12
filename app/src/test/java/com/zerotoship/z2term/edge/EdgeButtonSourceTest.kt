package com.zerotoship.z2term.edge

import org.junit.Assert.*
import org.junit.Test

class EdgeButtonSourceTest {
    private fun item(vararg fields: Pair<String, String>) =
        EdgeStore.Item("x", mapOf("button-state" to "on", "run" to "timer.sh") + fields)

    @Test fun deviceCommandsAreRecognizedWithoutExecutingThem() {
        for (command in listOf("z2-torch", "z2-torch toggle", "/usr/local/bin/z2-torch on", "z2-torch off"))
            assertEquals(command, "torch", EdgeButtonSource.direct(command))
        assertEquals("screen", EdgeButtonSource.direct(" z2-screen  keepon 30m "))
        for (command in listOf("echo z2-torch on", "z2-torch on; false", "z2-torch status",
            "z2-screen keepon off", "z2-screen keepon 1h && false", "z2-screen keepon ${'$'}TIME"))
            assertNull(command, EdgeButtonSource.direct(command))
    }

    @Test fun explicitQueriesAndSourcesOverrideAutomaticLearning() {
        assertEquals("screen", EdgeButtonSource.resolve(item(), "screen"))
        assertEquals("torch", EdgeButtonSource.resolve(item("button-source" to "torch"), "screen"))
        assertEquals("process", EdgeButtonSource.resolve(item("button-source" to "process"), "torch"))
        assertNull(EdgeButtonSource.resolve(item("button-source" to "remember"), "torch"))
        assertNull(EdgeButtonSource.resolve(item("state" to "check.sh", "button-source" to "torch"), "screen"))
        assertNull(EdgeButtonSource.resolve(item("button-state" to "off"), "torch"))
        assertNull(EdgeButtonSource.resolve(item("run" to "z2-torch on"), null, listOf("z2-torch")))
    }

    @Test fun actualOnStateSelectsTheOffAction() {
        val light = item("run" to "z2-torch on")
        assertEquals("z2-torch off", EdgeButtonSource.action(light, true, "torch"))
        assertEquals("z2-torch on", EdgeButtonSource.action(light, false, "torch"))
        assertEquals("z2-screen keepon off", EdgeButtonSource.action(item(), true, "screen"))
        assertEquals("cleanup.sh", EdgeButtonSource.action(item("off" to "cleanup.sh"), true, "torch"))
    }
}
