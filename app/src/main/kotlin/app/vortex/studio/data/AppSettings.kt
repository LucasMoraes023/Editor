package app.vortex.studio.data

import android.content.Context
import app.vortex.studio.core.export.ExportQuality
import app.vortex.studio.core.export.ExportResolution

enum class PreviewQuality(val shortSide: Int) { LOW(360), MEDIUM(540), HIGH(720) }

/** Preferências locais (SharedPreferences privado do app; nada sai do aparelho). */
class AppSettings(context: Context) {
    private val p = context.getSharedPreferences("vortex_settings", Context.MODE_PRIVATE)

    private inline fun <reified E : Enum<E>> enumPref(key: String, default: E): E =
        enumValues<E>().firstOrNull { it.name == p.getString(key, null) } ?: default

    var previewQuality: PreviewQuality
        get() = enumPref("previewQuality", PreviewQuality.MEDIUM)
        set(v) = p.edit().putString("previewQuality", v.name).apply()

    /** Efeitos visuais na prévia (desligue em aparelhos básicos para reproduzir mais fluido). */
    var previewEffects: Boolean
        get() = p.getBoolean("previewEffects", true)
        set(v) = p.edit().putBoolean("previewEffects", v).apply()

    var defaultResolution: ExportResolution
        get() = enumPref("defaultResolution", ExportResolution.P1080)
        set(v) = p.edit().putString("defaultResolution", v.name).apply()

    var defaultFps: Int
        get() = p.getInt("defaultFps", 30)
        set(v) = p.edit().putInt("defaultFps", v).apply()

    var defaultQuality: ExportQuality
        get() = enumPref("defaultQuality", ExportQuality.MEDIUM)
        set(v) = p.edit().putString("defaultQuality", v.name).apply()
}
