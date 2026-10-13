package app.vortex.studio.ui.export

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.vortex.studio.AppContainer
import app.vortex.studio.core.export.DeviceEncodeCaps
import app.vortex.studio.core.export.ExportMath
import app.vortex.studio.core.export.ExportResolution
import app.vortex.studio.core.export.ExportSettings
import app.vortex.studio.core.model.Project
import app.vortex.studio.engine.ExportState
import app.vortex.studio.media.DeviceEncoders
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ExportViewModel(private val c: AppContainer, private val projectId: String) : ViewModel() {
    private val _project = MutableStateFlow<Project?>(null)
    val project: StateFlow<Project?> = _project.asStateFlow()
    private val _settings = MutableStateFlow(ExportSettings(c.settings.defaultResolution, c.settings.defaultFps, c.settings.defaultQuality))
    val settings: StateFlow<ExportSettings> = _settings.asStateFlow()
    private val _caps = MutableStateFlow(DeviceEncodeCaps({ _, _, _ -> true }))
    val caps: StateFlow<DeviceEncodeCaps> = _caps.asStateFlow()
    val state: StateFlow<ExportState> = c.exportManager.state

    init {
        c.exportManager.reset()
        viewModelScope.launch {
            val p = c.repository.load(projectId)
            val caps = withContext(Dispatchers.Default) { DeviceEncoders.h264Caps(c.app) }
            _caps.value = caps
            _project.value = p
            if (p != null) _settings.update { fit(it, p, caps) }
        }
    }

    /** Só mantém opções que o aparelho realmente codifica: rebaixa resolução/fps se necessário. */
    private fun fit(s: ExportSettings, p: Project, caps: DeviceEncodeCaps): ExportSettings {
        var res = s.resolution
        while (!caps.supports(ExportMath.outputSize(p.canvas.aspect, res).first, ExportMath.outputSize(p.canvas.aspect, res).second, 30) && res != ExportResolution.P720) {
            res = if (res == ExportResolution.P2160) ExportResolution.P1080 else ExportResolution.P720
        }
        val (w, h) = ExportMath.outputSize(p.canvas.aspect, res)
        val fps = if (caps.supports(w, h, s.fps)) s.fps else 30
        return s.copy(resolution = res, fps = fps)
    }

    fun update(f: (ExportSettings) -> ExportSettings) { _settings.update(f) }

    fun start() {
        val p = _project.value ?: return
        c.exportManager.start(p, _settings.value)
    }

    fun cancel() = c.exportManager.cancel()
}
