package app.vortex.studio.core

import app.vortex.studio.core.model.*

object Fixtures {
    fun sec(s: Double): Long = (s * US_PER_SEC).toLong()
    fun video(id: String, seconds: Double, audio: Boolean = true) =
        MediaAsset(id, "content://v/$id", "$id.mp4", MediaKind.VIDEO, sec(seconds), 1920, 1080, 0, "video/mp4", 1000L, audio, 30f)
    fun image(id: String) = MediaAsset(id, "content://i/$id", "$id.jpg", MediaKind.IMAGE, 0L, 1000, 1000, 0, "image/jpeg")
    fun audio(id: String, seconds: Double) =
        MediaAsset(id, "content://a/$id", "$id.mp3", MediaKind.AUDIO, sec(seconds), mimeType = "audio/mpeg", hasAudio = true)
    fun project(vararg assets: MediaAsset): Project =
        Project("p1", "Teste", 1000L, 1000L, assets = assets.associateBy { it.id }).normalized()
}

inline fun <reified T : Throwable> expectThrows(block: () -> Unit): T {
    try { block() } catch (e: Throwable) {
        if (e is T) return e
        throw AssertionError("esperava ${T::class.simpleName}, mas veio $e", e)
    }
    throw AssertionError("esperava ${T::class.simpleName}, mas nada foi lançado")
}
