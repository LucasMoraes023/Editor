package app.vortex.studio.core.timeline

import app.vortex.studio.core.model.US_PER_SEC
import kotlin.math.abs

/** Conversões entre tempo e pixels da linha do tempo, régua e ímãs de alinhamento. */
object TimelineMath {
    const val MIN_PX_PER_SEC = 6f
    const val MAX_PX_PER_SEC = 800f
    const val DEFAULT_PX_PER_SEC = 80f

    private val NICE_STEPS_US = longArrayOf(
        100_000L, 200_000L, 500_000L, 1_000_000L, 2_000_000L, 5_000_000L, 10_000_000L,
        15_000_000L, 30_000_000L, 60_000_000L, 120_000_000L, 300_000_000L, 600_000_000L,
    )

    fun clampZoom(pxPerSec: Float): Float = pxPerSec.coerceIn(MIN_PX_PER_SEC, MAX_PX_PER_SEC)
    fun usToPx(us: Long, pxPerSec: Float): Float = us * pxPerSec / US_PER_SEC
    fun pxToUs(px: Float, pxPerSec: Float): Long = (px / pxPerSec * US_PER_SEC).toLong()

    /** Menor passo "redondo" cujo espaçamento na tela seja de pelo menos [minLabelPx]. */
    fun rulerStepUs(pxPerSec: Float, minLabelPx: Float = 64f): Long =
        NICE_STEPS_US.firstOrNull { usToPx(it, pxPerSec) >= minLabelPx } ?: NICE_STEPS_US.last()

    fun rulerTicks(startUs: Long, endUs: Long, pxPerSec: Float): List<Long> {
        val step = rulerStepUs(pxPerSec)
        val first = (startUs.coerceAtLeast(0L) / step) * step
        val out = ArrayList<Long>()
        var t = first
        while (t <= endUs) { out += t; t += step }
        return out
    }

    /** Se [valueUs] estiver a até [thresholdUs] de algum candidato, devolve o candidato; senão, o próprio valor. */
    fun snap(valueUs: Long, candidatesUs: List<Long>, thresholdUs: Long): Long {
        var best = valueUs
        var bestDist = thresholdUs + 1
        for (c in candidatesUs) { val d = abs(c - valueUs); if (d <= thresholdUs && d < bestDist) { best = c; bestDist = d } }
        return best
    }

    /** Escala que faz [durationUs] caber em [viewportPx], respeitando os limites de zoom. */
    fun zoomToFit(durationUs: Long, viewportPx: Float): Float =
        if (durationUs <= 0L) DEFAULT_PX_PER_SEC else clampZoom(viewportPx / (durationUs.toFloat() / US_PER_SEC))
}
