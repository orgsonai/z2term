package com.zerotoship.z2term.proot

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class AndroidGuiFontsTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun registersExistingHostFontDirectoriesWithoutCopyingFontsOrReplacingUserConfig() {
        val rootfs = temporary.newFolder("rootfs")
        val systemFonts = temporary.newFolder("system", "fonts")
        val productFonts = temporary.newFolder("product", "fonts")
        File(systemFonts, "font.ttf").writeText("fixture")
        val userConfig = File(rootfs, "etc/fonts/local.conf")
        userConfig.parentFile.mkdirs()
        userConfig.writeText("user settings")

        val binds = AndroidGuiFonts.prepare(rootfs, listOf(systemFonts, productFonts, File(rootfs, "missing")))

        assertEquals(listOf(systemFonts, productFonts), binds.map { it.first })
        assertEquals(listOf("system", "product").map { "${AndroidGuiFonts.GUEST_DIR}/$it" }, binds.map { it.second })
        for ((_, target) in binds) {
            val mountpoint = File(rootfs, target.removePrefix("/"))
            assertTrue(mountpoint.isDirectory)
            assertTrue(mountpoint.listFiles()!!.isEmpty())
        }
        assertEquals("user settings", userConfig.readText())
        val config = File(rootfs, "etc/fonts/conf.d/99-z2term-android-fonts.conf")
        val factory = DocumentBuilderFactory.newInstance().apply {
            setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        }
        val xml = factory.newDocumentBuilder().parse(config)
        assertEquals(AndroidGuiFonts.GUEST_DIR, xml.getElementsByTagName("dir").item(0).textContent)
        assertEquals(binds, AndroidGuiFonts.prepare(rootfs, listOf(systemFonts, productFonts)))
    }

    @Test fun missingHostFontsLeaveTheDistroAlone() {
        val rootfs = temporary.newFolder("rootfs")
        assertTrue(AndroidGuiFonts.prepare(rootfs, listOf(File(rootfs, "missing"))).isEmpty())
        assertFalse(File(rootfs, "etc/fonts").exists())
    }
}
