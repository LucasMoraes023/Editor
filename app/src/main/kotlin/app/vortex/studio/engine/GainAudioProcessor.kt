@file:OptIn(UnstableApi::class)

package app.vortex.studio.engine

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer

/**
 * Aplica volume (0..2) e fades de entrada/saída em PCM 16 bits. É executado depois da mudança de
 * velocidade do Transformer, então [totalDurationUs] e os fades estão em tempo de linha do tempo.
 */
class GainAudioProcessor(
    private val volume: Float,
    private val fadeInUs: Long,
    private val fadeOutUs: Long,
    private val totalDurationUs: Long,
) : BaseAudioProcessor() {

    private var framesSeen = 0L

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val size = inputBuffer.remaining()
        if (size == 0) return
        val out = replaceOutputBuffer(size)
        val channels = inputAudioFormat.channelCount
        val rate = inputAudioFormat.sampleRate.toLong()
        val totalFrames = totalDurationUs * rate / 1_000_000L
        val inFrames = fadeInUs * rate / 1_000_000L
        val outFrames = fadeOutUs * rate / 1_000_000L
        while (inputBuffer.remaining() >= 2 * channels) {
            var g = volume
            if (inFrames > 0 && framesSeen < inFrames) g *= framesSeen.toFloat() / inFrames
            if (outFrames > 0) {
                val left = totalFrames - framesSeen
                if (left < outFrames) g *= left.coerceAtLeast(0L).toFloat() / outFrames
            }
            for (c in 0 until channels) {
                val sample = inputBuffer.short.toInt()
                out.putShort((sample * g).toInt().coerceIn(-32768, 32767).toShort())
            }
            framesSeen += 1
        }
        out.flip()
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        framesSeen = 0L
    }
}
