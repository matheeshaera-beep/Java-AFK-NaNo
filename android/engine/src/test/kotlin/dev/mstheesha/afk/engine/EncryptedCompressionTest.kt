package dev.mstheesha.afk.engine

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.security.KeyPairGenerator
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.Deflater
import java.util.zip.Inflater
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Online-mode style login: RSA handshake, AES/CFB8, then Set Compression and a
 * large Login Plugin Request in the compressed format. This is the exact path
 * that donutsmp (Velocity) takes after "Compression enabled".
 */
class EncryptedCompressionTest {
    private val protocol = 775
    private val table = PacketTable.forProtocol(protocol)!!

    private class Recorder : BotListener {
        val snapshots = CopyOnWriteArrayList<BotSnapshot>()
        val logs = CopyOnWriteArrayList<String>()

        override fun onSnapshot(snapshot: BotSnapshot) {
            snapshots.add(snapshot)
        }

        override fun onLog(line: String) {
            logs.add(line)
        }

        override fun onChat(line: ChatLine) = Unit
        override fun onAuthRequired(url: String, code: String) = Unit
        fun latest(): BotSnapshot? = snapshots.lastOrNull()
    }

    /** Byte-at-a-time AES/CFB8 decrypting input; CFB8 is a stream cipher. */
    private class CipherIn(private val raw: InputStream) : InputStream() {
        var cipher: Cipher? = null
        private val one = ByteArray(1)
        private val out = ByteArray(1)

        override fun read(): Int {
            if (raw.read(one) != 1) return -1
            val c = cipher ?: return one[0].toInt() and 0xFF
            c.update(one, 0, 1, out, 0)
            return out[0].toInt() and 0xFF
        }
    }

    private class CipherOut(private val raw: OutputStream) : OutputStream() {
        var cipher: Cipher? = null
        private val one = ByteArray(1)
        private val out = ByteArray(1)

        override fun write(b: Int) {
            val c = cipher
            if (c == null) {
                raw.write(b)
                return
            }
            one[0] = b.toByte()
            c.update(one, 0, 1, out, 0)
            raw.write(out[0].toInt())
        }

        override fun flush() {
            raw.flush()
        }
    }

    private inner class ServerConn(socket: java.net.Socket) {
        val cin = CipherIn(socket.getInputStream())
        val cout = CipherOut(socket.getOutputStream())
        private val input = DataInputStream(cin)
        private val output = DataOutputStream(cout)
        var threshold = -1

        /** Some proxies write frame lengths as an over-long varint (extra 0x00). */
        var overlongLength = false

        fun writeFrameLength(frame: PacketWriter, value: Int) {
            if (!overlongLength) {
                frame.writeVarInt(value)
                return
            }
            var v = value
            while (v > 0) {
                frame.writeByte((v and 0x7F) or 0x80)
                v = v ushr 7
            }
            frame.writeByte(0)
        }

        fun readFrame(): Pair<Int, PacketReader>? {
            val length = try {
                readVarInt(input)
            } catch (_: EOFException) {
                return null
            }
            if (length <= 0 || length > 1 shl 20) return null
            val body = ByteArray(length)
            input.readFully(body)
            var buf = body
            var start = 0
            var end = body.size
            if (threshold >= 0) {
                val probe = PacketReader(body, 0, body.size)
                val dataLength = probe.readVarInt()
                start = probe.position()
                if (dataLength > 0) {
                    buf = inflate(body, start, body.size - start, dataLength)
                    start = 0
                    end = dataLength
                }
            }
            val reader = PacketReader(buf, start, end)
            return reader.readVarInt() to reader
        }

        fun buildFrame(id: Int, body: (PacketWriter) -> Unit): ByteArray {
            val packet = PacketWriter()
            packet.writeVarInt(id)
            body(packet)
            val frame = PacketWriter()
            if (threshold >= 0) {
                if (packet.size >= threshold) {
                    val deflated = deflate(packet.buf, 0, packet.size)
                    writeFrameLength(frame, varIntSize(packet.size) + deflated.size)
                    frame.writeVarInt(packet.size)
                    frame.writeBytes(deflated)
                } else {
                    writeFrameLength(frame, varIntSize(0) + packet.size)
                    frame.writeVarInt(0)
                    frame.writeBytes(packet.buf, 0, packet.size)
                }
            } else {
                writeFrameLength(frame, packet.size)
                frame.writeBytes(packet.buf, 0, packet.size)
            }
            return frame.buf.copyOf(frame.size)
        }

        fun send(id: Int, body: (PacketWriter) -> Unit = {}) {
            writeRaw(buildFrame(id, body))
        }

        fun writeRaw(bytes: ByteArray) {
            output.write(bytes, 0, bytes.size)
            output.flush()
        }
    }

    private fun readVarInt(input: DataInputStream): Int {
        var result = 0
        var shift = 0
        while (true) {
            val b = input.readUnsignedByte()
            result = result or ((b and 0x7F) shl shift)
            if (b and 0x80 == 0) return result
            shift += 7
            if (shift > 35) throw IllegalStateException("bad varint")
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

    private fun deflate(src: ByteArray, off: Int, len: Int): ByteArray {
        val deflater = Deflater(1)
        deflater.setInput(src, off, len)
        deflater.finish()
        val out = ByteArray(len + (len shr 3) + 64)
        var written = 0
        while (!deflater.finished()) {
            val n = deflater.deflate(out, written, out.size - written)
            if (n == 0) break
            written += n
        }
        deflater.end()
        return out.copyOf(written)
    }

    private fun inflate(src: ByteArray, off: Int, len: Int, expected: Int): ByteArray {
        val inflater = Inflater()
        inflater.setInput(src, off, len)
        val out = ByteArray(expected)
        var written = 0
        while (!inflater.finished() && written < out.size) {
            val n = inflater.inflate(out, written, out.size - written)
            if (n == 0) break
            written += n
        }
        inflater.end()
        assertEquals(expected, written, "server could not inflate the client frame")
        return out
    }

    @Test
    fun encryptedCompressedLoginSurvivesLargePluginRequest() = runLogin(overlongLength = false)

    @Test
    fun overlongFrameLengthFromProxyIsDecoded() = runLogin(overlongLength = true)

    private fun runLogin(overlongLength: Boolean) {
        val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val port = server.localPort
        val reachedConfig = CountDownLatch(1)
        val chatSeen = CountDownLatch(1)
        val serverError = CopyOnWriteArrayList<Throwable>()
        val longChat = "afk-" + "x".repeat(400)

        val serverThread = Thread {
            try {
                server.accept().use { socket ->
                    socket.tcpNoDelay = true
                    val sc = ServerConn(socket)
                    sc.overlongLength = overlongLength

                    val handshake = sc.readFrame() ?: return@use
                    assertEquals(0, handshake.first)
                    assertEquals(protocol, handshake.second.readVarInt())
                    handshake.second.readString()
                    handshake.second.readUnsignedShort()
                    assertEquals(2, handshake.second.readVarInt())

                    val login = sc.readFrame() ?: return@use
                    assertEquals(table.id(P.LOGIN_C2S_LOGIN_START), login.first)
                    assertEquals("TestBot", login.second.readString())

                    val keyPair = KeyPairGenerator.getInstance("RSA").apply { initialize(1024) }
                        .generateKeyPair()
                    val verifyToken = ByteArray(4) { (it + 1).toByte() }
                    sc.send(table.id(P.LOGIN_S2C_ENCRYPTION_REQUEST)) {
                        it.writeString("")
                        it.writeByteArray(keyPair.public.encoded)
                        it.writeByteArray(verifyToken)
                        it.writeBoolean(false) // no session join required
                    }

                    val response = sc.readFrame() ?: return@use
                    assertEquals(table.id(P.LOGIN_C2S_ENCRYPTION_RESPONSE), response.first)
                    val encryptedSecret = response.second.readByteArray()
                    val encryptedToken = response.second.readByteArray()
                    val rsa = Cipher.getInstance("RSA/ECB/PKCS1Padding")
                    rsa.init(Cipher.DECRYPT_MODE, keyPair.private)
                    val secret = rsa.doFinal(encryptedSecret)
                    assertEquals(16, secret.size)
                    assertTrue(rsa.doFinal(encryptedToken).contentEquals(verifyToken))

                    val key = SecretKeySpec(secret, "AES")
                    val iv = IvParameterSpec(secret)
                    sc.cin.cipher = Cipher.getInstance("AES/CFB8/NoPadding").apply {
                        init(Cipher.DECRYPT_MODE, key, iv)
                    }
                    sc.cout.cipher = Cipher.getInstance("AES/CFB8/NoPadding").apply {
                        init(Cipher.ENCRYPT_MODE, key, iv)
                    }

                    // Compression is enabled by a packet that is itself sent in
                    // the old (uncompressed) format; the threshold applies from
                    // the next packet on.
                    sc.send(table.id(P.LOGIN_S2C_COMPRESS)) { it.writeVarInt(256) }
                    sc.threshold = 256
                    sc.send(table.id(P.LOGIN_S2C_PLUGIN_REQUEST)) {
                        it.writeVarInt(7)
                        it.writeString("velocity:player_info")
                        it.writeBytes(ByteArray(1290) { 'x'.code.toByte() })
                    }
                    sc.send(table.id(P.LOGIN_S2C_SUCCESS)) {
                        it.writeUuid(UUID.randomUUID())
                        it.writeString("TestBot")
                        it.writeVarInt(0)
                        it.writeBoolean(false)
                    }

                    val pluginResponse = sc.readFrame() ?: return@use
                    assertEquals(table.id(P.LOGIN_C2S_PLUGIN_RESPONSE), pluginResponse.first)
                    assertEquals(7, pluginResponse.second.readVarInt())
                    assertEquals(false, pluginResponse.second.readBoolean())

                    val ack = sc.readFrame() ?: return@use
                    assertEquals(table.id(P.LOGIN_C2S_LOGIN_ACKNOWLEDGED), ack.first)
                    val settings = sc.readFrame() ?: return@use
                    assertEquals(table.id(P.CONFIG_C2S_SETTINGS), settings.first)
                    reachedConfig.countDown()

                    // Real servers block the registry transfer on this answer.
                    sc.send(table.id(P.CONFIG_S2C_SELECT_KNOWN_PACKS)) {
                        it.writeVarInt(1)
                        it.writeString("minecraft")
                        it.writeString("core")
                        it.writeString("1.21.10")
                    }
                    val knownPacks = sc.readFrame() ?: return@use
                    assertEquals(table.id(P.CONFIG_C2S_SELECT_KNOWN_PACKS), knownPacks.first)
                    assertEquals(0, knownPacks.second.readVarInt())

                    sc.send(table.id(P.CONFIG_S2C_FINISH))
                    val finish = sc.readFrame() ?: return@use
                    assertEquals(table.id(P.CONFIG_C2S_FINISH), finish.first)
                    sc.send(table.id(P.PLAY_S2C_JOIN_GAME))

                    // A packet larger than the threshold exercises the client's
                    // outbound compressed framing too.
                    val chat = sc.readFrame() ?: return@use
                    assertEquals(table.id(P.PLAY_C2S_CHAT), chat.first)
                    assertEquals(longChat, chat.second.readString())
                    chatSeen.countDown()
                }
            } catch (e: Throwable) {
                serverError.add(e)
            } finally {
                try {
                    server.close()
                } catch (_: Exception) {
                }
            }
        }.apply {
            isDaemon = true
            start()
        }

        val recorder = Recorder()
        val engine = AfkEngine({ recorder }, null)
        try {
            engine.startBot(
                BotConfig(
                    id = 9,
                    host = "127.0.0.1",
                    port = port,
                    username = "TestBot",
                    onlineMode = false,
                    autoJump = false,
                    protocolOverride = protocol,
                ),
            )
            assertTrue(
                reachedConfig.await(10, TimeUnit.SECONDS),
                "never reached configuration: ${recorder.logs} server=$serverError",
            )
            assertTrue(
                awaitUntil(5_000) { recorder.latest()?.phase == Phase.PLAYING },
                "never reached play: ${recorder.logs}",
            )
            engine.sendChat(9, longChat)
            assertTrue(chatSeen.await(10, TimeUnit.SECONDS), "server never received the chat: ${recorder.logs}")
            assertTrue(
                recorder.logs.any { it.contains("Logged in as TestBot") },
                "no login log: ${recorder.logs}",
            )
        } finally {
            engine.close()
            serverThread.join(2_000)
            server.close()
        }
        assertTrue(serverError.isEmpty(), "server failed: $serverError")
    }

    private fun awaitUntil(timeoutMs: Long, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(20)
        }
        return condition()
    }
}
