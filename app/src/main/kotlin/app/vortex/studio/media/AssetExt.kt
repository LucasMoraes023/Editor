package app.vortex.studio.media

import app.vortex.studio.core.model.MediaAsset

/** Proporção (largura/altura) como o vídeo aparece na tela, já considerando a rotação dos metadados. */
fun MediaAsset.displayAspect(): Float {
    val swap = rotationDegrees % 180 != 0
    val w = if (swap) height else width
    val h = if (swap) width else height
    return if (w > 0 && h > 0) w.toFloat() / h else 16f / 9f
}
