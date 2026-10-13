package app.vortex.studio.core.model

/**
 * Estado completo de efeitos visuais num instante. É calculado aqui (código puro e testado) e apenas
 * enviado à GPU pela camada Android, o que mantém prévia e exportação consistentes.
 */
data class FxState(
    val color: ColorMatrix = ColorMatrix.identity(),
    val vignette: Float = 0f,
    val grain: Float = 0f,
    val blur: Float = 0f,
    val sharpen: Float = 0f,
    val glow: Float = 0f,
    val rgbSplit: Float = 0f,
    val chromatic: Float = 0f,
    val glitch: Float = 0f,
    val zoom: Float = 1f,
    val rotationDeg: Float = 0f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val mirrorX: Boolean = false,
    val mirrorY: Boolean = false,
    /** 0..1: quanto o quadro está "mergulhado" em preto/branco (transições fade). */
    val dip: Float = 0f,
    val dipWhite: Boolean = false,
) {
    val isIdentity: Boolean get() = this == FxState()
}

object FxEvaluator {
    private fun bip(i: Float) = (i.coerceIn(0f, 1f) - 0.5f) * 2f

    fun evaluate(effects: List<EffectInstance>, transform: ClipTransform = ClipTransform()): FxState {
        var color = ColorMatrix.identity()
        var s = FxState()
        for (e in effects) {
            if (!e.enabled) continue
            val i = e.intensity.coerceIn(0f, 1f)
            when (e.type) {
                EffectType.BLACK_WHITE -> color = ColorMath.grayscale(i) * color
                EffectType.SEPIA -> color = ColorMath.sepia(i) * color
                EffectType.VINTAGE -> color = ColorMath.vintage(i) * color
                EffectType.CINEMA -> color = ColorMath.cinema(i) * color
                EffectType.CONTRAST -> color = ColorMath.contrast(1f + bip(i) * 0.5f) * color
                EffectType.SATURATION -> color = ColorMath.saturation(1f + bip(i)) * color
                EffectType.TEMPERATURE -> color = ColorMath.temperature(bip(i)) * color
                EffectType.BRIGHTNESS -> color = ColorMath.exposure(bip(i)) * color
                EffectType.SHARPEN -> s = s.copy(sharpen = i)
                EffectType.BLUR -> s = s.copy(blur = i)
                EffectType.VIGNETTE -> s = s.copy(vignette = i)
                EffectType.FILM_GRAIN -> s = s.copy(grain = i)
                EffectType.ZOOM -> s = s.copy(zoom = s.zoom * (1f + i))
                EffectType.ROTATION -> s = s.copy(rotationDeg = s.rotationDeg + bip(i) * 180f)
                EffectType.MIRROR_H -> s = s.copy(mirrorX = !s.mirrorX)
                EffectType.MIRROR_V -> s = s.copy(mirrorY = !s.mirrorY)
                EffectType.RGB_SPLIT -> s = s.copy(rgbSplit = i)
                EffectType.GLITCH -> s = s.copy(glitch = i)
                EffectType.CHROMATIC_ABERRATION -> s = s.copy(chromatic = i)
                EffectType.GLOW -> s = s.copy(glow = i)
            }
        }
        return s.copy(
            color = color,
            zoom = s.zoom * transform.zoom,
            offsetX = s.offsetX + transform.offsetX,
            offsetY = s.offsetY + transform.offsetY,
            mirrorX = s.mirrorX != transform.mirrorH,
            mirrorY = s.mirrorY != transform.mirrorV,
        )
    }
}
