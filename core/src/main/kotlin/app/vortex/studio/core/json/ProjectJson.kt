package app.vortex.studio.core.json

import app.vortex.studio.core.model.*

class UnsupportedSchemaException(val found: Int, val supported: Int) :
    Exception("Projeto criado por uma versão mais nova do app (esquema $found; este app entende até $supported).")

/** Migrações entre versões do esquema: cada passo converte o esquema N para N+1. */
object ProjectMigrations {
    val steps: Map<Int, (Json.Obj) -> Json.Obj> = emptyMap()

    fun migrate(root: Json.Obj, from: Int, steps: Map<Int, (Json.Obj) -> Json.Obj> = this.steps, target: Int = CURRENT_SCHEMA_VERSION): Json.Obj {
        var cur = root
        var v = from
        while (v < target) {
            val step = steps[v] ?: throw JsonException("sem migração do esquema $v para ${v + 1}")
            cur = step(cur)
            v += 1
        }
        return cur
    }
}

/** Formato de projeto em disco: JSON estruturado e versionado ("schemaVersion"). */
object ProjectJson {
    fun encode(p: Project, pretty: Boolean = false): String = JsonCodec.stringify(toJson(p), pretty)

    fun decode(text: String): Project {
        val root = JsonCodec.parse(text) as? Json.Obj ?: throw JsonException("o arquivo de projeto deve ser um objeto JSON")
        val version = root.int("schemaVersion", 1)
        if (version > CURRENT_SCHEMA_VERSION) throw UnsupportedSchemaException(version, CURRENT_SCHEMA_VERSION)
        return fromJson(ProjectMigrations.migrate(root, version))
    }

    private inline fun <reified E : Enum<E>> enumOf(name: String?, default: E): E = enumValues<E>().firstOrNull { it.name == name } ?: default

    // ---------- escrita ----------

    fun toJson(p: Project): Json.Obj = jsonObj(
        "schemaVersion" to jNum(p.schemaVersion),
        "id" to jStr(p.id),
        "name" to jStr(p.name),
        "createdAtMs" to jNum(p.createdAtMs),
        "modifiedAtMs" to jNum(p.modifiedAtMs),
        "templateId" to p.templateId?.let { jStr(it) },
        "canvas" to jsonObj("aspect" to jStr(p.canvas.aspect.name), "fps" to jNum(p.canvas.fps), "fit" to jStr(p.canvas.fit.name)),
        "assets" to jArr(p.assets.values.map(::assetJson)),
        "tracks" to jArr(p.tracks.map(::trackJson)),
    )

    private fun assetJson(a: MediaAsset) = jsonObj(
        "id" to jStr(a.id), "uri" to jStr(a.uri), "displayName" to jStr(a.displayName), "kind" to jStr(a.kind.name),
        "durationUs" to jNum(a.durationUs), "width" to jNum(a.width), "height" to jNum(a.height),
        "rotationDegrees" to jNum(a.rotationDegrees), "mimeType" to a.mimeType?.let { jStr(it) },
        "sizeBytes" to jNum(a.sizeBytes), "hasAudio" to jBool(a.hasAudio), "frameRate" to jNum(a.frameRate),
    )

    private fun trackJson(t: Track) = jsonObj(
        "id" to jStr(t.id), "kind" to jStr(t.kind.name), "name" to jStr(t.name), "isMain" to jBool(t.isMain),
        "muted" to jBool(t.muted), "locked" to jBool(t.locked), "items" to jArr(t.items.map(::itemJson)),
    )

    private fun itemJson(i: TimelineItem): Json.Obj = when (i) {
        is MediaClip -> jsonObj(
            "type" to jStr("clip"), "id" to jStr(i.id), "assetId" to jStr(i.assetId), "startUs" to jNum(i.startUs),
            "sourceInUs" to jNum(i.sourceInUs), "sourceOutUs" to jNum(i.sourceOutUs), "isStill" to jBool(i.isStill),
            "speed" to jNum(i.speed), "volume" to jNum(i.volume), "muted" to jBool(i.muted),
            "fadeInUs" to jNum(i.fadeInUs), "fadeOutUs" to jNum(i.fadeOutUs),
            "transform" to transformJson(i.transform), "effects" to jArr(i.effects.map(::effectJson)),
            "keyframes" to keyframesJson(i.keyframes),
            "transitionIn" to i.transitionIn?.let { jsonObj("type" to jStr(it.type.name), "durationUs" to jNum(it.durationUs)) },
        )
        is AudioClip -> jsonObj(
            "type" to jStr("audio"), "id" to jStr(i.id), "assetId" to jStr(i.assetId), "startUs" to jNum(i.startUs),
            "sourceInUs" to jNum(i.sourceInUs), "sourceOutUs" to jNum(i.sourceOutUs), "speed" to jNum(i.speed),
            "volume" to jNum(i.volume), "muted" to jBool(i.muted), "fadeInUs" to jNum(i.fadeInUs), "fadeOutUs" to jNum(i.fadeOutUs),
        )
        is TextItem -> jsonObj(
            "type" to jStr("text"), "id" to jStr(i.id), "startUs" to jNum(i.startUs), "durationUs" to jNum(i.durationUs),
            "text" to jStr(i.text), "style" to styleJson(i.style), "x" to jNum(i.x), "y" to jNum(i.y),
            "rotationDeg" to jNum(i.rotationDeg), "animIn" to jStr(i.animIn.name), "animOut" to jStr(i.animOut.name),
            "animDurationUs" to jNum(i.animDurationUs),
        )
        is OverlayItem -> jsonObj(
            "type" to jStr("overlay"), "id" to jStr(i.id), "assetId" to jStr(i.assetId), "startUs" to jNum(i.startUs),
            "durationUs" to jNum(i.durationUs), "x" to jNum(i.x), "y" to jNum(i.y), "scale" to jNum(i.scale),
            "rotationDeg" to jNum(i.rotationDeg), "opacity" to jNum(i.opacity), "animIn" to jStr(i.animIn.name),
            "animOut" to jStr(i.animOut.name), "animDurationUs" to jNum(i.animDurationUs),
        )
    }

    private fun transformJson(t: ClipTransform) = jsonObj(
        "rotationQuarters" to jNum(t.rotationQuarters), "zoom" to jNum(t.zoom), "offsetX" to jNum(t.offsetX),
        "offsetY" to jNum(t.offsetY), "mirrorH" to jBool(t.mirrorH), "mirrorV" to jBool(t.mirrorV),
    )

    private fun effectJson(e: EffectInstance) = jsonObj("id" to jStr(e.id), "type" to jStr(e.type.name), "intensity" to jNum(e.intensity), "enabled" to jBool(e.enabled))

    private fun keyframesJson(k: Map<KeyframeProperty, List<Keyframe>>) = Json.Obj(LinkedHashMap<String, Json>().also { m ->
        for ((prop, frames) in k) m[prop.name] = jArr(frames.map { jsonObj("t" to jNum(it.timeUs), "v" to jNum(it.value), "e" to jStr(it.easing.name)) })
    })

    private fun styleJson(s: TextStyleSpec) = jsonObj(
        "fontKey" to jStr(s.fontKey), "sizeFrac" to jNum(s.sizeFrac), "colorArgb" to jNum(s.colorArgb), "bold" to jBool(s.bold),
        "italic" to jBool(s.italic), "alignment" to jStr(s.alignment.name), "strokeColorArgb" to jNum(s.strokeColorArgb),
        "strokeWidthFrac" to jNum(s.strokeWidthFrac), "shadow" to jBool(s.shadow), "backgroundArgb" to jNum(s.backgroundArgb),
    )

    // ---------- leitura ----------

    fun fromJson(o: Json.Obj): Project {
        val canvasObj = o.objOrNull("canvas")
        val canvas = if (canvasObj == null) CanvasSpec() else CanvasSpec(
            enumOf(canvasObj.strOrNull("aspect"), AspectPreset.PORTRAIT_9_16), canvasObj.int("fps", 30), enumOf(canvasObj.strOrNull("fit"), FitMode.FIT),
        )
        val assets = o.arr("assets").map { assetFrom(it as? Json.Obj ?: throw JsonException("mídia inválida")) }.associateBy { it.id }
        var tracks = o.arr("tracks").map { trackFrom(it as? Json.Obj ?: throw JsonException("faixa inválida")) }
        if (tracks.none { it.isMain }) tracks = listOf(Track(MAIN_TRACK_ID, TrackKind.VIDEO, "main", isMain = true)) + tracks
        return Project(
            id = o.str("id"), name = o.str("name"), createdAtMs = o.long("createdAtMs", 0L), modifiedAtMs = o.long("modifiedAtMs", 0L),
            schemaVersion = CURRENT_SCHEMA_VERSION, canvas = canvas, assets = assets, tracks = tracks, templateId = o.strOrNull("templateId"),
        )
    }

    private fun assetFrom(o: Json.Obj) = MediaAsset(
        id = o.str("id"), uri = o.str("uri"), displayName = o.strOrNull("displayName") ?: "", kind = enumOf(o.strOrNull("kind"), MediaKind.VIDEO),
        durationUs = o.long("durationUs", 0L), width = o.int("width", 0), height = o.int("height", 0), rotationDegrees = o.int("rotationDegrees", 0),
        mimeType = o.strOrNull("mimeType"), sizeBytes = o.long("sizeBytes", 0L), hasAudio = o.bool("hasAudio", false), frameRate = o.float("frameRate", 0f),
    )

    private fun trackFrom(o: Json.Obj) = Track(
        id = o.str("id"), kind = enumOf(o.strOrNull("kind"), TrackKind.VIDEO), name = o.strOrNull("name") ?: "",
        items = o.arr("items").mapNotNull { itemFrom(it as? Json.Obj ?: throw JsonException("item inválido")) },
        isMain = o.bool("isMain", false), muted = o.bool("muted", false), locked = o.bool("locked", false),
    )

    /** Tipos de item desconhecidos (de versões futuras) são ignorados em vez de derrubar a abertura do projeto. */
    private fun itemFrom(o: Json.Obj): TimelineItem? = when (o.strOrNull("type")) {
        "clip" -> MediaClip(
            id = o.str("id"), assetId = o.str("assetId"), startUs = o.long("startUs", 0L), sourceInUs = o.long("sourceInUs", 0L),
            sourceOutUs = o.long("sourceOutUs"), isStill = o.bool("isStill", false), speed = o.float("speed", 1f), volume = o.float("volume", 1f),
            muted = o.bool("muted", false), fadeInUs = o.long("fadeInUs", 0L), fadeOutUs = o.long("fadeOutUs", 0L),
            transform = o.objOrNull("transform")?.let(::transformFrom) ?: ClipTransform(),
            effects = o.arr("effects").mapNotNull { (it as? Json.Obj)?.let(::effectFrom) },
            keyframes = o.objOrNull("keyframes")?.let(::keyframesFrom) ?: emptyMap(),
            transitionIn = o.objOrNull("transitionIn")?.let { TransitionSpec(enumOf(it.strOrNull("type"), TransitionType.FADE_BLACK), it.long("durationUs", 600_000L)) },
        )
        "audio" -> AudioClip(
            id = o.str("id"), assetId = o.str("assetId"), startUs = o.long("startUs", 0L), sourceInUs = o.long("sourceInUs", 0L),
            sourceOutUs = o.long("sourceOutUs"), speed = o.float("speed", 1f), volume = o.float("volume", 1f), muted = o.bool("muted", false),
            fadeInUs = o.long("fadeInUs", 0L), fadeOutUs = o.long("fadeOutUs", 0L),
        )
        "text" -> TextItem(
            id = o.str("id"), startUs = o.long("startUs", 0L), durationUs = o.long("durationUs"), text = o.str("text"),
            style = o.objOrNull("style")?.let(::styleFrom) ?: TextStyleSpec(), x = o.float("x", 0f), y = o.float("y", 0f),
            rotationDeg = o.float("rotationDeg", 0f), animIn = enumOf(o.strOrNull("animIn"), ItemAnimation.FADE),
            animOut = enumOf(o.strOrNull("animOut"), ItemAnimation.FADE), animDurationUs = o.long("animDurationUs", 400_000L),
        )
        "overlay" -> OverlayItem(
            id = o.str("id"), assetId = o.str("assetId"), startUs = o.long("startUs", 0L), durationUs = o.long("durationUs"),
            x = o.float("x", 0f), y = o.float("y", 0f), scale = o.float("scale", 0.3f), rotationDeg = o.float("rotationDeg", 0f),
            opacity = o.float("opacity", 1f), animIn = enumOf(o.strOrNull("animIn"), ItemAnimation.FADE),
            animOut = enumOf(o.strOrNull("animOut"), ItemAnimation.FADE), animDurationUs = o.long("animDurationUs", 400_000L),
        )
        else -> null
    }

    private fun transformFrom(o: Json.Obj) = ClipTransform(
        o.int("rotationQuarters", 0), o.float("zoom", 1f), o.float("offsetX", 0f), o.float("offsetY", 0f), o.bool("mirrorH", false), o.bool("mirrorV", false),
    )

    private fun effectFrom(o: Json.Obj): EffectInstance? {
        val type = enumValues<EffectType>().firstOrNull { it.name == o.strOrNull("type") } ?: return null
        return EffectInstance(o.str("id"), type, o.float("intensity", type.defaultIntensity), o.bool("enabled", true))
    }

    private fun keyframesFrom(o: Json.Obj): Map<KeyframeProperty, List<Keyframe>> {
        val out = LinkedHashMap<KeyframeProperty, List<Keyframe>>()
        for ((name, arr) in o.v) {
            val prop = enumValues<KeyframeProperty>().firstOrNull { it.name == name } ?: continue
            out[prop] = (arr as? Json.Arr)?.v.orEmpty().mapNotNull { f ->
                (f as? Json.Obj)?.let { Keyframe(it.long("t", 0L), it.float("v", 0f), enumOf(it.strOrNull("e"), Easing.LINEAR)) }
            }.sortedBy { it.timeUs }
        }
        return out
    }

    private fun styleFrom(o: Json.Obj) = TextStyleSpec(
        fontKey = o.strOrNull("fontKey") ?: "sans", sizeFrac = o.float("sizeFrac", 0.06f), colorArgb = o.long("colorArgb", 0xFFFFFFFFL).toInt(),
        bold = o.bool("bold", true), italic = o.bool("italic", false), alignment = enumOf(o.strOrNull("alignment"), TextAlignment.CENTER),
        strokeColorArgb = o.long("strokeColorArgb", 0xFF000000L).toInt(), strokeWidthFrac = o.float("strokeWidthFrac", 0f),
        shadow = o.bool("shadow", false), backgroundArgb = o.long("backgroundArgb", 0L).toInt(),
    )
}
