package dev.mstheesha.afk

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ServerBackupTest {

    @Test
    fun exportsServersWithoutTokensOrIds() {
        val servers = listOf(
            ServerEntity(
                id = 42L,
                name = "My SMP",
                host = "smp.example.com",
                port = 25565,
                chatCommand = "/afk",
                commandDelaySeconds = 10,
                onlineMode = true,
                username = "MinePlayer",
                viewDistance = 4,
                chatMode = "enabled",
                autoJump = true,
                loopCommand = true,
                protocolOverride = 767,
                disconnectOnLowHealth = true,
                lowHealthThreshold = 8,
                disconnectOnDeath = true,
                disconnectOnKick = false,
                notifyOnMention = true,
            ),
        )

        val json = ServerBackup.exportToJson(servers)

        // Must NOT contain internal ids or any token / auth keys
        assertFalse(json.contains("\"id\""))
        assertFalse(json.contains("token"))
        assertFalse(json.contains("password"))

        // Must contain server parameters
        assertTrue(json.contains("\"name\": \"My SMP\""))
        assertTrue(json.contains("\"host\": \"smp.example.com\""))
        assertTrue(json.contains("\"disconnectOnLowHealth\": true"))
        assertTrue(json.contains("\"lowHealthThreshold\": 8"))
    }

    @Test
    fun importsServersCleanlyWithZeroIds() {
        val json = """
            [
              {
                "name": "Imported Server",
                "host": "mc.imported.net",
                "port": 25565,
                "onlineMode": false,
                "username": "TestBot",
                "disconnectOnLowHealth": true,
                "lowHealthThreshold": 6
              }
            ]
        """.trimIndent()

        val imported = ServerBackup.importFromJson(json)
        assertEquals(1, imported.size)
        val server = imported[0]
        assertEquals(0L, server.id) // Room generates fresh ID
        assertEquals("Imported Server", server.name)
        assertEquals("mc.imported.net", server.host)
        assertTrue(server.disconnectOnLowHealth)
        assertEquals(6, server.lowHealthThreshold)
    }

    @Test
    fun handlesInvalidJsonGracefully() {
        val result = ServerBackup.importFromJson("invalid json text {{{")
        assertTrue(result.isEmpty())
    }
}
