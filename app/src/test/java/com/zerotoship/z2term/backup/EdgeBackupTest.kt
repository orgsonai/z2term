package com.zerotoship.z2term.backup

import com.zerotoship.z2term.edge.EdgeNote
import com.zerotoship.z2term.edge.EdgeStore
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

class EdgeBackupTest {
    @get:Rule val temp = TemporaryFolder()
    private fun store(name: String) = EdgeStore(File(temp.newFolder(name), "shared_home/.z2term/edge"))

    @Test fun restoresTabsGesturesNotesHistoryAndStateAcrossHomes() {
        val source = store("source")
        source.setPanel("child", emptyMap())
        source.setPanel("main", mapOf("tabs" to "child", "handle" to "bar", "side" to "left", "scroll-how" to "node"))
        source.setItem("child:memo", mapOf("type" to "note"))
        val item = source.item("child:memo")
        val note = EdgeNote(source.noteFile("child", item), source.noteHistoryFile(source.noteFile("child", item)))
        note.edit("メモ original"); note.save(); note.edit("メモ updated"); note.save()
        source.enable(true)
        val backup = EdgeBackup.snapshot(source)
        assertEquals(2, backup.panelCount); assertEquals(1, backup.noteCount)
        val target = store("target")
        target.setPanel("extra", emptyMap())
        EdgeBackup.apply(target, checkNotNull(EdgeBackup.prepareRestore(target, backup.entries, temp.root)))
        assertTrue(target.enabled())
        assertEquals(listOf("child"), target.panel("main").tabs)
        assertEquals("node", target.panel("main").fields["scroll-how"])
        assertTrue(target.panels().any { it.id == "extra" })
        val restoredItem = target.item("child:memo")
        val file = target.noteFile("child", restoredItem)
        val restored = EdgeNote(file, target.noteHistoryFile(file))
        assertEquals("メモ updated", restored.text)
        assertTrue(restored.canUndo)
        assertEquals("メモ original", restored.undo())
    }

    @Test fun externalNotesBecomePortableCopiesWithoutOverwritingTheirOriginals() {
        val source = store("external-source")
        source.setPanel("main", emptyMap())
        val external = temp.newFile("external.txt").apply { writeText("outside note") }
        source.setItem("main:memo", mapOf("type" to "note", "file" to external.path))
        val backup = EdgeBackup.snapshot(source)
        external.writeText("new outside content")
        val target = store("external-target")
        EdgeBackup.apply(target, checkNotNull(EdgeBackup.prepareRestore(target, backup.entries, temp.root)))
        val restored = target.noteFile("main", target.item("main:memo"))
        assertEquals("outside note", restored.readText())
        assertTrue(restored.path.startsWith(target.root.canonicalPath + File.separator))
        assertEquals("new outside content", external.readText())
        assertFalse(target.enabled())
    }

    @Test fun disabledStateRestoresAndOlderArchivesLeavePanelsAlone() {
        val source = store("disabled-source")
        val target = store("disabled-target")
        target.setPanel("keep", emptyMap()); target.enable(true)
        assertNull(EdgeBackup.prepareRestore(target, emptyMap(), temp.root))
        assertTrue(target.enabled())
        EdgeBackup.apply(target, checkNotNull(EdgeBackup.prepareRestore(target, EdgeBackup.snapshot(source).entries, temp.root)))
        assertFalse(target.enabled()); assertEquals("keep", target.panels().single().id)
    }

    @Test fun acceptsPlatformParentAliasesButRejectsAnEdgeDirectoryLink() {
        val real = temp.newFolder("real-home")
        val alias = File(temp.root, "home-alias")
        Files.createSymbolicLink(alias.toPath(), real.toPath())
        val source = EdgeStore(File(alias, "shared_home/.z2term/edge"))
        source.setPanel("main", emptyMap())
        source.setItem("main:memo", mapOf("type" to "note"))
        source.noteFile("main", source.item("main:memo")).writeText("alias note")
        val target = store("alias-target")
        val backup = EdgeBackup.snapshot(source)
        EdgeBackup.apply(target, checkNotNull(EdgeBackup.prepareRestore(target, backup.entries, temp.root)))
        assertEquals("alias note", target.noteFile("main", target.item("main:memo")).readText())
        val linkedRoot = File(temp.root, "edge-alias")
        Files.createSymbolicLink(linkedRoot.toPath(), source.root.toPath())
        assertThrows(IllegalArgumentException::class.java) { EdgeBackup.snapshot(EdgeStore(linkedRoot)) }
    }

    @Test fun refusesTraversalAndInvalidTabGraphBeforeWriting() {
        val target = store("invalid-target")
        target.setPanel("keep", emptyMap())
        for (path in listOf("../outside", "/outside", "keep/../../outside", "keep\\outside")) {
            assertThrows(IllegalArgumentException::class.java) {
                EdgeBackup.prepareRestore(target, mapOf("edge-state" to "1".toByteArray(), "edge/$path" to byteArrayOf()), temp.root)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            EdgeBackup.prepareRestore(target, mapOf("edge-state" to "1".toByteArray(),
                "edge/keep/panel.conf" to "tabs=missing\n".toByteArray()), temp.root)
        }
        assertFalse(target.enabled()); assertTrue(target.panel("keep").tabs.isEmpty())
    }

    @Test fun refusesSymbolicDestinationsAndExternalArchiveNoteReferences() {
        val target = store("symbolic-target")
        target.setPanel("keep", emptyMap())
        val outside = temp.newFile("untouched.txt").apply { writeText("untouched") }
        val linked = File(target.root, "keep/memo.txt")
        Files.createSymbolicLink(linked.toPath(), outside.toPath())
        assertThrows(IllegalArgumentException::class.java) {
            EdgeBackup.prepareRestore(target, mapOf("edge-state" to "1".toByteArray(),
                "edge/keep/memo.txt" to "overwrite".toByteArray()), temp.root)
        }
        linked.delete()
        assertThrows(IllegalArgumentException::class.java) {
            EdgeBackup.prepareRestore(target, mapOf("edge-state" to "1".toByteArray(),
                "edge/keep/memo.item" to "type=note\nfile=${outside.path}\n".toByteArray()), temp.root)
        }
        assertEquals("untouched", outside.readText())
    }
}
