package app.vortex.studio.media

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import app.vortex.studio.core.model.MediaAsset
import app.vortex.studio.core.model.MediaKind
import app.vortex.studio.core.model.RandomIds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class ImportFailure { UNREADABLE, UNSUPPORTED, EMPTY }
enum class ImportWarning { LARGE_FILE, NO_AUDIO_TRACK }

sealed class ImportResult {
    data class Ok(val asset: MediaAsset, val warnings: List<ImportWarning>) : ImportResult()
    data class Failed(val reason: ImportFailure, val name: String?) : ImportResult()
}

/**
 * Importa mídia por URI (Photo Picker / SAF), sem depender de caminhos de arquivo. Lê duração, resolução,
 * rotação, formato e áudio dos metadados; nada é copiado nem enviado para fora do aparelho.
 */
class MediaImporter(private val context: Context) {
    suspend fun import(uri: Uri, kindHint: MediaKind? = null): ImportResult = withContext(Dispatchers.IO) {
        val resolver = context.contentResolver
        try {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) {
            // Nem todo provedor permite permissão persistente; a verificação de disponibilidade cobre o resto.
        }
        var name: String? = null
        var size = 0L
        try {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    name = c.getString(0)
                    size = if (c.isNull(1)) 0L else c.getLong(1)
                }
            }
        } catch (_: Exception) {
        }
        val mime = resolver.getType(uri)
        val kind = when {
            mime?.startsWith("video/") == true -> MediaKind.VIDEO
            mime?.startsWith("image/") == true -> MediaKind.IMAGE
            mime?.startsWith("audio/") == true -> MediaKind.AUDIO
            kindHint != null -> kindHint
            else -> return@withContext ImportResult.Failed(ImportFailure.UNSUPPORTED, name)
        }
        val display = name ?: uri.lastPathSegment ?: "mídia"
        val warnings = ArrayList<ImportWarning>()
        if (size > LARGE_FILE_BYTES) warnings.add(ImportWarning.LARGE_FILE)

        when (kind) {
            MediaKind.IMAGE -> {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                try {
                    resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
                } catch (e: Exception) {
                    return@withContext ImportResult.Failed(ImportFailure.UNREADABLE, display)
                }
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext ImportResult.Failed(ImportFailure.UNSUPPORTED, display)
                ImportResult.Ok(MediaAsset(RandomIds.next(), uri.toString(), display, kind, 0L, bounds.outWidth, bounds.outHeight, 0, mime, size), warnings)
            }
            else -> {
                val r = MediaMetadataRetriever()
                try {
                    r.setDataSource(context, uri)
                    val durationMs = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    if (durationMs <= 0L) return@withContext ImportResult.Failed(ImportFailure.EMPTY, display)
                    val hasAudio = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes"
                    if (kind == MediaKind.AUDIO && !hasAudio) return@withContext ImportResult.Failed(ImportFailure.UNSUPPORTED, display)
                    val w = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
                    val h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
                    val rot = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
                    val fps = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)?.toFloatOrNull() ?: 0f
                    if (kind == MediaKind.VIDEO && (w <= 0 || h <= 0)) return@withContext ImportResult.Failed(ImportFailure.UNSUPPORTED, display)
                    ImportResult.Ok(MediaAsset(RandomIds.next(), uri.toString(), display, kind, durationMs * 1000L, w, h, rot, mime, size, hasAudio, fps), warnings)
                } catch (e: Exception) {
                    ImportResult.Failed(ImportFailure.UNREADABLE, display)
                } finally {
                    r.release()
                }
            }
        }
    }

    private companion object {
        const val LARGE_FILE_BYTES = 2L * 1024 * 1024 * 1024
    }
}
