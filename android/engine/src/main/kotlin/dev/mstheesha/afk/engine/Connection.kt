package dev.mstheesha.afk.engine

import java.nio.ByteBuffer
import java.nio.channels.SocketChannel
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

internal enum class ConnState { HANDSHAKE, STATUS, LOGIN, CONFIG, PLAY }

/**
 * One bot's socket, buffers, ciphers and frame decoder. Every method is called
 * from the engine thread only.
 */
internal class Connection(
    val channel: SocketChannel,
    private val bot: BotSession,
) {
    companion object {
        const val MAX_FRAME = 256 * 1024
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
    private var cryptBuf = ByteArray(0)
    private var loginFramesLogged = 0

    private val out = PacketWriter(256)
    private val frame = PacketWriter(WRITE_INITIAL)

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
                // JCE does not promise safe in-place CFB8, so decrypt through a
                // scratch buffer. Only encrypted (online-mode) bots allocate it.
                if (cryptBuf.size < n) cryptBuf = ByteArray(n)
                val produced = cipher.update(readBuf, readEnd, n, cryptBuf, 0)
                if (produced != n) {
                    // A provider that buffers partial input would silently
                    // shift every later frame; fail loudly instead.
                    throw ProtocolException("decrypt produced $produced of $n bytes")
                }
                System.arraycopy(cryptBuf, 0, readBuf, readEnd, n)
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
            // Use the width actually present on the wire, not varIntSize(value):
            // some proxies emit non-canonical (over-long) frame lengths.
            val headerInfo = readVarIntPacked(readStart, readEnd) ?: break
            val length = (headerInfo ushr 32).toInt()
            val header = (headerInfo and 0xFFFFFFFFL).toInt()
            if (length < 0 || length > MAX_FRAME) throw ProtocolException("frame length $length")
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
            var dataEnd = payloadEnd
            var inflated = false
            if (compressionThreshold >= 0) {
                val dataInfo = readVarIntPacked(payloadStart, payloadEnd)
                    ?: throw ProtocolException("missing data length")
                val dataLength = (dataInfo ushr 32).toInt()
                dataStart = payloadStart + (dataInfo and 0xFFFFFFFFL).toInt()
                if (dataLength > 0) {
                    val n = Compression.inflate(readBuf, dataStart, payloadEnd - dataStart, dataLength)
                    dataStart = 0
                    dataEnd = n
                    inflated = true
                }
            }
            val reader = if (inflated) PacketReader(Compression.scratch(), dataStart, dataEnd)
            else PacketReader(readBuf, dataStart, dataEnd)
            readStart = payloadEnd
            frames++
            if (reader.remaining() == 0) continue
            val id = reader.readVarInt()
            if (id !in 0..255) throw ProtocolException("packet id $id")
            if (!bot.allows(state, id)) continue
            if (!bot.handle(state, id, reader)) break
        }
        compact()
        return frames
    }

    fun send(id: Int, body: (PacketWriter) -> Unit = {}) {
        out.reset()
        out.writeVarInt(id)
        body(out)
        writeFrame()
    }

    private fun writeFrame() {
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
            if (cryptBuf.size < frame.size) cryptBuf = ByteArray(frame.size)
            val produced = cipher.update(frame.buf, 0, frame.size, cryptBuf, 0)
            if (produced != frame.size) {
                throw ProtocolException("encrypt produced $produced of ${frame.size} bytes")
            }
            System.arraycopy(cryptBuf, 0, frame.buf, 0, frame.size)
        }
        bytesOut += frame.size
        writeFully()
    }

    private fun writeFully() {
        if (writeQueue.isNotEmpty()) {
            writeQueue.addLast(frame.buf.copyOf(frame.size))
            return
        }
        var off = 0
        while (off < frame.size) {
            val n = try {
                channel.write(ByteBuffer.wrap(frame.buf, off, frame.size - off))
            } catch (e: java.io.IOException) {
                writeQueue.addLast(frame.buf.copyOfRange(off, frame.size))
                return
            }
            if (n <= 0) {
                writeQueue.addLast(frame.buf.copyOfRange(off, frame.size))
                return
            }
            off += n
        }
    }

    /** @return true when everything queued is on the wire. */
    fun flushPending(): Boolean {
        while (writeQueue.isNotEmpty()) {
            val head = writeQueue.first()
            var off = queueOffset
            while (off < head.size) {
                val n = try {
                    channel.write(ByteBuffer.wrap(head, off, head.size - off))
                } catch (e: java.io.IOException) {
                    return false
                }
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

    fun close() {
        try {
            channel.close()
        } catch (_: Exception) {
        }
        encrypt = null
        decrypt = null
        writeQueue.clear()
        queueOffset = 0
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
