package app.vortex.studio.core.json

/** Árvore JSON mínima, sem dependências, para o formato de projeto versionado. */
sealed class Json {
    object Null : Json()
    data class Bool(val v: Boolean) : Json()
    data class Num(val v: Double) : Json()
    data class Str(val v: String) : Json()
    data class Arr(val v: List<Json>) : Json()
    data class Obj(val v: Map<String, Json>) : Json()
}

class JsonException(message: String) : Exception(message)

object JsonCodec {
    private const val MAX_DEPTH = 64

    fun parse(text: String): Json {
        val p = Parser(text)
        val v = p.value(0)
        p.skipWs()
        if (p.i != text.length) p.fail("conteúdo extra após o fim do JSON")
        return v
    }

    fun stringify(j: Json, pretty: Boolean = false): String = StringBuilder().also { write(it, j, pretty, 0) }.toString()

    private fun write(sb: StringBuilder, j: Json, pretty: Boolean, depth: Int) {
        when (j) {
            is Json.Null -> sb.append("null")
            is Json.Bool -> sb.append(if (j.v) "true" else "false")
            is Json.Num -> {
                if (j.v.isNaN() || j.v.isInfinite()) throw JsonException("número inválido")
                if (j.v == Math.floor(j.v) && Math.abs(j.v) <= 9007199254740992.0) sb.append(j.v.toLong()) else sb.append(j.v)
            }
            is Json.Str -> writeString(sb, j.v)
            is Json.Arr -> {
                if (j.v.isEmpty()) { sb.append("[]"); return }
                sb.append('[')
                j.v.forEachIndexed { idx, e ->
                    if (idx > 0) sb.append(',')
                    if (pretty) newline(sb, depth + 1)
                    write(sb, e, pretty, depth + 1)
                }
                if (pretty) newline(sb, depth)
                sb.append(']')
            }
            is Json.Obj -> {
                if (j.v.isEmpty()) { sb.append("{}"); return }
                sb.append('{')
                var first = true
                for ((k, v) in j.v) {
                    if (!first) sb.append(',')
                    first = false
                    if (pretty) newline(sb, depth + 1)
                    writeString(sb, k)
                    sb.append(if (pretty) ": " else ":")
                    write(sb, v, pretty, depth + 1)
                }
                if (pretty) newline(sb, depth)
                sb.append('}')
            }
        }
    }

    private fun newline(sb: StringBuilder, depth: Int) { sb.append('\n'); repeat(depth) { sb.append("  ") } }

    private fun writeString(sb: StringBuilder, s: String) {
        sb.append('"')
        for (c in s) when {
            c == '"' -> sb.append("\\\"")
            c == '\\' -> sb.append("\\\\")
            c == '\n' -> sb.append("\\n")
            c == '\r' -> sb.append("\\r")
            c == '\t' -> sb.append("\\t")
            c < ' ' -> sb.append(String.format("\\u%04x", c.code))
            else -> sb.append(c)
        }
        sb.append('"')
    }

    private class Parser(val s: String) {
        var i = 0

        fun fail(msg: String): Nothing = throw JsonException("JSON inválido na posição $i: $msg")

        fun skipWs() { while (i < s.length && (s[i] == ' ' || s[i] == '\n' || s[i] == '\r' || s[i] == '\t')) i++ }

        fun value(depth: Int): Json {
            if (depth > MAX_DEPTH) fail("aninhamento excessivo")
            skipWs()
            if (i >= s.length) fail("fim inesperado")
            return when (val c = s[i]) {
                '{' -> obj(depth)
                '[' -> arr(depth)
                '"' -> Json.Str(str())
                't' -> lit("true", Json.Bool(true))
                'f' -> lit("false", Json.Bool(false))
                'n' -> lit("null", Json.Null)
                else -> if (c == '-' || c in '0'..'9') num() else fail("caractere inesperado '$c'")
            }
        }

        private fun lit(word: String, v: Json): Json {
            if (!s.startsWith(word, i)) fail("literal inválido")
            i += word.length
            return v
        }

        private fun num(): Json {
            val st = i
            if (s[i] == '-') i++
            if (i >= s.length) fail("número incompleto")
            if (s[i] == '0') {
                i++
            } else if (s[i].isDigit()) {
                while (i < s.length && s[i].isDigit()) i++
            } else {
                fail("número inválido")
            }
            if (i < s.length && s[i] == '.') {
                i++
                if (i >= s.length || !s[i].isDigit()) fail("parte fracionária inválida")
                while (i < s.length && s[i].isDigit()) i++
            }
            if (i < s.length && (s[i] == 'e' || s[i] == 'E')) {
                i++
                if (i < s.length && (s[i] == '+' || s[i] == '-')) i++
                if (i >= s.length || !s[i].isDigit()) fail("expoente inválido")
                while (i < s.length && s[i].isDigit()) i++
            }
            return Json.Num(s.substring(st, i).toDoubleOrNull() ?: fail("número inválido"))
        }

        private fun str(): String {
            i++ // aspas de abertura
            val sb = StringBuilder()
            while (true) {
                if (i >= s.length) fail("string não terminada")
                val c = s[i++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (i >= s.length) fail("escape incompleto")
                        when (val e = s[i++]) {
                            '"' -> sb.append('"'); '\\' -> sb.append('\\'); '/' -> sb.append('/')
                            'b' -> sb.append('\b'); 'f' -> sb.append('\u000c'); 'n' -> sb.append('\n')
                            'r' -> sb.append('\r'); 't' -> sb.append('\t')
                            'u' -> {
                                if (i + 4 > s.length) fail("escape \\u incompleto")
                                sb.append(s.substring(i, i + 4).toIntOrNull(16)?.toChar() ?: fail("escape \\u inválido"))
                                i += 4
                            }
                            else -> fail("escape desconhecido '\\$e'")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun arr(depth: Int): Json {
            i++
            val out = ArrayList<Json>()
            skipWs()
            if (i < s.length && s[i] == ']') { i++; return Json.Arr(out) }
            while (true) {
                out.add(value(depth + 1))
                skipWs()
                if (i >= s.length) fail("array não terminado")
                if (s[i] == ',') { i++; continue }
                if (s[i] == ']') { i++; return Json.Arr(out) }
                fail("esperado ',' ou ']'")
            }
        }

        private fun obj(depth: Int): Json {
            i++
            val out = LinkedHashMap<String, Json>()
            skipWs()
            if (i < s.length && s[i] == '}') { i++; return Json.Obj(out) }
            while (true) {
                skipWs()
                if (i >= s.length || s[i] != '"') fail("esperada chave de objeto")
                val k = str()
                skipWs()
                if (i >= s.length || s[i] != ':') fail("esperado ':'")
                i++
                out[k] = value(depth + 1)
                skipWs()
                if (i >= s.length) fail("objeto não terminado")
                if (s[i] == ',') { i++; continue }
                if (s[i] == '}') { i++; return Json.Obj(out) }
                fail("esperado ',' ou '}'")
            }
        }
    }
}

// ---------- ajudantes de leitura/escrita ----------

fun jsonObj(vararg pairs: Pair<String, Json?>): Json.Obj =
    Json.Obj(LinkedHashMap<String, Json>().also { m -> for ((k, v) in pairs) if (v != null) m[k] = v })

fun jStr(v: String) = Json.Str(v)
fun jNum(v: Number) = Json.Num(v.toDouble())
fun jBool(v: Boolean) = Json.Bool(v)
fun jArr(v: List<Json>) = Json.Arr(v)

private fun Json.Obj.need(key: String): Json = v[key] ?: throw JsonException("campo obrigatório ausente: $key")

fun Json.Obj.str(key: String): String = (need(key) as? Json.Str)?.v ?: throw JsonException("campo '$key' deve ser texto")
fun Json.Obj.strOrNull(key: String): String? = (v[key] as? Json.Str)?.v
fun Json.Obj.long(key: String): Long = (need(key) as? Json.Num)?.v?.toLong() ?: throw JsonException("campo '$key' deve ser número")
fun Json.Obj.long(key: String, default: Long): Long = (v[key] as? Json.Num)?.v?.toLong() ?: default
fun Json.Obj.int(key: String, default: Int): Int = (v[key] as? Json.Num)?.v?.toInt() ?: default
fun Json.Obj.float(key: String, default: Float): Float = (v[key] as? Json.Num)?.v?.toFloat() ?: default
fun Json.Obj.bool(key: String, default: Boolean): Boolean = (v[key] as? Json.Bool)?.v ?: default
fun Json.Obj.obj(key: String): Json.Obj = need(key) as? Json.Obj ?: throw JsonException("campo '$key' deve ser objeto")
fun Json.Obj.objOrNull(key: String): Json.Obj? = v[key] as? Json.Obj
fun Json.Obj.arr(key: String): List<Json> = (v[key] as? Json.Arr)?.v ?: emptyList()
