package dev.mstheesha.afk.engine

import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket

/** Server List Ping: used to detect the protocol version before logging in. */
internal object ServerListPing {
    data class Result(
        val protocol: Int,
        val versionName: String,
        val motd: String,
        val favicon: String?,
    )

    fun ping(host: String, port: Int, timeoutMs: Int = 6_000): Result {
        val socket = Socket()
        try {
            socket.tcpNoDelay = true
            socket.connect(InetSocketAddress(host, port), timeoutMs)
            socket.soTimeout = timeoutMs
            val out = DataOutputStream(socket.getOutputStream())
            val input = DataInputStream(socket.getInputStream())

            val handshake = PacketWriter(128)
            handshake.writeVarInt(0) // handshake
            handshake.writeVarInt(-1) // "unknown" protocol: ask the server
            handshake.writeString(host)
            handshake.writeShort(port)
            handshake.writeVarInt(1) // status
            writeFrame(out, handshake)

            val request = PacketWriter(8)
            request.writeVarInt(0)
            writeFrame(out, request)
            out.flush()

            val length = readVarInt(input)
            if (length <= 0 || length > 1 shl 21) throw ProtocolException("bad status length $length")
            val body = ByteArray(length)
            input.readFully(body)
            val reader = PacketReader(body, 0, body.size)
            val id = reader.readVarInt()
            if (id != 0) throw ProtocolException("unexpected status packet $id")
            val json = reader.readString(1 shl 21)
            val root = Json.parse(json).asObj() ?: throw ProtocolException("status is not an object")
            val version = root.obj("version")
            val protocol = version?.int("protocol") ?: -1
            val name = version?.str("name") ?: ""
            val motd = ChatText.flatten(root["description"])
            val favicon = root.str("favicon")
            return Result(protocol, name, motd, favicon)
        } finally {
            try {
                socket.close()
            } catch (_: Exception) {
            }
        }
    }

    private fun writeFrame(out: DataOutputStream, writer: PacketWriter) {
        out.writeByteArrayVarInt(writer)
    }

    private fun DataOutputStream.writeByteArrayVarInt(writer: PacketWriter) {
        var v = writer.size
        while (v and 0x7F.inv() != 0) {
            writeByte((v and 0x7F) or 0x80)
            v = v ushr 7
        }
        writeByte(v)
        write(writer.buf, 0, writer.size)
    }

    private fun readVarInt(input: DataInputStream): Int {
        var result = 0
        var shift = 0
        while (true) {
            val b = input.readUnsignedByte()
            result = result or ((b and 0x7F) shl shift)
            if (b and 0x80 == 0) return result
            shift += 7
            if (shift > 35) throw ProtocolException("VarInt too big")
        }
    }
}
