package app.vortex.studio.core.model

object Keyframes {
    /** Avalia uma curva de keyframes (ordenada por tempo) em [timeUs]. Antes/depois dos extremos, mantém o valor. */
    fun evaluate(frames: List<Keyframe>, timeUs: Long, default: Float): Float {
        if (frames.isEmpty()) return default
        if (timeUs <= frames.first().timeUs) return frames.first().value
        if (timeUs >= frames.last().timeUs) return frames.last().value
        var idx = 0
        for (i in frames.indices) if (frames[i].timeUs <= timeUs) idx = i
        val a = frames[idx]
        val b = frames[idx + 1]
        if (a.easing == Easing.HOLD) return a.value
        val t = (timeUs - a.timeUs).toFloat() / (b.timeUs - a.timeUs).toFloat()
        return a.value + (b.value - a.value) * ease(a.easing, t)
    }

    fun ease(e: Easing, t: Float): Float = when (e) {
        Easing.LINEAR, Easing.HOLD -> t
        Easing.EASE_IN -> t * t
        Easing.EASE_OUT -> 1f - (1f - t) * (1f - t)
        Easing.EASE_IN_OUT -> t * t * (3f - 2f * t)
    }

    /** Zoom lento (efeito "Ken Burns") como keyframes de escala. */
    fun kenBurns(durationUs: Long, from: Float = 1f, to: Float = 1.25f): Map<KeyframeProperty, List<Keyframe>> =
        mapOf(KeyframeProperty.SCALE to listOf(Keyframe(0L, from, Easing.EASE_IN_OUT), Keyframe(durationUs, to)))
}

/** Estado de animação de entrada/saída de textos e sobreposições. dx/dy em unidades normalizadas (-1..1). */
data class OverlayAnimState(val alpha: Float, val dx: Float, val dy: Float, val scale: Float)

object OverlayAnimation {
    private const val SLIDE = 0.3f

    fun evaluate(localUs: Long, durationUs: Long, animIn: ItemAnimation, animOut: ItemAnimation, animDurUs: Long): OverlayAnimState {
        if (localUs < 0L || localUs > durationUs) return OverlayAnimState(0f, 0f, 0f, 1f)
        val dIn = minOf(animDurUs, durationUs / 2)
        val dOut = minOf(animDurUs, durationUs / 2)
        return if (animIn != ItemAnimation.NONE && dIn > 0 && localUs < dIn) {
            stage(animIn, localUs.toFloat() / dIn, entering = true)
        } else if (animOut != ItemAnimation.NONE && dOut > 0 && localUs > durationUs - dOut) {
            stage(animOut, (localUs - (durationUs - dOut)).toFloat() / dOut, entering = false)
        } else OverlayAnimState(1f, 0f, 0f, 1f)
    }

    /** [p]: progresso 0..1 da fase. k: 0 = repouso, 1 = totalmente "fora" do estado final. */
    private fun stage(anim: ItemAnimation, p: Float, entering: Boolean): OverlayAnimState {
        val k = if (entering) 1f - Keyframes.ease(Easing.EASE_OUT, p) else Keyframes.ease(Easing.EASE_IN, p)
        val dir = if (entering) -1f else 1f
        return when (anim) {
            ItemAnimation.NONE -> OverlayAnimState(1f, 0f, 0f, 1f)
            ItemAnimation.FADE -> OverlayAnimState(1f - k, 0f, 0f, 1f)
            ItemAnimation.SLIDE_UP -> OverlayAnimState(1f - k, 0f, dir * k * SLIDE, 1f)
            ItemAnimation.SLIDE_DOWN -> OverlayAnimState(1f - k, 0f, -dir * k * SLIDE, 1f)
            ItemAnimation.SLIDE_LEFT -> OverlayAnimState(1f - k, -dir * k * SLIDE, 0f, 1f)
            ItemAnimation.SLIDE_RIGHT -> OverlayAnimState(1f - k, dir * k * SLIDE, 0f, 1f)
            ItemAnimation.ZOOM -> OverlayAnimState(1f - k, 0f, 0f, 1f - 0.4f * k)
            ItemAnimation.POP -> OverlayAnimState(
                if (entering) minOf(1f, p * 3f) else 1f - k, 0f, 0f,
                if (entering) easeOutBack(p) else 1f - 0.4f * k,
            )
        }
    }

    private fun easeOutBack(t: Float): Float {
        val c1 = 1.70158f
        val c3 = c1 + 1f
        val x = t - 1f
        return 1f + c3 * x * x * x + c1 * x * x
    }
}
