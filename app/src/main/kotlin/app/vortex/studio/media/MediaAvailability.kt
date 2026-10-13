package app.vortex.studio.media

import android.content.Context
import android.net.Uri

object MediaAvailability {
    /** True se a URI ainda pode ser aberta para leitura (arquivo não movido/excluído e permissão válida). */
    fun isReadable(context: Context, uri: String): Boolean = try {
        context.contentResolver.openAssetFileDescriptor(Uri.parse(uri), "r")?.use { true } ?: false
    } catch (e: Exception) {
        false
    }
}
