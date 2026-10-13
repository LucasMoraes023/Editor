package app.vortex.studio.data

import android.content.Context
import android.graphics.Bitmap
import app.vortex.studio.core.json.ProjectJson
import app.vortex.studio.core.model.Project
import app.vortex.studio.core.model.RandomIds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

data class ProjectSummary(
    val id: String,
    val name: String,
    val modifiedAtMs: Long,
    val durationUs: Long,
    val clipCount: Int,
    val thumbnail: File?,
)

/**
 * Projetos em JSON versionado dentro do armazenamento PRIVADO do app. A gravação é atômica
 * (arquivo temporário + rename, com cópia .bak do estado anterior), então um fechamento inesperado
 * nunca deixa um projeto corrompido. Operações de projeto nunca tocam as mídias originais do usuário.
 */
class ProjectRepository(context: Context) {
    private val root = File(context.filesDir, "projects").apply { mkdirs() }
    private val sessionMarker = File(context.filesDir, "session.marker")
    private val mutex = Mutex()

    private fun dir(id: String): File = File(root, id).apply { mkdirs() }
    private fun jsonFile(id: String) = File(dir(id), "project.json")
    private fun backupFile(id: String) = File(dir(id), "project.json.bak")
    fun thumbnailFile(id: String) = File(dir(id), "thumb.jpg")

    suspend fun save(p: Project) = withContext(Dispatchers.IO) {
        mutex.withLock {
            val target = jsonFile(p.id)
            val tmp = File(target.parentFile, "project.json.tmp")
            tmp.writeText(ProjectJson.encode(p))
            if (target.exists()) target.copyTo(backupFile(p.id), overwrite = true)
            if (!tmp.renameTo(target)) {
                target.delete()
                tmp.renameTo(target)
            }
        }
    }

    /** Lê o projeto; se o arquivo principal estiver ilegível, tenta a cópia anterior (.bak). */
    suspend fun load(id: String): Project? = withContext(Dispatchers.IO) {
        runCatching { ProjectJson.decode(jsonFile(id).readText()) }.getOrElse {
            runCatching { ProjectJson.decode(backupFile(id).readText()) }.getOrNull()
        }
    }

    suspend fun list(): List<ProjectSummary> = withContext(Dispatchers.IO) {
        (root.listFiles() ?: emptyArray()).filter { it.isDirectory }.mapNotNull { d ->
            val p = runCatching { ProjectJson.decode(File(d, "project.json").readText()) }.getOrNull() ?: return@mapNotNull null
            ProjectSummary(
                p.id, p.name, p.modifiedAtMs, p.durationUs,
                p.mainTrack.items.size, File(d, "thumb.jpg").takeIf { it.exists() },
            )
        }.sortedByDescending { it.modifiedAtMs }
    }

    suspend fun delete(id: String) = withContext(Dispatchers.IO) { mutex.withLock { File(root, id).deleteRecursively() } }

    suspend fun rename(id: String, name: String) {
        val p = load(id) ?: return
        save(p.copy(name = name.trim().ifEmpty { p.name }, modifiedAtMs = System.currentTimeMillis()))
    }

    suspend fun duplicate(id: String, newName: String): Project? {
        val p = load(id) ?: return null
        val now = System.currentTimeMillis()
        val copy = p.copy(id = RandomIds.next(), name = newName, createdAtMs = now, modifiedAtMs = now)
        save(copy)
        withContext(Dispatchers.IO) { thumbnailFile(id).takeIf { it.exists() }?.copyTo(thumbnailFile(copy.id), overwrite = true) }
        return copy
    }

    suspend fun saveThumbnail(id: String, bitmap: Bitmap) = withContext(Dispatchers.IO) {
        thumbnailFile(id).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }
    }

    // ----- recuperação após fechamento inesperado -----

    fun markSessionStart(projectId: String) { runCatching { sessionMarker.writeText(projectId) } }
    fun markSessionEnd() { sessionMarker.delete() }

    /** Se o app foi encerrado durante uma edição, devolve o id do projeto (e limpa a marca). */
    fun consumeCrashedSession(): String? {
        if (!sessionMarker.exists()) return null
        val id = runCatching { sessionMarker.readText().trim() }.getOrNull()
        sessionMarker.delete()
        return id?.takeIf { it.isNotEmpty() && File(root, it).exists() }
    }
}
