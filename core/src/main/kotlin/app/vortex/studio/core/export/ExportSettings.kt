package app.vortex.studio.core.export

import app.vortex.studio.core.model.AspectPreset
import app.vortex.studio.core.model.US_PER_SEC

enum class ExportResolution(val shortSide: Int) { P720(720), P1080(1080), P2160(2160) }

/** [bitsPerPixel] = bits por pixel por quadro, usado para estimar a taxa de bits do H.264. */
enum class ExportQuality(val bitsPerPixel: Double) { LOW(0.06), MEDIUM(0.09), HIGH(0.14) }

data class ExportSettings(
    val resolution: ExportResolution = ExportResolution.P1080,
    val fps: Int = 30,
    val quality: ExportQuality = ExportQuality.MEDIUM,
)

enum class DisabledReason { UNSUPPORTED_BY_DEVICE, INSUFFICIENT_STORAGE }

data class ExportOption<T>(val value: T, val enabled: Boolean, val reason: DisabledReason? = null)

/** Capacidade de codificação H.264 do aparelho, consultada pela camada Android (MediaCodecList). */
class DeviceEncodeCaps(
    val supports: (width: Int, height: Int, fps: Int) -> Boolean,
    val freeBytes: Long = Long.MAX_VALUE,
)

object ExportMath {
    const val AUDIO_BITRATE = 128_000
    const val MIN_VIDEO_BITRATE = 1_500_000
    const val MAX_VIDEO_BITRATE = 80_000_000

    fun outputSize(aspect: AspectPreset, resolution: ExportResolution): Pair<Int, Int> = aspect.sizeForShortSide(resolution.shortSide)

    fun videoBitrate(width: Int, height: Int, fps: Int, quality: ExportQuality): Int =
        (width.toDouble() * height * fps * quality.bitsPerPixel).toLong().coerceIn(MIN_VIDEO_BITRATE.toLong(), MAX_VIDEO_BITRATE.toLong()).toInt()

    /** Estimativa do tamanho do arquivo (vídeo + áudio AAC), sem contar o contêiner. */
    fun estimateBytes(durationUs: Long, videoBitrate: Int): Long =
        ((videoBitrate + AUDIO_BITRATE).toDouble() * durationUs / US_PER_SEC / 8.0).toLong()

    /** Exige 20% de folga sobre a estimativa, pois a taxa de bits real varia. */
    fun hasEnoughStorage(estimateBytes: Long, freeBytes: Long): Boolean = freeBytes >= (estimateBytes * 1.2).toLong()

    fun resolutionOptions(aspect: AspectPreset, fps: Int, caps: DeviceEncodeCaps): List<ExportOption<ExportResolution>> =
        ExportResolution.values().map { r ->
            val (w, h) = outputSize(aspect, r)
            val ok = caps.supports(w, h, fps)
            ExportOption(r, ok, if (ok) null else DisabledReason.UNSUPPORTED_BY_DEVICE)
        }

    fun fpsOptions(aspect: AspectPreset, resolution: ExportResolution, caps: DeviceEncodeCaps): List<ExportOption<Int>> {
        val (w, h) = outputSize(aspect, resolution)
        return listOf(30, 60).map { f ->
            val ok = caps.supports(w, h, f)
            ExportOption(f, ok, if (ok) null else DisabledReason.UNSUPPORTED_BY_DEVICE)
        }
    }
}
