package com.zerotoship.z2term.legal

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OssComponentsTest {
    @Test
    fun smbjRuntimeComponentsAreListed() {
        val names = OssComponents.list().map { it.name }.toSet()

        assertTrue("SMBJ 0.15.0" in names)
        assertTrue("asn-one 0.6.0" in names)
        assertTrue("MBassador 1.3.2" in names)
        assertTrue("SLF4J API 2.0.18" in names)
        assertTrue("Bouncy Castle" in names)
    }

    @Test
    fun everyComponentHasAReadableLicenseAsset() {
        val licenseDir = listOf(
            File("src/main/assets/licenses"),
            File("app/src/main/assets/licenses"),
        ).first { it.isDirectory }

        OssComponents.list().forEach { component ->
            val asset = File(licenseDir, "${component.licenseAsset}.txt")
            assertTrue("Missing license asset for ${component.name}: $asset", asset.isFile)
            assertTrue("Empty license asset for ${component.name}: $asset", asset.length() > 100L)
            assertFalse("Placeholder copyright for ${component.name}", component.copyright.contains("<year>"))
            assertFalse("Template marker for ${component.name}", component.copyright.contains("<<var"))
        }
    }

    @Test
    fun componentNamesAreUniqueAndRetiredJcifsLicenseIsGone() {
        val components = OssComponents.list()
        assertEquals(components.size, components.map { it.name }.distinct().size)

        val licenseDir = listOf(
            File("src/main/assets/licenses"),
            File("app/src/main/assets/licenses"),
        ).first { it.isDirectory }
        assertFalse(File(licenseDir, "LGPL-2.1.txt").exists())
    }
}
