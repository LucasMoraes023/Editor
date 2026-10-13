package app.vortex.studio.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import app.vortex.studio.core.model.TextAlignment
import app.vortex.studio.core.model.TextItem
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

object Fonts {
    /** Famílias do sistema disponíveis no seletor de fontes (existem em todos os aparelhos Android). */
    val keys = listOf("sans", "sans-condensed", "serif", "mono", "cursive", "casual")

    fun typeface(key: String, bold: Boolean, italic: Boolean): Typeface {
        val family = when (key) {
            "serif" -> "serif"
            "mono" -> "monospace"
            "sans-condensed" -> "sans-serif-condensed"
            "cursive" -> "cursive"
            "casual" -> "casual"
            else -> "sans-serif"
        }
        val style = when {
            bold && italic -> Typeface.BOLD_ITALIC
            bold -> Typeface.BOLD
            italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        return Typeface.create(family, style)
    }
}

/**
 * Desenha textos em bitmap relativo ao tamanho do QUADRO (fração da altura), garantindo o mesmo
 * resultado na prévia e na exportação, em qualquer resolução.
 */
object TextBitmapRenderer {
    fun render(item: TextItem, frameWidth: Int, frameHeight: Int): Bitmap {
        val st = item.style
        val textSize = max(8f, st.sizeFrac * frameHeight)
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            this.textSize = textSize
            color = st.colorArgb
            typeface = Fonts.typeface(st.fontKey, st.bold, st.italic)
        }
        val text = item.text.ifEmpty { " " }
        val maxWidth = max(1, (frameWidth * 0.9f).toInt())
        val width = min(ceil(Layout.getDesiredWidth(text, paint)).toInt() + 2, maxWidth).coerceAtLeast(1)
        val align = when (st.alignment) {
            TextAlignment.START -> Layout.Alignment.ALIGN_NORMAL
            TextAlignment.CENTER -> Layout.Alignment.ALIGN_CENTER
            TextAlignment.END -> Layout.Alignment.ALIGN_OPPOSITE
        }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(align).setLineSpacing(0f, 1.05f).setIncludePad(false).build()

        val hasBox = st.backgroundArgb != 0
        val pad = if (hasBox) textSize * 0.4f else 0f
        val stroke = st.strokeWidthFrac * frameHeight
        val extra = pad + stroke + if (st.shadow) textSize * 0.1f else 0f
        val bw = ceil(width + 2 * extra).toInt().coerceAtLeast(1)
        val bh = ceil(layout.height + 2 * extra).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        if (hasBox) {
            val box = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = st.backgroundArgb }
            canvas.drawRoundRect(0f, 0f, bw.toFloat(), bh.toFloat(), textSize * 0.3f, textSize * 0.3f, box)
        }
        canvas.translate(extra, extra)
        if (stroke > 0f) {
            layout.paint.style = Paint.Style.STROKE
            layout.paint.strokeWidth = stroke * 2f
            layout.paint.strokeJoin = Paint.Join.ROUND
            layout.paint.color = st.strokeColorArgb
            layout.draw(canvas)
        }
        layout.paint.style = Paint.Style.FILL
        layout.paint.color = st.colorArgb
        if (st.shadow) layout.paint.setShadowLayer(textSize * 0.06f, textSize * 0.03f, textSize * 0.04f, 0xAA000000.toInt())
        else layout.paint.clearShadowLayer()
        layout.draw(canvas)
        return bmp
    }

    /** Decodifica uma imagem/logotipo já na largura desejada (sem carregar a imagem inteira em memória). */
    fun decodeScaled(context: Context, uri: Uri, targetWidth: Int): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetWidth) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        val h = max(1, (decoded.height.toFloat() * targetWidth / decoded.width).toInt())
        return if (decoded.width == targetWidth) decoded else Bitmap.createScaledBitmap(decoded, targetWidth, h, true).also { if (it !== decoded) decoded.recycle() }
    }
}
