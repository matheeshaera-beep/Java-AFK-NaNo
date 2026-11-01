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

    fun fromJson(json: String): String = try {
        flatten(Json.parse(json))
    } catch (e: Exception) {
        json
    }

    fun fromNbt(reader: PacketReader, namedRoot: Boolean): String = try {
        flatten(Nbt.read(reader, namedRoot))
    } catch (e: Exception) {
        ""
    }

    fun flatten(value: Any?): String = when (value) {
        null -> ""
        is String -> value
        is Number, is Boolean -> value.toString()
        is List<*> -> value.joinToString("") { flatten(it) }
        is Map<*, *> -> flattenComponent(value)
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
