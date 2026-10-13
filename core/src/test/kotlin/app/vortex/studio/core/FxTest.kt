package app.vortex.studio.core

import app.vortex.studio.core.Fixtures.sec
import app.vortex.studio.core.export.FxSegment
import app.vortex.studio.core.export.FxTimeline
import app.vortex.studio.core.model.*
import org.junit.Assert.*
import org.junit.Test

class ColorMathTest {
    private fun apply(m: ColorMatrix, r: Float, g: Float, b: Float): FloatArray {
        val v = floatArrayOf(r, g, b, 1f)
        return FloatArray(3) { i -> m.m[i * 5] * v[0] + m.m[i * 5 + 1] * v[1] + m.m[i * 5 + 2] * v[2] + m.m[i * 5 + 3] * v[3] + m.m[i * 5 + 4] }
    }

    @Test fun identity_isNeutral() {
        val m = ColorMath.sepia(1f)
        assertEquals(m, ColorMatrix.identity() * m); assertEquals(m, m * ColorMatrix.identity())
        assertTrue(ColorMatrix.identity().isIdentity)
    }

    @Test fun saturationZero_producesLuma() {
        val out = apply(ColorMath.saturation(0f), 0f, 1f, 0f)
        for (c in out) assertEquals(0.7152f, c, 1e-5f)
        assertTrue(ColorMath.saturation(1f).isIdentity)
    }

    @Test fun grayscale_andAmountInterpolation() {
        val full = apply(ColorMath.grayscale(1f), 1f, 0f, 0f)
        assertEquals(0.2126f, full[0], 1e-5f); assertEquals(full[0], full[1], 1e-6f)
        assertTrue(ColorMath.sepia(1f).amount(0f).isIdentity)
        assertEquals(ColorMath.sepia(1f), ColorMath.sepia(1f).amount(1f))
        val half = apply(ColorMath.grayscale(0.5f), 1f, 0f, 0f)
        assertTrue(half[0] > full[0] && half[0] < 1f)
    }

    @Test fun exposure_scalesRgb() {
        val out = apply(ColorMath.exposure(1f), 0.1f, 0.2f, 0.3f)
        assertEquals(0.2f, out[0], 1e-6f); assertEquals(0.4f, out[1], 1e-6f); assertEquals(0.6f, out[2], 1e-6f)
    }

    @Test fun temperature_warmsAndCools() {
        val warm = apply(ColorMath.temperature(1f), 0.5f, 0.5f, 0.5f)
        assertTrue(warm[0] > 0.5f && warm[2] < 0.5f)
        val cool = apply(ColorMath.temperature(-1f), 0.5f, 0.5f, 0.5f)
        assertTrue(cool[0] < 0.5f && cool[2] > 0.5f)
    }

    @Test fun composition_appliesRightOperandFirst() {
        val a = ColorMath.exposure(1f); val b = ColorMath.contrast(1.2f)
        val inner = apply(b, 0.3f, 0.4f, 0.5f)
        val direct = apply(a, inner[0], inner[1], inner[2])
        val composed = apply(a * b, 0.3f, 0.4f, 0.5f)
        for (i in 0..2) assertEquals(direct[i], composed[i], 1e-5f)
    }

    @Test fun glMatrix_isColumnMajorWithOffsetsInLastColumn() {
        val m = ColorMath.scaleOffset(2f, 3f, 4f, 0.1f, 0.2f, 0.3f).toGlMat4()
        assertArrayEquals(floatArrayOf(2f, 0f, 0f, 0f,  0f, 3f, 0f, 0f,  0f, 0f, 4f, 0f,  0.1f, 0.2f, 0.3f, 1f), m, 1e-6f)
        assertArrayEquals(floatArrayOf(1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f), ColorMatrix.identity().toGlMat4(), 0f)
    }

    @Test fun invalidSize_isRejected() { expectThrows<IllegalArgumentException> { ColorMatrix(FloatArray(5)) } }
}

class FxEvaluatorTest {
    private fun e(t: EffectType, i: Float = t.defaultIntensity, on: Boolean = true) = EffectInstance("e-${t.name}", t, i, on)

    @Test fun noEffects_isIdentity() { assertTrue(FxEvaluator.evaluate(emptyList()).isIdentity) }

    @Test fun neutralBipolarValues_changeNothing() {
        val s = FxEvaluator.evaluate(listOf(e(EffectType.BRIGHTNESS, 0.5f), e(EffectType.CONTRAST, 0.5f), e(EffectType.SATURATION, 0.5f), e(EffectType.TEMPERATURE, 0.5f)))
        assertTrue(s.color.isIdentity)
    }

    @Test fun brightnessMax_isOneStop() { assertEquals(2f, FxEvaluator.evaluate(listOf(e(EffectType.BRIGHTNESS, 1f))).color.m[0], 1e-5f) }

    @Test fun zoomAndMirrors_combine() {
        assertEquals(2f, FxEvaluator.evaluate(listOf(e(EffectType.ZOOM, 1f))).zoom, 1e-6f)
        assertEquals(3f, FxEvaluator.evaluate(listOf(e(EffectType.ZOOM, 1f)), ClipTransform(zoom = 1.5f)).zoom, 1e-6f)
        assertTrue(FxEvaluator.evaluate(listOf(e(EffectType.MIRROR_H))).mirrorX)
        assertFalse(FxEvaluator.evaluate(listOf(e(EffectType.MIRROR_H)), ClipTransform(mirrorH = true)).mirrorX)   // dois espelhos se anulam
    }

    @Test fun disabledEffects_areIgnored() { assertTrue(FxEvaluator.evaluate(listOf(e(EffectType.GLITCH, 1f, on = false))).isIdentity) }

    @Test fun shaderEffects_mapIntensity() {
        val s = FxEvaluator.evaluate(listOf(e(EffectType.VIGNETTE, 0.7f), e(EffectType.FILM_GRAIN, 0.2f), e(EffectType.GLOW, 0.4f), e(EffectType.CHROMATIC_ABERRATION, 0.9f)))
        assertEquals(0.7f, s.vignette, 0f); assertEquals(0.2f, s.grain, 0f); assertEquals(0.4f, s.glow, 0f); assertEquals(0.9f, s.chromatic, 0f)
    }

    @Test fun rotationEffect_isBipolar() {
        assertEquals(0f, FxEvaluator.evaluate(listOf(e(EffectType.ROTATION, 0.5f))).rotationDeg, 1e-4f)
        assertEquals(180f, FxEvaluator.evaluate(listOf(e(EffectType.ROTATION, 1f))).rotationDeg, 1e-4f)
    }

    @Test fun catalog_isConsistent() {
        assertEquals(20, EffectType.values().size)
        for (t in EffectType.values()) { assertTrue(t.defaultIntensity in 0f..1f); if (t.bipolar) assertEquals(0.5f, t.defaultIntensity, 0f) }
    }
}

class FxTimelineTest {
    private fun dipTimeline() = FxTimeline(listOf(
        FxSegment(0L, sec(2.0), FxState(), dipOutUs = 500_000L, dipOutWhite = true),
        FxSegment(sec(2.0), sec(4.0), FxState(), dipInUs = 500_000L, dipInWhite = true),
    ))

    @Test fun dipRampsDownAndUpAroundTheCut() {
        val t = dipTimeline()
        assertEquals(0f, t.stateAt(sec(1.0)).dip, 0f)
        assertEquals(0.5f, t.stateAt(sec(1.75)).dip, 1e-4f)
        assertEquals(1f, t.stateAt(sec(2.0)).dip, 1e-4f)                       // corte: quadro totalmente branco
        assertEquals(0.5f, t.stateAt(sec(2.25)).dip, 1e-4f)
        assertEquals(0f, t.stateAt(sec(3.0)).dip, 0f)
        assertTrue(t.stateAt(sec(1.9)).dipWhite)
    }

    @Test fun outsideTimeline_isIdentity() {
        assertTrue(dipTimeline().stateAt(sec(10.0)).isIdentity)
        assertTrue(FxTimeline(emptyList()).stateAt(0L).isIdentity)
    }

    @Test fun keyframes_animateZoomOverClip() {
        val seg = FxSegment(0L, sec(10.0), FxState(), Keyframes.kenBurns(sec(10.0), 1f, 1.25f))
        val t = FxTimeline(listOf(seg))
        assertEquals(1f, t.stateAt(0L).zoom, 1e-5f)
        assertEquals(1.125f, t.stateAt(sec(5.0)).zoom, 1e-4f)
        assertEquals(1.25f, t.stateAt(sec(9.999)).zoom, 1e-2f)
    }

    @Test fun isIdentity_flag() {
        assertTrue(FxTimeline(listOf(FxSegment(0L, 1L, FxState()))).isIdentity)
        assertFalse(dipTimeline().isIdentity)
    }
}

class KeyframesAndAnimationTest {
    @Test fun evaluate_clampsInterpolatesAndHolds() {
        val f = listOf(Keyframe(0L, 0f), Keyframe(sec(2.0), 10f), Keyframe(sec(4.0), 20f, Easing.HOLD), Keyframe(sec(6.0), 0f))
        assertEquals(0f, Keyframes.evaluate(f, -5L, 99f), 0f)
        assertEquals(5f, Keyframes.evaluate(f, sec(1.0), 99f), 1e-4f)
        assertEquals(20f, Keyframes.evaluate(f, sec(5.0), 99f), 1e-4f)         // HOLD mantém o valor
        assertEquals(0f, Keyframes.evaluate(f, sec(60.0), 99f), 0f)
        assertEquals(99f, Keyframes.evaluate(emptyList(), 0L, 99f), 0f)
    }

    @Test fun easingEndpoints() {
        for (e in Easing.values()) { assertEquals(0f, Keyframes.ease(e, 0f), 1e-6f); assertEquals(1f, Keyframes.ease(e, 1f), 1e-6f) }
    }

    @Test fun fade_inAndOut() {
        fun a(t: Double) = OverlayAnimation.evaluate(sec(t), sec(4.0), ItemAnimation.FADE, ItemAnimation.FADE, 400_000L)
        assertEquals(0f, a(0.0).alpha, 1e-5f); assertEquals(1f, a(2.0).alpha, 0f); assertEquals(0f, a(4.0).alpha, 1e-5f)
        assertTrue(a(0.2).alpha in 0.1f..0.99f)
    }

    @Test fun none_andOutsideRange() {
        assertEquals(1f, OverlayAnimation.evaluate(0L, sec(2.0), ItemAnimation.NONE, ItemAnimation.NONE, 400_000L).alpha, 0f)
        assertEquals(0f, OverlayAnimation.evaluate(sec(3.0), sec(2.0), ItemAnimation.FADE, ItemAnimation.FADE, 400_000L).alpha, 0f)
    }

    @Test fun slideAndZoomStartDisplacedAndSettle() {
        val up = OverlayAnimation.evaluate(0L, sec(4.0), ItemAnimation.SLIDE_UP, ItemAnimation.NONE, 400_000L)
        assertTrue(up.dy < 0f)                                                  // entra vindo de baixo
        assertEquals(0f, OverlayAnimation.evaluate(sec(2.0), sec(4.0), ItemAnimation.SLIDE_UP, ItemAnimation.NONE, 400_000L).dy, 0f)
        assertTrue(OverlayAnimation.evaluate(0L, sec(4.0), ItemAnimation.ZOOM, ItemAnimation.NONE, 400_000L).scale < 1f)
        val pops = (1..39).map { OverlayAnimation.evaluate(it * 10_000L, sec(4.0), ItemAnimation.POP, ItemAnimation.NONE, 400_000L).scale }
        assertTrue(pops.any { it > 1f })                                         // "pop" ultrapassa 1 antes de assentar
    }

    @Test fun shortItems_limitAnimationTime() {
        val s = OverlayAnimation.evaluate(100_000L, 200_000L, ItemAnimation.FADE, ItemAnimation.FADE, 400_000L)
        assertTrue(s.alpha in 0f..1f)
    }
}
