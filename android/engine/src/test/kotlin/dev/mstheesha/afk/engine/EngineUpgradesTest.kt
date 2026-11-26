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

class EngineUpgradesTest {
    private val protocol = 766
    private val table = PacketTable.forProtocol(protocol)!!

    @Test
    fun partialInflateSkipsDisallowedLargePacketWithoutGrowingScratch() {
        Compression.shrink()
        val initialScratchSize = Compression.scratch().size

        // Build a 150 KiB compressed payload whose packet ID is 0x27 (e.g. chunk data).
        val writer = PacketWriter(160_000)
        writer.writeVarInt(0x27)
        writer.writeBytes(ByteArray(150_000) { (it and 0x7F).toByte() })
        val compressedLen = Compression.deflate(writer.buf, 0, writer.size)

        val result = Compression.inflateIfAllowed(
            Compression.deflated(),
            0,
            compressedLen,
            writer.size,
        ) { id -> id == 0x05 }

        assertEquals(-1, result, "disallowed packet should short-circuit with -1")
        assertEquals(0x27, Compression.lastPacketId)
        assertEquals(initialScratchSize, Compression.scratch().size, "scratch should not grow for skipped packets")
    }

    @Test
    fun partialInflateDecodesAllowedPacketFully() {
        val writer = PacketWriter(256)
        writer.writeVarInt(0x19)
        writer.writeString("hello partial inflate")
        val compressedLen = Compression.deflate(writer.buf, 0, writer.size)

        val result = Compression.inflateIfAllowed(
            Compression.deflated(),
            0,
            compressedLen,
            writer.size,
        ) { id -> id == 0x19 }

        assertEquals(writer.size, result)
        assertEquals(0x19, Compression.lastPacketId)
        val reader = PacketReader(Compression.scratch(), Compression.lastHeaderLen, result)
        assertEquals("hello partial inflate", reader.readString())
    }

    @Test
    fun styledChatPreservesColorsAndPlainStripsThem() {
        val json = """{"text":"Alert: ","color":"red","bold":true,"extra":[{"text":"low HP","color":"#ffaa00"}]}"""
        val styled = ChatText.fromJsonStyled(json)
        assertTrue(styled.contains("§c"), "expected red code in $styled")
        assertTrue(styled.contains("§l"), "expected bold code in $styled")
        assertTrue(styled.contains("§#ffaa00"), "expected hex color in $styled")
        assertEquals("Alert: low HP", ChatText.stripFormatting(styled))
        assertEquals("Alert: low HP", ChatText.fromJson(json))
    }

    @Test
    fun oversizedFrameIsSkippedAndSubsequentChatArrives() {
        val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val port = server.localPort
        val chatLatch = CountDownLatch(1)
        val chats = CopyOnWriteArrayList<ChatLine>()

        val serverThread = Thread {
            server.accept().use { socket ->
                val out = finishLogin(socket)
                // Send a 300 KiB uncompressed dummy frame (> Connection.MAX_FRAME = 256 KiB).
                val big = ByteArray(300 * 1024)
                big[0] = 0x7F // unused packet id
                writeVarInt(out, big.size)
                out.write(big)
                out.flush()

                // Follow immediately with a valid system chat frame.
                sendPacket(out, table.id(P.PLAY_S2C_SYSTEM_CHAT)) {
                    writeNbtText(it, "survived giant frame")
                    it.writeBoolean(false)
                }
                Thread.sleep(500)
            }
        }.apply { isDaemon = true; start() }

        val engine = AfkEngine({
            object : BotListener {
                override fun onSnapshot(snapshot: BotSnapshot) = Unit
                override fun onLog(line: String) = Unit
                override fun onChat(line: ChatLine) {
                    chats.add(line)
                    chatLatch.countDown()
                }
                override fun onAuthRequired(url: String, code: String) = Unit
            }
        })
        try {
            engine.startBot(
                BotConfig(
                    id = 101,
                    host = "127.0.0.1",
                    port = port,
                    username = "SkipBot",
                    onlineMode = false,
                    antiAfkMode = AntiAfkMode.OFF,
                    protocolOverride = protocol,
                ),
            )
            assertTrue(chatLatch.await(10, TimeUnit.SECONDS), "did not receive chat after oversized frame")
            assertTrue(chats.any { it.text.contains("survived giant frame") }, "chats=$chats")
        } finally {
            engine.close()
            serverThread.join(2_000)
            server.close()
        }
    }

    @Test
    fun lowHealthDisconnectAndChatTriggersWork() {
        val server = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val port = server.localPort
        val commandLatch = CountDownLatch(1)
        val lowHealthLatch = CountDownLatch(1)
        val receivedCommands = CopyOnWriteArrayList<String>()
        val events = CopyOnWriteArrayList<BotEvent>()

        val serverThread = Thread {
            server.accept().use { socket ->
                val input = DataInputStream(socket.getInputStream())
                val out = finishLogin(socket, input)
                // Send a system chat that matches our trigger.
                sendPacket(out, table.id(P.PLAY_S2C_SYSTEM_CHAT)) {
                    writeNbtText(it, "You were kicked to lobby")
                    it.writeBoolean(false)
                }
                // Wait for the triggered /server survival command.
                val deadline = System.currentTimeMillis() + 5_000
                while (System.currentTimeMillis() < deadline) {
                    val frame = readFrame(input) ?: break
                    if (frame.first == table.id(P.PLAY_C2S_CHAT_COMMAND)) {
                        receivedCommands.add(frame.second.readString())
                        commandLatch.countDown()
                        break
                    }
                }
                // Now drop health to 4.0 (2 hearts <= 6.0 threshold) to trigger safety stop.
                sendPacket(out, table.id(P.PLAY_S2C_UPDATE_HEALTH)) {
                    it.writeFloat(4.0f)
                    it.writeVarInt(18)
                    it.writeFloat(5.0f)
                }
                Thread.sleep(400)
            }
        }.apply { isDaemon = true; start() }

        val engine = AfkEngine({
            object : BotListener {
                override fun onSnapshot(snapshot: BotSnapshot) = Unit
                override fun onLog(line: String) = Unit
                override fun onChat(line: ChatLine) = Unit
                override fun onAuthRequired(url: String, code: String) = Unit
                override fun onEvent(event: BotEvent) {
                    events.add(event)
                    if (event.kind == BotEventKind.LOW_HEALTH) lowHealthLatch.countDown()
                }
            }
        })
        try {
            engine.startBot(
                BotConfig(
                    id = 102,
                    host = "127.0.0.1",
                    port = port,
                    username = "SafeBot",
                    onlineMode = false,
                    antiAfkMode = AntiAfkMode.OFF,
                    protocolOverride = protocol,
                    lowHealthDisconnect = 6f,
                    chatTriggers = listOf(ChatTrigger("kicked to lobby", "/server survival", delaySeconds = 0)),
                ),
            )
            assertTrue(commandLatch.await(10, TimeUnit.SECONDS), "chat trigger did not send command")
            assertEquals("server survival", receivedCommands.firstOrNull())
            assertTrue(lowHealthLatch.await(10, TimeUnit.SECONDS), "low-health event did not fire")
            assertEquals(Phase.STOPPED, engine.snapshot(102)?.phase)
        } finally {
            engine.close()
            serverThread.join(2_000)
            server.close()
        }
    }

    @Test
    fun transferPacketConnectsToNewServer() {
        val origin = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val dest = ServerSocket(0, 1, InetAddress.getLoopbackAddress())
        val destJoined = CountDownLatch(1)
        val transferredEvent = CountDownLatch(1)

        val originThread = Thread {
            origin.accept().use { socket ->
                val out = finishLogin(socket)
                sendPacket(out, table.id(P.PLAY_S2C_TRANSFER)) {
                    it.writeString("127.0.0.1")
                    it.writeVarInt(dest.localPort)
                }
                Thread.sleep(300)
            }
        }.apply { isDaemon = true; start() }

        val destThread = Thread {
            dest.accept().use { socket ->
                val input = DataInputStream(socket.getInputStream())
                val hs = readFrame(input) ?: return@use
                assertEquals(0, hs.first)
                hs.second.readVarInt() // protocol
                hs.second.readString() // host
                hs.second.readUnsignedShort() // port
                assertEquals(3, hs.second.readVarInt(), "expected transfer intent 3")
                destJoined.countDown()
            }
        }.apply { isDaemon = true; start() }

        val engine = AfkEngine({
            object : BotListener {
                override fun onSnapshot(snapshot: BotSnapshot) = Unit
                override fun onLog(line: String) = Unit
                override fun onChat(line: ChatLine) = Unit
                override fun onAuthRequired(url: String, code: String) = Unit
                override fun onEvent(event: BotEvent) {
                    if (event.kind == BotEventKind.TRANSFERRED) transferredEvent.countDown()
                }
            }
        })
        try {
            engine.startBot(
                BotConfig(
                    id = 103,
                    host = "127.0.0.1",
                    port = origin.localPort,
                    username = "XferBot",
                    onlineMode = false,
                    antiAfkMode = AntiAfkMode.OFF,
                    protocolOverride = protocol,
                ),
            )
            assertTrue(transferredEvent.await(10, TimeUnit.SECONDS), "no TRANSFERRED event")
            assertTrue(destJoined.await(10, TimeUnit.SECONDS), "bot did not connect to transferred host")
        } finally {
            engine.close()
            originThread.join(2_000)
            destThread.join(2_000)
            origin.close()
            dest.close()
        }
    }

    // ---------------------------------------------------------------- helpers

    private fun finishLogin(
        socket: java.net.Socket,
        input: DataInputStream = DataInputStream(socket.getInputStream()),
    ): DataOutputStream {
        socket.tcpNoDelay = true
        val output = DataOutputStream(socket.getOutputStream())
        readFrame(input) // handshake
        readFrame(input) // login start
        sendPacket(output, table.id(P.LOGIN_S2C_SUCCESS)) {
            it.writeUuid(UUID.randomUUID())
            it.writeString("Bot")
            it.writeVarInt(0)
            it.writeBoolean(false)
        }
        readFrame(input) // login ack
        readFrame(input) // settings
        sendPacket(output, table.id(P.CONFIG_S2C_FINISH))
        readFrame(input) // config finish
        sendPacket(output, table.id(P.PLAY_S2C_JOIN_GAME))
        return output
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

    private fun sendPacket(out: DataOutputStream, id: Int, body: (PacketWriter) -> Unit = {}) {
        val writer = PacketWriter()
        writer.writeVarInt(id)
        body(writer)
        writeVarInt(out, writer.size)
        out.write(writer.buf, 0, writer.size)
        out.flush()
    }

    private fun writeNbtText(writer: PacketWriter, text: String) {
        writer.writeByte(10) // compound
        writer.writeByte(8) // string
        writer.writeString("text")
        writer.writeString(text)
        writer.writeByte(0) // end
    }
}
