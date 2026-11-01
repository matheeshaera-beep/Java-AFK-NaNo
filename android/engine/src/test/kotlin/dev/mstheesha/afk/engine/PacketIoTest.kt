package dev.mstheesha.afk.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PacketIoTest {
    private fun roundTripVarInt(value: Int) {
        val writer = PacketWriter(1)
        writer.writeVarInt(value)
        val reader = PacketReader(writer.buf, 0, writer.size)
        assertEquals(value, reader.readVarInt())
        assertEquals(0, reader.remaining())
    }

    @Test
    fun varIntRoundTrip() {
        for (value in listOf(0, 1, 2, 127, 128, 255, 300, 2097151, 2097152, Int.MAX_VALUE)) {
            roundTripVarInt(value)
        }
    }

    @Test
    fun varLongRoundTrip() {
        for (value in listOf(0L, 1L, 127L, 128L, 1L shl 40, Long.MAX_VALUE)) {
            val writer = PacketWriter(1)
            writer.writeVarLong(value)
            val reader = PacketReader(writer.buf, 0, writer.size)
            assertEquals(value, reader.readVarLong())
        }
    }

    @Test
    fun stringsAndArrays() {
        val writer = PacketWriter(1)
        writer.writeString("hello \u00e7\u00fc\u00df")
        writer.writeByteArray(byteArrayOf(1, 2, 3))
        writer.writeUuid(java.util.UUID(1L, 2L))
        val reader = PacketReader(writer.buf, 0, writer.size)
        assertEquals("hello \u00e7\u00fc\u00df", reader.readString())
        assertTrue(reader.readByteArray().contentEquals(byteArrayOf(1, 2, 3)))
        assertEquals(java.util.UUID(1L, 2L), reader.readUuid())
    }

    @Test
    fun readerRejectsOverrun() {
        val reader = PacketReader(byteArrayOf(1), 0, 1)
        assertFailsWith<ProtocolException> { reader.readInt() }
    }

    @Test
    fun writerGrows() {
        val writer = PacketWriter(1)
        repeat(1000) { writer.writeInt(it) }
        assertEquals(4000, writer.size)
    }
}
