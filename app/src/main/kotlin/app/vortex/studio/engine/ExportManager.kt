@file:OptIn(UnstableApi::class)

package app.vortex.studio.engine

import android.Manifest
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.SystemClock
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import app.vortex.studio.R
import app.vortex.studio.core.export.ExportIssue
import app.vortex.studio.core.export.ExportIssueCode
import app.vortex.studio.core.export.ExportMath
import app.vortex.studio.core.export.ExportPlanner
import app.vortex.studio.core.export.ExportSettings
import app.vortex.studio.core.export.PlanResult
import app.vortex.studio.core.model.Project
import app.vortex.studio.media.MediaAvailability
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

enum class FailureCode { EMPTY_PROJECT, MISSING_MEDIA, INSUFFICIENT_STORAGE, UNSUPPORTED_FORMAT, ENCODER_FAILED, IO_ERROR, OUT_OF_MEMORY, UNKNOWN }

sealed interface ExportState {
    data object Idle : ExportState
    data class Preparing(val projectName: String) : ExportState
    data class Running(val projectName: String, val progress: Int, val startedAtMs: Long) : ExportState
    data class Success(val uri: Uri?, val displayName: String, val sizeBytes: Long, val tookMs: Long, val warnings: List<ExportIssue>) : ExportState
    data class Failed(val code: FailureCode, val detail: String?) : ExportState
    data object Cancelled : ExportState
}

/**
 * Exporta o projeto de verdade: planeja (núcleo testado), monta a Composition, executa o Transformer
 * (H.264 + AAC em MP4), publica na galeria e avisa ao concluir. O progresso é o informado pelo
 * Transformer (nunca simulado); falhas e cancelamentos limpam o arquivo temporário.
 */
class ExportManager(private val app: Application, private val scope: CoroutineScope) {
    private val _state = MutableStateFlow<ExportState>(ExportState.Idle)
    val state: StateFlow<ExportState> = _state.asStateFlow()

    private val exportsDir = File(app.cacheDir, "exports")
    private val marker = File(exportsDir, "RUNNING")
    private var transformer: Transformer? = null
    private var pending: CompletableDeferred<Result<ExportResult>>? = null
    private var tempFile: File? = null
    private var job: Job? = null

    val isBusy: Boolean get() = _state.value is ExportState.Preparing || _state.value is ExportState.Running

    /** Detecta exportação interrompida (app encerrado no meio) e limpa restos. Retorna true se houve interrupção. */
    fun recoverInterrupted(): Boolean {
        val interrupted = marker.exists()
        exportsDir.listFiles()?.forEach { it.delete() }
        return interrupted
    }

    fun start(project: Project, settings: ExportSettings) {
        if (isBusy) return
        job = scope.launch(Dispatchers.Main) { run(project, settings) }
    }

    fun cancel() {
        val t = transformer
        if (t != null && isBusy) {
            t.cancel()
            pending?.complete(Result.failure(CancellationException("cancelado pelo usuário")))
        } else if (isBusy) {
            job?.cancel()
        }
    }

    fun reset() { if (!isBusy) _state.value = ExportState.Idle }

    private suspend fun run(project: Project, settings: ExportSettings) {
        val startedAt = SystemClock.elapsedRealtime()
        _state.value = ExportState.Preparing(project.name)
        exportsDir.mkdirs()
        try {
            val planResult = withContext(Dispatchers.IO) {
                ExportPlanner.plan(project, settings) { MediaAvailability.isReadable(app, it.uri) }
            }
            val ok = when (planResult) {
                is PlanResult.Failed -> {
                    val first = planResult.issues.first()
                    _state.value = if (first.code == ExportIssueCode.EMPTY_PROJECT) ExportState.Failed(FailureCode.EMPTY_PROJECT, null)
                    else ExportState.Failed(FailureCode.MISSING_MEDIA, planResult.issues.mapNotNull { it.detail }.distinct().joinToString())
                    return
                }
                is PlanResult.Ok -> planResult
            }
            val plan = ok.plan

            // O arquivo temporário e a cópia final coexistem por instantes: exige espaço para ambos.
            if (!ExportMath.hasEnoughStorage(plan.estimatedBytes * 2, StatFs(exportsDir.path).availableBytes)) {
                _state.value = ExportState.Failed(FailureCode.INSUFFICIENT_STORAGE, null)
                return
            }

            val overlays = withContext(Dispatchers.IO) { OverlayFactory.build(app, plan) }
            val composition: Composition = CompositionFactory.build(plan, overlays)
            val out = File(exportsDir, "vortex_${System.currentTimeMillis()}.mp4").also { tempFile = it }
            marker.writeText(project.id)

            val done = CompletableDeferred<Result<ExportResult>>().also { pending = it }
            val listener = object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    done.complete(Result.success(exportResult))
                }

                override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                    done.complete(Result.failure(exportException))
                }
            }
            val encoderFactory = DefaultEncoderFactory.Builder(app)
                .setRequestedVideoEncoderSettings(VideoEncoderSettings.Builder().setBitrate(plan.videoBitrate).build())
                .setEnableFallback(true)
                .build()
            val t = Transformer.Builder(app)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .setEncoderFactory(encoderFactory)
                .addListener(listener)
                .build()
            transformer = t

            _state.value = ExportState.Running(project.name, 0, startedAt)
            t.start(composition, out.absolutePath)

            val holder = ProgressHolder()
            while (!done.isCompleted) {
                delay(250)
                if (t.getProgress(holder) == Transformer.PROGRESS_STATE_AVAILABLE) {
                    _state.value = ExportState.Running(project.name, holder.progress.coerceIn(0, 99), startedAt)
                }
            }
            val result = done.await()
            result.fold(
                onSuccess = {
                    val name = "Vortex_${project.name.filter { c -> c.isLetterOrDigit() }.take(24)}_${System.currentTimeMillis()}.mp4"
                    val size = out.length()
                    val uri = withContext(Dispatchers.IO) { publish(out, name) }
                    _state.value = ExportState.Success(uri, name, size, SystemClock.elapsedRealtime() - startedAt, ok.warnings)
                    notify(uri, success = true)
                },
                onFailure = { e ->
                    if (e is CancellationException) {
                        _state.value = ExportState.Cancelled
                    } else {
                        _state.value = ExportState.Failed(classify(e), (e as? ExportException)?.let { ExportException.getErrorCodeName(it.errorCode) } ?: e.message)
                        notify(null, success = false)
                    }
                },
            )
        } catch (e: CancellationException) {
            _state.value = ExportState.Cancelled
        } catch (e: OutOfMemoryError) {
            _state.value = ExportState.Failed(FailureCode.OUT_OF_MEMORY, null)
        } catch (e: IOException) {
            _state.value = ExportState.Failed(if (e.message?.contains("ENOSPC") == true) FailureCode.INSUFFICIENT_STORAGE else FailureCode.IO_ERROR, e.message)
        } catch (e: Exception) {
            _state.value = ExportState.Failed(FailureCode.UNKNOWN, e.message)
        } finally {
            transformer = null
            pending = null
            tempFile?.delete()
            tempFile = null
            marker.delete()
        }
    }

    private fun classify(e: Throwable): FailureCode {
        val name = (e as? ExportException)?.let { ExportException.getErrorCodeName(it.errorCode) } ?: return FailureCode.UNKNOWN
        return when {
            name.contains("FILE_NOT_FOUND") || name.contains("NO_PERMISSION") -> FailureCode.MISSING_MEDIA
            name.contains("DECOD") || name.contains("PARSING") || name.contains("FORMAT") -> FailureCode.UNSUPPORTED_FORMAT
            name.contains("ENCOD") || name.contains("MUXING") -> FailureCode.ENCODER_FAILED
            name.contains("IO_") -> FailureCode.IO_ERROR
            else -> FailureCode.UNKNOWN
        }
    }

    /** Publica o MP4 na galeria (MediaStore, pasta Movies) no Android 10+; antes disso usa a pasta do app. */
    private fun publish(src: File, displayName: String): Uri? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/Vortex Video Studio")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val resolver = app.contentResolver
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: throw IOException("MediaStore recusou a criação do arquivo")
            try {
                (resolver.openOutputStream(uri) ?: throw IOException("sem fluxo de saída")).use { o -> src.inputStream().use { it.copyTo(o) } }
                values.clear()
                values.put(MediaStore.Video.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                return uri
            } catch (e: Exception) {
                resolver.delete(uri, null, null)
                throw e
            }
        }
        val dir = File(app.getExternalFilesDir(Environment.DIRECTORY_MOVIES), "Vortex").apply { mkdirs() }
        val dest = File(dir, displayName)
        src.copyTo(dest, overwrite = true)
        return FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", dest)
    }

    private fun notify(uri: Uri?, success: Boolean) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = app.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(NotificationChannel(CHANNEL, app.getString(R.string.notif_channel_export), NotificationManager.IMPORTANCE_DEFAULT))
        }
        val builder = NotificationCompat.Builder(app, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_vortex)
            .setContentTitle(app.getString(if (success) R.string.notif_export_done_title else R.string.notif_export_failed_title))
            .setContentText(app.getString(if (success) R.string.notif_export_done_text else R.string.notif_export_failed_text))
            .setAutoCancel(true)
        if (success && uri != null) {
            val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "video/mp4").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            builder.setContentIntent(PendingIntent.getActivity(app, 0, view, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT))
        }
        NotificationManagerCompat.from(app).notify(NOTIF_ID, builder.build())
    }

    private companion object {
        const val CHANNEL = "export"
        const val NOTIF_ID = 1001
    }
}
