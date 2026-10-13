package app.vortex.studio.media

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteOrder
import kotlin.math.sqrt

/** Decodifica o áudio localmente (MediaCodec) e devolve níveis RMS por janela: forma de onda e detecção de pausas. */
object AudioAnalyzer {
    class Levels(val rms: FloatArray, val windowUs: Long)

    suspend fun analyze(context: Context, uri: Uri, windowMs: Int = 50, maxMinutes: Int = 20): Levels? =
        withContext(Dispatchers.IO) { runCatching { decode(context, uri, windowMs, maxMinutes) }.getOrNull() }

    private fun decode(context: Context, uri: Uri, windowMs: Int, maxMinutes: Int): Levels? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(context, uri, null)
            var track = -1
            for (i in 0 until extractor.trackCount) {
                if (extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) { track = i; break }
            }
            if (track < 0) return null
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            var sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            var channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            var windowSamples = (sampleRate.toLong() * channels * windowMs / 1000L).toInt().coerceAtLeast(1)
            val levels = ArrayList<Float>()
            var acc = 0.0
            var count = 0
            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            val maxWindows = maxMinutes * 60_000 / windowMs
            while (!outputDone && levels.size < maxWindows) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(10_000)
                    if (inIdx >= 0) {
                        val buf = codec.getInputBuffer(inIdx) ?: break
                        val n = extractor.readSampleData(buf, 0)
                        if (n < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, n, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }
                val outIdx = codec.dequeueOutputBuffer(info, 10_000)
                when {
                    outIdx >= 0 -> {
                        val out = codec.getOutputBuffer(outIdx)
                        if (out != null && info.size > 0) {
                            out.position(info.offset)
                            out.limit(info.offset + info.size)
                            val shorts = out.order(ByteOrder.nativeOrder()).asShortBuffer()
                            while (shorts.hasRemaining()) {
                                val v = shorts.get() / 32768.0
                                acc += v * v
                                count++
                                if (count >= windowSamples) {
                                    levels.add(sqrt(acc / count).toFloat())
                                    acc = 0.0
                                    count = 0
                                }
                            }
                        }
                        codec.releaseOutputBuffer(outIdx, false)
                        if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputDone = true
                    }
                    outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        val nf = codec.outputFormat
                        sampleRate = nf.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        channels = nf.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
                        windowSamples = (sampleRate.toLong() * channels * windowMs / 1000L).toInt().coerceAtLeast(1)
                    }
                }
            }
            return Levels(levels.toFloatArray(), windowMs * 1000L)
        } finally {
            try { codec?.stop() } catch (_: Exception) {}
            try { codec?.release() } catch (_: Exception) {}
            extractor.release()
        }
    }
}
