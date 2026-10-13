package app.vortex.studio.core.model

import kotlin.math.pow

/**
 * Matriz de cor 4x5 (linha a linha, como android.graphics.ColorMatrix, porém com deslocamentos
 * normalizados em 0..1). out = M * [r g b a 1]. Compartilhada por prévia e exportação.
 */
class ColorMatrix(val m: FloatArray) {
    init { require(m.size == 20) { "ColorMatrix exige 20 valores" } }

    /** Compõe: aplica [other] primeiro e depois esta matriz. */
    operator fun times(other: ColorMatrix): ColorMatrix {
        val r = FloatArray(20)
        for (i in 0 until 4) {
            for (j in 0 until 5) {
                var s = 0f
                for (k in 0 until 4) s += m[i * 5 + k] * other.m[k * 5 + j]
                if (j == 4) s += m[i * 5 + 4]
                r[i * 5 + j] = s
            }
        }
        return ColorMatrix(r)
    }

    /** Interpolação linear entre a identidade (t=0) e esta matriz (t=1). */
    fun amount(t: Float): ColorMatrix {
        val id = identity().m
        val k = t.coerceIn(0f, 1f)
        return ColorMatrix(FloatArray(20) { id[it] + (m[it] - id[it]) * k })
    }

    val isIdentity: Boolean get() = m.indices.all { kotlin.math.abs(m[it] - identity().m[it]) < 1e-6f }

    /**
     * Converte para mat4 em ordem de colunas (como android.opengl.Matrix) para uso em GLSL como
     * M * vec4(rgb, 1): os deslocamentos ficam na quarta coluna e o alfa é preservado.
     */
    fun toGlMat4(): FloatArray = floatArrayOf(
        m[0], m[5], m[10], 0f,
        m[1], m[6], m[11], 0f,
        m[2], m[7], m[12], 0f,
        m[4], m[9], m[14], 1f,
    )

    override fun equals(other: Any?): Boolean = other is ColorMatrix && m.contentEquals(other.m)
    override fun hashCode(): Int = m.contentHashCode()

    companion object {
        fun identity() = ColorMatrix(floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        ))
    }
}

/** Construtores de matrizes de cor, definidos em RGB linear. O shader do app converte de/para gama (≈2,2) ao aplicá-las. */
object ColorMath {
    private const val LR = 0.2126f
    private const val LG = 0.7152f
    private const val LB = 0.0722f

    fun scaleOffset(r: Float, g: Float, b: Float, or: Float = 0f, og: Float = 0f, ob: Float = 0f) = ColorMatrix(floatArrayOf(
        r, 0f, 0f, 0f, or,
        0f, g, 0f, 0f, og,
        0f, 0f, b, 0f, ob,
        0f, 0f, 0f, 1f, 0f,
    ))

    fun saturation(s: Float): ColorMatrix {
        val ir = (1f - s) * LR; val ig = (1f - s) * LG; val ib = (1f - s) * LB
        return ColorMatrix(floatArrayOf(
            ir + s, ig, ib, 0f, 0f,
            ir, ig + s, ib, 0f, 0f,
            ir, ig, ib + s, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        ))
    }

    fun grayscale(amount: Float): ColorMatrix = saturation(1f - amount.coerceIn(0f, 1f))

    /** Exposição em "stops": 2^stops. */
    fun exposure(stops: Float): ColorMatrix { val g = 2f.pow(stops); return scaleOffset(g, g, g) }

    /** Contraste em torno do cinza médio linear (0,18). */
    fun contrast(c: Float, pivot: Float = 0.18f): ColorMatrix = scaleOffset(c, c, c, pivot * (1f - c), pivot * (1f - c), pivot * (1f - c))

    /** [t] em -1..1: negativo = mais frio (azul), positivo = mais quente (laranja). */
    fun temperature(t: Float): ColorMatrix = scaleOffset(1f + 0.22f * t, 1f + 0.02f * t, 1f - 0.22f * t)

    fun sepia(amount: Float): ColorMatrix = ColorMatrix(floatArrayOf(
        0.393f, 0.769f, 0.189f, 0f, 0f,
        0.349f, 0.686f, 0.168f, 0f, 0f,
        0.272f, 0.534f, 0.131f, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )).amount(amount)

    /** Cores desbotadas, pretos levantados e tom quente. */
    fun vintage(amount: Float): ColorMatrix =
        (scaleOffset(1f, 1f, 1f, 0.02f, 0.018f, 0.012f) * temperature(0.35f) * saturation(0.7f)).amount(amount)

    /** Contraste suave, sombras frias e realces quentes. */
    fun cinema(amount: Float): ColorMatrix =
        (scaleOffset(1.04f, 1f, 0.96f, -0.008f, 0f, 0.012f) * saturation(0.92f) * contrast(1.12f)).amount(amount)
}
