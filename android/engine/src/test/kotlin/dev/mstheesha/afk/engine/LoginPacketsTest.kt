package dev.mstheesha.afk.engine

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The encryption request layout is identical for 1.20.5 (766) and 26.1 (775);
 * the only variable is the trailing `shouldAuthenticate` flag.
 */
class LoginPacketsTest {
    private val publicKey = ByteArray(162) { (it * 7).toByte() }
    private val verifyToken = byteArrayOf(1, 2, 3, 4)

    private fun packet(protocol: Int, flag: Boolean?): PacketReader {
        val writer = PacketWriter()
        writer.writeString("server-id")
        writer.writeByteArray(publicKey)
        writer.writeByteArray(verifyToken)
        if (protocol >= 766 && flag != null) writer.writeBoolean(flag)
        return PacketReader(writer.buf, 0, writer.size)
    }

    private fun assertParsed(protocol: Int, flag: Boolean, expected: Boolean) {
        val reader = packet(protocol, flag)
        val request = LoginPacketCodec.readEncryptionRequest(protocol, reader)
        assertEquals("server-id", request.serverId)
        assertContentEquals(publicKey, request.publicKey)
        assertContentEquals(verifyToken, request.verifyToken)
        assertEquals(expected, request.shouldAuthenticate)
        assertEquals(0, reader.remaining(), "layout must consume the whole body")
    }

    @Test
    fun parsesAuthenticationFlagAt766() {
        assertParsed(766, flag = true, expected = true)
        assertParsed(766, flag = false, expected = false)
    }

    @Test
    fun parsesAuthenticationFlagAt775() {
        assertParsed(775, flag = true, expected = true)
        assertParsed(775, flag = false, expected = false)
    }

    @Test
    fun olderProtocolsAlwaysAuthenticate() {
        val reader = packet(765, null)
        val request = LoginPacketCodec.readEncryptionRequest(765, reader)
        assertTrue(request.shouldAuthenticate)
        assertEquals(0, reader.remaining(), "layout must consume the whole body")
    }
}
