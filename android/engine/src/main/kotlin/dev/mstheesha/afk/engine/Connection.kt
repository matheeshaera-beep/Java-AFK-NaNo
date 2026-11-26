package dev.mstheesha.afk.engine

import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

internal enum class ConnState { HANDSHAKE, STATUS, LOGIN, CONFIG, PLAY }

/**
 * Reusable scratch buffers owned by one [AfkEngine] and shared across all of
 * its [Connection]s on the single `afk-engine` thread.
 */
internal class EngineScratch {
    val out = PacketWriter(256)
    val frame = PacketWriter(Connection.WRITE_INITIAL)
    val reader = PacketReader()
    var cryptBuf = ByteArray(0)

    fun ensureCrypt(size: Int): ByteArray {
        if (cryptBuf.size < size) {
            var cap = if (cryptBuf.isEmpty()) 4096 else cryptBuf.size
            while (cap < size) cap = cap shl 1
            cryptBuf = ByteArray(cap)
        }
        return cryptBuf
    }

    fun maybeShrink() {
        out.shrinkIfLarge(256, 32 * 1024)
        frame.shrinkIfLarge(Connection.WRITE_INITIAL, 32 * 1024)
        if (cryptBuf.size > 32 * 1024) cryptBuf = ByteArray(4096)
    }

    fun shrink() {
        out.shrinkIfLarge(256, 1024)
        frame.shrinkIfLarge(Connection.WRITE_INITIAL, 4096)
        if (cryptBuf.size > 4096) cryptBuf = ByteArray(0)
    }
}

/**
 * One bot's socket, buffers, ciphers and frame decoder. Every method is called
 * from the engine thread only.
 */
internal class Connection(
    val channel: SocketChannel,
    private val bot: BotSession,
    private val scratch: EngineScratch = EngineScratch(),
) {
    companion object {
        const val MAX_FRAME = 256 * 1024
        /** Upper sanity cap for streaming-skipped frames (e.g. giant modded chunks/maps). */
        const val MAX_SKIP_FRAME = 16 * 1024 * 1024
        const val READ_INITIAL = 4096
        const val WRITE_INITIAL = 1024
    }

    var state = ConnState.HANDSHAKE
    var protocol = 0
    var compressionThreshold = -1
    var bytesIn = 0L
        private set
    var bytesOut = 0L
        private set

    private var encrypt: Cipher? = null
    private var decrypt: Cipher? = null

    private var readBuf = ByteArray(READ_INITIAL)
    private var readStart = 0
    private var readEnd = 0
    private var skipRemaining = 0
    private var loginFramesLogged = 0

    private val writeQueue = ArrayDeque<ByteArray>()
    private var queueOffset = 0

    fun enableEncryption(secret: ByteArray) {
        val key = SecretKeySpec(secret, "AES")
        val iv = IvParameterSpec(secret)
        encrypt = Cipher.getInstance("AES/CFB8/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, key, iv)
        }
        decrypt = Cipher.getInstance("AES/CFB8/NoPadding").apply {
            init(Cipher.DECRYPT_MODE, key, iv)
        }
    }

    fun setCompression(threshold: Int) {
        compressionThreshold = threshold
    }

    /** @return bytes read, 0 if the socket has nothing, -1 on EOF. */
    fun readFromChannel(): Int {
        ensureReadSpace()
        val n = try {
            channel.read(ByteBuffer.wrap(readBuf, readEnd, readBuf.size - readEnd))
        } catch (e: java.io.IOException) {
            return -1
        }
        if (n > 0) {
            bytesIn += n
            val cipher = decrypt
            if (cipher == null) {
                readEnd += n
            } else {
                // JCE does not promise safe in-place CFB8, so decrypt through the
                // engine's shared scratch buffer. Only encrypted bots use it.
                val crypt = scratch.ensureCrypt(n)
                val produced = cipher.update(readBuf, readEnd, n, crypt, 0)
                if (produced != n) {
                    // A provider that buffers partial input would silently
                    // shift every later frame; fail loudly instead.
                    throw ProtocolException("decrypt produced $produced of $n bytes")
                }
                System.arraycopy(crypt, 0, readBuf, readEnd, n)
                readEnd += n
            }
        }
        return n
    }

    /**
     * Decodes at most [maxFrames] frames and dispatches them.
     * @return number of frames handled.
     */
    fun processFrames(maxFrames: Int = 16): Int {
        var frames = 0
        while (frames < maxFrames) {
            // While an auth response is in flight the bot cannot handle packets;
            // stop before consuming one so it is decoded after the response.
            if (bot.isPaused()) break

            // Finish discarding an oversized frame (> MAX_FRAME) in-place without
            // allocating or disconnecting. Incoming bytes were already decrypted
            // in readFromChannel(), so AES/CFB8 stays synchronized.
            if (skipRemaining > 0) {
                val avail = readEnd - readStart
                if (avail <= 0) break
                val take = minOf(skipRemaining, avail)
                readStart += take
                skipRemaining -= take
                if (skipRemaining > 0) break
                frames++
                continue
            }

            // Use the width actually present on the wire, not varIntSize(value):
            // some proxies emit non-canonical (over-long) frame lengths.
            val headerInfo = readVarIntPacked(readStart, readEnd) ?: break
            val length = (headerInfo ushr 32).toInt()
            val header = (headerInfo and 0xFFFFFFFFL).toInt()
            if (length < 0 || length > MAX_SKIP_FRAME) throw ProtocolException("frame length $length")
            if (length > MAX_FRAME) {
                // Oversized frame (giant chunk/map/registry payload): skip its
                // bytes directly without buffering.
                readStart += header
                skipRemaining = length
                val avail = readEnd - readStart
                if (avail > 0) {
                    val take = minOf(skipRemaining, avail)
                    readStart += take
                    skipRemaining -= take
                }
                if (skipRemaining > 0) break
                frames++
                continue
            }
            if (readEnd - readStart < header + length) break
            val payloadStart = readStart + header
            val payloadEnd = payloadStart + length
            // Proxy logins are the only place where the frame format changes
            // mid-stream (encryption, then compression), so keep the first few
            // decrypted frames visible: they make "wrong packet id" reports
            // diagnosable without a packet capture.
            if (state == ConnState.LOGIN && loginFramesLogged < 8) {
                loginFramesLogged++
                bot.logExternal(
                    "Frame: len=$length comp=$compressionThreshold " +
                        "head=${hex(readStart, minOf(readEnd, readStart + 12))}",
                )
            }
            var dataStart = payloadStart
            val dataEnd = payloadEnd
            var compressed = false
            var dataLength = 0
            if (compressionThreshold >= 0) {
                val dataInfo = readVarIntPacked(payloadStart, payloadEnd)
                    ?: throw ProtocolException("missing data length")
                dataLength = (dataInfo ushr 32).toInt()
                dataStart = payloadStart + (dataInfo and 0xFFFFFFFFL).toInt()
                compressed = dataLength > 0
            }

            readStart = payloadEnd
            frames++

            if (compressed) {
                // Two-stage inflate: peek the VarInt packet ID first and abort
                // decompression immediately for disallowed packets (chunks, etc.).
                val inflatedLen = Compression.inflateIfAllowed(
                    readBuf,
                    dataStart,
                    payloadEnd - dataStart,
                    dataLength,
                ) { id -> bot.allows(state, id) }
                if (inflatedLen <= 0) continue
                val id = Compression.lastPacketId
                val reader = scratch.reader.reset(
                    Compression.scratch(),
                    Compression.lastHeaderLen,
                    inflatedLen,
                )
                val keepGoing = bot.handle(state, id, reader)
                if (inflatedLen > 64 * 1024) Compression.maybeShrink()
                if (!keepGoing) break
            } else {
                if (dataStart >= dataEnd) continue
                val idInfo = readVarIntPacked(dataStart, dataEnd)
                    ?: throw ProtocolException("truncated packet id")
                val id = (idInfo ushr 32).toInt()
                val idLen = (idInfo and 0xFFFFFFFFL).toInt()
                if (id !in 0..255) throw ProtocolException("packet id $id")
                if (!bot.allows(state, id)) continue
                val reader = scratch.reader.reset(readBuf, dataStart + idLen, dataEnd)
                if (!bot.handle(state, id, reader)) break
            }
        }
        compact()
        return frames
    }

    fun send(id: Int, body: (PacketWriter) -> Unit = {}) {
        val out = scratch.out
        out.reset()
        out.writeVarInt(id)
        body(out)
        writeFrame()
    }

    private fun writeFrame() {
        val out = scratch.out
        val frame = scratch.frame
        frame.reset()
        val threshold = compressionThreshold
        if (threshold >= 0 && out.size >= threshold) {
            val deflatedSize = Compression.deflate(out.buf, 0, out.size)
            // Data Length is the UNCOMPRESSED size; the compressed size is only
            // implied by the outer frame length.
            frame.writeVarInt(varIntSize(out.size) + deflatedSize)
            frame.writeVarInt(out.size)
            frame.writeBytes(Compression.deflated(), 0, deflatedSize)
        } else {
            if (threshold >= 0) {
                frame.writeVarInt(varIntSize(0) + out.size)
                frame.writeVarInt(0)
            } else {
                frame.writeVarInt(out.size)
            }
            frame.writeBytes(out.buf, 0, out.size)
        }
        val cipher = encrypt
        if (cipher != null) {
            val crypt = scratch.ensureCrypt(frame.size)
            val produced = cipher.update(frame.buf, 0, frame.size, crypt, 0)
            if (produced != frame.size) {
                throw ProtocolException("encrypt produced $produced of ${frame.size} bytes")
            }
            System.arraycopy(crypt, 0, frame.buf, 0, frame.size)
        }
        bytesOut += frame.size
        writeFully(frame)
        if (frame.size > 32 * 1024) scratch.maybeShrink()
    }

    private fun writeFully(frame: PacketWriter) {
        if (writeQueue.isNotEmpty()) {
            writeQueue.addLast(frame.buf.copyOf(frame.size))
            return
        }
        var off = 0
        while (off < frame.size) {
            // A failed write means the socket is gone. Propagate instead of
            // queueing bytes that can never be flushed.
            val n = channel.write(ByteBuffer.wrap(frame.buf, off, frame.size - off))
            if (n <= 0) {
                writeQueue.addLast(frame.buf.copyOfRange(off, frame.size))
                return
            }
            off += n
        }
    }

    /**
     * @return true when everything queued is on the wire.
     * @throws java.io.IOException when the peer is gone; the engine turns that
     * into a reconnect instead of waiting for a writable event that never comes.
     */
    fun flushPending(): Boolean {
        while (writeQueue.isNotEmpty()) {
            val head = writeQueue.first()
            var off = queueOffset
            while (off < head.size) {
                val n = channel.write(ByteBuffer.wrap(head, off, head.size - off))
                if (n <= 0) {
                    queueOffset = off
                    return false
                }
                off += n
            }
            writeQueue.removeFirst()
            queueOffset = 0
        }
        return true
    }

    fun hasPendingWrite(): Boolean = writeQueue.isNotEmpty()

    private fun ensureReadSpace() {
        if (readEnd < readBuf.size) return
        if (readStart > 0) {
            val len = readEnd - readStart
            System.arraycopy(readBuf, readStart, readBuf, 0, len)
            readStart = 0
            readEnd = len
            return
        }
        if (readBuf.size >= MAX_FRAME + 16) throw ProtocolException("read buffer full")
        readBuf = readBuf.copyOf(minOf(readBuf.size * 2, MAX_FRAME + 16))
    }

    private fun compact() {
        if (readStart == readEnd) {
            readStart = 0
            readEnd = 0
            if (readBuf.size > 32 * 1024) readBuf = ByteArray(READ_INITIAL)
        } else if (readStart > 0) {
            val len = readEnd - readStart
            System.arraycopy(readBuf, readStart, readBuf, 0, len)
            readStart = 0
            readEnd = len
        }
    }

    fun shrinkBuffers() {
        if (readStart == readEnd && readBuf.size > READ_INITIAL) {
            readBuf = ByteArray(READ_INITIAL)
            readStart = 0
            readEnd = 0
        }
    }

    fun close() {
        try {
            channel.close()
        } catch (_: Exception) {
        }
        encrypt = null
        decrypt = null
        writeQueue.clear()
        queueOffset = 0
        skipRemaining = 0
    }

    /**
     * Reads a VarInt at [start] without assuming a canonical encoding.
     *
     * @return the value in the high 32 bits and the number of bytes consumed in
     * the low 32 bits, or null when the varint is not fully buffered yet.
     */
    private fun readVarIntPacked(start: Int, end: Int): Long? {
        var result = 0
        var shift = 0
        var pos = start
        while (true) {
            if (pos >= end) return null
            val b = readBuf[pos++].toInt()
            result = result or ((b and 0x7F) shl shift)
            if (b and 0x80 == 0) {
                return (result.toLong() shl 32) or (pos - start).toLong()
            }
            shift += 7
            if (shift > 35) throw ProtocolException("VarInt too big")
        }
    }

    private fun varIntSize(value: Int): Int {
        var v = value
        var n = 1
        while (v and 0x7F.inv() != 0) {
            v = v ushr 7
            n++
        }
        return n
    }

    private fun hex(start: Int, end: Int): String {
        if (end <= start) return "-"
        val sb = StringBuilder((end - start) * 3)
        for (i in start until end) {
            if (i > start) sb.append(' ')
            val b = readBuf[i].toInt() and 0xFF
            if (b < 0x10) sb.append('0')
            sb.append(Integer.toHexString(b))
        }
        return sb.toString()
    }
}
