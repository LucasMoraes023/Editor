package app.vortex.studio.core

import app.vortex.studio.core.Fixtures.sec
import app.vortex.studio.core.ai.TimeRange
import app.vortex.studio.core.edit.EditError
import app.vortex.studio.core.edit.Edits
import app.vortex.studio.core.model.*
import org.junit.Assert.*
import org.junit.Test

class EditsTest {
    private fun base() = Fixtures.project(Fixtures.video("v1", 10.0), Fixtures.video("v2", 6.0), Fixtures.image("i1"), Fixtures.audio("a1", 30.0))
    private fun clips(p: Project) = p.mainTrack.items.filterIsInstance<MediaClip>()

    @Test fun appendClip_layoutIsContiguous() {
        var p = base()
        p = Edits.appendClip(p, "c1", "v1"); p = Edits.appendClip(p, "c2", "v2").normalized()
        val c = clips(p)
        assertEquals(0L, c[0].startUs); assertEquals(sec(10.0), c[1].startUs)
        assertEquals(sec(16.0), p.durationUs)
    }

    @Test fun appendClip_rejectsAudioAndUnknownAsset() {
        val p = base()
        expectThrows<EditError.WrongKind> { Edits.appendClip(p, "c", "a1") }
        expectThrows<EditError.AssetNotFound> { Edits.appendClip(p, "c", "nope") }
    }

    @Test fun split_mapsSourceTimeAndKeepsTotalDuration() {
        var p = Edits.appendClip(base(), "c1", "v1")
        p = Edits.splitAt(p, "c1", sec(4.0), "c2").normalized()
        val (a, b) = clips(p)
        assertEquals(sec(4.0), a.sourceOutUs); assertEquals(sec(4.0), b.sourceInUs)
        assertEquals(sec(4.0), a.durationUs); assertEquals(sec(6.0), b.durationUs)
        assertEquals(sec(4.0), b.startUs); assertEquals(sec(10.0), p.durationUs)
        assertEquals("c2", b.id)
    }

    @Test fun split_withSpeedUsesTimelineTime() {
        var p = Edits.appendClip(base(), "c1", "v1")
        p = Edits.setSpeed(p, "c1", 2f).normalized()
        assertEquals(sec(5.0), p.durationUs)
        p = Edits.splitAt(p, "c1", sec(2.0), "c2").normalized()
        val (a, b) = clips(p)
        assertEquals(sec(4.0), a.sourceOutUs)           // 2 s de linha do tempo a 2x = 4 s de origem
        assertEquals(sec(2.0), a.durationUs); assertEquals(sec(3.0), b.durationUs)
    }

    @Test fun split_nearEdgesIsRejected() {
        val p = Edits.appendClip(base(), "c1", "v1")
        expectThrows<EditError.InvalidPosition> { Edits.splitAt(p, "c1", 10_000L, "c2") }
        expectThrows<EditError.InvalidPosition> { Edits.splitAt(p, "c1", sec(10.0) - 10_000L, "c2") }
        expectThrows<EditError.ItemNotFound> { Edits.splitAt(p, "zzz", sec(1.0), "c2") }
    }

    @Test fun remove_ripplesFollowingClips() {
        var p = Edits.appendClip(Edits.appendClip(base(), "c1", "v1"), "c2", "v2").normalized()
        p = Edits.removeItem(p, "c1").normalized()
        assertEquals(0L, clips(p)[0].startUs); assertEquals(sec(6.0), p.durationUs)
    }

    @Test fun moveMainClip_reorders() {
        var p = Edits.appendClip(Edits.appendClip(Edits.appendClip(base(), "c1", "v1"), "c2", "v2"), "c3", "i1")
        p = Edits.moveMainClip(p, "c3", 0).normalized()
        assertEquals(listOf("c3", "c1", "c2"), clips(p).map { it.id })
        assertEquals(0L, clips(p)[0].startUs)
    }

    @Test fun trim_startAndEnd() {
        var p = Edits.appendClip(base(), "c1", "v1")
        p = Edits.trimStartTo(p, "c1", sec(2.0)).normalized()
        assertEquals(sec(2.0), clips(p)[0].sourceInUs); assertEquals(sec(8.0), p.durationUs)
        p = Edits.trimEndTo(p, "c1", sec(5.0)).normalized()
        assertEquals(sec(7.0), clips(p)[0].sourceOutUs); assertEquals(sec(5.0), p.durationUs)
    }

    @Test fun setSourceRange_enforcesBoundsAndMinimum() {
        var p = Edits.appendClip(base(), "c1", "v1")
        p = Edits.setSourceRange(p, "c1", sec(1.0), sec(1.0) + 10L)
        assertTrue(clips(p)[0].durationUs >= MIN_ITEM_US)
        p = Edits.setSourceRange(p, "c1", 0L, sec(99.0))
        assertEquals(sec(10.0), clips(p)[0].sourceOutUs)      // limitado à duração real da mídia
    }

    @Test fun speed_changesDurationAndIsClamped() {
        var p = Edits.appendClip(base(), "c1", "v1")
        p = Edits.setSpeed(p, "c1", 0.5f).normalized(); assertEquals(sec(20.0), p.durationUs)
        p = Edits.setSpeed(p, "c1", 100f).normalized(); assertEquals(MAX_SPEED, clips(p)[0].speed, 0f)
    }

    @Test fun stills_haveDefaultDurationAndNoSpeed() {
        var p = Edits.appendClip(base(), "c1", "i1")
        assertEquals(DEFAULT_STILL_US, clips(p)[0].durationUs)
        p = Edits.setStillDuration(p, "c1", sec(2.0)).normalized(); assertEquals(sec(2.0), p.durationUs)
        expectThrows<EditError.WrongKind> { Edits.setSpeed(p, "c1", 2f) }
    }

    @Test fun duplicate_insertsCopyRightAfterWithNewIds() {
        var p = Edits.appendClip(base(), "c1", "v1")
        p = Edits.addEffect(p, "c1", "e1", EffectType.SEPIA)
        p = Edits.duplicate(p, "c1", "c1b").normalized()
        val c = clips(p)
        assertEquals(listOf("c1", "c1b"), c.map { it.id })
        assertNotEquals(c[0].effects[0].id, c[1].effects[0].id)
        assertEquals(sec(20.0), p.durationUs)
    }

    @Test fun volumeAndFades_areClamped() {
        var p = Edits.appendClip(base(), "c1", "v1")
        p = Edits.setVolume(p, "c1", 9f); assertEquals(2f, clips(p)[0].volume, 0f)
        p = Edits.setFades(p, "c1", sec(8.0), sec(8.0))
        val c = clips(p)[0]
        assertTrue(c.fadeInUs + c.fadeOutUs <= c.durationUs)
    }

    @Test fun rotate_wrapsAround() {
        var p = Edits.appendClip(base(), "c1", "v1")
        p = Edits.rotateClip(p, "c1", 3); p = Edits.rotateClip(p, "c1", 2)
        assertEquals(1, clips(p)[0].transform.rotationQuarters)
        p = Edits.rotateClip(p, "c1", -2); assertEquals(3, clips(p)[0].transform.rotationQuarters)
    }

    @Test fun effects_addUpdateRemove() {
        var p = Edits.appendClip(base(), "c1", "v1")
        p = Edits.addEffect(p, "c1", "e1", EffectType.VIGNETTE)
        p = Edits.updateEffect(p, "c1", "e1", intensity = 2f, enabled = false)
        val e = clips(p)[0].effects[0]
        assertEquals(1f, e.intensity, 0f); assertFalse(e.enabled)
        expectThrows<EditError.ItemNotFound> { Edits.updateEffect(p, "c1", "nope", intensity = 0.1f) }
        p = Edits.removeEffect(p, "c1", "e1"); assertTrue(clips(p)[0].effects.isEmpty())
    }

    @Test fun textAndAudioCreateTheirTracks() {
        var p = Edits.appendClip(base(), "c1", "v1")
        p = Edits.addText(p, "t1", "Olá", sec(1.0), sec(2.0))
        p = Edits.addAudio(p, "m1", "a1", 0L)
        assertEquals(TrackKind.TEXT, p.tracks.first { it.id == Edits.TRACK_TEXT }.kind)
        assertEquals(TrackKind.MUSIC, p.tracks.first { it.id == Edits.TRACK_MUSIC }.kind)
        expectThrows<EditError.WrongKind> { Edits.addAudio(p, "m2", "i1", 0L) }
        expectThrows<EditError.WrongKind> { Edits.addOverlay(p, "o1", "v1", 0L, sec(1.0)) }
    }

    @Test fun updateText_keepsIdAndChangesContent() {
        var p = Edits.addText(Edits.appendClip(base(), "c1", "v1"), "t1", "A", 0L, sec(2.0))
        p = Edits.updateText(p, "t1") { it.copy(id = "hack", text = "B") }
        val t = p.findItem("t1")!!.second as TextItem
        assertEquals("B", t.text)
        assertNull(p.findItem("hack"))
    }

    @Test fun moveItem_onlyForFreeTracks() {
        var p = Edits.addAudio(Edits.appendClip(base(), "c1", "v1"), "m1", "a1", 0L)
        p = Edits.moveItem(p, "m1", sec(3.0)); assertEquals(sec(3.0), p.findItem("m1")!!.second.startUs)
        expectThrows<EditError.WrongKind> { Edits.moveItem(p, "c1", sec(1.0)) }
    }

    @Test fun splitKeyframes_areDistributed() {
        var p = Edits.appendClip(base(), "c1", "v1")
        p = Edits.applyKenBurns(p, "c1", 1f, 1.5f)
        p = Edits.splitAt(p, "c1", sec(5.0), "c2").normalized()
        val (a, b) = clips(p)
        val ka = a.keyframes.getValue(KeyframeProperty.SCALE); val kb = b.keyframes.getValue(KeyframeProperty.SCALE)
        assertEquals(sec(5.0), ka.last().timeUs); assertEquals(0L, kb.first().timeUs)
        assertEquals(ka.last().value, kb.first().value, 1e-6f)
        assertEquals(1.5f, kb.last().value, 1e-6f)
    }

    @Test fun relink_replacesUriKeepingId() {
        val p = Edits.relinkAsset(base(), "v1", Fixtures.video("outro", 12.0))
        assertEquals("content://v/outro", p.assets.getValue("v1").uri); assertEquals("v1", p.assets.getValue("v1").id)
    }

    @Test fun transition_durationIsClamped() {
        var p = Edits.appendClip(base(), "c1", "v1")
        p = Edits.setTransition(p, "c1", TransitionSpec(TransitionType.FADE_BLACK, 99_000_000L))
        assertEquals(3_000_000L, clips(p)[0].transitionIn!!.durationUs)
    }

    @Test fun trimBy_extendsAndShortensWithinSource() {
        var p = Edits.appendClip(base(), "c1", "v1")
        p = Edits.setSourceRange(p, "c1", sec(2.0), sec(8.0)).normalized()
        p = Edits.trimStartBy(p, "c1", -sec(1.0)).normalized()            // estende 1 s para trás
        assertEquals(sec(1.0), clips(p)[0].sourceInUs)
        p = Edits.trimStartBy(p, "c1", -sec(5.0)).normalized()            // não passa do início da mídia
        assertEquals(0L, clips(p)[0].sourceInUs)
        p = Edits.trimEndBy(p, "c1", sec(50.0)).normalized()              // não passa do fim da mídia
        assertEquals(sec(10.0), clips(p)[0].sourceOutUs)
        p = Edits.trimEndBy(p, "c1", -sec(4.0)).normalized()
        assertEquals(sec(6.0), p.durationUs)
    }

    @Test fun trimBy_audioKeepsContentAligned() {
        var p = Edits.addAudio(Edits.appendClip(base(), "c1", "v1"), "m1", "a1", sec(5.0))
        p = Edits.trimStartBy(p, "m1", sec(2.0))
        val m = p.findItem("m1")!!.second as AudioClip
        assertEquals(sec(2.0), m.sourceInUs); assertEquals(sec(7.0), m.startUs)
        p = Edits.trimStartBy(p, "m1", -sec(9.0))                         // limitado ao começo da mídia
        val m2 = p.findItem("m1")!!.second as AudioClip
        assertEquals(0L, m2.sourceInUs); assertEquals(sec(5.0), m2.startUs)
    }

    @Test fun trimBy_textAndStills() {
        var p = Edits.addText(Edits.appendClip(base(), "c1", "i1"), "t1", "A", sec(1.0), sec(3.0))
        p = Edits.trimStartBy(p, "t1", sec(1.0)); p = Edits.trimEndBy(p, "t1", sec(1.0))
        val t = p.findItem("t1")!!.second as TextItem
        assertEquals(sec(2.0), t.startUs); assertEquals(sec(3.0), t.durationUs)
        p = Edits.trimEndBy(p, "c1", sec(2.0)).normalized()
        assertEquals(sec(6.0), (p.mainTrack.items[0] as MediaClip).durationUs)
    }

    @Test fun removeSourceRanges_closesGapsInOneStep() {
        val p0 = Edits.appendClip(base(), "c1", "v1").normalized()
        val ranges = listOf(TimeRange(sec(2.0), sec(3.0)), TimeRange(sec(6.0), sec(7.0)))
        val p = Edits.removeSourceRanges(p0, "c1", ranges, SequentialIds("n")).normalized()
        assertEquals(sec(8.0), p.durationUs)
        val c = clips(p)
        assertEquals(3, c.size)
        assertEquals(listOf(0L to sec(2.0), sec(3.0) to sec(6.0), sec(7.0) to sec(10.0)), c.map { it.sourceInUs to it.sourceOutUs })
        assertEquals(c.map { it.durationUs }.sum(), p.durationUs)
    }

    @Test fun removeSourceRanges_edgesAndSpeed() {
        val base = Edits.appendClip(base(), "c1", "v1").normalized()
        val atStart = Edits.removeSourceRanges(base, "c1", listOf(TimeRange(0L, sec(1.0))), SequentialIds("a")).normalized()
        assertEquals(sec(9.0), atStart.durationUs); assertEquals(sec(1.0), clips(atStart)[0].sourceInUs)
        val atEnd = Edits.removeSourceRanges(base, "c1", listOf(TimeRange(sec(8.0), sec(10.0))), SequentialIds("b")).normalized()
        assertEquals(sec(8.0), atEnd.durationUs)
        val whole = Edits.removeSourceRanges(base, "c1", listOf(TimeRange(0L, sec(10.0))), SequentialIds("c")).normalized()
        assertEquals(sec(10.0), whole.durationUs)                                  // não apaga o clipe inteiro
        val fast = Edits.setSpeed(base, "c1", 2f).normalized()                    // 10 s de origem = 5 s na timeline
        val cut = Edits.removeSourceRanges(fast, "c1", listOf(TimeRange(sec(4.0), sec(6.0))), SequentialIds("d")).normalized()
        assertEquals(sec(4.0), cut.durationUs)
        val outside = Edits.removeSourceRanges(base, "c1", listOf(TimeRange(sec(50.0), sec(60.0))), SequentialIds("e")).normalized()
        assertEquals(base, outside)
    }
}

