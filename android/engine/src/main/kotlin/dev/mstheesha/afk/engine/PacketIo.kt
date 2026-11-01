package dev.mstheesha.afk.engine

import java.util.UUID

/** Growable write buffer used to assemble one packet body. */
internal class PacketWriter(initial: Int = 256) {
    var buf = ByteArray(initial)
        private set
    var size = 0
        private set

    fun reset() {
        size = 0
    }

    private fun ensure(extra: Int) {
        if (size + extra <= buf.size) return
        var cap = buf.size
        while (cap < size + extra) cap = cap shl 1
        buf = buf.copyOf(cap)
    }

    fun writeByte(value: Int) {
        ensure(1)
        buf[size++] = value.toByte()
    }

    fun writeBoolean(value: Boolean) = writeByte(if (value) 1 else 0)

    fun writeShort(value: Int) {
        ensure(2)
        buf[size++] = (value ushr 8).toByte()
        buf[size++] = value.toByte()
    }

    fun writeInt(value: Int) {
        ensure(4)
        buf[size++] = (value ushr 24).toByte()
        buf[size++] = (value ushr 16).toByte()
        buf[size++] = (value ushr 8).toByte()
        buf[size++] = value.toByte()
    }

    fun writeLong(value: Long) {
        writeInt((value ushr 32).toInt())
        writeInt(value.toInt())
    }

    fun writeFloat(value: Float) = writeInt(value.toRawBits())

    fun writeDouble(value: Double) = writeLong(value.toRawBits())

    fun writeVarInt(value: Int) {
        var v = value
        ensure(5)
        while (v and 0x7F.inv() != 0) {
            buf[size++] = ((v and 0x7F) or 0x80).toByte()
            v = v ushr 7
        }
        buf[size++] = v.toByte()
    }

    fun writeVarLong(value: Long) {
        var v = value
        ensure(10)
        while (v and 0x7FL.inv() != 0L) {
            buf[size++] = ((v and 0x7F) or 0x80).toByte()
            v = v ushr 7
        }
        buf[size++] = v.toByte()
    }

    fun writeBytes(bytes: ByteArray) {
        ensure(bytes.size)
        System.arraycopy(bytes, 0, buf, size, bytes.size)
        size += bytes.size
    }

    fun writeBytes(bytes: ByteArray, offset: Int, length: Int) {
        ensure(length)
        System.arraycopy(bytes, offset, buf, size, length)
        size += length
    }

    fun writeString(value: String) {
        val bytes = value.toByteArray(Charsets.UTF_8)
        writeVarInt(bytes.size)
        writeBytes(bytes)
    }

    fun writeByteArray(bytes: ByteArray) {
        writeVarInt(bytes.size)
        writeBytes(bytes)
    }

    fun writeUuid(uuid: UUID) {
        writeLong(uuid.mostSignificantBits)
        writeLong(uuid.leastSignificantBits)
    }
}

/** Zero-copy view over one decoded frame. */
internal class PacketReader(
    private val buf: ByteArray,
    private var pos: Int,
    private val end: Int,
) {
    fun remaining(): Int = end - pos

    fun readVarInt(): Int {
        var result = 0
        var shift = 0
        while (true) {
            if (pos >= end) throw ProtocolException("truncated VarInt")
            val b = buf[pos++].toInt()
            result = result or ((b and 0x7F) shl shift)
            if (b and 0x80 == 0) return result
            shift += 7
            if (shift > 35) throw ProtocolException("VarInt too big")
        }
    }

    fun readVarLong(): Long {
        var result = 0L
        var shift = 0
        while (true) {
            if (pos >= end) throw ProtocolException("truncated VarLong")
            val b = buf[pos++].toInt()
            result = result or ((b and 0x7F).toLong() shl shift)
            if (b and 0x80 == 0) return result
            shift += 7
            if (shift > 70) throw ProtocolException("VarLong too big")
        }
    }

    fun readByte(): Byte {
        if (pos >= end) throw ProtocolException("truncated byte")
        return buf[pos++]
    }

    fun readUnsignedByte(): Int {
        if (pos >= end) throw ProtocolException("truncated byte")
        return buf[pos++].toInt() and 0xFF
    }

    fun readBoolean(): Boolean = readUnsignedByte() != 0

    fun readShort(): Int {
        if (pos + 2 > end) throw ProtocolException("truncated short")
        val v = ((buf[pos].toInt() and 0xFF) shl 8) or (buf[pos + 1].toInt() and 0xFF)
        pos += 2
        return v.toShort().toInt()
    }

    fun readUnsignedShort(): Int {
        if (pos + 2 > end) throw ProtocolException("truncated short")
        val v = ((buf[pos].toInt() and 0xFF) shl 8) or (buf[pos + 1].toInt() and 0xFF)
        pos += 2
        return v
    }

    fun readInt(): Int {
        if (pos + 4 > end) throw ProtocolException("truncated int")
        val v = ((buf[pos].toInt() and 0xFF) shl 24) or
            ((buf[pos + 1].toInt() and 0xFF) shl 16) or
            ((buf[pos + 2].toInt() and 0xFF) shl 8) or
            (buf[pos + 3].toInt() and 0xFF)
        pos += 4
        return v
    }

    fun readLong(): Long {
        val hi = readInt().toLong() and 0xFFFFFFFFL
        val lo = readInt().toLong() and 0xFFFFFFFFL
        return (hi shl 32) or lo
    }

    fun readFloat(): Float = Float.fromBits(readInt())

    fun readDouble(): Double = Double.fromBits(readLong())

    fun readString(maxBytes: Int = 1 shl 21): String {
        val len = readVarInt()
        if (len < 0 || len > maxBytes || pos + len > end) {
            throw ProtocolException("bad string length $len")
        }
        val s = String(buf, pos, len, Charsets.UTF_8)
        pos += len
        return s
    }

    fun readByteArray(maxBytes: Int = 1 shl 21): ByteArray {
        val len = readVarInt()
        if (len < 0 || len > maxBytes || pos + len > end) {
            throw ProtocolException("bad byte array length $len")
        }
        val out = buf.copyOfRange(pos, pos + len)
        pos += len
        return out
    }

    fun readUuid(): UUID {
        val hi = readLong()
        val lo = readLong()
        return UUID(hi, lo)
    }

    fun skip(n: Int) {
        if (n < 0 || pos + n > end) throw ProtocolException("skip past end")
        pos += n
    }

    fun position(): Int = pos
    fun limit(): Int = end
    fun backing(): ByteArray = buf
}

internal class ProtocolException(message: String) : Exception(message)
