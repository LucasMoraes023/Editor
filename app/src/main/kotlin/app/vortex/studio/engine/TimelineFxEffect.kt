@file:OptIn(UnstableApi::class)

package app.vortex.studio.engine

import android.content.Context
import android.opengl.GLES20
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import app.vortex.studio.core.model.FxState

/**
 * Efeito GL único do Vortex: aplica cor, geometria (zoom/rotação/espelho), lente (desfoque, nitidez,
 * vinheta, granulação, brilho), glitch, aberração cromática, divisão RGB e mergulho de transição em UM passe.
 * O [stateProvider] devolve o [FxState] de cada quadro (núcleo puro e testado); na exportação ele é
 * avaliado pelo tempo da linha do tempo e, na prévia, pelo estado "ao vivo" do player.
 */
class TimelineFxEffect(private val stateProvider: (presentationTimeUs: Long) -> FxState) : GlEffect {
    override fun toGlShaderProgram(context: Context, useHdr: Boolean): BaseGlShaderProgram =
        TimelineFxProgram(useHdr, stateProvider)
}

private class TimelineFxProgram(
    useHdr: Boolean,
    private val provider: (Long) -> FxState,
) : BaseGlShaderProgram(useHdr, /* texturePoolCapacity= */ 1) {

    private val program: GlProgram = createProgram()
    private var texelW = 1f
    private var texelH = 1f
    private var aspect = 1f

    private fun createProgram(): GlProgram = try {
        GlProgram(VERTEX, FRAGMENT).also {
            it.setBufferAttribute(
                "aFramePosition",
                GlUtil.getNormalizedCoordinateBounds(),
                GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE,
            )
        }
    } catch (e: GlUtil.GlException) {
        throw VideoFrameProcessingException(e)
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        texelW = 1f / inputWidth.coerceAtLeast(1)
        texelH = 1f / inputHeight.coerceAtLeast(1)
        aspect = inputWidth.toFloat() / inputHeight.coerceAtLeast(1)
        return Size(inputWidth, inputHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        val s = provider(presentationTimeUs)
        try {
            program.use()
            program.setSamplerTexIdUniform("uTexSampler", inputTexId, /* texUnitIndex= */ 0)
            program.setFloatsUniform("uColorMatrix", s.color.toGlMat4())
            program.setFloatUniform("uUseColor", if (s.color.isIdentity) 0f else 1f)
            program.setFloatsUniform("uTexel", floatArrayOf(texelW, texelH))
            program.setFloatUniform("uAspect", aspect)
            program.setFloatUniform("uTime", presentationTimeUs / 1_000_000f)
            program.setFloatsUniform("uGeo", floatArrayOf(s.zoom.coerceAtLeast(0.05f), Math.toRadians(s.rotationDeg.toDouble()).toFloat(), s.offsetX, s.offsetY))
            program.setFloatsUniform("uMirror", floatArrayOf(if (s.mirrorX) -1f else 1f, if (s.mirrorY) -1f else 1f))
            program.setFloatsUniform("uLens", floatArrayOf(s.vignette, s.grain, s.blur, s.sharpen))
            program.setFloatsUniform("uFx", floatArrayOf(s.glow, s.rgbSplit, s.chromatic, s.glitch))
            program.setFloatsUniform("uDip", floatArrayOf(s.dip, if (s.dipWhite) 1f else 0f))
            program.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, /* first= */ 0, /* count= */ 4)
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e, presentationTimeUs)
        }
    }

    override fun release() {
        super.release()
        try {
            program.delete()
        } catch (e: GlUtil.GlException) {
            throw VideoFrameProcessingException(e)
        }
    }

    private companion object {
        const val VERTEX = """
attribute vec4 aFramePosition;
varying vec2 vTexCoord;
void main() {
  gl_Position = aFramePosition;
  vTexCoord = aFramePosition.xy * 0.5 + 0.5;
}
"""

        // As texturas do pipeline de SDR chegam em gama (sRGB/BT.709 eletrônico); a matriz de cor do
        // núcleo é definida em RGB linear, então o shader converte (≈2,2) antes e depois de aplicá-la.
        const val FRAGMENT = """
#ifdef GL_FRAGMENT_PRECISION_HIGH
precision highp float;
#else
precision mediump float;
#endif
uniform sampler2D uTexSampler;
uniform mat4 uColorMatrix;
uniform float uUseColor;
uniform vec2 uTexel;
uniform float uAspect;
uniform float uTime;
uniform vec4 uGeo;
uniform vec2 uMirror;
uniform vec4 uLens;
uniform vec4 uFx;
uniform vec2 uDip;
varying vec2 vTexCoord;

float hash(vec2 p) {
  return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

vec3 tex(vec2 uv) {
  if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
    return vec3(0.0);
  }
  return texture2D(uTexSampler, uv).rgb;
}

vec2 geometry(vec2 uv) {
  vec2 p = uv - 0.5;
  p.x *= uAspect;
  float c = cos(uGeo.y);
  float s = sin(uGeo.y);
  p = mat2(c, -s, s, c) * p;
  p.x /= uAspect;
  p = p / uGeo.x - uGeo.zw * 0.5;
  p *= uMirror;
  return p + 0.5;
}

void main() {
  vec2 uv = geometry(vTexCoord);

  if (uFx.w > 0.0) {
    float band = floor(vTexCoord.y * 24.0);
    float tick = floor(uTime * 12.0);
    if (hash(vec2(band, tick)) > 1.0 - 0.35 * uFx.w) {
      uv.x += (hash(vec2(band, tick + 7.0)) - 0.5) * 0.12 * uFx.w;
    }
  }

  vec3 col;
  float ca = uFx.z * 0.012;
  float rs = uFx.y * 0.02;
  if (ca > 0.0 || rs > 0.0) {
    vec2 dir = (vTexCoord - 0.5) * ca;
    col.r = tex(uv + dir + vec2(rs, 0.0)).r;
    col.g = tex(uv).g;
    col.b = tex(uv - dir - vec2(rs, 0.0)).b;
  } else {
    col = tex(uv);
  }

  if (uLens.z > 0.0) {
    vec2 o = uTexel * (1.0 + 5.0 * uLens.z);
    vec3 sum = col;
    sum += tex(uv + vec2(o.x, 0.0)) + tex(uv - vec2(o.x, 0.0));
    sum += tex(uv + vec2(0.0, o.y)) + tex(uv - vec2(0.0, o.y));
    sum += tex(uv + o) + tex(uv - o);
    sum += tex(uv + vec2(o.x, -o.y)) + tex(uv + vec2(-o.x, o.y));
    col = mix(col, sum / 9.0, clamp(uLens.z * 1.5, 0.0, 1.0));
  }

  if (uLens.w > 0.0) {
    vec3 n = tex(uv + vec2(uTexel.x, 0.0)) + tex(uv - vec2(uTexel.x, 0.0));
    n += tex(uv + vec2(0.0, uTexel.y)) + tex(uv - vec2(0.0, uTexel.y));
    col = col + (col * 4.0 - n) * uLens.w * 0.8;
  }

  if (uFx.x > 0.0) {
    vec2 o = uTexel * 6.0;
    vec3 b = tex(uv + vec2(o.x, 0.0)) + tex(uv - vec2(o.x, 0.0));
    b += tex(uv + vec2(0.0, o.y)) + tex(uv - vec2(0.0, o.y));
    b += tex(uv + o) + tex(uv - o);
    b += tex(uv + vec2(o.x, -o.y)) + tex(uv + vec2(-o.x, o.y));
    b = b / 8.0;
    col += max(b - 0.55, 0.0) * 2.2 * uFx.x * 1.2;
  }

  if (uUseColor > 0.5) {
    vec3 lin = pow(max(col, 0.0), vec3(2.2));
    lin = (uColorMatrix * vec4(lin, 1.0)).rgb;
    col = pow(max(lin, 0.0), vec3(1.0 / 2.2));
  }

  if (uLens.x > 0.0) {
    float d = distance(vTexCoord, vec2(0.5));
    col *= 1.0 - uLens.x * smoothstep(0.35, 0.85, d) * 0.9;
  }

  if (uLens.y > 0.0) {
    float n = hash(vTexCoord / uTexel + fract(uTime) * 100.0) - 0.5;
    col += n * uLens.y * 0.25;
  }

  col = mix(col, vec3(uDip.y), uDip.x);
  gl_FragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
"""
    }
}
