@file:OptIn(UnstableApi::class)

package app.vortex.studio.engine

import android.content.Context
import android.net.Uri
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.exoplayer.ExoPlayer
import app.vortex.studio.core.export.AudioItemPlan
import app.vortex.studio.core.export.FxTimeline
import app.vortex.studio.core.export.RenderPlan
import app.vortex.studio.core.model.FitMode
import app.vortex.studio.core.model.FxState
import app.vortex.studio.core.model.MediaAsset
import app.vortex.studio.core.model.MediaClip
import app.vortex.studio.core.model.Project
import kotlin.math.abs

/**
 * Prévia com ExoPlayer: a faixa principal vira uma playlist (cortes via ClippingConfiguration, velocidade
 * por item) e os efeitos visuais usam o MESMO [TimelineFxEffect] da exportação, alimentado com o estado da
 * linha do tempo na posição atual. Música/narração tocam em players auxiliares sincronizados.
 * Limites da prévia (a exportação não tem): volume até 100%, sem fades de áudio, textos desenhados pela interface.
 */
class PreviewController(private val context: Context) {
    private class Slot(val startUs: Long, val durationUs: Long, val speed: Float, val volume: Float, val rotation: Int)

    @Volatile private var liveFx = FxState()
    private val fxEffect = TimelineFxEffect { liveFx }
    val player: ExoPlayer = ExoPlayer.Builder(context).build()

    private var slots: List<Slot> = emptyList()
    private var signature: Any? = null
    private var fxTimeline = FxTimeline(emptyList())
    private var effectsOn = true
    private var canvasW = 540
    private var canvasH = 960
    private var layout = Presentation.LAYOUT_SCALE_TO_FIT
    private var appliedKey: Any? = null
    private val audio = ArrayList<AudioSlot>()
    private var audioSignature: Any? = null

    var onError: ((PlaybackException) -> Unit)? = null
    var onEnded: (() -> Unit)? = null
    var onPlayingChanged: ((Boolean) -> Unit)? = null

    init {
        // Obrigatório antes de prepare(); a lista é trocada quando rotação/tamanho/formato mudam.
        player.setVideoEffects(listOf(fxEffect))
        player.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) { onError?.invoke(error) }
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isPlaying) pauseAudio()
                onPlayingChanged?.invoke(isPlaying)
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) { pauseAudio(); onEnded?.invoke() }
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { applyClipProps() }
        })
    }

    val isPlaying: Boolean get() = player.isPlaying

    fun update(project: Project, plan: RenderPlan?, previewShortSide: Int, useEffects: Boolean) {
        val clips = project.mainTrack.items.filterIsInstance<MediaClip>().filter { project.assets[it.assetId] != null }
        if (plan == null || clips.isEmpty()) { clear(); return }

        val (w, h) = project.canvas.aspect.sizeForShortSide(previewShortSide)
        canvasW = w; canvasH = h
        layout = if (project.canvas.fit == FitMode.FILL) Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP else Presentation.LAYOUT_SCALE_TO_FIT
        effectsOn = useEffects
        fxTimeline = plan.fx

        val sig = clips.map { listOf(it.assetId, project.assets[it.assetId]?.uri, it.sourceInUs, it.sourceOutUs, it.isStill) }
        val reload = sig != signature
        val pos = if (reload) currentTimelineUs() else 0L
        slots = clips.map { Slot(it.startUs, it.durationUs, it.effectiveSpeed, if (it.muted) 0f else it.volume, it.transform.rotationQuarters) }
        if (reload) {
            signature = sig
            player.setMediaItems(clips.map { toMediaItem(it, project.assets.getValue(it.assetId)) })
            player.prepare()
            seekToTimeline(pos)
        } else {
            applyClipProps()
        }
        updateAudio(plan)
        refresh()
    }

    /** Recalcula o estado visual na posição atual e força o redesenho do quadro quando pausado. */
    fun refresh() {
        liveFx = if (effectsOn) fxTimeline.stateAt(currentTimelineUs()) else FxState()
        if (!player.isPlaying && player.mediaItemCount > 0) player.seekTo(player.currentMediaItemIndex, player.currentPosition)
    }

    /** Chamado ~30x/s durante a reprodução: devolve a posição na linha do tempo e sincroniza efeitos e áudio. */
    fun tick(): Long {
        val t = currentTimelineUs()
        liveFx = if (effectsOn) fxTimeline.stateAt(t) else FxState()
        syncAudio(t, player.isPlaying)
        return t
    }

    fun currentTimelineUs(): Long {
        val s = slots.getOrNull(player.currentMediaItemIndex) ?: return 0L
        val posUs = player.currentPosition.coerceAtLeast(0L) * 1000L
        return (s.startUs + posUs / s.speed).toLong().coerceAtMost(s.startUs + s.durationUs)
    }

    fun seekToTimeline(us: Long) {
        if (slots.isEmpty()) return
        val end = slots.last().startUs + slots.last().durationUs
        val t = us.coerceIn(0L, end)
        val idx = slots.indexOfLast { it.startUs <= t }.coerceAtLeast(0)
        val s = slots[idx]
        player.seekTo(idx, ((t - s.startUs) * s.speed / 1000f).toLong().coerceAtLeast(0L))
        applyClipProps()
        liveFx = if (effectsOn) fxTimeline.stateAt(t) else FxState()
        syncAudio(t, false)
    }

    fun play() {
        if (slots.isEmpty()) return
        if (player.playbackState == Player.STATE_ENDED) seekToTimeline(0L)
        player.play()
    }

    fun pause() { player.pause(); pauseAudio() }

    fun clear() {
        player.clearMediaItems()
        slots = emptyList(); signature = null
        releaseAudio()
    }

    fun release() {
        releaseAudio()
        player.release()
    }

    private fun applyClipProps() {
        val s = slots.getOrNull(player.currentMediaItemIndex) ?: return
        player.playbackParameters = PlaybackParameters(s.speed)
        player.volume = s.volume.coerceIn(0f, 1f)
        ensureEffects(s.rotation)
    }

    private fun ensureEffects(rotationQuarters: Int) {
        val key = listOf(canvasW, canvasH, layout, rotationQuarters, effectsOn)
        if (key == appliedKey) return
        appliedKey = key
        val list = ArrayList<Effect>()
        if (rotationQuarters != 0) list.add(ScaleAndRotateTransformation.Builder().setRotationDegrees(-90f * rotationQuarters).build())
        list.add(Presentation.createForWidthAndHeight(canvasW, canvasH, layout))
        if (effectsOn) list.add(fxEffect)
        player.setVideoEffects(list)
    }

    private fun toMediaItem(c: MediaClip, a: MediaAsset): MediaItem {
        val uri = Uri.parse(a.uri)
        return if (c.isStill) {
            MediaItem.Builder().setUri(uri).setImageDurationMs(c.durationUs / 1000L).build()
        } else {
            MediaItem.Builder().setUri(uri).setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(c.sourceInUs / 1000L)
                    .setEndPositionMs(c.sourceOutUs / 1000L)
                    .build(),
            ).build()
        }
    }

    // ---------- áudio auxiliar (música, efeitos, narração) ----------

    private inner class AudioSlot(var plan: AudioItemPlan) {
        val exo: ExoPlayer = ExoPlayer.Builder(context).build().apply {
            setMediaItem(
                MediaItem.Builder().setUri(Uri.parse(plan.uri)).setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(plan.sourceInUs / 1000L)
                        .setEndPositionMs(plan.sourceOutUs / 1000L)
                        .build(),
                ).build(),
            )
            playbackParameters = PlaybackParameters(plan.speed)
            prepare()
        }

        fun gainAt(t: Long): Float {
            val local = t - plan.timelineStartUs
            var g = plan.volume
            if (plan.fadeInUs > 0L && local < plan.fadeInUs) g *= local.toFloat() / plan.fadeInUs
            val left = plan.timelineDurationUs - local
            if (plan.fadeOutUs > 0L && left < plan.fadeOutUs) g *= left.toFloat() / plan.fadeOutUs
            return g.coerceIn(0f, 1f)
        }
    }

    private fun updateAudio(plan: RenderPlan) {
        val items = plan.audioLanes.flatten()
        val sig = items.map { listOf(it.itemId, it.uri, it.sourceInUs, it.sourceOutUs, it.speed, it.timelineStartUs, it.timelineDurationUs) }
        if (sig != audioSignature) {
            audioSignature = sig
            releaseAudio()
            items.forEach { audio.add(AudioSlot(it)) }
        } else {
            items.forEach { p -> audio.firstOrNull { it.plan.itemId == p.itemId }?.plan = p }
        }
    }

    private fun syncAudio(t: Long, playing: Boolean) {
        for (s in audio) {
            val p = s.plan
            val active = t >= p.timelineStartUs && t < p.timelineEndUs
            if (active && playing) {
                val expected = ((t - p.timelineStartUs) * p.speed / 1000f).toLong()
                if (!s.exo.isPlaying) {
                    s.exo.seekTo(expected)
                    s.exo.play()
                } else if (abs(s.exo.currentPosition - expected) > 250L) {
                    s.exo.seekTo(expected)
                }
                s.exo.volume = s.gainAt(t)
            } else if (s.exo.isPlaying) {
                s.exo.pause()
            }
        }
    }

    private fun pauseAudio() { audio.forEach { if (it.exo.isPlaying) it.exo.pause() } }

    private fun releaseAudio() {
        audio.forEach { it.exo.release() }
        audio.clear()
        audioSignature = null
    }
}
