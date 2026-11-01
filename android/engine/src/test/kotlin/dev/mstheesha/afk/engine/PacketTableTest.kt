package dev.mstheesha.afk.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PacketTableTest {
    @Test
    fun knownIdsFor18() {
        val table = assertNotNull(PacketTable.forProtocol(47))
        assertEquals(0, table.id(P.PLAY_S2C_KEEP_ALIVE))
        assertEquals(1, table.id(P.PLAY_S2C_JOIN_GAME))
        assertEquals(2, table.id(P.PLAY_S2C_CHAT))
        assertEquals(64, table.id(P.PLAY_S2C_DISCONNECT))
        assertEquals(8, table.id(P.PLAY_S2C_POSITION))
        assertEquals(0, table.id(P.HANDSHAKE_C2S_SET_PROTOCOL))
    }

    @Test
    fun modernProtocolsResolveCoreSlots() {
        for (protocol in listOf(340, 754, 759, 762, 766, 767, 769, 773, 774, 775)) {
            val table = assertNotNull(PacketTable.forProtocol(protocol), "protocol $protocol")
            for (slot in listOf(
                P.PLAY_S2C_KEEP_ALIVE,
                P.PLAY_S2C_JOIN_GAME,
                P.PLAY_S2C_DISCONNECT,
                P.PLAY_S2C_POSITION,
                P.PLAY_S2C_CHAT,
                P.PLAY_C2S_CHAT,
                P.PLAY_C2S_POSITION,
                P.LOGIN_C2S_LOGIN_START,
                P.LOGIN_S2C_ENCRYPTION_REQUEST,
                P.LOGIN_S2C_SUCCESS,
            )) {
                assertTrue(table.has(slot), "protocol $protocol slot $slot")
            }
        }
    }

    @Test
    fun versionNamesMapToProtocols() {
        assertEquals(47, PacketTable.protocolForName("1.8"))
        assertEquals(754, PacketTable.protocolForName("1.16.5"))
        assertEquals(774, PacketTable.protocolForName("1.21.11"))
        assertEquals(775, PacketTable.protocolForName("26.1"))
        assertEquals(775, PacketTable.latest.protocol)
    }

    @Test
    fun unsupportedProtocolsReturnNull() {
        assertNull(PacketTable.forProtocol(776))
        assertNull(PacketTable.forProtocol(1))
    }

    @Test
    fun srvLiteralDetection() {
        assertTrue(HostPolicy.isLiteral("127.0.0.1"))
        assertTrue(HostPolicy.isLiteral("localhost"))
        assertTrue(HostPolicy.isLiteral("::1"))
        assertTrue(HostPolicy.isLiteral("mcserver"))
        assertFalse(HostPolicy.isLiteral("play.example.com"))
        assertFalse(HostPolicy.isLiteral("999.1.1.1"))
    }

    @Test
    fun offlineUuidMatchesVanilla() {
        assertEquals(
            "b50ad385-829d-3141-a216-7e7d7539ba7f",
            Protocol.offlineUuid("Notch").toString(),
        )
    }
}
