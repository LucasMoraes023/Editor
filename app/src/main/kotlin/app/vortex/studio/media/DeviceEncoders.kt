package app.vortex.studio.media

import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.StatFs
import app.vortex.studio.core.export.DeviceEncodeCaps
import java.io.File

object DeviceEncoders {
    /** Consulta os codificadores H.264 do aparelho: só habilita resolução/fps que ele realmente codifica. */
    fun h264Caps(context: Context): DeviceEncodeCaps {
        val infos: List<MediaCodecInfo> = try {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { info ->
                info.isEncoder && info.supportedTypes.any { it.equals(MediaFormat.MIMETYPE_VIDEO_AVC, ignoreCase = true) }
            }
        } catch (e: Exception) {
            emptyList()
        }
        val supports: (Int, Int, Int) -> Boolean = { w, h, fps ->
            infos.any { info ->
                try {
                    val vc = info.getCapabilitiesForType(MediaFormat.MIMETYPE_VIDEO_AVC).videoCapabilities
                    vc != null && vc.isSizeSupported(w, h) && vc.areSizeAndRateSupported(w, h, fps.toDouble())
                } catch (e: Exception) {
                    false
                }
            }
        }
        return DeviceEncodeCaps(supports, freeBytes(context))
    }

    fun freeBytes(context: Context): Long = try {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        StatFs(dir.path).availableBytes
    } catch (e: Exception) {
        Long.MAX_VALUE
    }
}
