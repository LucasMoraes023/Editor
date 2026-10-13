package app.vortex.studio.core

import app.vortex.studio.core.edit.*
import app.vortex.studio.core.model.*
import org.junit.Assert.*
import org.junit.Test

class ProjectEditorTest {
    private var now = 10_000L
    private fun editor(limit: Int = 200) = ProjectEditor(
        Fixtures.project(Fixtures.video("v1", 10.0)), SequentialIds("x"), clock = { now }, historyLimit = limit,
    ).also { it.exec("add") { p -> Edits.appendClip(p, "c1", "v1") } }

    @Test fun undoRedo_restoreExactStates() {
        val e = editor()
        val afterAdd = e.project
        e.exec("split") { Edits.splitAt(it, "c1", Fixtures.sec(4.0), "c2") }
        assertEquals(2, e.project.mainTrack.items.size)
        assertTrue(e.undo()); assertEquals(afterAdd, e.project)
        assertTrue(e.redo()); assertEquals(2, e.project.mainTrack.items.size)
        assertFalse(e.redo())
    }

    @Test fun noChange_doesNotCreateHistory() {
        val e = editor()
        val n = e.canUndo
        assertEquals(EditOutcome.NoChange, e.exec("speed") { Edits.setSpeed(it, "c1", 1f) })
        assertEquals(n, e.canUndo)
        assertEquals("add", e.undoLabel)
    }

    @Test fun failedOperation_leavesStateUntouched() {
        val e = editor()
        val before = e.project
        val r = e.exec("bad") { Edits.splitAt(it, "c1", 5L, "c2") }
        assertTrue(r is EditOutcome.Failed)
        assertEquals(before, e.project)
        assertEquals("add", e.undoLabel)
    }

    @Test fun coalescing_mergesRapidSliderChanges() {
        val e = editor()
        e.exec("vol", "volume:c1") { Edits.setVolume(it, "c1", 0.9f) }; now += 100
        e.exec("vol", "volume:c1") { Edits.setVolume(it, "c1", 0.7f) }; now += 100
        e.exec("vol", "volume:c1") { Edits.setVolume(it, "c1", 0.5f) }
        assertTrue(e.undo())                                   // desfaz as três de uma vez
        assertEquals(1f, (e.project.mainTrack.items[0] as MediaClip).volume, 0f)
        assertEquals("add", e.undoLabel)
    }

    @Test fun coalescing_respectsTimeWindow() {
        val e = editor()
        e.exec("vol", "volume:c1") { Edits.setVolume(it, "c1", 0.9f) }; now += 5_000
        e.exec("vol", "volume:c1") { Edits.setVolume(it, "c1", 0.5f) }
        e.undo()
        assertEquals(0.9f, (e.project.mainTrack.items[0] as MediaClip).volume, 0f)
    }

    @Test fun newEdit_clearsRedo() {
        val e = editor()
        e.exec("a") { Edits.setVolume(it, "c1", 0.5f) }; e.undo(); assertTrue(e.canRedo)
        e.exec("b") { Edits.setVolume(it, "c1", 0.2f) }; assertFalse(e.canRedo)
    }

    @Test fun historyLimit_dropsOldest() {
        val e = editor(limit = 3)
        for (i in 1..6) { now += 10_000; e.exec("v$i") { Edits.setVolume(it, "c1", i / 10f) } }
        var undone = 0
        while (e.undo()) undone++
        assertEquals(3, undone)
    }

    @Test fun modifiedTimestamp_isUpdatedOnEdit() {
        val e = editor(); now = 99_999L
        e.exec("v") { Edits.setVolume(it, "c1", 0.3f) }
        assertEquals(99_999L, e.project.modifiedAtMs)
    }
}
