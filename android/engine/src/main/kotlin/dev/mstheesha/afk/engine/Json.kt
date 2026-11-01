package dev.mstheesha.afk.engine

/**
 * Tiny JSON reader/writer. The engine talks to Mojang, Xbox and the server list
 * ping, all of which return small JSON documents; pulling in a full JSON library
 * for that would defeat the point of a lightweight engine.
 */
internal object Json {

    fun parse(text: String): Any? {
        val p = Parser(text)
        p.skipWs()
        val value = p.value()
        p.skipWs()
        if (!p.done()) throw IllegalArgumentException("trailing JSON at ${p.pos}")
        return value
    }

    fun write(value: Any?): String = StringBuilder().also { write(it, value) }.toString()

    fun write(sb: StringBuilder, value: Any?) {
        when (value) {
            null -> sb.append("null")
            is String -> quote(sb, value)
            is Boolean -> sb.append(value)
            is Int, is Long, is Short, is Byte -> sb.append(value)
            is Float -> sb.append(if (value.isFinite()) value.toString() else "0")
            is Double -> sb.append(if (value.isFinite()) value.toString() else "0")
            is Map<*, *> -> {
                sb.append('{')
                var first = true
                for ((k, v) in value) {
                    if (!first) sb.append(',')
                    first = false
                    quote(sb, k.toString())
                    sb.append(':')
                    write(sb, v)
                }
                sb.append('}')
            }
            is Iterable<*> -> {
                sb.append('[')
                var first = true
                for (v in value) {
                    if (!first) sb.append(',')
                    first = false
                    write(sb, v)
                }
                sb.append(']')
            }
            is Array<*> -> write(sb, value.toList())
            else -> quote(sb, value.toString())
        }
    }

    private fun quote(sb: StringBuilder, s: String) {
        sb.append('"')
        for (ch in s) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\b' -> sb.append("\\b")
                '\u000C' -> sb.append("\\f")
                else -> if (ch < ' ') sb.append("\\u").append(ch.code.toString(16).padStart(4, '0'))
                else sb.append(ch)
            }
        }
        sb.append('"')
    }

    private class Parser(private val s: String) {
        var pos = 0
        fun done() = pos >= s.length
        fun skipWs() {
            while (pos < s.length && s[pos].isWhitespace()) pos++
        }

        fun value(): Any? = when (val c = peek()) {
            '{' -> obj()
            '[' -> array()
            '"' -> string()
            't' -> literal("true", true)
            'f' -> literal("false", false)
            'n' -> literal("null", null)
            else -> if (c == '-' || c.isDigit()) number() else error("unexpected '$c'")
        }

        private fun peek(): Char = if (pos < s.length) s[pos] else error("unexpected end")

        private fun error(msg: String): Nothing = throw IllegalArgumentException("JSON: $msg at $pos")

        private fun literal(text: String, value: Any?): Any? {
            if (!s.startsWith(text, pos)) error("expected $text")
            pos += text.length
            return value
        }

        private fun obj(): Map<String, Any?> {
            pos++ // {
            val map = LinkedHashMap<String, Any?>()
            skipWs()
            if (peek() == '}') { pos++; return map }
            while (true) {
                skipWs()
                if (peek() != '"') error("expected key")
                val key = string()
                skipWs()
                if (peek() != ':') error("expected ':'")
                pos++
                skipWs()
                map[key] = value()
                skipWs()
                when (peek()) {
                    ',' -> pos++
                    '}' -> { pos++; return map }
                    else -> error("expected ',' or '}'")
                }
            }
        }

        private fun array(): List<Any?> {
            pos++ // [
            val list = ArrayList<Any?>()
            skipWs()
            if (peek() == ']') { pos++; return list }
            while (true) {
                skipWs()
                list.add(value())
                skipWs()
                when (peek()) {
                    ',' -> pos++
                    ']' -> { pos++; return list }
                    else -> error("expected ',' or ']'")
                }
            }
        }

        private fun string(): String {
            pos++ // opening quote
            val sb = StringBuilder()
            while (true) {
                if (pos >= s.length) error("unterminated string")
                val c = s[pos++]
                when (c) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (pos >= s.length) error("bad escape")
                        when (val e = s[pos++]) {
                            '"' -> sb.append('"')
                            '\\' -> sb.append('\\')
                            '/' -> sb.append('/')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (pos + 4 > s.length) error("bad unicode escape")
                                val hex = s.substring(pos, pos + 4)
                                pos += 4
                                sb.append(hex.toInt(16).toChar())
                            }
                            else -> error("bad escape \\$e")
                        }
                    }
                    else -> sb.append(c)
                }
            }
        }

        private fun number(): Number {
            val start = pos
            if (peek() == '-') pos++
            while (pos < s.length && s[pos].isDigit()) pos++
            var isDouble = false
            if (pos < s.length && s[pos] == '.') {
                isDouble = true
                pos++
                while (pos < s.length && s[pos].isDigit()) pos++
            }
            if (pos < s.length && (s[pos] == 'e' || s[pos] == 'E')) {
                isDouble = true
                pos++
                if (pos < s.length && (s[pos] == '+' || s[pos] == '-')) pos++
                while (pos < s.length && s[pos].isDigit()) pos++
            }
            val text = s.substring(start, pos)
            return if (isDouble) text.toDouble() else text.toLongOrNull() ?: text.toDouble()
        }
    }
}

@Suppress("UNCHECKED_CAST")
internal fun Any?.asObj(): Map<String, Any?>? = this as? Map<String, Any?>

internal fun Map<String, Any?>.obj(key: String): Map<String, Any?>? = this[key].asObj()

internal fun Map<String, Any?>.arr(key: String): List<Any?>? = this[key] as? List<Any?>

internal fun Map<String, Any?>.str(key: String): String? = this[key] as? String

internal fun Map<String, Any?>.num(key: String): Long? = (this[key] as? Number)?.toLong()

internal fun Map<String, Any?>.int(key: String): Int? = (this[key] as? Number)?.toInt()

internal fun Map<String, Any?>.bool(key: String): Boolean? = this[key] as? Boolean
