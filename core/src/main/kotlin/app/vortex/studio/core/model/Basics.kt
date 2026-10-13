package app.vortex.studio.core.model

import java.util.UUID

/** Todas as posições de tempo do modelo usam microssegundos (mesma unidade do Media3). */
const val US_PER_SEC = 1_000_000L
const val MIN_ITEM_US = 100_000L
const val DEFAULT_STILL_US = 4_000_000L
const val MIN_SPEED = 0.1f
const val MAX_SPEED = 8f
const val CURRENT_SCHEMA_VERSION = 1
const val MAIN_TRACK_ID = "track-main"

interface IdGenerator { fun next(): String }

object RandomIds : IdGenerator {
    override fun next(): String = UUID.randomUUID().toString()
}

/** Gerador determinístico, usado em testes. */
class SequentialIds(private val prefix: String = "id") : IdGenerator {
    private var counter = 0
    override fun next(): String { counter += 1; return "$prefix$counter" }
}

fun formatTimecode(us: Long, withTenths: Boolean = true): String {
    val total = us.coerceAtLeast(0L)
    val totalSec = total / US_PER_SEC
    val base = String.format("%02d:%02d", totalSec / 60, totalSec % 60)
    return if (withTenths) "$base.${(total % US_PER_SEC) / 100_000L}" else base
}
