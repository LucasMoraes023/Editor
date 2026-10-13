package app.vortex.studio.core

import app.vortex.studio.core.Fixtures.sec
import app.vortex.studio.core.edit.Edits
import app.vortex.studio.core.json.*
import app.vortex.studio.core.model.*
import org.junit.Assert.*
import org.junit.Test

class JsonCodecTest {
    @Test fun roundTrip_preservesStructure() {
        val text = """{"a":[1,2.5,true,null,"x\n\"y"],"b":{"c":-3}}"""
        val j = JsonCodec.parse(text)
        assertEquals(j, JsonCodec.parse(JsonCodec.stringify(j)))
        assertEquals(j, JsonCodec.parse(JsonCodec.stringify(j, pretty = true)))
    }

    @Test fun unicodeAndControlCharacters() {
        assertEquals(Json.Str("é"), JsonCodec.parse("\"\\u00e9\""))
        assertEquals("\"é\"", JsonCodec.stringify(Json.Str("é")))
        assertEquals("\"\\u0001\"", JsonCodec.stringify(Json.Str("\u0001")))
        assertEquals(Json.Str("日本語🎬"), JsonCodec.parse(JsonCodec.stringify(Json.Str("日本語🎬"))))
    }

    @Test fun largeIntegersKeepPrecision() {
        assertEquals("9007199254740991", JsonCodec.stringify(Json.Num(9007199254740991.0)))
        assertEquals("1.5", JsonCodec.stringify(Json.Num(1.5)))
    }

    @Test fun invalidDocuments_throwJsonException() {
        for (bad in listOf("", "{", "[1,", "{\"a\" 1}", "nul", "\"abc", "12 13", "{\"a\":}", "[1 2]", "{1:2}", "\"\\x\"", "01", "1.", "1e", "-01", "1e+") ) {
            expectThrows<JsonException> { JsonCodec.parse(bad) }
        }
        expectThrows<JsonException> { JsonCodec.parse("[".repeat(100) + "]".repeat(100)) }   // aninhamento excessivo
        expectThrows<JsonException> { JsonCodec.stringify(Json.Num(Double.NaN)) }
    }
}

class ProjectJsonTest {
    private fun rich(): Project {
        var p = Fixtures.project(Fixtures.video("v1", 10.0), Fixtures.image("i1"), Fixtures.audio("a1", 20.0))
        p = Edits.appendClip(p, "c1", "v1"); p = Edits.appendClip(p, "c2", "i1")
        p = Edits.addEffect(p, "c1", "e1", EffectType.VINTAGE, 0.3f)
        p = Edits.setTransform(p, "c1", ClipTransform(1, 1.5f, 0.1f, -0.2f, mirrorH = true))
        p = Edits.setTransition(p, "c2", TransitionSpec(TransitionType.FADE_WHITE, 800_000L))
        p = Edits.setSpeed(p, "c1", 1.5f); p = Edits.setFades(p, "c1", 300_000L, 500_000L)
        p = Edits.applyKenBurns(p, "c2")
        p = Edits.addText(p, "t1", "Olá \"mundo\"\n2ª linha", 0L, sec(3.0), TextStyleSpec(fontKey = "serif", colorArgb = 0xFFFFD54F.toInt(), strokeWidthFrac = 0.01f, shadow = true, backgroundArgb = 0xB3000000.toInt()), 0.1f, -0.4f, ItemAnimation.POP, ItemAnimation.SLIDE_UP)
        p = Edits.addOverlay(p, "o1", "i1", sec(1.0), sec(2.0), 0.5f, 0.5f, 0.2f)
        p = Edits.addAudio(p, "m1", "a1", sec(2.0)); p = Edits.setVolume(p, "m1", 0.6f)
        return p.normalized()
    }

    @Test fun fullProject_roundTrips() {
        val p = rich()
        assertEquals(p, ProjectJson.decode(ProjectJson.encode(p)))
        assertEquals(p, ProjectJson.decode(ProjectJson.encode(p, pretty = true)))
    }

    @Test fun newerSchema_isRejectedExplicitly() {
        val json = ProjectJson.encode(rich()).replace("\"schemaVersion\":1", "\"schemaVersion\":99")
        val e = expectThrows<UnsupportedSchemaException> { ProjectJson.decode(json) }
        assertEquals(99, e.found)
    }

    @Test fun unknownContent_isToleratedAndInvalidIsRejected() {
        val ok = """{"schemaVersion":1,"id":"p","name":"n","tracks":[{"id":"x","kind":"ALIEN","items":[{"type":"hologram","id":"h"}]}],"canvas":{"aspect":"???"}}"""
        val p = ProjectJson.decode(ok)
        assertTrue(p.tracks.any { it.isMain })                                   // faixa principal restaurada
        assertEquals(AspectPreset.PORTRAIT_9_16, p.canvas.aspect)                // enum desconhecido → padrão
        assertTrue(p.tracks.first { it.id == "x" }.items.isEmpty())              // item desconhecido ignorado
        expectThrows<JsonException> { ProjectJson.decode("""{"schemaVersion":1,"name":"sem id"}""") }
        expectThrows<JsonException> { ProjectJson.decode("[]") }
        expectThrows<JsonException> { ProjectJson.decode("não é json") }
    }

    @Test fun migrations_runInOrder() {
        val steps = mapOf<Int, (Json.Obj) -> Json.Obj>(1 to { o -> Json.Obj(o.v + ("migrated" to Json.Bool(true))) })
        val out = ProjectMigrations.migrate(Json.Obj(mapOf("schemaVersion" to Json.Num(1.0))), 1, steps, 2)
        assertEquals(Json.Bool(true), out.v["migrated"])
        expectThrows<JsonException> { ProjectMigrations.migrate(Json.Obj(emptyMap()), 1, emptyMap(), 3) }
    }

    @Test fun referencedAssets_listedOnce() {
        val p = rich()
        assertEquals(setOf("v1", "i1", "a1"), p.referencedAssetIds().toSet())
        assertEquals(p.referencedAssetIds().size, p.referencedAssetIds().toSet().size)
    }
}
