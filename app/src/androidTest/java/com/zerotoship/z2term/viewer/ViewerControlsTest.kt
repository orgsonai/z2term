package com.zerotoship.z2term.viewer

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ViewerControlsTest {
    @Test fun genericFieldsAndLiteralArgumentsHaveNoDomainSpecificInterpretation() {
        val controls = ViewerControls.parse("""{
          "handler":"custom-export.sh","refresh":["preview"],"actions":[{
            "id":"export-1","label":"Export","args":["write","literal ${'$'}(exit 9)"],"toolbar":true,
            "fields":[{"label":"Name"},{"label":"When","type":"datetime"},
              {"label":"Format","type":"choice","choices":[{"value":"csv","label":"CSV"}]}]
          }]}""")
        assertEquals("custom-export.sh", controls.handler)
        assertEquals("literal ${'$'}(exit 9)", controls.actions.single().args[1])
        assertEquals(listOf("text", "datetime", "choice"), controls.actions.single().fields.map { it.type })
        val page = ViewerPage("<p>Export</p>", "Title", controls)
        assertEquals(page, ViewerPage.decode(page.encode()))
    }

    @Test fun definitionsCannotSupplyPathsOrUnknownInputsOrDuplicateActions() {
        for (handler in listOf("../outside.sh", "/tmp/outside.sh", "x.sh;echo bad", "x${'$'}(id).sh")) {
            assertThrows(IllegalArgumentException::class.java) { ViewerControls.parse("""{"handler":"$handler"}""") }
        }
        assertThrows(IllegalArgumentException::class.java) {
            ViewerControls.parse("""{"handler":"example.sh","actions":[{"id":"a","label":"A","args":[],"fields":[{"label":"X","type":"shell"}]}]}""")
        }
        assertThrows(IllegalArgumentException::class.java) {
            ViewerControls.parse("""{"handler":"example.sh","actions":[{"id":"a","label":"A","args":[]},{"id":"a","label":"B","args":[]}]}""")
        }
    }
}
