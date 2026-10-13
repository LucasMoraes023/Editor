package app.vortex.studio.core.model

import kotlin.math.roundToLong

private fun scaledDuration(sourceSpanUs: Long, speed: Float): Long =
    (sourceSpanUs / speed.toDouble()).roundToLong()

/** Item posicionado na linha do tempo. Todos os tempos em microssegundos. */
sealed interface TimelineItem {
    val id: String
    val startUs: Long
    val durationUs: Long
    val endUs: Long get() = startUs + durationUs
    fun withStart(newStartUs: Long): TimelineItem
}

/** Vídeo ou foto (still) na faixa de vídeo. Na faixa principal, [startUs] é recalculado em cascata. */
data class MediaClip(
    override val id: String,
    val assetId: String,
    override val startUs: Long,
    val sourceInUs: Long,
    val sourceOutUs: Long,
    val isStill: Boolean = false,
    val speed: Float = 1f,
    val volume: Float = 1f,
    val muted: Boolean = false,
    val fadeInUs: Long = 0L,
    val fadeOutUs: Long = 0L,
    val transform: ClipTransform = ClipTransform(),
    val effects: List<EffectInstance> = emptyList(),
    val keyframes: Map<KeyframeProperty, List<Keyframe>> = emptyMap(),
    val transitionIn: TransitionSpec? = null,
) : TimelineItem {
    val effectiveSpeed: Float get() = if (isStill) 1f else speed
    override val durationUs: Long get() = scaledDuration(sourceOutUs - sourceInUs, effectiveSpeed)
    override fun withStart(newStartUs: Long): MediaClip = copy(startUs = newStartUs)
}

/** Música, efeito sonoro ou narração. */
data class AudioClip(
    override val id: String,
    val assetId: String,
    override val startUs: Long,
    val sourceInUs: Long,
    val sourceOutUs: Long,
    val speed: Float = 1f,
    val volume: Float = 1f,
    val muted: Boolean = false,
    val fadeInUs: Long = 0L,
    val fadeOutUs: Long = 0L,
) : TimelineItem {
    override val durationUs: Long get() = scaledDuration(sourceOutUs - sourceInUs, speed)
    override fun withStart(newStartUs: Long): AudioClip = copy(startUs = newStartUs)
}

/** Texto/legenda. [x] e [y] em coordenadas normalizadas (-1..1, y positivo para cima). */
data class TextItem(
    override val id: String,
    override val startUs: Long,
    override val durationUs: Long,
    val text: String,
    val style: TextStyleSpec = TextStyleSpec(),
    val x: Float = 0f,
    val y: Float = 0f,
    val rotationDeg: Float = 0f,
    val animIn: ItemAnimation = ItemAnimation.FADE,
    val animOut: ItemAnimation = ItemAnimation.FADE,
    val animDurationUs: Long = 400_000L,
) : TimelineItem {
    override fun withStart(newStartUs: Long): TextItem = copy(startUs = newStartUs)
}

/** Imagem/logotipo sobreposto. [scale] é a largura relativa à largura do quadro. */
data class OverlayItem(
    override val id: String,
    val assetId: String,
    override val startUs: Long,
    override val durationUs: Long,
    val x: Float = 0f,
    val y: Float = 0f,
    val scale: Float = 0.3f,
    val rotationDeg: Float = 0f,
    val opacity: Float = 1f,
    val animIn: ItemAnimation = ItemAnimation.FADE,
    val animOut: ItemAnimation = ItemAnimation.FADE,
    val animDurationUs: Long = 400_000L,
) : TimelineItem {
    override fun withStart(newStartUs: Long): OverlayItem = copy(startUs = newStartUs)
}

enum class TrackKind { VIDEO, IMAGE, TEXT, MUSIC, SFX, VOICE;
    val isAudio: Boolean get() = this == MUSIC || this == SFX || this == VOICE
}

data class Track(
    val id: String,
    val kind: TrackKind,
    val name: String,
    val items: List<TimelineItem> = emptyList(),
    val isMain: Boolean = false,
    val muted: Boolean = false,
    val locked: Boolean = false,
)

enum class FitMode { FIT, FILL }

enum class AspectPreset(val ratioW: Int, val ratioH: Int) {
    PORTRAIT_9_16(9, 16), LANDSCAPE_16_9(16, 9), SQUARE_1_1(1, 1),
    PORTRAIT_4_5(4, 5), CINEMA_21_9(21, 9), CLASSIC_4_3(4, 3);

    /** Tamanho (largura, altura) com o menor lado igual a [shortSide], sempre com valores pares. */
    fun sizeForShortSide(shortSide: Int): Pair<Int, Int> {
        fun even(v: Double): Int = (Math.round(v / 2.0) * 2L).toInt()
        return if (ratioW >= ratioH) Pair(even(shortSide.toDouble() * ratioW / ratioH), shortSide)
        else Pair(shortSide, even(shortSide.toDouble() * ratioH / ratioW))
    }
}

data class CanvasSpec(
    val aspect: AspectPreset = AspectPreset.PORTRAIT_9_16,
    val fps: Int = 30,
    val fit: FitMode = FitMode.FIT,
)

data class Project(
    val id: String,
    val name: String,
    val createdAtMs: Long,
    val modifiedAtMs: Long,
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val canvas: CanvasSpec = CanvasSpec(),
    val assets: Map<String, MediaAsset> = emptyMap(),
    val tracks: List<Track> = listOf(Track(MAIN_TRACK_ID, TrackKind.VIDEO, "main", isMain = true)),
    val templateId: String? = null,
) {
    val mainTrack: Track get() = tracks.first { it.isMain }

    /** Duração do projeto: fim da faixa principal (ou do último item, se a principal estiver vazia). */
    val durationUs: Long
        get() = mainTrack.items.lastOrNull()?.endUs ?: tracks.maxOfOrNull { t -> t.items.maxOfOrNull { it.endUs } ?: 0L } ?: 0L

    fun findItem(id: String): Pair<Track, TimelineItem>? {
        for (t in tracks) for (i in t.items) if (i.id == id) return t to i
        return null
    }

    fun mainClipAt(timeUs: Long): MediaClip? {
        val clips = mainTrack.items.filterIsInstance<MediaClip>()
        return clips.firstOrNull { timeUs >= it.startUs && timeUs < it.endUs } ?: clips.lastOrNull()?.takeIf { timeUs == it.endUs }
    }

    /** Refaz a disposição: a faixa principal fica contínua; as demais ficam ordenadas por início. */
    fun normalized(): Project = copy(tracks = tracks.map { t ->
        if (t.isMain) {
            var cursor = 0L
            t.copy(items = t.items.map { it.withStart(cursor).also { moved -> cursor += moved.durationUs } })
        } else t.copy(items = t.items.sortedBy { it.startUs })
    })

    /** Mídias referenciadas por algum item, em ordem de uso. */
    fun referencedAssetIds(): List<String> = tracks.flatMap { t ->
        t.items.mapNotNull { when (it) { is MediaClip -> it.assetId; is AudioClip -> it.assetId; is OverlayItem -> it.assetId; else -> null } }
    }.distinct()
}
