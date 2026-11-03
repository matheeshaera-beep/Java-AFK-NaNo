package dev.mstheesha.afk.engine

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.net.InetAddress
import java.net.ServerSocket
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * End-to-end test against a hand-written 1.20.6 server: login, configuration,
 * join, keep-alive echo, system chat, kick and reconnect. No encryption and no
 * compression, which is exactly the offline-mode path.
 */
class FakeServerTest {
    private val protocol = 766
    private val table = PacketTable.forProtocol(protocol)!!

    private class Recorder : BotListener {
        val snapshots = CopyOnWriteArrayList<BotSnapshot>()
        val chats = CopyOnWriteArrayList<ChatLine>()
        val logs = CopyOnWriteArrayList<String>()
        val chatLatch = CountDownLatch(1)

        override fun onSnapshot(snapshot: BotSnapshot) {
            snapshots.add(snapshot)
        }

        override fun onLog(line: String) {
            logs.add(line)
        }

        override fun onChat(line: ChatLine) {
            chats.add(line)
            chatLatch.countDown()
        }

        override fun onAuthRequired(url: String, code: String) = Unit

        fun latest(): BotSnapshot? = snapshots.lastOrNull()
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

    private fun writeVarInt(out: DataOutputStream, value: Int) {
        var v = value
        while (true) {
            if (v and 0x7F.inv() == 0) {
                out.writeByte(v)
                return
            }
            out.writeByte((v and 0x7F) or 0x80)
            v = v ushr 7
        }
    }

    private fun readFrame(input: DataInputStream): Pair<Int, PacketReader>? {
        val length = try {
            readVarInt(input)
        } catch (_: EOFException) {
            return null
        }
        if (length <= 0 || length > 1 shl 20) return null
        val body = ByteArray(length)
        input.readFully(body)
        val reader = PacketReader(body, 0, body.size)
        return reader.readVarInt() to reader
    }

    private fun send(out: DataOutputStream, id: Int, body: (PacketWriter) -> Unit = {}) {
        val writer = PacketWriter()
        writer.writeVarInt(id)
        body(writer)
        writeVarInt(out, writer.size)
        out.write(writer.buf, 0, writer.size)
        out.flush()
    }

    private fun writeTextComponent(writer: PacketWriter, text: String) {
        writer.writeByte(10) // compound
        writer.writeByte(8) // string
        writer.writeString("text")
        writer.writeString(text)
        writer.writeByte(0) // end
    }

    @Test
    fun joinKeepAliveChatKickReconnect() {
        val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val port = server.localPort
        val reconnected = CountDownLatch(1)
        val serverError = CopyOnWriteArrayList<Throwable>()

        val serverThread = Thread {
            try {
                // --- first session
                server.accept().use { socket ->
                    socket.tcpNoDelay = true
                    val input = DataInputStream(socket.getInputStream())
                    val output = DataOutputStream(socket.getOutputStream())

                    val handshake = readFrame(input) ?: return@use
                    assertEquals(0, handshake.first)
                    assertEquals(protocol, handshake.second.readVarInt())
                    handshake.second.readString() // host
                    handshake.second.readUnsignedShort() // port
                    assertEquals(2, handshake.second.readVarInt()) // login

                    val login = readFrame(input) ?: return@use
                    assertEquals(table.id(P.LOGIN_C2S_LOGIN_START), login.first)
                    assertEquals("TestBot", login.second.readString())

                    send(output, table.id(P.LOGIN_S2C_SUCCESS)) {
                        it.writeUuid(UUID.randomUUID())
                        it.writeString("TestBot")
                        it.writeVarInt(0) // properties
                        it.writeBoolean(false) // strict error handling
                    }

                    val ack = readFrame(input) ?: return@use
                    assertEquals(table.id(P.LOGIN_C2S_LOGIN_ACKNOWLEDGED), ack.first)
                    val settings = readFrame(input) ?: return@use
                    assertEquals(table.id(P.CONFIG_C2S_SETTINGS), settings.first)

                    send(output, table.id(P.CONFIG_S2C_FINISH))
                    val finish = readFrame(input) ?: return@use
                    assertEquals(table.id(P.CONFIG_C2S_FINISH), finish.first)

                    send(output, table.id(P.PLAY_S2C_JOIN_GAME))
                    send(output, table.id(P.PLAY_S2C_KEEP_ALIVE)) { it.writeLong(42L) }

                    val keepAlive = readFrame(input) ?: return@use
                    assertEquals(table.id(P.PLAY_C2S_KEEP_ALIVE), keepAlive.first)
                    assertEquals(42L, keepAlive.second.readLong())

                    send(output, table.id(P.PLAY_S2C_SYSTEM_CHAT)) {
                        writeTextComponent(it, "hello from server")
                        it.writeBoolean(false)
                    }
                    send(output, table.id(P.PLAY_S2C_DISCONNECT)) {
                        writeTextComponent(it, "bye")
                    }
                }

                // --- second session: proves the reconnect path runs
                server.accept().use {
                    reconnected.countDown()
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
                    id = 7,
                    host = "127.0.0.1",
                    port = port,
                    username = "TestBot",
                    onlineMode = false,
                    autoJump = false,
                    protocolOverride = protocol,
                ),
            )

            assertTrue(recorder.chatLatch.await(10, TimeUnit.SECONDS), "no system chat received")
            assertTrue(
                recorder.chats.any { it.text.contains("hello from server") },
                "unexpected chat: ${recorder.chats}",
            )
            assertTrue(reconnected.await(10, TimeUnit.SECONDS), "bot did not reconnect")
            assertTrue((recorder.latest()?.kicks ?: 0) >= 1, "kick was not counted")
            assertTrue(
                recorder.snapshots.any { it.phase == Phase.PLAYING },
                "never reached play: ${recorder.snapshots.map { it.phase }}",
            )
        } finally {
            engine.close()
            serverThread.join(2_000)
            server.close()
        }
        assertTrue(serverError.isEmpty(), "server failed: $serverError")
    }

    /**
     * Brings one connection all the way to PLAY. The caller owns the socket
     * afterwards and can send whatever it wants.
     */
    private fun java.net.Socket.loginHandshake(): DataOutputStream {
        tcpNoDelay = true
        val input = DataInputStream(getInputStream())
        val output = DataOutputStream(getOutputStream())

        val handshake = readFrame(input) ?: throw IllegalStateException("no handshake")
        assertEquals(0, handshake.first)
        handshake.second.readVarInt() // protocol
        handshake.second.readString() // host
        handshake.second.readUnsignedShort() // port
        assertEquals(2, handshake.second.readVarInt()) // next state = login

        val login = readFrame(input) ?: throw IllegalStateException("no login start")
        assertEquals(table.id(P.LOGIN_C2S_LOGIN_START), login.first)

        send(output, table.id(P.LOGIN_S2C_SUCCESS)) {
            it.writeUuid(UUID.randomUUID())
            it.writeString("TestBot")
            it.writeVarInt(0) // properties
            it.writeBoolean(false) // strict error handling
        }
        readFrame(input) // login acknowledged
        readFrame(input) // client settings

        send(output, table.id(P.CONFIG_S2C_FINISH))
        readFrame(input) // config finish

        send(output, table.id(P.PLAY_S2C_JOIN_GAME))
        return output
    }

    /**
     * A server that sends its final chat + disconnect and then closes must not
     * lose those frames: the engine has to decode what it already read before
     * it reports EOF.
     */
    @Test
    fun chatAndKickReasonSurviveImmediateClose() {
        val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val port = server.localPort
        val serverError = CopyOnWriteArrayList<Throwable>()

        val serverThread = Thread {
            try {
                server.accept().use { socket ->
                    val output = socket.loginHandshake()
                    send(output, table.id(P.PLAY_S2C_SYSTEM_CHAT)) {
                        writeTextComponent(it, "hello before close")
                        it.writeBoolean(false)
                    }
                    send(output, table.id(P.PLAY_S2C_DISCONNECT)) {
                        writeTextComponent(it, "bye now")
                    }
                    output.flush()
                }
            } catch (e: Throwable) {
                serverError.add(e)
            } finally {
                runCatching { server.close() }
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
                    id = 11,
                    host = "127.0.0.1",
                    port = port,
                    username = "TestBot",
                    autoJump = false,
                    protocolOverride = protocol,
                ),
            )

            assertTrue(
                recorder.chatLatch.await(5, TimeUnit.SECONDS),
                "chat lost when the server closed immediately",
            )
            assertEquals("hello before close", recorder.chats.first().text)
            val deadline = System.currentTimeMillis() + 5_000
            while (System.currentTimeMillis() < deadline &&
                recorder.snapshots.none {
                    it.phase == Phase.RECONNECTING && it.detail.contains("bye now")
                }
            ) {
                Thread.sleep(20)
            }
            assertTrue(
                recorder.snapshots.any {
                    it.phase == Phase.RECONNECTING && it.detail.contains("bye now")
                },
                "kick reason lost: ${recorder.snapshots.map { it.detail }}",
            )
        } finally {
            engine.close()
            serverThread.join(2_000)
            server.close()
        }
        assertTrue(serverError.isEmpty(), "server failed: $serverError")
    }

    /**
     * One read can hold more than 16 frames. They must all be decoded even if
     * the peer sends nothing else afterwards (it may be waiting for a reply).
     */
    @Test
    fun moreThanSixteenFramesInOneBatchAreAllProcessed() {
        val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val port = server.localPort
        val serverError = CopyOnWriteArrayList<Throwable>()
        val release = CountDownLatch(1)

        val serverThread = Thread {
            try {
                server.accept().use { socket ->
                    val output = socket.loginHandshake()
                    repeat(40) { index ->
                        send(output, table.id(P.PLAY_S2C_SYSTEM_CHAT)) {
                            writeTextComponent(it, "burst $index")
                            it.writeBoolean(false)
                        }
                    }
                    output.flush()
                    // Keep the socket open and silent: a stalled decoder has no
                    // later packet to wake it up.
                    release.await(5, TimeUnit.SECONDS)
                }
            } catch (e: Throwable) {
                serverError.add(e)
            } finally {
                runCatching { server.close() }
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
                    id = 12,
                    host = "127.0.0.1",
                    port = port,
                    username = "TestBot",
                    autoJump = false,
                    protocolOverride = protocol,
                ),
            )

            val deadline = System.currentTimeMillis() + 5_000
            while (recorder.chats.size < 40 && System.currentTimeMillis() < deadline) {
                Thread.sleep(20)
            }
            assertEquals(40, recorder.chats.size, "frames were left undecoded in the buffer")
        } finally {
            release.countDown()
            engine.close()
            serverThread.join(2_000)
            server.close()
        }
        assertTrue(serverError.isEmpty(), "server failed: $serverError")
    }

    /**
     * A server that accepts the connection and then goes silent must not leave
     * the bot "Connecting" until the OS TCP keep-alive fires hours later.
     */
    @Test
    fun silentServerTimesOutBeforePlay() {
        val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val port = server.localPort

        val serverThread = Thread {
            try {
                server.accept().use { socket ->
                    readFrame(DataInputStream(socket.getInputStream()))
                    Thread.sleep(8_000)
                }
            } catch (_: Throwable) {
            } finally {
                runCatching { server.close() }
            }
        }.apply {
            isDaemon = true
            start()
        }

        val recorder = Recorder()
        val engine = AfkEngine(
            { recorder },
            null,
            AfkEngine.Options(prePlayTimeoutMs = 400),
        )
        try {
            engine.startBot(
                BotConfig(
                    id = 13,
                    host = "127.0.0.1",
                    port = port,
                    username = "TestBot",
                    autoJump = false,
                    protocolOverride = protocol,
                ),
            )

            val deadline = System.currentTimeMillis() + 6_000
            while (System.currentTimeMillis() < deadline &&
                recorder.snapshots.none { it.phase == Phase.RECONNECTING }
            ) {
                Thread.sleep(20)
            }
            assertTrue(
                recorder.snapshots.any { it.phase == Phase.RECONNECTING },
                "silent server never timed out: ${recorder.snapshots.map { it.phase }}",
            )
        } finally {
            engine.close()
            serverThread.join(2_000)
            server.close()
        }
    }

    /**
     * A proxy on a newer protocol than the table knows (777) must downgrade to
     * the nearest supported release (775 / 26.1) instead of failing the connect.
     */
    @Test
    fun unsupportedProtocolFallsBackToNearestSupported() {
        val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val port = server.localPort
        val handshakeProtocols = CopyOnWriteArrayList<Int>()
        val handshakeLatch = CountDownLatch(1)
        val serverError = CopyOnWriteArrayList<Throwable>()

        val serverThread = Thread {
            try {
                server.accept().use { socket ->
                    val input = DataInputStream(socket.getInputStream())
                    val handshake = readFrame(input) ?: return@use
                    assertEquals(0, handshake.first)
                    handshakeProtocols.add(handshake.second.readVarInt())
                    handshakeLatch.countDown()
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
                    id = 8,
                    host = "127.0.0.1",
                    port = port,
                    username = "TestBot",
                    onlineMode = false,
                    autoJump = false,
                    protocolOverride = 777,
                ),
            )

            assertTrue(handshakeLatch.await(10, TimeUnit.SECONDS), "no handshake")
            assertEquals(775, handshakeProtocols.first())
            assertTrue(awaitUntil(5_000) { recorder.logs.any { it.contains("Connecting to") } }, "no connect log")
            assertTrue(
                recorder.logs.any {
                    it.contains("Server reports protocol 777") && it.contains("using 775")
                },
                "missing fallback log: ${recorder.logs}",
            )
            assertTrue(awaitUntil(5_000) { recorder.latest()?.protocol == 775 }, "snapshot protocol is not 775")
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
