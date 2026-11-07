package dev.mstheesha.afk

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

object ServerPinger {
    /** Status JSON is ~50 KB with a favicon; anything far larger is hostile. */
    private const val MAX_JSON_BYTES = 1 shl 20
    /** A 64x64 PNG is a few KB; cap the base64 and decoded form generously. */
    private const val MAX_FAVICON_CHARS = 400_000
    private const val MAX_FAVICON_BYTES = 300_000
    private const val MAX_FAVICON_EDGE = 1024

    data class ServerStatus(
        val latencyMs: Long,
        val onlinePlayers: Int,
        val maxPlayers: Int,
        val motd: String,
        val favicon: Bitmap?,
    )

    // In-memory cache of ping results: (host:port -> Status to timestamp)
    private val statusCache = ConcurrentHashMap<String, Pair<Long, ServerStatus>>()

    fun fetchFavicon(host: String, port: Int): Bitmap? {
        val cached = statusCache["$host:$port"]?.second?.favicon
        if (cached != null) return cached
        return ping(host, port)?.favicon
    }

    /**
     * Executes one Server List Ping to retrieve latency, player counts and MOTD.
     * Caches results for 60 seconds so server cards do not generate constant polling or network spam.
     */
    fun ping(host: String, port: Int, forceFresh: Boolean = false): ServerStatus? {
        val key = "$host:$port"
        val cached = statusCache[key]
        val now = System.currentTimeMillis()
        if (!forceFresh && cached != null && now - cached.first < 60_000L) {
            return cached.second
        }

        val socket = Socket()
        return try {
            val start = System.currentTimeMillis()
            socket.tcpNoDelay = true
            socket.connect(InetSocketAddress(host, port), 2500)
            socket.soTimeout = 2500
            val out = DataOutputStream(socket.getOutputStream())
            val inp = DataInputStream(socket.getInputStream())

            val handshake = ByteArrayOutputStream()
            DataOutputStream(handshake).also { w ->
                writeVarInt(w, 0)     // handshake packet id
                writeVarInt(w, 767)   // protocol version
                writeString(w, host)  // server address
                w.writeShort(port)    // server port
                writeVarInt(w, 1)     // next state = status
            }
            writePacket(out, handshake.toByteArray())
            out.flush()

            val request = ByteArrayOutputStream()
            DataOutputStream(request).also { writeVarInt(it, 0) }
            writePacket(out, request.toByteArray())
            out.flush()

            val packetLen = readVarInt(inp)    // packet length (full)
            if (packetLen <= 0 || packetLen > MAX_JSON_BYTES + 16) return null
            readVarInt(inp)                    // packet id (0x00 status response)
            val jsonLen = readVarInt(inp)
            if (jsonLen < 0 || jsonLen > packetLen || jsonLen > MAX_JSON_BYTES) return null
            val jsonBytes = ByteArray(jsonLen)
            inp.readFully(jsonBytes)
            val latency = (System.currentTimeMillis() - start).coerceAtLeast(1L)
            val jsonStr = String(jsonBytes, Charsets.UTF_8)

            val status = parseStatus(jsonStr, latency)
            if (status != null) {
                statusCache[key] = now to status
            }
            status
        } catch (_: Exception) {
            null
        } finally {
            try { socket.close() } catch (_: Exception) {}
        }
    }

    private fun parseStatus(jsonStr: String, latencyMs: Long): ServerStatus? = try {
        val root = JSONObject(jsonStr)
        val players = root.optJSONObject("players")
        val online = players?.optInt("online", -1) ?: -1
        val max = players?.optInt("max", -1) ?: -1
        val motdObj = root.opt("description")
        val motd = when (motdObj) {
            is String -> motdObj
            is JSONObject -> motdObj.optString("text")
            null -> ""
            else -> ""
        }
        val faviconB64 = faviconBase64(jsonStr)
        val bitmap = faviconB64?.let { decodeFavicon(it) }
        ServerStatus(latencyMs, online, max, motd, bitmap)
    } catch (_: Exception) {
        null
    }

    private fun decodeFavicon(base64: String): Bitmap? {
        if (base64.length > MAX_FAVICON_CHARS) return null
        val bytes = android.util.Base64.decode(base64, android.util.Base64.DEFAULT)
        if (bytes.size > MAX_FAVICON_BYTES) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth !in 1..MAX_FAVICON_EDGE) return null
        if (bounds.outHeight !in 1..MAX_FAVICON_EDGE) return null
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun faviconBase64(json: String): String? {
        val marker = "\"favicon\":\"data:image/png;base64,"
        val idx = json.indexOf(marker)
        if (idx < 0) return null
        val start = idx + marker.length
        val end = json.indexOf('"', start)
        if (end < 0) return null
        return json.substring(start, end)
    }

    private fun writePacket(out: DataOutputStream, payload: ByteArray) {
        writeVarInt(out, payload.size)
        out.write(payload)
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

    private fun writeString(out: DataOutputStream, s: String) {
        val bytes = s.toByteArray(Charsets.UTF_8)
        writeVarInt(out, bytes.size)
        out.write(bytes)
    }

    private fun readVarInt(inp: DataInputStream): Int {
        var numRead = 0
        var result = 0
        var read: Byte
        do {
            read = inp.readByte()
            val value = (read.toInt() and 0x7F)
            result = result or (value shl (7 * numRead))
            numRead++
            if (numRead > 5) throw IllegalArgumentException("VarInt is too big")
        } while ((read.toInt() and 0x80) != 0)
        return result
    }
}