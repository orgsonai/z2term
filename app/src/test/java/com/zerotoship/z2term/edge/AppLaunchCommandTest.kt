package com.zerotoship.z2term.edge

import org.junit.Assert.*
import org.junit.Test
import android.content.Intent

class AppLaunchCommandTest {
    @Test fun standardFreeformLeavesBoundsToAndroidAndReusesTheTask() {
        assertFalse(AppLaunch.FreeformOptions().requestsBounds)
        assertFalse(AppLaunch.FreeformOptions(reuseTask = true, osBounds = true).requestsBounds)
        assertTrue(AppLaunch.FreeformOptions(boundsOnly = true).requestsBounds)
        val input = Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or Intent.FLAG_ACTIVITY_CLEAR_TOP
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP,
            AppLaunch.activityFlags(input, "freeform"))
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK, AppLaunch.activityFlags(0, "freeform"))
        assertEquals(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_MULTIPLE_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP,
            AppLaunch.activityFlags(input, "full"))
    }

    @Test fun optionsRetainTheIconAndModeChangesRemoveIncompatibleOptions() {
        val command = "z2-intent -p org.example.app --reuse-task --window freeform --os-bounds"
        assertEquals("org.example.app", AppLaunchCommand.packageFrom(command))
        assertEquals(command, AppLaunchCommand.withMode(command, "freeform"))
        assertEquals("z2-intent -p org.example.app --window full", AppLaunchCommand.withMode(command, "full"))
        assertEquals("split", AppLaunchCommand.modeFrom(AppLaunchCommand.withMode(command, "split")))
        assertEquals("z2-intent -p org.example.app --window ask",
            AppLaunchCommand.withMode("z2-intent -p org.example.app", "ask"))
    }

    @Test fun freeformScaleDefaultsOnAndCanBeSavedAsAnOptOut() {
        val command = "z2-intent -p org.example.app --window freeform"
        assertTrue(AppLaunchCommand.scalesFreeform(command))
        val unscaled = AppLaunchCommand.withFreeformScale(command, false)
        assertEquals("z2-intent -p org.example.app --window freeform --no-scale", unscaled)
        assertFalse(AppLaunchCommand.scalesFreeform(unscaled))
        assertEquals(command, AppLaunchCommand.withFreeformScale(unscaled, true))
        assertEquals("z2-intent -p org.example.app --window full",
            AppLaunchCommand.withMode(unscaled, "full"))
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
            AppLaunch.FreeformOptions(noScale = true) to "full",
            AppLaunch.FreeformOptions(reuseTask = true) to "full",
            AppLaunch.FreeformOptions(osBounds = true) to "")) {
            try { options.validate(mode); fail("Expected invalid options") }
            catch (_: IllegalArgumentException) { }
        }
    }
}
