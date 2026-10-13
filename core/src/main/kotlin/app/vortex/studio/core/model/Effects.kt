package app.vortex.studio.core.model

enum class EffectCategory { COLOR, STYLE, FRAMING, GLITCH, LIGHT }

/**
 * Catálogo de efeitos. A intensidade é sempre 0..1. Efeitos [bipolar] têm o ponto neutro em 0,5
 * (ex.: contraste, saturação, temperatura, brilho).
 */
enum class EffectType(
    val category: EffectCategory,
    val defaultIntensity: Float,
    val hasIntensity: Boolean = true,
    val bipolar: Boolean = false,
) {
    BLACK_WHITE(EffectCategory.COLOR, 1f),
    SEPIA(EffectCategory.COLOR, 1f),
    VINTAGE(EffectCategory.COLOR, 0.8f),
    CINEMA(EffectCategory.COLOR, 0.8f),
    CONTRAST(EffectCategory.COLOR, 0.5f, bipolar = true),
    SATURATION(EffectCategory.COLOR, 0.5f, bipolar = true),
    TEMPERATURE(EffectCategory.COLOR, 0.5f, bipolar = true),
    BRIGHTNESS(EffectCategory.COLOR, 0.5f, bipolar = true),
    SHARPEN(EffectCategory.STYLE, 0.5f),
    BLUR(EffectCategory.STYLE, 0.4f),
    VIGNETTE(EffectCategory.STYLE, 0.6f),
    FILM_GRAIN(EffectCategory.STYLE, 0.5f),
    ZOOM(EffectCategory.FRAMING, 0.3f),
    ROTATION(EffectCategory.FRAMING, 0.5f, bipolar = true),
    MIRROR_H(EffectCategory.FRAMING, 1f, hasIntensity = false),
    MIRROR_V(EffectCategory.FRAMING, 1f, hasIntensity = false),
    RGB_SPLIT(EffectCategory.GLITCH, 0.5f),
    GLITCH(EffectCategory.GLITCH, 0.5f),
    CHROMATIC_ABERRATION(EffectCategory.GLITCH, 0.5f),
    GLOW(EffectCategory.LIGHT, 0.5f);

    val isColorMatrix: Boolean get() = category == EffectCategory.COLOR
}

data class EffectInstance(
    val id: String,
    val type: EffectType,
    val intensity: Float = type.defaultIntensity,
    val enabled: Boolean = true,
)

/** Rotação em múltiplos de 90° (sentido horário) + ajustes de enquadramento do clipe. */
data class ClipTransform(
    val rotationQuarters: Int = 0,
    val zoom: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val mirrorH: Boolean = false,
    val mirrorV: Boolean = false,
) {
    val isIdentity: Boolean get() = this == ClipTransform()
}

enum class KeyframeProperty { SCALE, POS_X, POS_Y, ROTATION }
enum class Easing { LINEAR, EASE_IN, EASE_OUT, EASE_IN_OUT, HOLD }

/** [timeUs] é relativo ao início do item na linha do tempo. */
data class Keyframe(val timeUs: Long, val value: Float, val easing: Easing = Easing.LINEAR)

enum class TransitionType { FADE_BLACK, FADE_WHITE, DISSOLVE, SLIDE_LEFT, SLIDE_RIGHT, SLIDE_UP, SLIDE_DOWN, ZOOM_IN, ZOOM_OUT }

/** Transição entre o clipe anterior e este (armazenada no clipe que entra). */
data class TransitionSpec(val type: TransitionType, val durationUs: Long = 600_000L)

enum class TextAlignment { START, CENTER, END }
enum class ItemAnimation { NONE, FADE, SLIDE_UP, SLIDE_DOWN, SLIDE_LEFT, SLIDE_RIGHT, ZOOM, POP }

data class TextStyleSpec(
    val fontKey: String = "sans",
    /** Altura da fonte como fração da altura do quadro. */
    val sizeFrac: Float = 0.06f,
    val colorArgb: Int = 0xFFFFFFFF.toInt(),
    val bold: Boolean = true,
    val italic: Boolean = false,
    val alignment: TextAlignment = TextAlignment.CENTER,
    val strokeColorArgb: Int = 0xFF000000.toInt(),
    val strokeWidthFrac: Float = 0f,
    val shadow: Boolean = false,
    val backgroundArgb: Int = 0,
)
