package app.vortex.studio.core.model

enum class MediaKind { VIDEO, IMAGE, AUDIO }

/** Mídia importada. [uri] é sempre uma URI concedida pelo sistema (nunca um caminho de arquivo). */
data class MediaAsset(
    val id: String,
    val uri: String,
    val displayName: String,
    val kind: MediaKind,
    val durationUs: Long,
    val width: Int = 0,
    val height: Int = 0,
    val rotationDegrees: Int = 0,
    val mimeType: String? = null,
    val sizeBytes: Long = 0L,
    val hasAudio: Boolean = false,
    val frameRate: Float = 0f,
)
