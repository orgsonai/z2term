package com.zerotoship.z2term.icon

import org.junit.Assert.*
import org.junit.Test

class IconSampleUpgradeTest {
    private fun legacy(name: String): BooleanArray {
        val art = javaClass.getResourceAsStream("/icons/$name-legacy.txt")!!
            .bufferedReader().use { it.readText() }
        return IconStore.parse(art)
    }

    @Test fun oldStockAssignmentsUseTheCorrectedShapeAtEveryGridSize() {
        for (name in listOf("bell", "heart")) {
            for (grid in IconStore.GRIDS) {
                val old = IconStore.toText(IconStore.parse(IconStore.zoomText(legacy(name), grid), grid))
                val current = IconStore.toText(IconStore.parse(
                    IconStore.zoomText(IconStore.parse(IconSamples.get(name)!!), grid), grid))
                assertNotEquals("$name/$grid should have a corrected contour", old, current)
                val upgraded = IconStore.updatedBuiltinArt(old)
                assertEquals("$name/$grid", current, upgraded)
                assertEquals(grid, IconStore.gridOf(IconStore.parse(upgraded, grid)))
                assertEquals(upgraded, IconStore.updatedBuiltinArt(upgraded))
            }
        }
    }

    @Test fun editedDrawingsAndOtherSamplesStayIntact() {
        for (name in listOf("bell", "heart")) {
            val drawing = legacy(name).copyOf()
            drawing[0] = !drawing[0]
            val edited = IconStore.toText(drawing)
            assertEquals(edited, IconStore.updatedBuiltinArt(edited))
        }
        for (name in IconSamples.names().filter { it !in setOf("bell", "heart") }) {
            val art = IconStore.toText(IconStore.parse(IconSamples.get(name)!!))
            assertEquals(name, art, IconStore.updatedBuiltinArt(art))
        }
    }
}
