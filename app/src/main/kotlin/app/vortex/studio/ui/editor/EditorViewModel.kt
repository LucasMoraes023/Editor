package app.vortex.studio.ui.editor

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vortex.studio.AppContainer
import app.vortex.studio.R
import app.vortex.studio.core.ai.SilenceDetector
import app.vortex.studio.core.ai.TimeRange
import app.vortex.studio.core.edit.EditOutcome
import app.vortex.studio.core.edit.Edits
import app.vortex.studio.core.edit.ProjectEditor
import app.vortex.studio.core.export.ExportPlanner
import app.vortex.studio.core.export.ExportSettings
import app.vortex.studio.core.export.PlanResult
import app.vortex.studio.core.export.RenderPlan
import app.vortex.studio.core.model.*
import app.vortex.studio.core.timeline.TimelineMath
import app.vortex.studio.media.AudioAnalyzer
import app.vortex.studio.media.ImportFailure
import app.vortex.studio.media.ImportResult
import app.vortex.studio.media.ImportWarning
import app.vortex.studio.media.MediaAvailability
import app.vortex.studio.media.Thumbnails
import app.vortex.studio.media.displayAspect
import app.vortex.studio.engine.PreviewController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class Tool { CUT, SPEED, AUDIO, EFFECTS, TEXT, IMAGE, TRANSFORM, TRANSITION }

data class UiMessage(@StringRes val res: Int, val arg: String? = null, val token: Long = System.nanoTime())

data class EditorUi(
    val loaded: Boolean = false,
    val loadFailed: Boolean = false,
    val project: Project? = null,
    val plan: RenderPlan? = null,
    val selectedId: String? = null,
    val playheadUs: Long = 0L,
    val playing: Boolean = false,
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val pxPerSec: Float = TimelineMath.DEFAULT_PX_PER_SEC,
    val tool: Tool? = null,
    val missingAssets: Set<String> = emptySet(),
    val message: UiMessage? = null,
    val busy: Boolean = false,
    val suggestions: List<TimeRange> = emptyList(),
    val suggestionClipId: String? = null,
    val playerError: Boolean = false,
) {
    val selected: TimelineItem? get() = selectedId?.let { project?.findItem(it)?.second }
}

class EditorViewModel(private val c: AppContainer, private val projectId: String) : ViewModel() {
    private val _ui = MutableStateFlow(EditorUi())
    val ui: StateFlow<EditorUi> = _ui.asStateFlow()
    private val _waveforms = MutableStateFlow<Map<String, FloatArray>>(emptyMap())
    val waveforms: StateFlow<Map<String, FloatArray>> = _waveforms.asStateFlow()

    val preview = PreviewController(c.app)
    val thumbnails: Thumbnails get() = c.thumbnails

    private var editor: ProjectEditor? = null
    private var saveJob: Job? = null
    private var thumbKey: String? = null

    init {
        preview.onPlayingChanged = { p -> _ui.update { it.copy(playing = p) } }
        preview.onEnded = { _ui.update { it.copy(playing = false) } }
        preview.onError = { _ui.update { it.copy(playerError = true, playing = false) } }
        viewModelScope.launch { load() }
        viewModelScope.launch {
            while (isActive) {
                delay(33)
                if (preview.isPlaying) {
                    val t = preview.tick()
                    _ui.update { it.copy(playheadUs = t) }
                }
            }
        }
    }

    private suspend fun load() {
        val p = c.repository.load(projectId)
        if (p == null) { _ui.update { it.copy(loaded = true, loadFailed = true) }; return }
        editor = ProjectEditor(p)
        c.repository.markSessionStart(projectId)
        publish()
        refreshMissing()
        p.assets.values.filter { it.kind == MediaKind.AUDIO }.forEach { loadWaveform(it) }
        _ui.update { it.copy(loaded = true) }
    }

    // ---------- estado, prévia e salvamento ----------

    private fun publish() {
        val ed = editor ?: return
        val p = ed.project
        // Plano "leniente" (todas as mídias consideradas disponíveis): alimenta prévia, textos e áudio.
        val plan = (ExportPlanner.plan(p, ExportSettings()) { true } as? PlanResult.Ok)?.plan
        val sel = _ui.value.selectedId?.takeIf { p.findItem(it) != null }
        _ui.update { it.copy(project = p, plan = plan, selectedId = sel, canUndo = ed.canUndo, canRedo = ed.canRedo) }
        preview.update(p, plan, c.settings.previewQuality.shortSide, c.settings.previewEffects)
    }

    private fun refreshMissing() {
        val p = editor?.project ?: return
        viewModelScope.launch {
            val missing = withContext(Dispatchers.IO) {
                p.referencedAssetIds().mapNotNull { p.assets[it] }.filter { !MediaAvailability.isReadable(c.app, it.uri) }.map { it.id }.toSet()
            }
            _ui.update { it.copy(missingAssets = missing) }
        }
    }

    private fun msg(@StringRes res: Int, arg: String? = null) { _ui.update { it.copy(message = UiMessage(res, arg)) } }
    fun messageShown() { _ui.update { it.copy(message = null) } }

    private fun edit(key: String? = null, op: (Project) -> Project): Boolean {
        val ed = editor ?: return false
        return when (val r = ed.exec("edit", key, op)) {
            is EditOutcome.Applied -> { publish(); scheduleSave(); true }
            is EditOutcome.NoChange -> false
            is EditOutcome.Failed -> { msg(R.string.msg_edit_failed, r.error.message); false }
        }
    }

    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = viewModelScope.launch { delay(600); saveNow() }
    }

    suspend fun saveNow() {
        val p = editor?.project ?: return
        try {
            c.repository.save(p)
            updateThumbnail(p)
        } catch (e: java.io.IOException) {
            msg(R.string.msg_save_failed)
        }
    }

    fun saveThen(next: () -> Unit) { viewModelScope.launch { saveNow(); next() } }

    fun pauseAndSave() {
        preview.pause()
        saveJob?.cancel()
        c.appScope.launch { saveNow() }
    }

    private suspend fun updateThumbnail(p: Project) {
        val first = p.mainTrack.items.firstOrNull() as? MediaClip ?: return
        val key = first.id + first.sourceInUs
        if (key == thumbKey && c.repository.thumbnailFile(p.id).exists()) return
        val a = p.assets[first.assetId] ?: return
        val h = 240
        val w = (h * a.displayAspect()).toInt().coerceIn(120, 480)
        val bmp = if (first.isStill) c.thumbnails.image(a.uri, h) else c.thumbnails.video(a.uri, first.sourceInUs, w, h)
        if (bmp != null) { c.repository.saveThumbnail(p.id, bmp); thumbKey = key }
    }

    override fun onCleared() {
        preview.release()
        c.appScope.launch {
            saveNow()
            c.repository.markSessionEnd()
        }
    }

    // ---------- navegação e reprodução ----------

    fun select(id: String?) { _ui.update { it.copy(selectedId = id) } }
    fun selectTool(t: Tool?) { _ui.update { it.copy(tool = if (it.tool == t) null else t) } }
    fun setZoom(px: Float) { _ui.update { it.copy(pxPerSec = TimelineMath.clampZoom(px)) } }

    fun seek(us: Long) {
        val t = us.coerceIn(0L, (editor?.project?.durationUs ?: 0L))
        preview.seekToTimeline(t)
        _ui.update { it.copy(playheadUs = t) }
    }

    fun togglePlay() {
        if (_ui.value.missingAssets.isNotEmpty()) { msg(R.string.msg_missing_media); return }
        if (preview.isPlaying) preview.pause() else { _ui.update { it.copy(playerError = false) }; preview.play() }
    }

    // ---------- importação ----------

    fun addMedia(uris: List<Uri>) {
        viewModelScope.launch {
            _ui.update { it.copy(busy = true) }
            for (u in uris) when (val r = c.importer.import(u)) {
                is ImportResult.Ok -> {
                    val a = r.asset
                    if (a.kind == MediaKind.AUDIO) addAudioAsset(a, TrackKind.MUSIC) else {
                        val id = RandomIds.next()
                        edit { Edits.appendClip(Edits.registerAsset(it, a), id, a.id) }
                    }
                    if (ImportWarning.LARGE_FILE in r.warnings) msg(R.string.msg_large_file, a.displayName)
                }
                is ImportResult.Failed -> msg(importErrorRes(r.reason), r.name)
            }
            _ui.update { it.copy(busy = false) }
            refreshMissing()
        }
    }

    fun addAudio(uris: List<Uri>, kind: TrackKind) {
        viewModelScope.launch {
            _ui.update { it.copy(busy = true) }
            for (u in uris) when (val r = c.importer.import(u, MediaKind.AUDIO)) {
                is ImportResult.Ok -> addAudioAsset(r.asset, kind)
                is ImportResult.Failed -> msg(importErrorRes(r.reason), r.name)
            }
            _ui.update { it.copy(busy = false) }
        }
    }

    private fun addAudioAsset(a: MediaAsset, kind: TrackKind) {
        val id = RandomIds.next()
        val start = _ui.value.playheadUs
        if (edit { Edits.addAudio(Edits.registerAsset(it, a), id, a.id, start, kind) }) {
            select(id)
            loadWaveform(a)
        }
    }

    fun addOverlay(uri: Uri) {
        viewModelScope.launch {
            val r = c.importer.import(uri, MediaKind.IMAGE)
            if (r is ImportResult.Ok && r.asset.kind == MediaKind.IMAGE) {
                val id = RandomIds.next()
                val start = _ui.value.playheadUs
                if (edit { Edits.addOverlay(Edits.registerAsset(it, r.asset), id, r.asset.id, start, 3 * US_PER_SEC) }) select(id)
            } else msg(R.string.msg_import_unsupported, (r as? ImportResult.Failed)?.name)
        }
    }

    @StringRes private fun importErrorRes(f: ImportFailure): Int = when (f) {
        ImportFailure.UNREADABLE -> R.string.msg_import_unreadable
        ImportFailure.UNSUPPORTED -> R.string.msg_import_unsupported
        ImportFailure.EMPTY -> R.string.msg_import_empty
    }

    /** Relocaliza uma mídia que foi movida/excluída, mantendo todas as edições do projeto. */
    fun relink(assetId: String, uri: Uri) {
        viewModelScope.launch {
            val r = c.importer.import(uri)
            if (r is ImportResult.Ok) {
                edit { Edits.relinkAsset(it, assetId, r.asset) }
                refreshMissing()
            } else msg(R.string.msg_import_unreadable, (r as? ImportResult.Failed)?.name)
        }
    }

    private fun loadWaveform(a: MediaAsset) {
        if (_waveforms.value.containsKey(a.id)) return
        viewModelScope.launch {
            val levels = AudioAnalyzer.analyze(c.app, Uri.parse(a.uri)) ?: return@launch
            _waveforms.update { it + (a.id to SilenceDetector.downsamplePeaks(levels.rms, 800)) }
        }
    }

    // ---------- cortes e organização ----------

    private fun targetId(): String? = _ui.value.selectedId ?: editor?.project?.mainClipAt(_ui.value.playheadUs)?.id

    fun split() {
        val id = targetId() ?: return
        val at = _ui.value.playheadUs
        val newId = RandomIds.next()
        edit { Edits.splitAt(it, id, at, newId) }
    }

    fun trimStartHere() { targetId()?.let { id -> val at = _ui.value.playheadUs; edit { Edits.trimStartTo(it, id, at) } } }
    fun trimEndHere() { targetId()?.let { id -> val at = _ui.value.playheadUs; edit { Edits.trimEndTo(it, id, at) } } }

    fun deleteSelected() { _ui.value.selectedId?.let { id -> if (edit { Edits.removeItem(it, id) }) select(null) } }

    fun duplicateSelected() {
        val id = _ui.value.selectedId ?: return
        val newId = RandomIds.next()
        if (edit { Edits.duplicate(it, id, newId) }) select(newId)
    }

    fun trimStartBy(id: String, deltaUs: Long) { edit("trimS:$id") { Edits.trimStartBy(it, id, deltaUs) } }
    fun trimEndBy(id: String, deltaUs: Long) { edit("trimE:$id") { Edits.trimEndBy(it, id, deltaUs) } }
    fun moveMain(id: String, toIndex: Int) { edit { Edits.moveMainClip(it, id, toIndex) } }
    fun moveFree(id: String, startUs: Long) { edit { Edits.moveItem(it, id, startUs) } }

    // ---------- propriedades ----------

    fun setSpeed(v: Float) { _ui.value.selectedId?.let { id -> edit("speed:$id") { Edits.setSpeed(it, id, v) } } }
    fun setVolume(v: Float) { _ui.value.selectedId?.let { id -> edit("vol:$id") { Edits.setVolume(it, id, v) } } }
    fun toggleMute() {
        val sel = _ui.value.selected ?: return
        val muted = when (sel) { is MediaClip -> sel.muted; is AudioClip -> sel.muted; else -> return }
        edit { Edits.setMuted(it, sel.id, !muted) }
    }
    fun setStillDuration(us: Long) { _ui.value.selectedId?.let { id -> edit("dur:$id") { Edits.setStillDuration(it, id, us) } } }
    fun setFades(inUs: Long, outUs: Long) { _ui.value.selectedId?.let { id -> edit("fade:$id") { Edits.setFades(it, id, inUs, outUs) } } }

    fun addEffect(type: EffectType) {
        val id = _ui.value.selectedId ?: return
        val eid = RandomIds.next()
        edit { Edits.addEffect(it, id, eid, type) }
    }
    fun setEffectIntensity(effectId: String, v: Float) { _ui.value.selectedId?.let { id -> edit("fx:$effectId") { Edits.updateEffect(it, id, effectId, intensity = v) } } }
    fun toggleEffect(effectId: String, on: Boolean) { _ui.value.selectedId?.let { id -> edit { Edits.updateEffect(it, id, effectId, enabled = on) } } }
    fun removeEffect(effectId: String) { _ui.value.selectedId?.let { id -> edit { Edits.removeEffect(it, id, effectId) } } }

    fun rotate() { _ui.value.selectedId?.let { id -> edit { Edits.rotateClip(it, id, 1) } } }
    fun updateTransform(f: (ClipTransform) -> ClipTransform) {
        val sel = _ui.value.selected as? MediaClip ?: return
        edit("tf:${sel.id}") { Edits.setTransform(it, sel.id, f((it.findItem(sel.id)!!.second as MediaClip).transform)) }
    }
    fun setCanvas(f: (CanvasSpec) -> CanvasSpec) { edit { Edits.setCanvas(it, f(it.canvas)) } }
    fun kenBurns() { _ui.value.selectedId?.let { id -> edit { Edits.applyKenBurns(it, id) } } }
    fun clearKeyframes() {
        _ui.value.selectedId?.let { id -> edit { p -> KeyframeProperty.values().fold(p) { acc, k -> Edits.setKeyframes(acc, id, k, emptyList()) } } }
    }
    fun setTransition(spec: TransitionSpec?) { _ui.value.selectedId?.let { id -> edit("tr:$id") { Edits.setTransition(it, id, spec) } } }

    // ---------- texto e sobreposições ----------

    fun addText(defaultText: String) {
        val id = RandomIds.next()
        val p = editor?.project ?: return
        val start = _ui.value.playheadUs
        val dur = (p.durationUs - start).coerceIn(US_PER_SEC, 3 * US_PER_SEC)
        if (edit { Edits.addText(it, id, defaultText, start, dur, y = 0.55f, animIn = ItemAnimation.FADE, animOut = ItemAnimation.FADE) }) select(id)
    }
    fun updateText(f: (TextItem) -> TextItem) { _ui.value.selectedId?.let { id -> edit("text:$id") { Edits.updateText(it, id, f) } } }
    fun updateOverlay(f: (OverlayItem) -> OverlayItem) { _ui.value.selectedId?.let { id -> edit("ov:$id") { Edits.updateOverlay(it, id, f) } } }

    /** Arrasta texto/imagem direto na prévia (deslocamento em coordenadas normalizadas). */
    fun nudge(dx: Float, dy: Float) {
        when (val sel = _ui.value.selected) {
            is TextItem -> edit("nudge:${sel.id}") { Edits.updateText(it, sel.id) { t -> t.copy(x = (t.x + dx).coerceIn(-1f, 1f), y = (t.y + dy).coerceIn(-1f, 1f)) } }
            is OverlayItem -> edit("nudge:${sel.id}") { Edits.updateOverlay(it, sel.id) { o -> o.copy(x = (o.x + dx).coerceIn(-1f, 1f), y = (o.y + dy).coerceIn(-1f, 1f)) } }
            else -> Unit
        }
    }

    // ---------- histórico ----------

    fun undo() { if (editor?.undo() == true) { publish(); scheduleSave() } }
    fun redo() { if (editor?.redo() == true) { publish(); scheduleSave() } }

    // ---------- sugestões inteligentes (locais): cortar pausas ----------

    fun suggestCuts() {
        val clip = _ui.value.selected as? MediaClip
        val asset = clip?.let { editor?.project?.assets?.get(it.assetId) }
        if (clip == null || asset == null || clip.isStill || !asset.hasAudio) { msg(R.string.msg_suggest_needs_audio_clip); return }
        viewModelScope.launch {
            _ui.update { it.copy(busy = true) }
            val levels = AudioAnalyzer.analyze(c.app, Uri.parse(asset.uri))
            val ranges = levels?.let { SilenceDetector.detect(it.rms, it.windowUs) }
                ?.filter { it.endUs > clip.sourceInUs && it.startUs < clip.sourceOutUs } ?: emptyList()
            _ui.update { it.copy(busy = false, suggestions = ranges, suggestionClipId = clip.id) }
            if (levels == null) msg(R.string.msg_analysis_failed) else if (ranges.isEmpty()) msg(R.string.msg_no_pauses)
        }
    }

    fun applySuggestions() {
        val u = _ui.value
        val clipId = u.suggestionClipId ?: return
        if (u.suggestions.isEmpty()) return
        edit { Edits.removeSourceRanges(it, clipId, u.suggestions, RandomIds) }
        _ui.update { it.copy(suggestions = emptyList(), suggestionClipId = null) }
    }

    fun dismissSuggestions() { _ui.update { it.copy(suggestions = emptyList(), suggestionClipId = null) } }
}
