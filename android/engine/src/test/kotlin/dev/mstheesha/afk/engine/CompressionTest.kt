package dev.mstheesha.afk.engine

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CompressionTest {
    @Test
    fun deflateInflateRoundTrip() {
        val payload = ByteArray(20_000) { (it * 31 % 251).toByte() }
        val size = Compression.deflate(payload, 0, payload.size)
        val inflated = Compression.inflate(Compression.deflated(), 0, size, payload.size)
        assertEquals(payload.size, inflated)
        assertEquals(payload.toList(), Compression.scratch().take(payload.size))
    }

    @Test
    fun rejectsOversizedExpectedLength() {
        assertFailsWith<ProtocolException> {
            Compression.inflate(ByteArray(4), 0, 4, Compression.MAX_INFLATED + 1)
        }
    }

    @Test
    fun rejectsWrongExpectedLength() {
        val payload = "hello hello hello".toByteArray()
        val size = Compression.deflate(payload, 0, payload.size)
        assertFailsWith<ProtocolException> {
            Compression.inflate(Compression.deflated(), 0, size, payload.size + 5)
        }
    }
}
