package app.vortex.studio.core.export

import app.vortex.studio.core.model.*

/** O que o motor de renderização atual realmente consegue produzir. A interface consulta isto para habilitar funções. */
object EngineCapabilities {
    /** Transições renderizadas de verdade (mergulho em preto/branco, sem sobreposição de clipes). */
    val supportedTransitions: Set<TransitionType> = setOf(TransitionType.FADE_BLACK, TransitionType.FADE_WHITE)
    /** Composição de faixas de vídeo adicionais ainda não foi implementada no motor. */
    const val EXTRA_VIDEO_TRACKS = false
    fun isTransitionSupported(t: TransitionType): Boolean = t in supportedTransitions
}

enum class ExportIssueCode {
    EMPTY_PROJECT, MISSING_ASSET,
    EXTRA_VIDEO_TRACK_IGNORED, TRANSITION_NOT_SUPPORTED, AUDIO_TRIMMED_TO_PROJECT_END, ITEM_OUTSIDE_PROJECT,
}

data class ExportIssue(val code: ExportIssueCode, val itemId: String? = null, val detail: String? = null)

data class VideoSegment(
    val clipId: String,
    val uri: String,
    val isStill: Boolean,
    val sourceInUs: Long,
    val sourceOutUs: Long,
    val speed: Float,
    /** Rotação em quartos de volta, sentido horário. */
    val rotationQuarters: Int,
    val hasAudio: Boolean,
    val volume: Float,
    val fadeInUs: Long,
    val fadeOutUs: Long,
    val timelineStartUs: Long,
    val timelineDurationUs: Long,
)

data class AudioItemPlan(
    val itemId: String,
    val uri: String,
    val sourceInUs: Long,
    val sourceOutUs: Long,
    val speed: Float,
    val volume: Float,
    val fadeInUs: Long,
    val fadeOutUs: Long,
    val timelineStartUs: Long,
    val timelineDurationUs: Long,
) { val timelineEndUs: Long get() = timelineStartUs + timelineDurationUs }

sealed class OverlayPlan {
    abstract val startUs: Long
    abstract val durationUs: Long
    data class Text(val item: TextItem, override val startUs: Long, override val durationUs: Long) : OverlayPlan()
    data class Image(val item: OverlayItem, val uri: String, override val startUs: Long, override val durationUs: Long) : OverlayPlan()
}

data class RenderPlan(
    val width: Int,
    val height: Int,
    val fps: Int,
    val fit: FitMode,
    val durationUs: Long,
    val videoSegments: List<VideoSegment>,
    /** Faixas de áudio sem sobreposição interna (cada raia vira uma sequência no Media3). */
    val audioLanes: List<List<AudioItemPlan>>,
    val overlays: List<OverlayPlan>,
    val fx: FxTimeline,
    val videoBitrate: Int,
    val settings: ExportSettings,
) {
    val estimatedBytes: Long get() = ExportMath.estimateBytes(durationUs, videoBitrate)
}

/** Efeitos por trecho da linha do tempo, avaliados por instante (códigos puros, testados). */
data class FxSegment(
    val startUs: Long,
    val endUs: Long,
    val base: FxState,
    val keyframes: Map<KeyframeProperty, List<Keyframe>> = emptyMap(),
    val dipInUs: Long = 0L,
    val dipInWhite: Boolean = false,
    val dipOutUs: Long = 0L,
    val dipOutWhite: Boolean = false,
)

class FxTimeline(val segments: List<FxSegment>) {
    val isIdentity: Boolean
        get() = segments.all { it.base.isIdentity && it.keyframes.isEmpty() && it.dipInUs == 0L && it.dipOutUs == 0L }

    fun stateAt(timeUs: Long): FxState {
        val seg = segmentAt(timeUs) ?: return FxState()
        val local = timeUs - seg.startUs
        var s = seg.base
        if (seg.keyframes.isNotEmpty()) {
            val k = seg.keyframes
            s = s.copy(
                zoom = s.zoom * (k[KeyframeProperty.SCALE]?.let { Keyframes.evaluate(it, local, 1f) } ?: 1f),
                offsetX = s.offsetX + (k[KeyframeProperty.POS_X]?.let { Keyframes.evaluate(it, local, 0f) } ?: 0f),
                offsetY = s.offsetY + (k[KeyframeProperty.POS_Y]?.let { Keyframes.evaluate(it, local, 0f) } ?: 0f),
                rotationDeg = s.rotationDeg + (k[KeyframeProperty.ROTATION]?.let { Keyframes.evaluate(it, local, 0f) } ?: 0f),
            )
        }
        var dip = 0f
        var white = false
        if (seg.dipInUs > 0L && local < seg.dipInUs) { dip = 1f - local.toFloat() / seg.dipInUs; white = seg.dipInWhite }
        val remaining = (seg.endUs - seg.startUs) - local
        if (seg.dipOutUs > 0L && remaining < seg.dipOutUs) {
            val d = 1f - remaining.toFloat() / seg.dipOutUs
            if (d > dip) { dip = d; white = seg.dipOutWhite }
        }
        return if (dip > 0f) s.copy(dip = dip, dipWhite = white) else s
    }

    private fun segmentAt(timeUs: Long): FxSegment? {
        var lo = 0
        var hi = segments.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val s = segments[mid]
            when {
                timeUs < s.startUs -> hi = mid - 1
                timeUs >= s.endUs -> lo = mid + 1
                else -> return s
            }
        }
        return segments.lastOrNull()?.takeIf { timeUs == it.endUs }
    }
}
