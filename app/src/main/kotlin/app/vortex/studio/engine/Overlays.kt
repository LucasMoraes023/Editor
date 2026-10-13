@file:OptIn(UnstableApi::class)

package app.vortex.studio.engine

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Pair as AndroidPair
import androidx.media3.common.OverlaySettings
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.TextureOverlay
import app.vortex.studio.core.export.OverlayPlan
import app.vortex.studio.core.export.RenderPlan
import app.vortex.studio.core.model.ItemAnimation
import app.vortex.studio.core.model.OverlayAnimation

/**
 * Sobreposição (texto ou imagem) com posição, escala, rotação e opacidade animadas pelo tempo da
 * linha do tempo. O bitmap tem o tamanho exato que deve ocupar no quadro (o Media3 desenha o
 * overlay em pixels relativos ao quadro), então a escala base é 1.
 */
private class AnimatedOverlay(
    private val bitmap: Bitmap,
    private val startUs: Long,
    private val durationUs: Long,
    private val x: Float,
    private val y: Float,
    private val rotationDeg: Float,
    private val baseAlpha: Float,
    private val animIn: ItemAnimation,
    private val animOut: ItemAnimation,
    private val animDurationUs: Long,
) : BitmapOverlay() {

    override fun getBitmap(presentationTimeUs: Long): Bitmap = bitmap

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        val s = OverlayAnimation.evaluate(presentationTimeUs - startUs, durationUs, animIn, animOut, animDurationUs)
        return object : OverlaySettings {
            override fun getAlphaScale(): Float = baseAlpha * s.alpha
            override fun getBackgroundFrameAnchor(): AndroidPair<Float, Float> = AndroidPair.create(x + s.dx, y + s.dy)
            override fun getOverlayFrameAnchor(): AndroidPair<Float, Float> = AndroidPair.create(0f, 0f)
            override fun getScale(): AndroidPair<Float, Float> = AndroidPair.create(s.scale, s.scale)
            override fun getRotationDegrees(): Float = rotationDeg
        }
    }
}

object OverlayFactory {
    /** Cria as sobreposições do plano. Chame fora da thread principal (decodifica imagens). */
    fun build(context: Context, plan: RenderPlan): List<TextureOverlay> = plan.overlays.mapNotNull { o ->
        when (o) {
            is OverlayPlan.Text -> AnimatedOverlay(
                TextBitmapRenderer.render(o.item, plan.width, plan.height), o.startUs, o.durationUs,
                o.item.x, o.item.y, o.item.rotationDeg, 1f, o.item.animIn, o.item.animOut, o.item.animDurationUs,
            )
            is OverlayPlan.Image -> {
                val target = (plan.width * o.item.scale).toInt().coerceAtLeast(2)
                TextBitmapRenderer.decodeScaled(context, Uri.parse(o.uri), target)?.let { bmp ->
                    AnimatedOverlay(
                        bmp, o.startUs, o.durationUs, o.item.x, o.item.y, o.item.rotationDeg, o.item.opacity,
                        o.item.animIn, o.item.animOut, o.item.animDurationUs,
                    )
                }
            }
        }
    }
}
