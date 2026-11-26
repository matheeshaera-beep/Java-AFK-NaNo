package dev.mstheesha.afk.engine

/** Turns JSON or NBT chat components into one plain-text line. */
internal object ChatText {

    private val TRANSLATIONS = mapOf(
        "chat.type.text" to "<%s> %s",
        "chat.type.announcement" to "[%s] %s",
        "chat.type.emote" to "* %s %s",
        "chat.type.admin" to "[%s: %s]",
        "chat.type.achievement" to "%s has made the advancement %s",
        "chat.type.advancement.task" to "%s has made the advancement %s",
        "chat.type.advancement.goal" to "%s has reached the goal %s",
        "chat.type.advancement.challenge" to "%s has completed the challenge %s",
        "multiplayer.player.joined" to "%s joined the game",
        "multiplayer.player.left" to "%s left the game",
        "multiplayer.player.joined.renamed" to "%s joined the game",
        "commands.message.display.incoming" to "%s whispers to you: %s",
        "commands.message.display.outgoing" to "You whisper to %s: %s",
    )

    private val COLOR_CODES = mapOf(
        "black" to '0',
        "dark_blue" to '1',
        "dark_green" to '2',
        "dark_aqua" to '3',
        "dark_red" to '4',
        "dark_purple" to '5',
        "gold" to '6',
        "gray" to '7',
        "dark_gray" to '8',
        "blue" to '9',
        "green" to 'a',
        "aqua" to 'b',
        "red" to 'c',
        "light_purple" to 'd',
        "yellow" to 'e',
        "white" to 'f',
    )

    fun fromJson(json: String): String = try {
        stripFormatting(flatten(Json.parse(json)))
    } catch (e: Exception) {
        stripFormatting(json)
    }

    fun fromJsonStyled(json: String): String = try {
        flattenStyled(Json.parse(json))
    } catch (e: Exception) {
        json
    }

    fun fromNbt(reader: PacketReader, namedRoot: Boolean): String = try {
        stripFormatting(flatten(Nbt.read(reader, namedRoot)))
    } catch (e: Exception) {
        ""
    }

    fun fromNbtStyled(reader: PacketReader, namedRoot: Boolean): String = try {
        flattenStyled(Nbt.read(reader, namedRoot))
    } catch (e: Exception) {
        ""
    }

    /** Strips legacy `§x` and extended `§#rrggbb` color/style markers. */
    fun stripFormatting(text: String): String {
        if (text.indexOf('§') < 0) return text
        val sb = StringBuilder(text.length)
        var i = 0
        while (i < text.length) {
            if (text[i] == '§' && i + 1 < text.length) {
                val next = text[i + 1]
                if (next == '#' && i + 7 < text.length) {
                    i += 8
                    continue
                }
                i += 2
                continue
            }
            sb.append(text[i++])
        }
        return sb.toString()
    }

    fun flatten(value: Any?): String = when (value) {
        null -> ""
        is String -> value
        is Number, is Boolean -> value.toString()
        is List<*> -> value.joinToString("") { flatten(it) }
        is Map<*, *> -> flattenComponent(value)
        else -> ""
    }

    fun flattenStyled(value: Any?): String = when (value) {
        null -> ""
        is String -> value
        is Number, is Boolean -> value.toString()
        is List<*> -> value.joinToString("") { flattenStyled(it) }
        is Map<*, *> -> flattenStyledComponent(value)
        else -> ""
    }

    private fun flattenComponent(map: Map<*, *>): String {
        val sb = StringBuilder()
        val text = map["text"]
        if (text != null) sb.append(flatten(text))
        val translate = map["translate"] as? String
        if (translate != null) {
            val args = (map["with"] as? List<*>)?.map { flatten(it) } ?: emptyList()
            sb.append(translate(translate, args))
        }
        val extra = map["extra"] as? List<*>
        if (extra != null) for (part in extra) sb.append(flatten(part))
        // Fall back to a bare "contents" component (1.21.5+ item/entity names).
        if (sb.isEmpty() && map["contents"] != null) sb.append(flatten(map["contents"]))
        return sb.toString()
    }

    private fun flattenStyledComponent(map: Map<*, *>): String {
        val prefix = StringBuilder()
        val color = map["color"] as? String
        if (color != null) {
            val code = COLOR_CODES[color.lowercase()]
            if (code != null) {
                prefix.append('§').append(code)
            } else if (color.length == 7 && color[0] == '#') {
                prefix.append('§').append(color)
            }
        }
        if (isTruthy(map["bold"])) prefix.append("§l")
        if (isTruthy(map["italic"])) prefix.append("§o")
        if (isTruthy(map["underlined"])) prefix.append("§n")
        if (isTruthy(map["strikethrough"])) prefix.append("§m")

        val body = StringBuilder()
        val text = map["text"]
        if (text != null) body.append(flattenStyled(text))
        val translate = map["translate"] as? String
        if (translate != null) {
            val args = (map["with"] as? List<*>)?.map { flattenStyled(it) } ?: emptyList()
            body.append(translate(translate, args))
        }
        val extra = map["extra"] as? List<*>
        if (extra != null) {
            for (part in extra) {
                if (prefix.isNotEmpty()) body.append(prefix)
                body.append(flattenStyled(part))
            }
        }
        if (body.isEmpty() && map["contents"] != null) body.append(flattenStyled(map["contents"]))
        if (body.isEmpty()) return ""
        return if (prefix.isEmpty()) {
            body.toString()
        } else {
            "$prefix$body§r"
        }
    }

    private fun isTruthy(v: Any?): Boolean = when (v) {
        is Boolean -> v
        is Number -> v.toInt() != 0
        else -> false
    }

    private fun translate(key: String, args: List<String>): String {
        val format = TRANSLATIONS[key]
        if (format == null) {
            return if (args.isEmpty()) key else args.joinToString(" ")
        }
        val sb = StringBuilder()
        var argIndex = 0
        var i = 0
        while (i < format.length) {
            if (format[i] == '%' && i + 1 < format.length && format[i + 1] == 's') {
                sb.append(args.getOrElse(argIndex++) { "" })
                i += 2
            } else {
                sb.append(format[i++])
            }
        }
        return sb.toString()
    }
}
