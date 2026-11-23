package com.zerotoship.z2term.channel

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 覚えた機器の表し方 (1.0.212)。「USB 機器を待っています」に、どの機器を待っているかを出すために使う。
 * ⚠ 保存の形 ([SerialConfig.toJson]) はここでは確かめられない (単体試験の org.json は Android の空の版)。
 */
class SerialConfigTest {

    @Test
    fun noRememberedDeviceIsEmpty() {
        assertEquals("", SerialConfig().deviceLabel())
    }

    @Test
    fun rememberedDeviceShowsItsNameAndIds() {
        val c = SerialConfig(vendorId = 0x2e8a, productId = 0x0005, deviceName = "Board in FS mode")
        assertEquals("Board in FS mode · 2e8a:0005", c.deviceLabel())
    }

    @Test
    fun deviceWithoutANameShowsOnlyIds() {
        // 1.0.211 以前に覚えた機器と、製品名を持たない機器。
        assertEquals("1a86:7523", SerialConfig(vendorId = 0x1a86, productId = 0x7523).deviceLabel())
        assertEquals("1a86:7523", SerialConfig(vendorId = 0x1a86, productId = 0x7523, deviceName = "  ").deviceLabel())
    }

    @Test
    fun secondPortOfAChipIsNumbered() {
        val c = SerialConfig(vendorId = 0x0403, productId = 0x6010, portIndex = 1, deviceName = "Dual RS232-HS")
        assertEquals("Dual RS232-HS · 0403:6010 #2", c.deviceLabel())
    }
}
