package dev.mstheesha.afk

import org.json.JSONArray
import org.json.JSONObject

/**
 * Handles exporting and importing server configurations.
 * Strictly strips passwords, Microsoft tokens, and authentication secrets.
 */
object ServerBackup {

    /** Exports list of servers to pretty JSON string. Omits any credentials or database IDs. */
    fun exportToJson(servers: List<ServerEntity>): String {
        val array = JSONArray()
        for (server in servers) {
            val obj = JSONObject()
            obj.put("name", server.name)
            obj.put("host", server.host)
            obj.put("port", server.port)
            obj.put("username", server.username)
            obj.put("onlineMode", server.onlineMode)
            obj.put("chatCommand", server.chatCommand)
            obj.put("commandDelaySeconds", server.commandDelaySeconds)
            obj.put("viewDistance", server.viewDistance)
            obj.put("chatMode", server.chatMode)
            obj.put("autoJump", server.autoJump)
            obj.put("loopCommand", server.loopCommand)
            obj.put("protocolOverride", server.protocolOverride)
            obj.put("disconnectOnLowHealth", server.disconnectOnLowHealth)
            obj.put("lowHealthThreshold", server.lowHealthThreshold)
            obj.put("disconnectOnDeath", server.disconnectOnDeath)
            obj.put("disconnectOnKick", server.disconnectOnKick)
            obj.put("notifyOnMention", server.notifyOnMention)
            array.put(obj)
        }
        return array.toString(2)
    }

    /**
     * Parses JSON string (either an array or single object) into a list of ServerEntity.
     * All imported entities receive id = 0L so Room auto-generates fresh unique IDs.
     */
    fun importFromJson(jsonStr: String): List<ServerEntity> {
        val result = mutableListOf<ServerEntity>()
        val trimmed = jsonStr.trim()
        if (trimmed.isEmpty()) return emptyList()

        try {
            if (trimmed.startsWith("[")) {
                val array = JSONArray(trimmed)
                for (i in 0 until array.length()) {
                    val obj = array.optJSONObject(i) ?: continue
                    parseServerObject(obj)?.let { result.add(it) }
                }
            } else if (trimmed.startsWith("{")) {
                val obj = JSONObject(trimmed)
                parseServerObject(obj)?.let { result.add(it) }
            }
        } catch (_: Exception) {
            return emptyList()
        }

        return result
    }

    private fun parseServerObject(obj: JSONObject): ServerEntity? {
        val host = obj.optString("host").trim()
        if (host.isEmpty()) return null

        val name = obj.optString("name").trim().ifBlank { host }
        val port = obj.optInt("port", 25565).coerceIn(1, 65535)
        val username = obj.optString("username").trim()
        val onlineMode = obj.optBoolean("onlineMode", false)
        val chatCommand = obj.optString("chatCommand")
        val delay = obj.optInt("commandDelaySeconds", 5).coerceAtLeast(1)
        val viewDist = obj.optInt("viewDistance", 2).coerceIn(2, 12)
        val chatMode = obj.optString("chatMode", "enabled")
        val autoJump = obj.optBoolean("autoJump", true)
        val loopCommand = obj.optBoolean("loopCommand", false)
        val protocolOverride = obj.optInt("protocolOverride", 0).coerceAtLeast(0)
        val disconnectLowHealth = obj.optBoolean("disconnectOnLowHealth", false)
        val lowHealthThreshold = obj.optInt("lowHealthThreshold", 6).coerceIn(1, 20)
        val disconnectDeath = obj.optBoolean("disconnectOnDeath", false)
        val disconnectKick = obj.optBoolean("disconnectOnKick", false)
        val notifyMention = obj.optBoolean("notifyOnMention", true)

        return ServerEntity(
            id = 0L,
            name = name,
            host = host,
            port = port,
            chatCommand = chatCommand,
            commandDelaySeconds = delay,
            onlineMode = onlineMode,
            username = username,
            viewDistance = viewDist,
            chatMode = chatMode,
            autoJump = autoJump,
            loopCommand = loopCommand,
            protocolOverride = protocolOverride,
            disconnectOnLowHealth = disconnectLowHealth,
            lowHealthThreshold = lowHealthThreshold,
            disconnectOnDeath = disconnectDeath,
            disconnectOnKick = disconnectKick,
            notifyOnMention = notifyMention,
        )
    }
}
