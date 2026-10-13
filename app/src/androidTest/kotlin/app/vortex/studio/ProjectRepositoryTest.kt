package app.vortex.studio

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.vortex.studio.core.edit.Edits
import app.vortex.studio.core.model.*
import app.vortex.studio.core.templates.Templates
import app.vortex.studio.data.ProjectRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Salvamento, restauração, recuperação e duplicação de projetos no armazenamento real do aparelho. */
@RunWith(AndroidJUnit4::class)
class ProjectRepositoryTest {
    private lateinit var context: Context
    private lateinit var repo: ProjectRepository
    private val created = mutableListOf<String>()

    @Before fun setUp() { context = ApplicationProvider.getApplicationContext(); repo = ProjectRepository(context) }
    @After fun tearDown() = runBlocking { created.forEach { repo.delete(it) } }

    private fun sample(): Project {
        var p = Templates.instantiate(Templates.byId("social_reels_title")!!, RandomIds, "Teste instrumentado", 1_000L)
        val asset = MediaAsset("a1", "content://x/1", "v.mp4", MediaKind.VIDEO, 5_000_000L, 1080, 1920, 0, "video/mp4", 10L, true, 30f)
        p = Edits.appendClip(Edits.registerAsset(p, asset), "c1", "a1")
        created += p.id
        return p
    }

    @Test fun saveAndLoad_restoresTimelineExactly() = runBlocking {
        val p = sample().normalized()
        repo.save(p)
        assertEquals(p, repo.load(p.id))
        assertTrue(repo.list().any { it.id == p.id })
    }

    @Test fun corruptedMainFile_recoversFromBackup() = runBlocking {
        val p = sample()
        repo.save(p)
        repo.save(p.copy(name = "segunda versão"))                     // cria a cópia .bak da primeira
        File(File(context.filesDir, "projects"), p.id).resolve("project.json").writeText("{corrompido")
        assertEquals("Teste instrumentado", repo.load(p.id)?.name)
    }

    @Test fun duplicate_andDelete_keepOtherProjectsIntact() = runBlocking {
        val p = sample(); repo.save(p)
        val copy = repo.duplicate(p.id, "cópia")!!.also { created += it.id }
        assertNotEquals(p.id, copy.id)
        repo.delete(copy.id)
        assertNotNull(repo.load(p.id))
        assertNull(repo.load(copy.id))
    }

    @Test fun crashedSession_isReportedOnlyOnce() = runBlocking {
        val p = sample(); repo.save(p)
        repo.markSessionStart(p.id)
        assertEquals(p.id, repo.consumeCrashedSession())
        assertNull(repo.consumeCrashedSession())
        repo.markSessionStart(p.id); repo.markSessionEnd()
        assertNull(repo.consumeCrashedSession())
    }

    @Test fun invalidProjectFile_isNotListedNorCrashes() = runBlocking {
        val dir = File(context.filesDir, "projects/invalido-teste").apply { mkdirs() }
        File(dir, "project.json").writeText("não é json")
        assertNull(repo.load("invalido-teste"))
        assertTrue(repo.list().none { it.id == "invalido-teste" })
        repo.delete("invalido-teste")
    }
}
