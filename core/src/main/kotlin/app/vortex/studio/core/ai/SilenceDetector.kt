package app.vortex.studio.core.ai

import kotlin.math.log10

data class TimeRange(val startUs: Long, val endUs: Long) { val durationUs: Long get() = endUs - startUs }

/**
 * Detecção local de pausas a partir de níveis RMS (0..1) por janela. Algoritmo determinístico, sem rede
 * e sem modelo externo; os níveis vêm da decodificação do áudio feita pela camada Android.
 */
object SilenceDetector {
    fun detect(
        rms: FloatArray,
        windowUs: Long,
        thresholdDb: Float = -40f,
        minSilenceUs: Long = 500_000L,
        keepPadUs: Long = 100_000L,
    ): List<TimeRange> {
        require(windowUs > 0) { "windowUs deve ser positivo" }
        val out = mutableListOf<TimeRange>()
        var runStart = -1
        fun close(endIdx: Int) {
            if (runStart < 0) return
            val s = runStart * windowUs
            val e = endIdx * windowUs
            if (e - s >= minSilenceUs) {
                val ps = s + keepPadUs
                val pe = e - keepPadUs
                if (pe - ps > 0) out += TimeRange(ps, pe)
            }
            runStart = -1
        }
        for (i in rms.indices) {
            val db = 20f * log10(maxOf(rms[i], 1e-9f))
            if (db < thresholdDb) { if (runStart < 0) runStart = i } else close(i)
        }
        close(rms.size)
        return out
    }

    /** Reduz picos para [buckets] barras (máximo por balde), para desenhar a forma de onda. */
    fun downsamplePeaks(peaks: FloatArray, buckets: Int): FloatArray {
        if (peaks.isEmpty() || buckets <= 0) return FloatArray(0)
        if (peaks.size <= buckets) return peaks.copyOf()
        return FloatArray(buckets) { b ->
            val from = (b.toLong() * peaks.size / buckets).toInt()
            val to = maxOf(from + 1, ((b + 1).toLong() * peaks.size / buckets).toInt())
            var m = 0f
            for (i in from until minOf(to, peaks.size)) m = maxOf(m, peaks[i])
            m
        }
    }
}
