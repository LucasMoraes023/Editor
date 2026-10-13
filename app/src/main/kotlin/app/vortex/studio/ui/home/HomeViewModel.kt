package app.vortex.studio.ui.home

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vortex.studio.AppContainer
import app.vortex.studio.core.edit.Edits
import app.vortex.studio.core.model.AspectPreset
import app.vortex.studio.core.model.MediaAsset
import app.vortex.studio.core.model.MediaKind
import app.vortex.studio.core.model.RandomIds
import app.vortex.studio.core.templates.TemplateSpec
import app.vortex.studio.core.templates.Templates
import app.vortex.studio.data.ProjectSummary
import app.vortex.studio.media.ImportResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(private val c: AppContainer) : ViewModel() {
    private val _projects = MutableStateFlow<List<ProjectSummary>>(emptyList())
    val projects: StateFlow<List<ProjectSummary>> = _projects.asStateFlow()
    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    val recoveredProjectId: String? = c.recoveredProjectId
    val exportWasInterrupted: Boolean = c.exportWasInterrupted

    fun refresh() { viewModelScope.launch { _projects.value = c.repository.list() } }

    fun createFromTemplate(spec: TemplateSpec, name: String, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            val p = Templates.instantiate(spec, RandomIds, name, System.currentTimeMillis())
            c.repository.save(p)
            onCreated(p.id)
        }
    }

    /** Cria um projeto já com as mídias escolhidas na galeria; o formato segue a orientação do primeiro vídeo/foto. */
    fun createFromMedia(uris: List<Uri>, name: String, onCreated: (String) -> Unit) {
        viewModelScope.launch {
            _busy.value = true
            var p = Templates.instantiate(Templates.byId("blank_reels")!!, RandomIds, name, System.currentTimeMillis())
            var first = true
            for (u in uris) {
                val r = c.importer.import(u) as? ImportResult.Ok ?: continue
                val a = r.asset
                if (a.kind == MediaKind.AUDIO) continue
                if (first) {
                    first = false
                    p = p.copy(canvas = p.canvas.copy(aspect = if (isPortrait(a)) AspectPreset.PORTRAIT_9_16 else AspectPreset.LANDSCAPE_16_9))
                }
                p = Edits.appendClip(Edits.registerAsset(p, a), RandomIds.next(), a.id).normalized()
            }
            c.repository.save(p)
            _busy.value = false
            onCreated(p.id)
        }
    }

    private fun isPortrait(a: MediaAsset): Boolean {
        val swap = a.rotationDegrees % 180 != 0
        val w = if (swap) a.height else a.width
        val h = if (swap) a.width else a.height
        return h > w
    }

    fun rename(id: String, name: String) { viewModelScope.launch { c.repository.rename(id, name); refresh() } }
    fun duplicate(id: String, name: String) { viewModelScope.launch { c.repository.duplicate(id, name); refresh() } }
    fun delete(id: String) { viewModelScope.launch { c.repository.delete(id); refresh() } }
}
