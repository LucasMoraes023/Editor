package app.vortex.studio.core

import app.vortex.studio.core.Fixtures.sec
import app.vortex.studio.core.ai.SilenceDetector
import app.vortex.studio.core.edit.Edits
import app.vortex.studio.core.model.*
import app.vortex.studio.core.templates.*
import app.vortex.studio.core.timeline.TimelineMath
import org.junit.Assert.*
import org.junit.Test

class TemplatesTest {
    @Test fun everyTemplate_generatesAValidRealProject() {
        val ids = SequentialIds("t")
        assertEquals(Templates.all.size, Templates.all.map { it.id }.toSet().size)
        for (spec in Templates.all) {
            val p = Templates.instantiate(spec, ids, spec.id, 1L)
            assertEquals(spec.aspect, p.canvas.aspect); assertEquals(spec.fps, p.canvas.fps); assertEquals(spec.id, p.templateId)
            val texts = p.tracks.flatMap { it.items }.filterIsInstance<TextItem>()
            assertEquals(spec.texts.size, texts.size)
            assertEquals(texts.size, texts.map { it.id }.toSet().size)
            for (t in texts) { assertTrue(t.text.isNotBlank()); assertTrue(t.x in -1f..1f && t.y in -1f..1f); assertTrue(t.durationUs >= MIN_ITEM_US) }
        }
    }

    @Test fun specificTemplates() {
        val reels = Templates.byId("social_reels_title")!!
        assertEquals(AspectPreset.PORTRAIT_9_16, reels.aspect); assertEquals(2, reels.texts.size)
        val cine = Templates.byId("cinematic_title")!!
        assertEquals(AspectPreset.CINEMA_21_9, cine.aspect); assertEquals(24, cine.fps)
        assertNull(Templates.byId("nao_existe")); assertTrue(Templates.defaultEffectsFor(null).isEmpty())
    }

    @Test fun defaultEffects_areAppliedToNewClips() {
        var p = Templates.instantiate(Templates.byId("cinematic_title")!!, SequentialIds("t"), "Filme", 1L)
        p = Edits.registerAsset(p, Fixtures.video("v1", 5.0))
        p = Edits.appendClip(p, "c1", "v1")
        val clip = p.mainTrack.items.single() as MediaClip
        assertEquals(listOf(EffectType.CINEMA, EffectType.VIGNETTE, EffectType.FILM_GRAIN), clip.effects.map { it.type })
        assertEquals(3, clip.effects.map { it.id }.toSet().size)
    }
}

class TimelineMathTest {
    @Test fun conversions_roundTrip() {
        assertEquals(80f, TimelineMath.usToPx(sec(1.0), 80f), 1e-4f)
        assertEquals(sec(2.5), TimelineMath.pxToUs(200f, 80f))
    }

    @Test fun rulerStep_growsAsZoomShrinks() {
        assertEquals(1_000_000L, TimelineMath.rulerStepUs(80f))
        assertEquals(200_000L, TimelineMath.rulerStepUs(400f))
        assertEquals(15_000_000L, TimelineMath.rulerStepUs(6f))
        var last = 0L
        for (z in listOf(800f, 400f, 200f, 80f, 40f, 10f, 6f)) { val s = TimelineMath.rulerStepUs(z); assertTrue(s >= last); last = s }
    }

    @Test fun ticks_coverRange() {
        val t = TimelineMath.rulerTicks(sec(2.3), sec(6.0), 80f)
        assertEquals(listOf(sec(2.0), sec(3.0), sec(4.0), sec(5.0), sec(6.0)), t)
    }

    @Test fun snap_andZoomLimits() {
        assertEquals(sec(5.0), TimelineMath.snap(sec(5.0) + 20_000L, listOf(0L, sec(5.0), sec(9.0)), 50_000L))
        assertEquals(sec(1.0), TimelineMath.snap(sec(1.0), listOf(sec(5.0)), 50_000L))
        assertEquals(TimelineMath.MAX_PX_PER_SEC, TimelineMath.clampZoom(99999f), 0f)
        assertEquals(TimelineMath.MIN_PX_PER_SEC, TimelineMath.zoomToFit(sec(100000.0), 300f), 0f)
        assertEquals(TimelineMath.DEFAULT_PX_PER_SEC, TimelineMath.zoomToFit(0L, 300f), 0f)
        assertEquals(30f, TimelineMath.zoomToFit(sec(10.0), 300f), 1e-4f)
    }
}

class SilenceDetectorTest {
    private val w = 100_000L
    @Test fun detectsPausesLongEnoughAndKeepsPadding() {
        val rms = floatArrayOf(0.5f, 0.5f, 0f, 0f, 0f, 0f, 0f, 0f, 0.5f)    // pausa de 0,2 s a 0,8 s
        val r = SilenceDetector.detect(rms, w)
        assertEquals(1, r.size); assertEquals(300_000L, r[0].startUs); assertEquals(700_000L, r[0].endUs)
        assertTrue(SilenceDetector.detect(rms, w, minSilenceUs = 700_000L).isEmpty())
    }

    @Test fun edgeCases() {
        assertTrue(SilenceDetector.detect(FloatArray(20) { 0.3f }, w).isEmpty())
        assertEquals(1, SilenceDetector.detect(FloatArray(20), w).size)                  // tudo silêncio
        assertTrue(SilenceDetector.detect(FloatArray(0), w).isEmpty())
        expectThrows<IllegalArgumentException> { SilenceDetector.detect(FloatArray(3), 0L) }
    }

    @Test fun threshold_controlsSensitivity() {
        val quiet = FloatArray(20) { 0.02f }                                   // ≈ -34 dB
        assertTrue(SilenceDetector.detect(quiet, w, thresholdDb = -40f).isEmpty())
        assertEquals(1, SilenceDetector.detect(quiet, w, thresholdDb = -30f).size)
    }

    @Test fun downsample_usesMaxPooling() {
        assertArrayEquals(floatArrayOf(3f, 5f), SilenceDetector.downsamplePeaks(floatArrayOf(1f, 3f, 2f, 5f), 2), 0f)
        assertEquals(3, SilenceDetector.downsamplePeaks(floatArrayOf(1f, 2f, 3f), 10).size)
        assertEquals(0, SilenceDetector.downsamplePeaks(FloatArray(0), 4).size)
    }
}
