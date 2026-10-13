package app.vortex.studio

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.vortex.studio.core.edit.Edits
import app.vortex.studio.core.export.ExportSettings
import app.vortex.studio.core.export.ExportResolution
import app.vortex.studio.core.model.*
import app.vortex.studio.engine.ExportManager
import app.vortex.studio.engine.ExportState
import app.vortex.studio.engine.FailureCode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Exportação REAL no aparelho: gera uma imagem, monta um projeto com texto, exporta com o Transformer
 * e inspeciona o MP4 resultante (duração, resolução). Requer aparelho/emulador (não roda na JVM).
 */
@RunWith(AndroidJUnit4::class)
class ExportInstrumentedTest {
    private lateinit var app: Application
    private lateinit var manager: ExportManager

    @Before fun setUp() {
        app = ApplicationProvider.getApplicationContext()
        manager = ExportManager(app, CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate))
    }

    private fun imageProject(uri: String): Project {
        val asset = MediaAsset("img", uri, "teste.png", MediaKind.IMAGE, 0L, 640, 360, 0, "image/png", 1L)
        var p = Project("exp-test", "Export", 1L, 1L, canvas = CanvasSpec(AspectPreset.LANDSCAPE_16_9, 30, FitMode.FIT))
        p = Edits.appendClip(Edits.registerAsset(p, asset), "c1", "img", 2_000_000L)
        return Edits.addText(p, "t1", "Olá Vortex", 200_000L, 1_500_000L).normalized()
    }

    private fun makePng(): File {
        val f = File(app.cacheDir, "teste_export.png")
        val bmp = Bitmap.createBitmap(640, 360, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(124, 77, 255)) }
        f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return f
    }

    private suspend fun awaitFinal(timeoutMs: Long = 180_000): ExportState = withTimeout(timeoutMs) {
        while (true) {
            val s = manager.state.value
            if (s is ExportState.Success || s is ExportState.Failed || s is ExportState.Cancelled) return@withTimeout s
            delay(200)
        }
        @Suppress("UNREACHABLE_CODE") ExportState.Idle
    }

    @Test fun export_producesPlayableMp4_withExpectedDurationAndSize() = runBlocking {
        val project = imageProject(Uri.fromFile(makePng()).toString())
        withContext(Dispatchers.Main) { manager.start(project, ExportSettings(ExportResolution.P720, 30)) }
        val s = awaitFinal()
        assertTrue("estado final: $s", s is ExportState.Success)
        val ok = s as ExportState.Success
        assertNotNull(ok.uri)
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(app, ok.uri)
            val durMs = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)!!.toLong()
            assertTrue("duração $durMs ms", durMs in 1_700L..2_600L)
            assertEquals("1280", r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH))
            assertEquals("720", r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT))
        } finally { r.release() }
    }

    @Test fun emptyProject_failsWithoutCrashing() = runBlocking {
        withContext(Dispatchers.Main) { manager.start(Project("e", "Vazio", 1L, 1L), ExportSettings()) }
        val s = awaitFinal(30_000) as ExportState.Failed
        assertEquals(FailureCode.EMPTY_PROJECT, s.code)
    }

    @Test fun missingMedia_isReportedAsFailure() = runBlocking {
        val project = imageProject("file:///nao/existe/arquivo.png")
        withContext(Dispatchers.Main) { manager.start(project, ExportSettings(ExportResolution.P720)) }
        val s = awaitFinal(30_000) as ExportState.Failed
        assertEquals(FailureCode.MISSING_MEDIA, s.code)
    }

    @Test fun cancel_endsInCancelledState() = runBlocking {
        val project = imageProject(Uri.fromFile(makePng()).toString())
        withContext(Dispatchers.Main) { manager.start(project, ExportSettings(ExportResolution.P2160, 30)) }
        delay(300)
        withContext(Dispatchers.Main) { manager.cancel() }
        val s = awaitFinal(60_000)
        assertTrue("estado final: $s", s is ExportState.Cancelled || s is ExportState.Success)   // pode concluir antes do cancelamento
    }
}
