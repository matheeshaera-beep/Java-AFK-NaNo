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

    private val NAMED_COLORS = mapOf(
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

    fun fromJson(json: String, styled: Boolean = false): String = try {
        flatten(Json.parse(json), styled)
    } catch (e: Exception) {
        json
    }

    fun fromNbt(reader: PacketReader, namedRoot: Boolean, styled: Boolean = false): String = try {
        flatten(Nbt.read(reader, namedRoot), styled)
    } catch (e: Exception) {
        ""
    }

    private const val MAX_DEPTH = 64

    fun flatten(value: Any?, styled: Boolean = false, depth: Int = 0): String {
        if (depth > MAX_DEPTH) return ""
        return when (value) {
            null -> ""
            is String -> value
            is Number, is Boolean -> value.toString()
            is List<*> -> value.joinToString("") { flatten(it, styled, depth + 1) }
            is Map<*, *> -> flattenComponent(value, styled, depth + 1)
            else -> ""
        }
    }

    private fun flattenComponent(map: Map<*, *>, styled: Boolean, depth: Int): String {
        val sb = StringBuilder()
        var appliedStyle = false
        if (styled) {
            val colorName = (map["color"] as? String)?.lowercase()
            val colorCode = colorName?.let { NAMED_COLORS[it] }
            if (colorCode != null) {
                sb.append('§').append(colorCode)
                appliedStyle = true
            }
            if (isTruthy(map["bold"])) {
                sb.append("§l")
                appliedStyle = true
            }
            if (isTruthy(map["italic"])) {
                sb.append("§o")
                appliedStyle = true
            }
            if (isTruthy(map["underlined"])) {
                sb.append("§n")
                appliedStyle = true
            }
            if (isTruthy(map["strikethrough"])) {
                sb.append("§m")
                appliedStyle = true
            }
        }
        val text = map["text"]
        if (text != null) sb.append(flatten(text, styled, depth))
        val translate = map["translate"] as? String
        if (translate != null) {
            val args = (map["with"] as? List<*>)?.map { flatten(it, styled, depth) } ?: emptyList()
            sb.append(translate(translate, args))
        }
        val extra = map["extra"] as? List<*>
        if (extra != null) for (part in extra) sb.append(flatten(part, styled, depth))
        // Fall back to a bare "contents" component (1.21.5+ item/entity names).
        if (sb.isEmpty() && map["contents"] != null) sb.append(flatten(map["contents"], styled, depth))
        if (appliedStyle && sb.length > 2) sb.append("§r")
        return sb.toString()
    }

    private fun isTruthy(value: Any?): Boolean = when (value) {
        is Boolean -> value
        is Number -> value.toInt() != 0
        is String -> value.equals("true", ignoreCase = true)
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
