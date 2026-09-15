package com.zerotoship.z2term.automation

import org.junit.Assert.*
import org.junit.Test

class ActionInputEventsTest {
    private val description = """
        add device 1: /dev/input/event4
          name: "Internal touchscreen"
          ABS_MT_SLOT : value 0, min 0, max 9
          ABS_MT_TRACKING_ID : value 0, min 0, max 65535
          ABS_MT_POSITION_X : value 0, min 0, max 1000
          ABS_MT_POSITION_Y : value 0, min 0, max 2000
          input props:
            INPUT_PROP_DIRECT
    """.trimIndent()
    @Test fun detectsOnlyDirectSlotBasedTouchscreens() {
        val device = ActionInputEvents.devices(description).single()
        assertEquals("/dev/input/event4", device.path)
        assertEquals(ActionInputEvents.Axis(0, 1000), device.x)
        assertTrue(ActionInputEvents.devices(description.replace("INPUT_PROP_DIRECT", "INPUT_PROP_POINTER")).isEmpty())
        assertTrue(ActionInputEvents.devices(description.replace("ABS_MT_SLOT", "ABS_X")).isEmpty())
    }
    @Test fun readsBothContactsAndTrackingIdReleaseWithEventTimestamps() {
        val decoder = ActionInputEvents(ActionInputEvents.devices(description).single(), ActionDefinition.Screen(1001, 2001, 0))
        fun event(code: String, value: String) = decoder.line("[ 12.345678] EV_ABS $code $value")
        event("ABS_MT_TRACKING_ID", "00000001")
        event("ABS_MT_POSITION_X", "00000064")
        event("ABS_MT_POSITION_Y", "000000c8")
        event("ABS_MT_SLOT", "00000001")
        event("ABS_MT_TRACKING_ID", "00000002")
        event("ABS_MT_POSITION_X", "0000012c")
        event("ABS_MT_POSITION_Y", "00000190")
        val frame = decoder.line("[ 12.345678] EV_SYN SYN_REPORT 00000000")!!
        assertEquals(12345L, frame.ms)
        assertEquals(listOf(1, 2), frame.contacts.map { it.id })
        assertEquals(100f, frame.contacts.first().x, 0.001f)
        event("ABS_MT_TRACKING_ID", "ffffffff")
        assertEquals(listOf(1), decoder.line("[ 12.355678] EV_SYN SYN_REPORT 00000000")!!.contacts.map { it.id })
        assertThrows(IllegalArgumentException::class.java) { decoder.line("[ 12.365678] EV_SYN SYN_DROPPED 00000000") }
    }
    @Test fun rotatesNormalizedDeviceCoordinates() {
        val decoder = ActionInputEvents(ActionInputEvents.devices(description).single(), ActionDefinition.Screen(2001, 1001, 1))
        decoder.line("[ 1.000000] EV_ABS ABS_MT_TRACKING_ID 00000001")
        decoder.line("[ 1.000000] EV_ABS ABS_MT_POSITION_X 00000000")
        decoder.line("[ 1.000000] EV_ABS ABS_MT_POSITION_Y 000007d0")
        val p = decoder.line("[ 1.000000] EV_SYN SYN_REPORT 00000000")!!.contacts.single()
        assertEquals(2000f, p.x, 0.001f)
        assertEquals(1000f, p.y, 0.001f)
    }

    @Test fun reportsFinalCoordinatesWhenMotionAndReleaseShareAFrame() {
        val decoder = ActionInputEvents(ActionInputEvents.devices(description).single(), ActionDefinition.Screen(1001, 2001, 0))
        fun event(code: String, value: String) = decoder.line("[ 12.345678] EV_ABS $code $value")
        event("ABS_MT_TRACKING_ID", "00000001")
        event("ABS_MT_POSITION_X", "00000064")
        event("ABS_MT_POSITION_Y", "000000c8")
        decoder.line("[ 12.345678] EV_SYN SYN_REPORT 00000000")
        event("ABS_MT_POSITION_X", "00000320")
        event("ABS_MT_TRACKING_ID", "ffffffff")
        val frame = decoder.line("[ 12.365678] EV_SYN SYN_REPORT 00000000")!!
        assertTrue(frame.contacts.isEmpty())
        assertEquals(800f, frame.released.single().x, 0.001f)
        assertEquals(1, frame.released.single().id)
    }
}
