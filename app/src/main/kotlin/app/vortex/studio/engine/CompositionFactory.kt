@file:OptIn(UnstableApi::class)

package app.vortex.studio.engine

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.FrameDropEffect
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.effect.TextureOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import app.vortex.studio.core.export.AudioItemPlan
import app.vortex.studio.core.export.RenderPlan
import app.vortex.studio.core.export.VideoSegment
import app.vortex.studio.core.model.FitMode
import kotlin.math.abs

private class ConstantSpeed(private val speed: Float) : SpeedProvider {
    override fun getSpeed(timeUs: Long): Float = speed
    override fun getNextSpeedChangeTimeUs(timeUs: Long): Long = C.TIME_UNSET
}

/**
 * Converte o [RenderPlan] (puro, testado) em uma [Composition] do Media3:
 * - sequência principal: clipes/fotos com rotação, ajuste ao quadro, velocidade, volume e fades;
 * - uma sequência de áudio por raia (música, efeitos, narração) com lacunas para o posicionamento;
 * - efeitos de composição: efeitos visuais por tempo, textos/imagens sobrepostos e limite de fps.
 */
object CompositionFactory {
    fun build(plan: RenderPlan, overlays: List<TextureOverlay>): Composition {
        val layout = if (plan.fit == FitMode.FILL) Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP else Presentation.LAYOUT_SCALE_TO_FIT

        val main = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO, C.TRACK_TYPE_VIDEO))
        plan.videoSegments.forEach { main.addItem(videoItem(it, plan, layout)) }

        val sequences = ArrayList<EditedMediaItemSequence>()
        sequences.add(main.build())
        plan.audioLanes.filter { it.isNotEmpty() }.forEach { sequences.add(audioSequence(it)) }

        val compositionEffects = ArrayList<Effect>()
        if (!plan.fx.isIdentity) compositionEffects.add(TimelineFxEffect { t -> plan.fx.stateAt(t) })
        if (overlays.isNotEmpty()) compositionEffects.add(OverlayEffect(overlays))
        compositionEffects.add(FrameDropEffect.createDefaultFrameDropEffect(plan.fps.toFloat()))

        return Composition.Builder(sequences)
            .setEffects(Effects(emptyList(), compositionEffects))
            // H.264 é SDR: vídeos HDR (HLG/HDR10) dos celulares são convertidos para SDR na exportação.
            .setHdrMode(Composition.HDR_MODE_TONE_MAP_HDR_TO_SDR_USING_OPEN_GL)
            .build()
    }

    private fun videoItem(seg: VideoSegment, plan: RenderPlan, layout: Int): EditedMediaItem {
        val uri = Uri.parse(seg.uri)
        val mediaItem = if (seg.isStill) {
            MediaItem.Builder().setUri(uri).setImageDurationMs(seg.timelineDurationUs / 1000L).build()
        } else {
            MediaItem.Builder().setUri(uri).setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(seg.sourceInUs / 1000L)
                    .setEndPositionMs(seg.sourceOutUs / 1000L)
                    .build(),
            ).build()
        }

        val videoEffects = ArrayList<Effect>()
        if (seg.rotationQuarters != 0) {
            // O Media3 gira no sentido anti-horário; o modelo usa o sentido horário.
            videoEffects.add(ScaleAndRotateTransformation.Builder().setRotationDegrees(-90f * seg.rotationQuarters).build())
        }
        videoEffects.add(Presentation.createForWidthAndHeight(plan.width, plan.height, layout))

        val audioProcessors = ArrayList<AudioProcessor>()
        if (seg.volume != 1f || seg.fadeInUs > 0L || seg.fadeOutUs > 0L) {
            audioProcessors.add(GainAudioProcessor(seg.volume, seg.fadeInUs, seg.fadeOutUs, seg.timelineDurationUs))
        }

        val builder = EditedMediaItem.Builder(mediaItem).setEffects(Effects(audioProcessors, videoEffects))
        if (seg.isStill) {
            builder.setDurationUs(seg.timelineDurationUs).setFrameRate(plan.fps)
        } else {
            builder.setRemoveAudio(!seg.hasAudio)
            if (abs(seg.speed - 1f) > 1e-3f) builder.setSpeed(ConstantSpeed(seg.speed))
        }
        return builder.build()
    }

    private fun audioSequence(lane: List<AudioItemPlan>): EditedMediaItemSequence {
        val b = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO))
        var cursor = 0L
        for (item in lane) {
            if (item.timelineStartUs > cursor) b.addGap(item.timelineStartUs - cursor)
            b.addItem(audioItem(item))
            cursor = item.timelineEndUs
        }
        if (lane.first().timelineStartUs > 0L) b.experimentalSetForceAudioTrack(true)
        return b.build()
    }

    private fun audioItem(a: AudioItemPlan): EditedMediaItem {
        val mediaItem = MediaItem.Builder().setUri(Uri.parse(a.uri)).setClippingConfiguration(
            MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(a.sourceInUs / 1000L)
                .setEndPositionMs(a.sourceOutUs / 1000L)
                .build(),
        ).build()
        val processors = ArrayList<AudioProcessor>()
        if (a.volume != 1f || a.fadeInUs > 0L || a.fadeOutUs > 0L) {
            processors.add(GainAudioProcessor(a.volume, a.fadeInUs, a.fadeOutUs, a.timelineDurationUs))
        }
        val builder = EditedMediaItem.Builder(mediaItem).setRemoveVideo(true).setEffects(Effects(processors, emptyList()))
        if (abs(a.speed - 1f) > 1e-3f) builder.setSpeed(ConstantSpeed(a.speed))
        return builder.build()
    }
}
