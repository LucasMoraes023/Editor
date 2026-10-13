package app.vortex.studio.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** Miniaturas com cache em memória limitado e no máximo 2 decodificações simultâneas (baixo uso de CPU/memória). */
class Thumbnails(private val context: Context) {
    private val cache = object : LruCache<String, Bitmap>(cacheBytes()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }
    private val gate = Semaphore(2)

    suspend fun video(uri: String, timeUs: Long, width: Int, height: Int): Bitmap? {
        val key = "v|$uri|${timeUs / 100_000L}|$height"
        cache.get(key)?.let { return it }
        val bmp = gate.withPermit {
            withContext(Dispatchers.IO) {
                val r = MediaMetadataRetriever()
                try {
                    r.setDataSource(context, Uri.parse(uri))
                    if (Build.VERSION.SDK_INT >= 27) {
                        r.getScaledFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC, width.coerceAtLeast(2), height.coerceAtLeast(2))
                    } else {
                        r.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)?.let { f ->
                            Bitmap.createScaledBitmap(f, width.coerceAtLeast(2), height.coerceAtLeast(2), true).also { if (it !== f) f.recycle() }
                        }
                    }
                } catch (e: Exception) {
                    null
                } finally {
                    r.release()
                }
            }
        }
        if (bmp != null) cache.put(key, bmp)
        return bmp
    }

    suspend fun image(uri: String, targetHeight: Int): Bitmap? {
        val key = "i|$uri|$targetHeight"
        cache.get(key)?.let { return it }
        val bmp = gate.withPermit {
            withContext(Dispatchers.IO) {
                try {
                    val resolver = context.contentResolver
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    resolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it, null, bounds) }
                    var sample = 1
                    while (bounds.outHeight / (sample * 2) >= targetHeight) sample *= 2
                    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                    resolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it, null, opts) }
                } catch (e: Exception) {
                    null
                }
            }
        }
        if (bmp != null) cache.put(key, bmp)
        return bmp
    }

    fun clear() = cache.evictAll()

    private fun cacheBytes(): Int = (Runtime.getRuntime().maxMemory() / 16).toInt().coerceIn(4 * 1024 * 1024, 48 * 1024 * 1024)
}
