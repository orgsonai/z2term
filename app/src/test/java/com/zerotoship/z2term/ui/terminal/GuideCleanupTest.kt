package com.zerotoship.z2term.ui.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 案内の片付け ([Guide.cleanup]) が、**その案内で足したものと対になっているか** (1.0.6)。
 *
 * 手順を足したのに片付けを足し忘れると、「片付ける」を押しても一部が残ったままになり、
 * 残ったことに気付けない。案内を増やす・直すときにここで止める。
 */
class GuideCleanupTest {

    private fun commands(steps: List<GuideStep>) = steps.mapNotNull { it.command }

    @Test
    fun everyGuideCanBeCleanedUp() {
        Guide.ALL.forEach { assertTrue("${it.id}: 片付けが空", it.cleanup.isNotEmpty()) }
    }

    /** 入れたマクロは、同じ名前で消す。 */
    @Test
    fun everyInstalledMacroIsRemoved() {
        Guide.ALL.forEach { guide ->
            val installed = commands(guide.steps).filter { it.startsWith("z2-macro install ") }
                .map { it.removePrefix("z2-macro install ") }
            val removed = commands(guide.cleanup).filter { it.startsWith("z2-macro remove ") }
                .map { it.removePrefix("z2-macro remove ") }
            assertEquals("${guide.id}: 入れたマクロと消すマクロが合わない", installed, removed)
        }
    }

    /** `z2-when` でルールを足す案内は、そのマクロを呼ぶルールを消す行を持つ。 */
    @Test
    fun everyRegisteredRuleIsRemoved() {
        Guide.ALL.forEach { guide ->
            val macros = commands(guide.steps).filter { it.startsWith("z2-when ") }
                .mapNotNull { Regex("macros/([a-z-]+)\\.sh").find(it)?.groupValues?.get(1) }.toSet()
            macros.forEach { macro ->
                assertTrue(
                    "${guide.id}: $macro を呼ぶルールを消す行が無い",
                    commands(guide.cleanup).any { it.contains("grep -F macros/$macro.sh") && it.contains("z2-when remove") }
                )
            }
        }
    }

    /** マクロに後始末をさせる行は、そのマクロを消す行より前に置く。 */
    @Test
    fun macroIsRemovedAfterItsOwnCleanup() {
        val steps = commands(Guide.REMIND.cleanup)
        assertTrue(steps.indexOfFirst { it.contains("remind.sh del all") } < steps.indexOf("z2-macro remove remind"))
    }
}
