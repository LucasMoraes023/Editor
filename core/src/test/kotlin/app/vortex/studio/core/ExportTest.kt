package app.vortex.studio.core

import app.vortex.studio.core.Fixtures.sec
import app.vortex.studio.core.edit.Edits
import app.vortex.studio.core.export.*
import app.vortex.studio.core.model.*
import org.junit.Assert.*
import org.junit.Test

class ExportMathTest {
    @Test fun outputSizes_areEvenAndMatchAspect() {
        assertEquals(1080 to 1920, ExportMath.outputSize(AspectPreset.PORTRAIT_9_16, ExportResolution.P1080))
        assertEquals(1280 to 720, ExportMath.outputSize(AspectPreset.LANDSCAPE_16_9, ExportResolution.P720))
        assertEquals(2520 to 1080, ExportMath.outputSize(AspectPreset.CINEMA_21_9, ExportResolution.P1080))
        assertEquals(1080 to 1350, ExportMath.outputSize(AspectPreset.PORTRAIT_4_5, ExportResolution.P1080))
        assertEquals(3840 to 2160, ExportMath.outputSize(AspectPreset.LANDSCAPE_16_9, ExportResolution.P2160))
        for (a in AspectPreset.values()) for (r in ExportResolution.values()) {
            val (w, h) = ExportMath.outputSize(a, r); assertEquals(0, w % 2); assertEquals(0, h % 2)
            assertEquals(r.shortSide, minOf(w, h))
        }
    }

    @Test fun bitrate_scalesAndClamps() {
        assertEquals(5_598_720, ExportMath.videoBitrate(1920, 1080, 30, ExportQuality.MEDIUM))
        assertEquals(ExportMath.MIN_VIDEO_BITRATE, ExportMath.videoBitrate(320, 240, 15, ExportQuality.LOW))
        assertEquals(ExportMath.MAX_VIDEO_BITRATE, ExportMath.videoBitrate(7680, 4320, 60, ExportQuality.HIGH))
        assertTrue(ExportMath.videoBitrate(1920, 1080, 60, ExportQuality.MEDIUM) > ExportMath.videoBitrate(1920, 1080, 30, ExportQuality.MEDIUM))
    }

    @Test fun estimateAndStorage() {
        assertEquals(10_160_000L, ExportMath.estimateBytes(sec(10.0), 8_000_000))
        assertTrue(ExportMath.hasEnoughStorage(1000L, 1200L)); assertFalse(ExportMath.hasEnoughStorage(1000L, 1199L))
    }

    @Test fun options_reflectDeviceCapabilities() {
        val caps = DeviceEncodeCaps({ w, h, fps -> minOf(w, h) <= 1080 && fps <= 30 })
        val res = ExportMath.resolutionOptions(AspectPreset.PORTRAIT_9_16, 30, caps)
        assertEquals(listOf(true, true, false), res.map { it.enabled })
        assertEquals(DisabledReason.UNSUPPORTED_BY_DEVICE, res.last().reason)
        val fps = ExportMath.fpsOptions(AspectPreset.PORTRAIT_9_16, ExportResolution.P1080, caps)
        assertEquals(listOf(true, false), fps.map { it.enabled })
    }
}

class ExportPlannerTest {
    private val settings = ExportSettings()
    private fun base(): Project {
        var p = Fixtures.project(Fixtures.video("v1", 10.0), Fixtures.video("v2", 6.0, audio = false), Fixtures.image("i1"), Fixtures.audio("a1", 40.0))
        p = Edits.appendClip(p, "c1", "v1"); p = Edits.appendClip(p, "c2", "v2")
        return p.normalized()
    }
    private fun ok(p: Project, s: ExportSettings = settings): PlanResult.Ok = PlanResult.Ok::class.java.cast(ExportPlanner.plan(p, s))

    @Test fun emptyProject_isRejected() {
        val r = ExportPlanner.plan(Fixtures.project(), settings) as PlanResult.Failed
        assertEquals(ExportIssueCode.EMPTY_PROJECT, r.issues.single().code)
    }

    @Test fun missingMedia_isReportedPerItem() {
        val r = ExportPlanner.plan(base(), settings) { it.id != "v2" } as PlanResult.Failed
        val issue = r.issues.single()
        assertEquals(ExportIssueCode.MISSING_ASSET, issue.code); assertEquals("c2", issue.itemId); assertEquals("v2", issue.detail)
    }

    @Test fun segments_followTheTimeline() {
        val plan = ok(base()).plan
        assertEquals(2, plan.videoSegments.size); assertEquals(sec(16.0), plan.durationUs)
        assertEquals(sec(10.0), plan.videoSegments[1].timelineStartUs)
        assertTrue(plan.videoSegments[0].hasAudio); assertFalse(plan.videoSegments[1].hasAudio)    // v2 não tem áudio
        assertEquals(1080 to 1920, plan.width to plan.height)
        assertEquals(30, plan.fps)
    }

    @Test fun muteAndTrackMute_removeClipAudio() {
        var p = Edits.setMuted(base(), "c1", true)
        assertFalse(ok(p).plan.videoSegments[0].hasAudio)
        p = Edits.setMuted(p, "c1", false)
        val muted = p.copy(tracks = p.tracks.map { if (it.isMain) it.copy(muted = true) else it })
        assertFalse(ok(muted).plan.videoSegments[0].hasAudio)
    }

    @Test fun audio_isTrimmedAtProjectEndAndPackedInLanes() {
        var p = Edits.addAudio(base(), "m1", "a1", 0L)                       // 40 s de música num projeto de 16 s
        p = Edits.addAudio(p, "m2", "a1", sec(2.0), TrackKind.SFX)
        p = Edits.addAudio(p, "m3", "a1", sec(20.0), TrackKind.MUSIC, "track-music2")    // começa depois do fim
        val r = ok(p)
        val items = r.plan.audioLanes.flatten()
        assertEquals(2, items.size)
        val music = items.first { it.itemId == "m1" }
        assertEquals(sec(16.0), music.timelineDurationUs); assertEquals(sec(16.0), music.sourceOutUs)
        assertTrue(r.warnings.any { it.code == ExportIssueCode.AUDIO_TRIMMED_TO_PROJECT_END && it.itemId == "m1" })
        assertTrue(r.warnings.any { it.code == ExportIssueCode.ITEM_OUTSIDE_PROJECT && it.itemId == "m3" })
    }

    @Test fun audioLanes_separateOverlappingItems() {
        fun item(id: String, s: Long, d: Long) = AudioItemPlan(id, "u", 0L, d, 1f, 1f, 0L, 0L, s, d)
        val lanes = AudioLanes.pack(listOf(item("a", 0L, 100L), item("b", 50L, 100L), item("c", 100L, 50L), item("d", 150L, 10L)))
        assertEquals(2, lanes.size)
        for (lane in lanes) for (i in 1 until lane.size) assertTrue(lane[i - 1].timelineEndUs <= lane[i].timelineStartUs)
        assertEquals(4, lanes.sumOf { it.size })
        assertTrue(AudioLanes.pack(emptyList()).isEmpty())
    }

    @Test fun overlays_areClampedAndOrdered() {
        var p = Edits.addText(base(), "t1", "A", sec(15.0), sec(5.0))
        p = Edits.addText(p, "t2", "B", sec(30.0), sec(1.0))
        p = Edits.addOverlay(p, "o1", "i1", 0L, sec(2.0))
        val r = ok(p)
        val t1 = r.plan.overlays.filterIsInstance<OverlayPlan.Text>().single { it.item.id == "t1" }
        assertEquals(sec(1.0), t1.durationUs)                                // cortado no fim do projeto
        assertTrue(r.plan.overlays.any { it is OverlayPlan.Image })
        assertTrue(r.warnings.any { it.code == ExportIssueCode.ITEM_OUTSIDE_PROJECT && it.itemId == "t2" })
    }

    @Test fun hiddenTrack_isNotRendered() {
        var p = Edits.addText(base(), "t1", "A", 0L, sec(2.0))
        p = Edits.setTrackMuted(p, Edits.TRACK_TEXT, true)
        assertTrue(ok(p).plan.overlays.isEmpty())
    }

    @Test fun transitions_onlySupportedOnesAreRendered() {
        var p = Edits.setTransition(base(), "c2", TransitionSpec(TransitionType.FADE_BLACK, 1_000_000L))
        var r = ok(p)
        val fx = r.plan.fx.segments
        assertEquals(500_000L, fx[0].dipOutUs); assertEquals(500_000L, fx[1].dipInUs); assertTrue(r.warnings.isEmpty())
        p = Edits.setTransition(p, "c2", TransitionSpec(TransitionType.DISSOLVE, 1_000_000L))
        r = ok(p)
        assertEquals(0L, r.plan.fx.segments[1].dipInUs)
        assertTrue(r.warnings.any { it.code == ExportIssueCode.TRANSITION_NOT_SUPPORTED && it.itemId == "c2" })
        p = Edits.setTransition(Edits.setTransition(base(), "c1", TransitionSpec(TransitionType.FADE_BLACK)), "c2", null)
        assertEquals(0L, ok(p).plan.fx.segments[0].dipInUs)                  // transição no primeiro clipe é ignorada
    }

    @Test fun extraVideoTracks_areReportedNotSilentlyDropped() {
        var p = Edits.addTrack(base(), TrackKind.VIDEO, "track-v2", "v2")
        p = p.copy(tracks = p.tracks.map { if (it.id == "track-v2") it.copy(items = listOf(MediaClip("x", "v1", 0L, 0L, sec(1.0)))) else it })
        assertTrue(ok(p).warnings.any { it.code == ExportIssueCode.EXTRA_VIDEO_TRACK_IGNORED })
    }

    @Test fun effectsAndKeyframes_flowIntoFxTimeline() {
        var p = Edits.addEffect(base(), "c1", "e1", EffectType.VIGNETTE, 0.8f)
        p = Edits.applyKenBurns(p, "c2")
        val plan = ok(p).plan
        assertEquals(0.8f, plan.fx.stateAt(sec(1.0)).vignette, 0f)
        assertTrue(plan.fx.stateAt(sec(15.9)).zoom > 1.2f)
        assertFalse(plan.fx.isIdentity)
        assertTrue(ok(base()).plan.fx.isIdentity)
    }

    @Test fun settings_changeSizeBitrateAndEstimate() {
        val a = ok(base(), ExportSettings(ExportResolution.P720, 30, ExportQuality.LOW)).plan
        val b = ok(base(), ExportSettings(ExportResolution.P2160, 60, ExportQuality.HIGH)).plan
        assertEquals(720 to 1280, a.width to a.height); assertEquals(2160 to 3840, b.width to b.height)
        assertTrue(b.videoBitrate > a.videoBitrate); assertTrue(b.estimatedBytes > a.estimatedBytes)
    }

    @Test fun engineCapabilities_areHonest() {
        assertTrue(EngineCapabilities.isTransitionSupported(TransitionType.FADE_BLACK))
        assertFalse(EngineCapabilities.isTransitionSupported(TransitionType.DISSOLVE))
        assertFalse(EngineCapabilities.EXTRA_VIDEO_TRACKS)
    }
}
