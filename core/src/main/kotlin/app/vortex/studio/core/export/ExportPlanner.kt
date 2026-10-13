package app.vortex.studio.core.export

import app.vortex.studio.core.model.*

sealed class PlanResult {
    data class Ok(val plan: RenderPlan, val warnings: List<ExportIssue>) : PlanResult()
    data class Failed(val issues: List<ExportIssue>) : PlanResult()
}

/** Converte o projeto de edição num plano de renderização puro (sem Android), validando mídias e limites do motor. */
object ExportPlanner {
    fun plan(project: Project, settings: ExportSettings, isAvailable: (MediaAsset) -> Boolean = { true }): PlanResult {
        val clips = project.mainTrack.items.filterIsInstance<MediaClip>()
        if (clips.isEmpty()) return PlanResult.Failed(listOf(ExportIssue(ExportIssueCode.EMPTY_PROJECT)))

        val errors = mutableListOf<ExportIssue>()
        val warnings = mutableListOf<ExportIssue>()

        fun check(itemId: String, assetId: String): MediaAsset? {
            val a = project.assets[assetId]
            if (a == null || !isAvailable(a)) { errors += ExportIssue(ExportIssueCode.MISSING_ASSET, itemId, assetId); return null }
            return a
        }

        val duration = project.durationUs
        val segments = clips.map { c ->
            val a = check(c.id, c.assetId)
            VideoSegment(
                clipId = c.id, uri = a?.uri ?: "", isStill = c.isStill, sourceInUs = c.sourceInUs, sourceOutUs = c.sourceOutUs,
                speed = c.effectiveSpeed, rotationQuarters = c.transform.rotationQuarters,
                hasAudio = !c.isStill && (a?.hasAudio ?: false) && !c.muted && !project.mainTrack.muted,
                volume = c.volume, fadeInUs = c.fadeInUs, fadeOutUs = c.fadeOutUs, timelineStartUs = c.startUs, timelineDurationUs = c.durationUs,
            )
        }

        // Transições: apenas as que o motor renderiza de verdade (mergulho em preto/branco).
        val fx = FxTimelineBuilder.build(clips) { clip, type -> warnings += ExportIssue(ExportIssueCode.TRANSITION_NOT_SUPPORTED, clip.id, type.name) }

        // Faixas de vídeo extras: ainda sem composição no motor.
        for (t in project.tracks) if (t.kind == TrackKind.VIDEO && !t.isMain && t.items.isNotEmpty() && !EngineCapabilities.EXTRA_VIDEO_TRACKS) {
            warnings += ExportIssue(ExportIssueCode.EXTRA_VIDEO_TRACK_IGNORED, t.id)
        }

        // Textos e imagens sobrepostas.
        val overlays = mutableListOf<OverlayPlan>()
        for (t in project.tracks) {
            if (t.muted) continue
            for (item in t.items) {
                if (item !is TextItem && item !is OverlayItem) continue
                if (item.startUs >= duration) { warnings += ExportIssue(ExportIssueCode.ITEM_OUTSIDE_PROJECT, item.id); continue }
                val len = minOf(item.endUs, duration) - item.startUs
                when (item) {
                    is TextItem -> overlays += OverlayPlan.Text(item, item.startUs, len)
                    is OverlayItem -> check(item.id, item.assetId)?.let { overlays += OverlayPlan.Image(item, it.uri, item.startUs, len) }
                }
            }
        }

        // Áudio: música, efeitos e narração, cortados no fim do projeto.
        val audio = mutableListOf<AudioItemPlan>()
        for (t in project.tracks) {
            if (!t.kind.isAudio || t.muted) continue
            for (item in t.items) {
                if (item !is AudioClip || item.muted) continue
                val a = check(item.id, item.assetId) ?: continue
                if (item.startUs >= duration) { warnings += ExportIssue(ExportIssueCode.ITEM_OUTSIDE_PROJECT, item.id); continue }
                var len = item.durationUs
                var out = item.sourceOutUs
                if (item.endUs > duration) {
                    len = duration - item.startUs
                    out = item.sourceInUs + (len * item.speed).toLong()
                    warnings += ExportIssue(ExportIssueCode.AUDIO_TRIMMED_TO_PROJECT_END, item.id)
                }
                audio += AudioItemPlan(item.id, a.uri, item.sourceInUs, out, item.speed, item.volume, minOf(item.fadeInUs, len), minOf(item.fadeOutUs, len), item.startUs, len)
            }
        }

        if (errors.isNotEmpty()) return PlanResult.Failed(errors)

        val (w, h) = ExportMath.outputSize(project.canvas.aspect, settings.resolution)
        val plan = RenderPlan(
            width = w, height = h, fps = settings.fps, fit = project.canvas.fit, durationUs = duration,
            videoSegments = segments, audioLanes = AudioLanes.pack(audio), overlays = overlays,
            fx = fx, videoBitrate = ExportMath.videoBitrate(w, h, settings.fps, settings.quality), settings = settings,
        )
        return PlanResult.Ok(plan, warnings)
    }
}

object AudioLanes {
    /** Distribui itens em raias sem sobreposição (preenchimento guloso), pois uma sequência do Media3 é linear. */
    fun pack(items: List<AudioItemPlan>): List<List<AudioItemPlan>> {
        val lanes = mutableListOf<MutableList<AudioItemPlan>>()
        for (item in items.sortedBy { it.timelineStartUs }) {
            val lane = lanes.firstOrNull { it.last().timelineEndUs <= item.timelineStartUs }
            if (lane != null) lane += item else lanes += mutableListOf(item)
        }
        return lanes
    }
}

/** Monta os efeitos por trecho (inclui os mergulhos das transições suportadas). Usado por exportação e prévia. */
object FxTimelineBuilder {
    fun build(clips: List<MediaClip>, onUnsupportedTransition: (MediaClip, TransitionType) -> Unit = { _, _ -> }): FxTimeline {
        val n = clips.size
        val dipIn = LongArray(n); val dipOut = LongArray(n)
        val dipInWhite = BooleanArray(n); val dipOutWhite = BooleanArray(n)
        for (i in 1 until n) {
            val t = clips[i].transitionIn ?: continue
            if (!EngineCapabilities.isTransitionSupported(t.type)) { onUnsupportedTransition(clips[i], t.type); continue }
            val half = minOf(t.durationUs, clips[i - 1].durationUs, clips[i].durationUs) / 2
            dipIn[i] = half; dipInWhite[i] = t.type == TransitionType.FADE_WHITE
            dipOut[i - 1] = half; dipOutWhite[i - 1] = t.type == TransitionType.FADE_WHITE
        }
        return FxTimeline(clips.mapIndexed { i, c ->
            FxSegment(c.startUs, c.endUs, FxEvaluator.evaluate(c.effects, c.transform), c.keyframes, dipIn[i], dipInWhite[i], dipOut[i], dipOutWhite[i])
        })
    }
}
