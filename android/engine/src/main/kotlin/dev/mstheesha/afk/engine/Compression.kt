package dev.mstheesha.afk.engine

import java.util.zip.DataFormatException
import java.util.zip.Deflater
import java.util.zip.Inflater

/**
 * One Inflater, one Deflater(level 1) and one scratch array shared by every bot.
 * Only ever touched from the engine thread.
 */
internal object Compression {
    const val MAX_INFLATED = 4 * 1024 * 1024
    private const val SCRATCH_INITIAL = 16 * 1024
    private const val SHRINK_THRESHOLD = 64 * 1024

    private var inflater = Inflater()
    private var deflater = Deflater(1)
    private var scratch = ByteArray(SCRATCH_INITIAL)
    private var deflated = ByteArray(SCRATCH_INITIAL)
    private val headBuf = ByteArray(8)

    /** Packet ID and header length decoded by the most recent [inflateIfAllowed]. */
    var lastPacketId: Int = -1
        private set
    var lastHeaderLen: Int = 0
        private set

    fun scratch(): ByteArray = scratch

    fun ensureScratch(size: Int) {
        if (scratch.size < size) {
            var cap = scratch.size
            while (cap < size) cap = cap shl 1
            scratch = ByteArray(minOf(cap, MAX_INFLATED))
        }
    }

    /** Inflates [len] bytes of [src] into the shared scratch; returns the length. */
    fun inflate(src: ByteArray, off: Int, len: Int, expected: Int): Int {
        if (expected < 0 || expected > MAX_INFLATED) throw ProtocolException("inflated size $expected")
        if (expected == 0) return 0
        ensureScratch(expected)
        inflater.reset()
        inflater.setInput(src, off, len)
        var written = 0
        try {
            while (!inflater.finished()) {
                if (written >= scratch.size) {
                    if (scratch.size >= MAX_INFLATED) throw ProtocolException("inflated too big")
                    ensureScratch(scratch.size * 2)
                }
                val n = inflater.inflate(scratch, written, scratch.size - written)
                if (n == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) break
                }
                written += n
            }
        } catch (e: DataFormatException) {
            throw ProtocolException("bad compressed frame: ${e.message}")
        }
        if (written != expected) throw ProtocolException("inflated $written, expected $expected")
        return written
    }

    /**
     * Two-stage partial inflate: inflates only the leading VarInt packet ID
     * (at most 5 bytes) and asks [allow] whether the packet is needed.
     *
     * If disallowed (chunks, entities, lighting, particles — >95% of traffic),
     * returns `-1` immediately without inflating the remaining payload or
     * growing [scratch].
     *
     * When allowed, finishes inflating into [scratch] and returns the total
     * uncompressed length (with [lastPacketId] and [lastHeaderLen] populated).
     */
    fun inflateIfAllowed(
        src: ByteArray,
        off: Int,
        len: Int,
        expected: Int,
        allow: (packetId: Int) -> Boolean,
    ): Int {
        if (expected < 0 || expected > MAX_INFLATED) throw ProtocolException("inflated size $expected")
        if (expected == 0) return 0
        inflater.reset()
        inflater.setInput(src, off, len)

        // Stage 1: inflate just enough bytes to decode the VarInt packet ID.
        val headTarget = minOf(5, expected)
        var headLen = 0
        var packetId = 0
        var shift = 0
        var idPos = 0
        var idDone = false
        try {
            while (!idDone) {
                while (idPos < headLen) {
                    val b = headBuf[idPos++].toInt()
                    packetId = packetId or ((b and 0x7F) shl shift)
                    if (b and 0x80 == 0) {
                        idDone = true
                        break
                    }
                    shift += 7
                    if (shift > 35) throw ProtocolException("VarInt too big")
                }
                if (idDone) break
                if (headLen >= headTarget || inflater.finished()) {
                    throw ProtocolException("truncated packet id")
                }
                val n = inflater.inflate(headBuf, headLen, headTarget - headLen)
                if (n == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) {
                        throw ProtocolException("truncated compressed packet id")
                    }
                }
                headLen += n
            }
        } catch (e: DataFormatException) {
            throw ProtocolException("bad compressed frame: ${e.message}")
        }

        if (packetId !in 0..255) throw ProtocolException("packet id $packetId")
        lastPacketId = packetId
        lastHeaderLen = idPos

        if (!allow(packetId)) {
            return -1
        }

        // Stage 2: packet is needed; finish inflating into scratch.
        ensureScratch(expected)
        System.arraycopy(headBuf, 0, scratch, 0, headLen)
        var written = headLen
        try {
            while (!inflater.finished()) {
                if (written >= scratch.size) {
                    if (scratch.size >= MAX_INFLATED) throw ProtocolException("inflated too big")
                    ensureScratch(scratch.size * 2)
                }
                val n = inflater.inflate(scratch, written, scratch.size - written)
                if (n == 0) {
                    if (inflater.needsInput() || inflater.needsDictionary()) break
                }
                written += n
            }
        } catch (e: DataFormatException) {
            throw ProtocolException("bad compressed frame: ${e.message}")
        }
        if (written != expected) throw ProtocolException("inflated $written, expected $expected")
        return written
    }

    /** Release oversized scratch memory promptly after a large frame is handled. */
    fun maybeShrink() {
        if (scratch.size > SHRINK_THRESHOLD) scratch = ByteArray(SCRATCH_INITIAL)
        if (deflated.size > SHRINK_THRESHOLD) deflated = ByteArray(SCRATCH_INITIAL)
    }

    /** Deflates [len] bytes of [src]; returns the written length. */
    fun deflate(src: ByteArray, off: Int, len: Int): Int {
        val bound = (len + (len shr 3) + 64).coerceAtLeast(64)
        if (deflated.size < bound) deflated = ByteArray(bound)
        deflater.reset()
        deflater.setLevel(1)
        deflater.setInput(src, off, len)
        deflater.finish()
        var written = 0
        while (!deflater.finished()) {
            val n = deflater.deflate(deflated, written, deflated.size - written)
            if (n == 0) break
            written += n
        }
        if (written == 0) throw ProtocolException("deflate produced nothing")
        return written
    }

    /** Output of the last [deflate] call; valid until the next one. */
    fun deflated(): ByteArray = deflated

    fun shrink() {
        if (scratch.size > SCRATCH_INITIAL) scratch = ByteArray(SCRATCH_INITIAL)
        if (deflated.size > SCRATCH_INITIAL) deflated = ByteArray(SCRATCH_INITIAL)
        inflater.end()
        deflater.end()
        inflater = Inflater()
        deflater = Deflater(1)
    }
}
