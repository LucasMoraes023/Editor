package app.vortex.studio.core.edit

import app.vortex.studio.core.ai.TimeRange
import app.vortex.studio.core.model.*

sealed class EditError(message: String) : Exception(message) {
    class ItemNotFound(val id: String) : EditError("Item não encontrado: $id")
    class AssetNotFound(val id: String) : EditError("Mídia não encontrada: $id")
    class InvalidPosition(message: String) : EditError(message)
    class WrongKind(message: String) : EditError(message)
    class Invalid(message: String) : EditError(message)
}

/** Operações puras sobre o [Project]: cada uma devolve um novo projeto e nunca altera o original. */
object Edits {
    const val TRACK_TEXT = "track-text"
    const val TRACK_IMAGE = "track-image"
    const val TRACK_MUSIC = "track-music"
    const val TRACK_SFX = "track-sfx"
    const val TRACK_VOICE = "track-voice"

    // ---------- mídia e faixa principal ----------

    fun registerAsset(p: Project, asset: MediaAsset): Project = p.copy(assets = p.assets + (asset.id to asset))

    fun relinkAsset(p: Project, assetId: String, replacement: MediaAsset): Project {
        if (assetId !in p.assets) throw EditError.AssetNotFound(assetId)
        return p.copy(assets = p.assets + (assetId to replacement.copy(id = assetId)))
    }

    fun appendClip(p: Project, clipId: String, assetId: String, stillUs: Long = DEFAULT_STILL_US): Project =
        insertClip(p, clipId, assetId, p.mainTrack.items.size, stillUs)

    fun insertClip(p: Project, clipId: String, assetId: String, index: Int, stillUs: Long = DEFAULT_STILL_US): Project {
        val a = p.assets[assetId] ?: throw EditError.AssetNotFound(assetId)
        val base = when (a.kind) {
            MediaKind.IMAGE -> MediaClip(clipId, assetId, 0L, 0L, stillUs.coerceAtLeast(MIN_ITEM_US), isStill = true)
            MediaKind.VIDEO -> {
                if (a.durationUs <= 0L) throw EditError.Invalid("Duração do vídeo desconhecida")
                MediaClip(clipId, assetId, 0L, 0L, a.durationUs)
            }
            MediaKind.AUDIO -> throw EditError.WrongKind("Áudio não pode ir para a faixa de vídeo")
        }
        val auto = autoEffects(p, clipId)
        val clip = base.copy(effects = auto)
        val items = p.mainTrack.items.toMutableList().apply { add(index.coerceIn(0, size), clip) }
        return withMainItems(p, items)
    }

    private fun autoEffects(p: Project, clipId: String): List<EffectInstance> =
        app.vortex.studio.core.templates.Templates.defaultEffectsFor(p.templateId)
            .mapIndexed { i, t -> EffectInstance("$clipId-fx$i", t) }

    fun removeItem(p: Project, id: String): Project {
        if (p.findItem(id) == null) throw EditError.ItemNotFound(id)
        return p.copy(tracks = p.tracks.map { t -> if (t.items.any { it.id == id }) t.copy(items = t.items.filterNot { it.id == id }) else t })
    }

    fun moveMainClip(p: Project, clipId: String, toIndex: Int): Project {
        val items = p.mainTrack.items.toMutableList()
        val from = items.indexOfFirst { it.id == clipId }
        if (from < 0) throw EditError.ItemNotFound(clipId)
        val item = items.removeAt(from)
        items.add(toIndex.coerceIn(0, items.size), item)
        return withMainItems(p, items)
    }

    /** Move um item de faixa livre (texto, imagem, áudio) para um novo início. */
    fun moveItem(p: Project, id: String, newStartUs: Long): Project {
        val (track, _) = p.findItem(id) ?: throw EditError.ItemNotFound(id)
        if (track.isMain) throw EditError.WrongKind("Use moveMainClip para a faixa principal")
        return updateItem(p, id) { it.withStart(newStartUs.coerceAtLeast(0L)) }
    }

    fun duplicate(p: Project, id: String, newId: String): Project {
        val (track, item) = p.findItem(id) ?: throw EditError.ItemNotFound(id)
        val copy: TimelineItem = when (item) {
            is MediaClip -> item.copy(id = newId, effects = item.effects.mapIndexed { i, e -> e.copy(id = "$newId-fx$i") }, transitionIn = null)
            is AudioClip -> item.copy(id = newId, startUs = item.endUs)
            is TextItem -> item.copy(id = newId, startUs = item.endUs)
            is OverlayItem -> item.copy(id = newId, startUs = item.endUs)
        }
        return p.copy(tracks = p.tracks.map { t ->
            if (t.id != track.id) t else {
                val items = t.items.toMutableList()
                items.add(items.indexOfFirst { it.id == id } + 1, copy)
                t.copy(items = items)
            }
        })
    }

    // ---------- cortes ----------

    fun splitAt(p: Project, id: String, atUs: Long, newId: String): Project {
        val (track, item) = p.findItem(id) ?: throw EditError.ItemNotFound(id)
        if (atUs < item.startUs + MIN_ITEM_US || atUs > item.endUs - MIN_ITEM_US) {
            throw EditError.InvalidPosition("A divisão precisa estar a pelo menos 0,1 s das bordas do item")
        }
        val local = atUs - item.startUs
        val (first, second) = when (item) {
            is MediaClip -> {
                val split = item.sourceInUs + (local * item.effectiveSpeed).toLong()
                val (ka, kb) = splitKeyframes(item.keyframes, local)
                item.copy(sourceOutUs = split, fadeOutUs = 0L, keyframes = ka) to
                    item.copy(id = newId, startUs = atUs, sourceInUs = split, fadeInUs = 0L, transitionIn = null, keyframes = kb,
                        effects = item.effects.mapIndexed { i, e -> e.copy(id = "$newId-fx$i") })
            }
            is AudioClip -> {
                val split = item.sourceInUs + (local * item.speed).toLong()
                item.copy(sourceOutUs = split, fadeOutUs = 0L) to item.copy(id = newId, startUs = atUs, sourceInUs = split, fadeInUs = 0L)
            }
            is TextItem -> item.copy(durationUs = local) to item.copy(id = newId, startUs = atUs, durationUs = item.durationUs - local)
            is OverlayItem -> item.copy(durationUs = local) to item.copy(id = newId, startUs = atUs, durationUs = item.durationUs - local)
        }
        return p.copy(tracks = p.tracks.map { t ->
            if (t.id != track.id) t else {
                val items = t.items.toMutableList()
                val idx = items.indexOfFirst { it.id == id }
                items[idx] = first
                items.add(idx + 1, second)
                t.copy(items = items)
            }
        })
    }

    private fun splitKeyframes(k: Map<KeyframeProperty, List<Keyframe>>, local: Long): Pair<Map<KeyframeProperty, List<Keyframe>>, Map<KeyframeProperty, List<Keyframe>>> {
        val a = mutableMapOf<KeyframeProperty, List<Keyframe>>()
        val b = mutableMapOf<KeyframeProperty, List<Keyframe>>()
        for ((prop, frames) in k) {
            if (frames.isEmpty()) continue
            val v = Keyframes.evaluate(frames, local, frames.first().value)
            a[prop] = frames.filter { it.timeUs < local } + Keyframe(local, v)
            b[prop] = listOf(Keyframe(0L, v)) + frames.filter { it.timeUs > local }.map { it.copy(timeUs = it.timeUs - local) }
        }
        return a to b
    }

    /** Define o intervalo da mídia de origem (ponto de entrada/saída) de um clipe de vídeo/foto/áudio. */
    fun setSourceRange(p: Project, id: String, inUs: Long, outUs: Long): Project = when (val item = p.findItem(id)?.second ?: throw EditError.ItemNotFound(id)) {
        is MediaClip -> updateItem(p, id) {
            val maxOut = if (item.isStill) Long.MAX_VALUE else p.assets[item.assetId]?.durationUs?.takeIf { d -> d > 0 } ?: Long.MAX_VALUE
            val (i, o) = clampRange(inUs, outUs, maxOut, (MIN_ITEM_US * item.effectiveSpeed).toLong().coerceAtLeast(1L), item.isStill)
            clampFades((it as MediaClip).copy(sourceInUs = i, sourceOutUs = o))
        }
        is AudioClip -> updateItem(p, id) {
            val maxOut = p.assets[item.assetId]?.durationUs?.takeIf { d -> d > 0 } ?: Long.MAX_VALUE
            val (i, o) = clampRange(inUs, outUs, maxOut, (MIN_ITEM_US * item.speed).toLong().coerceAtLeast(1L), false)
            clampFades((it as AudioClip).copy(sourceInUs = i, sourceOutUs = o))
        }
        else -> throw EditError.WrongKind("Este item não possui mídia de origem")
    }

    private fun clampRange(inUs: Long, outUs: Long, maxOut: Long, minSpan: Long, still: Boolean): Pair<Long, Long> {
        if (still) return 0L to (outUs - inUs).coerceAtLeast(minSpan)
        val i = inUs.coerceIn(0L, maxOf(0L, maxOut - minSpan))
        val o = outUs.coerceIn(i + minSpan, maxOf(i + minSpan, maxOut))
        return i to o
    }

    /** Corta o início do item na posição [timelineUs] da linha do tempo. */
    fun trimStartTo(p: Project, id: String, timelineUs: Long): Project {
        val (track, item) = p.findItem(id) ?: throw EditError.ItemNotFound(id)
        val local = (timelineUs - item.startUs).coerceIn(0L, item.durationUs - MIN_ITEM_US)
        return when (item) {
            is MediaClip -> setSourceRange(p, id, item.sourceInUs + (local * item.effectiveSpeed).toLong(), item.sourceOutUs)
            is AudioClip -> setSourceRange(p, id, item.sourceInUs + (local * item.speed).toLong(), item.sourceOutUs)
                .let { q -> if (track.isMain) q else updateItem(q, id) { it.withStart(item.startUs + local) } }
            is TextItem -> updateItem(p, id) { (it as TextItem).copy(startUs = item.startUs + local, durationUs = item.durationUs - local) }
            is OverlayItem -> updateItem(p, id) { (it as OverlayItem).copy(startUs = item.startUs + local, durationUs = item.durationUs - local) }
        }
    }

    /** Corta o fim do item na posição [timelineUs] da linha do tempo. */
    fun trimEndTo(p: Project, id: String, timelineUs: Long): Project {
        val (_, item) = p.findItem(id) ?: throw EditError.ItemNotFound(id)
        val local = (timelineUs - item.startUs).coerceAtLeast(MIN_ITEM_US)
        return when (item) {
            is MediaClip -> setSourceRange(p, id, item.sourceInUs, item.sourceInUs + (local * item.effectiveSpeed).toLong())
            is AudioClip -> setSourceRange(p, id, item.sourceInUs, item.sourceInUs + (local * item.speed).toLong())
            is TextItem -> updateItem(p, id) { (it as TextItem).copy(durationUs = local) }
            is OverlayItem -> updateItem(p, id) { (it as OverlayItem).copy(durationUs = local) }
        }
    }

    /**
     * Move a borda inicial do item por [deltaUs] (positivo encurta, negativo estende até o início da mídia).
     * Em faixas livres o conteúdo permanece alinhado no tempo; na principal a faixa se refaz em cascata.
     */
    fun trimStartBy(p: Project, id: String, deltaUs: Long): Project {
        val (track, item) = p.findItem(id) ?: throw EditError.ItemNotFound(id)
        return when (item) {
            is MediaClip -> setSourceRange(p, id, item.sourceInUs + (deltaUs * item.effectiveSpeed).toLong(), item.sourceOutUs)
            is AudioClip -> {
                val q = setSourceRange(p, id, item.sourceInUs + (deltaUs * item.speed).toLong(), item.sourceOutUs)
                val applied = ((q.findItem(id)!!.second as AudioClip).sourceInUs - item.sourceInUs) / item.speed
                if (track.isMain) q else updateItem(q, id) { it.withStart((item.startUs + applied.toLong()).coerceAtLeast(0L)) }
            }
            is TextItem -> {
                val d = deltaUs.coerceIn(-item.startUs, item.durationUs - MIN_ITEM_US)
                updateItem(p, id) { (it as TextItem).copy(startUs = item.startUs + d, durationUs = item.durationUs - d) }
            }
            is OverlayItem -> {
                val d = deltaUs.coerceIn(-item.startUs, item.durationUs - MIN_ITEM_US)
                updateItem(p, id) { (it as OverlayItem).copy(startUs = item.startUs + d, durationUs = item.durationUs - d) }
            }
        }
    }

    /** Move a borda final do item por [deltaUs] (positivo estende, negativo encurta). */
    fun trimEndBy(p: Project, id: String, deltaUs: Long): Project {
        val (_, item) = p.findItem(id) ?: throw EditError.ItemNotFound(id)
        return when (item) {
            is MediaClip -> setSourceRange(p, id, item.sourceInUs, item.sourceOutUs + (deltaUs * item.effectiveSpeed).toLong())
            is AudioClip -> setSourceRange(p, id, item.sourceInUs, item.sourceOutUs + (deltaUs * item.speed).toLong())
            is TextItem -> updateItem(p, id) { (it as TextItem).copy(durationUs = (item.durationUs + deltaUs).coerceAtLeast(MIN_ITEM_US)) }
            is OverlayItem -> updateItem(p, id) { (it as OverlayItem).copy(durationUs = (item.durationUs + deltaUs).coerceAtLeast(MIN_ITEM_US)) }
        }
    }

    /**
     * Remove trechos (em tempo da MÍDIA DE ORIGEM) de um clipe da faixa principal, por exemplo pausas
     * detectadas. Cada trecho vira divisões + remoção, e a faixa se fecha em cascata. Trechos fora do
     * clipe são ignorados; o conjunto inteiro é uma única operação (um passo de desfazer).
     */
    fun removeSourceRanges(p: Project, clipId: String, ranges: List<TimeRange>, ids: IdGenerator): Project {
        var q = p
        for (r in ranges.sortedByDescending { it.startUs }) {
            val clip = q.findItem(clipId)?.second as? MediaClip ?: break
            val from = maxOf(r.startUs, clip.sourceInUs)
            val to = minOf(r.endUs, clip.sourceOutUs)
            if (to - from <= 0L) continue
            val startT = clip.startUs + ((from - clip.sourceInUs) / clip.effectiveSpeed).toLong()
            val endT = clip.startUs + ((to - clip.sourceInUs) / clip.effectiveSpeed).toLong()
            val splitStart = startT > clip.startUs + MIN_ITEM_US
            val splitEnd = endT < clip.endUs - MIN_ITEM_US
            if (!splitStart && !splitEnd) continue                       // o clipe inteiro seria removido
            when {
                splitStart && splitEnd -> {
                    q = splitAt(q, clipId, endT, ids.next())
                    val mid = ids.next()
                    q = splitAt(q, clipId, startT, mid)
                    q = removeItem(q, mid)
                }
                splitStart -> {                                          // silêncio até o fim do clipe
                    val tail = ids.next()
                    q = splitAt(q, clipId, startT, tail)
                    q = removeItem(q, tail)
                }
                else -> {                                                // silêncio no começo do clipe
                    val head = ids.next()
                    q = splitAt(q, clipId, endT, head)
                    q = removeItem(q, clipId)
                    return q
                }
            }
        }
        return q
    }

    /** Define a duração de uma foto na sequência. */
    fun setStillDuration(p: Project, id: String, durationUs: Long): Project = updateClip(p, id) {
        if (!it.isStill) throw EditError.WrongKind("Apenas fotos têm duração configurável")
        it.copy(sourceInUs = 0L, sourceOutUs = durationUs.coerceAtLeast(MIN_ITEM_US))
    }

    // ---------- propriedades ----------

    fun setSpeed(p: Project, id: String, speed: Float): Project {
        val s = speed.coerceIn(MIN_SPEED, MAX_SPEED)
        return when (p.findItem(id)?.second ?: throw EditError.ItemNotFound(id)) {
            is MediaClip -> updateClip(p, id) { if (it.isStill) throw EditError.WrongKind("Fotos não têm velocidade"); clampFades(it.copy(speed = s)) }
            is AudioClip -> updateAudio(p, id) { clampFades(it.copy(speed = s)) }
            else -> throw EditError.WrongKind("Este item não possui velocidade")
        }
    }

    fun setVolume(p: Project, id: String, volume: Float): Project = when (p.findItem(id)?.second ?: throw EditError.ItemNotFound(id)) {
        is MediaClip -> updateClip(p, id) { it.copy(volume = volume.coerceIn(0f, 2f)) }
        is AudioClip -> updateAudio(p, id) { it.copy(volume = volume.coerceIn(0f, 2f)) }
        else -> throw EditError.WrongKind("Este item não possui áudio")
    }

    fun setMuted(p: Project, id: String, muted: Boolean): Project = when (p.findItem(id)?.second ?: throw EditError.ItemNotFound(id)) {
        is MediaClip -> updateClip(p, id) { it.copy(muted = muted) }
        is AudioClip -> updateAudio(p, id) { it.copy(muted = muted) }
        else -> throw EditError.WrongKind("Este item não possui áudio")
    }

    fun setFades(p: Project, id: String, fadeInUs: Long, fadeOutUs: Long): Project = when (p.findItem(id)?.second ?: throw EditError.ItemNotFound(id)) {
        is MediaClip -> updateClip(p, id) { clampFades(it.copy(fadeInUs = fadeInUs.coerceAtLeast(0L), fadeOutUs = fadeOutUs.coerceAtLeast(0L))) }
        is AudioClip -> updateAudio(p, id) { clampFades(it.copy(fadeInUs = fadeInUs.coerceAtLeast(0L), fadeOutUs = fadeOutUs.coerceAtLeast(0L))) }
        else -> throw EditError.WrongKind("Este item não possui fade de áudio")
    }

    fun setTransform(p: Project, id: String, transform: ClipTransform): Project =
        updateClip(p, id) { it.copy(transform = transform.copy(rotationQuarters = Math.floorMod(transform.rotationQuarters, 4), zoom = transform.zoom.coerceIn(0.25f, 8f))) }

    fun rotateClip(p: Project, id: String, quarters: Int): Project =
        updateClip(p, id) { it.copy(transform = it.transform.copy(rotationQuarters = Math.floorMod(it.transform.rotationQuarters + quarters, 4))) }

    fun setCanvas(p: Project, canvas: CanvasSpec): Project = p.copy(canvas = canvas)

    fun renameProject(p: Project, name: String): Project = p.copy(name = name.trim().ifEmpty { p.name })

    // ---------- efeitos, transições, keyframes ----------

    fun addEffect(p: Project, clipId: String, effectId: String, type: EffectType, intensity: Float = type.defaultIntensity): Project =
        updateClip(p, clipId) { it.copy(effects = it.effects + EffectInstance(effectId, type, intensity.coerceIn(0f, 1f))) }

    fun updateEffect(p: Project, clipId: String, effectId: String, intensity: Float? = null, enabled: Boolean? = null): Project =
        updateClip(p, clipId) { c ->
            if (c.effects.none { it.id == effectId }) throw EditError.ItemNotFound(effectId)
            c.copy(effects = c.effects.map { e -> if (e.id == effectId) e.copy(intensity = intensity?.coerceIn(0f, 1f) ?: e.intensity, enabled = enabled ?: e.enabled) else e })
        }

    fun removeEffect(p: Project, clipId: String, effectId: String): Project =
        updateClip(p, clipId) { it.copy(effects = it.effects.filterNot { e -> e.id == effectId }) }

    fun setTransition(p: Project, clipId: String, spec: TransitionSpec?): Project =
        updateClip(p, clipId) { it.copy(transitionIn = spec?.copy(durationUs = spec.durationUs.coerceIn(100_000L, 3_000_000L))) }

    fun setKeyframes(p: Project, clipId: String, property: KeyframeProperty, frames: List<Keyframe>): Project =
        updateClip(p, clipId) {
            val next = if (frames.isEmpty()) it.keyframes - property else it.keyframes + (property to frames.sortedBy { f -> f.timeUs })
            it.copy(keyframes = next)
        }

    fun applyKenBurns(p: Project, clipId: String, from: Float = 1f, to: Float = 1.25f): Project =
        updateClip(p, clipId) { it.copy(keyframes = it.keyframes + Keyframes.kenBurns(it.durationUs, from, to)) }

    // ---------- textos, sobreposições e áudio ----------

    fun addText(
        p: Project, id: String, text: String, startUs: Long, durationUs: Long,
        style: TextStyleSpec = TextStyleSpec(), x: Float = 0f, y: Float = 0f,
        animIn: ItemAnimation = ItemAnimation.FADE, animOut: ItemAnimation = ItemAnimation.FADE,
        trackId: String = TRACK_TEXT,
    ): Project {
        val item = TextItem(id, startUs.coerceAtLeast(0L), durationUs.coerceAtLeast(MIN_ITEM_US), text, style, x, y, 0f, animIn, animOut)
        return addToTrack(p, trackId, TrackKind.TEXT, item)
    }

    fun updateText(p: Project, id: String, f: (TextItem) -> TextItem): Project = updateItem(p, id) {
        val t = it as? TextItem ?: throw EditError.WrongKind("O item não é um texto")
        f(t).copy(id = t.id)
    }

    fun addOverlay(p: Project, id: String, assetId: String, startUs: Long, durationUs: Long, x: Float = 0f, y: Float = 0f, scale: Float = 0.3f, trackId: String = TRACK_IMAGE): Project {
        val a = p.assets[assetId] ?: throw EditError.AssetNotFound(assetId)
        if (a.kind != MediaKind.IMAGE) throw EditError.WrongKind("Sobreposições usam imagens")
        return addToTrack(p, trackId, TrackKind.IMAGE, OverlayItem(id, assetId, startUs.coerceAtLeast(0L), durationUs.coerceAtLeast(MIN_ITEM_US), x, y, scale))
    }

    fun updateOverlay(p: Project, id: String, f: (OverlayItem) -> OverlayItem): Project = updateItem(p, id) {
        val o = it as? OverlayItem ?: throw EditError.WrongKind("O item não é uma sobreposição")
        f(o).copy(id = o.id)
    }

    fun addAudio(p: Project, id: String, assetId: String, startUs: Long, kind: TrackKind = TrackKind.MUSIC, trackId: String? = null): Project {
        val a = p.assets[assetId] ?: throw EditError.AssetNotFound(assetId)
        if (a.kind != MediaKind.AUDIO && !(a.kind == MediaKind.VIDEO && a.hasAudio)) throw EditError.WrongKind("A mídia não possui áudio")
        if (!kind.isAudio) throw EditError.WrongKind("Tipo de faixa inválido para áudio")
        if (a.durationUs <= 0L) throw EditError.Invalid("Duração do áudio desconhecida")
        val tid = trackId ?: when (kind) { TrackKind.MUSIC -> TRACK_MUSIC; TrackKind.SFX -> TRACK_SFX; else -> TRACK_VOICE }
        return addToTrack(p, tid, kind, AudioClip(id, assetId, startUs.coerceAtLeast(0L), 0L, a.durationUs))
    }

    fun addTrack(p: Project, kind: TrackKind, trackId: String, name: String): Project {
        if (p.tracks.any { it.id == trackId }) throw EditError.Invalid("Já existe uma faixa com este id")
        return p.copy(tracks = p.tracks + Track(trackId, kind, name))
    }

    fun setTrackMuted(p: Project, trackId: String, muted: Boolean): Project =
        p.copy(tracks = p.tracks.map { if (it.id == trackId) it.copy(muted = muted) else it })

    fun removeTrack(p: Project, trackId: String): Project {
        if (p.tracks.firstOrNull { it.id == trackId }?.isMain == true) throw EditError.Invalid("A faixa principal não pode ser removida")
        return p.copy(tracks = p.tracks.filterNot { it.id == trackId })
    }

    // ---------- auxiliares ----------

    private fun addToTrack(p: Project, trackId: String, kind: TrackKind, item: TimelineItem): Project {
        val existing = p.tracks.firstOrNull { it.id == trackId }
        if (existing != null && existing.kind != kind) throw EditError.WrongKind("A faixa $trackId não aceita este tipo de item")
        return if (existing == null) p.copy(tracks = p.tracks + Track(trackId, kind, trackId.removePrefix("track-"), listOf(item)))
        else p.copy(tracks = p.tracks.map { if (it.id == trackId) it.copy(items = it.items + item) else it })
    }

    private fun withMainItems(p: Project, items: List<TimelineItem>): Project =
        p.copy(tracks = p.tracks.map { if (it.isMain) it.copy(items = items) else it })

    private fun updateItem(p: Project, id: String, f: (TimelineItem) -> TimelineItem): Project {
        var found = false
        val tracks = p.tracks.map { t ->
            if (t.items.none { it.id == id }) t else { found = true; t.copy(items = t.items.map { if (it.id == id) f(it) else it }) }
        }
        if (!found) throw EditError.ItemNotFound(id)
        return p.copy(tracks = tracks)
    }

    private fun updateClip(p: Project, id: String, f: (MediaClip) -> MediaClip): Project = updateItem(p, id) {
        f(it as? MediaClip ?: throw EditError.WrongKind("O item não é um clipe de vídeo/foto"))
    }

    private fun updateAudio(p: Project, id: String, f: (AudioClip) -> AudioClip): Project = updateItem(p, id) {
        f(it as? AudioClip ?: throw EditError.WrongKind("O item não é um clipe de áudio"))
    }

    private fun clampFades(c: MediaClip): MediaClip {
        val (a, b) = fit(c.fadeInUs, c.fadeOutUs, c.durationUs); return c.copy(fadeInUs = a, fadeOutUs = b)
    }

    private fun clampFades(c: AudioClip): AudioClip {
        val (a, b) = fit(c.fadeInUs, c.fadeOutUs, c.durationUs); return c.copy(fadeInUs = a, fadeOutUs = b)
    }

    private fun clampFades(i: TimelineItem): TimelineItem = when (i) { is MediaClip -> clampFades(i); is AudioClip -> clampFades(i); else -> i }

    private fun fit(inUs: Long, outUs: Long, duration: Long): Pair<Long, Long> {
        if (inUs + outUs <= duration) return inUs to outUs
        val k = duration.toDouble() / (inUs + outUs)
        return (inUs * k).toLong() to (outUs * k).toLong()
    }
}
