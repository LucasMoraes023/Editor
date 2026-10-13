package app.vortex.studio

import android.app.Application
import app.vortex.studio.data.AppSettings
import app.vortex.studio.data.ProjectRepository
import app.vortex.studio.engine.ExportManager
import app.vortex.studio.media.MediaImporter
import app.vortex.studio.media.Thumbnails
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Contêiner simples de dependências (sem framework de injeção) compartilhado por todo o app. */
class AppContainer(val app: Application) {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val settings = AppSettings(app)
    val repository = ProjectRepository(app)
    val importer = MediaImporter(app)
    val thumbnails = Thumbnails(app)
    val exportManager = ExportManager(app, appScope)

    /** Estado do último encerramento: lido uma vez na inicialização para avisar o usuário na tela inicial. */
    val recoveredProjectId: String? = repository.consumeCrashedSession()
    val exportWasInterrupted: Boolean = exportManager.recoverInterrupted()
}

class VortexApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
