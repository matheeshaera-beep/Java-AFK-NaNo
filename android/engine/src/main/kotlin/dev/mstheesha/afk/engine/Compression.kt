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

    private var inflater = Inflater()
    private var deflater = Deflater(1)
    private var scratch = ByteArray(16 * 1024)
    private var deflated = ByteArray(16 * 1024)

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
        if (scratch.size > 16 * 1024) scratch = ByteArray(16 * 1024)
        if (deflated.size > 16 * 1024) deflated = ByteArray(16 * 1024)
        inflater.end()
        deflater.end()
        inflater = Inflater()
        deflater = Deflater(1)
    }
}
