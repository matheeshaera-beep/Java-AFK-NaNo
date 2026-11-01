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
