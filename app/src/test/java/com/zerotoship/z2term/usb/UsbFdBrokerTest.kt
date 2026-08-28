package com.zerotoship.z2term.usb

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbFdBrokerTest {
    @Test
    fun acceptsOnlyCanonicalUsbfsDevicePaths() {
        assertTrue(UsbFdBroker.isUsbfsPath("/dev/bus/usb/001/002"))
        assertTrue(UsbFdBroker.isUsbfsPath("/dev/bus/usb/999/000"))

        assertFalse(UsbFdBroker.isUsbfsPath("/dev/bus/usb/1/2"))
        assertFalse(UsbFdBroker.isUsbfsPath("/dev/bus/usb/001/002/extra"))
        assertFalse(UsbFdBroker.isUsbfsPath("/dev/bus/usb/001/../002"))
        assertFalse(UsbFdBroker.isUsbfsPath("/dev/null"))
    }
}
