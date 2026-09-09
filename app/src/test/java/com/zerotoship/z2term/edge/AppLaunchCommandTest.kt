package com.zerotoship.z2term.edge

import org.junit.Assert.*
import org.junit.Test

class AppLaunchCommandTest {
    @Test fun optionsRetainTheIconAndModeChangesRemoveIncompatibleOptions() {
        val command = "z2-intent -p org.example.app --reuse-task --window freeform --os-bounds"
        assertEquals("org.example.app", AppLaunchCommand.packageFrom(command))
        assertEquals(command, AppLaunchCommand.withMode(command, "freeform"))
        assertEquals("z2-intent -p org.example.app --window full", AppLaunchCommand.withMode(command, "full"))
        assertEquals("split", AppLaunchCommand.modeFrom(AppLaunchCommand.withMode(command, "split")))
        assertEquals("z2-intent -p org.example.app --window ask",
            AppLaunchCommand.withMode("z2-intent -p org.example.app", "ask"))
    }

    @Test fun recognitionDoesNotInterpretShellCommands() {
        assertNull(AppLaunchCommand.packageFrom("z2-intent -p org.example.app; echo other"))
        assertNull(AppLaunchCommand.packageFrom("z2-intent -p org.example.app --window freeform | cat"))
        assertNull(AppLaunchCommand.packageFrom("z2-intent -p org.example.app --unknown"))
    }

    @Test fun freeformOptionsRejectConflictingOrUnrelatedModes() {
        AppLaunch.FreeformOptions(reuseTask = true, osBounds = true).validate("freeform")
        AppLaunch.FreeformOptions(boundsOnly = true).validate("freeform")
        for ((options, mode) in listOf(
            AppLaunch.FreeformOptions(osBounds = true, boundsOnly = true) to "freeform",
            AppLaunch.FreeformOptions(reuseTask = true) to "full",
            AppLaunch.FreeformOptions(osBounds = true) to "")) {
            try { options.validate(mode); fail("Expected invalid options") }
            catch (_: IllegalArgumentException) { }
        }
    }
}
