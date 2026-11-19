package dev.mstheesha.afk

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import dev.mstheesha.afk.engine.ServerListPing
import java.util.concurrent.ConcurrentHashMap

object ServerPinger {
    /** A 64x64 PNG is a few KB; cap the base64 and decoded form generously. */
    private const val MAX_FAVICON_CHARS = 400_000
    private const val MAX_FAVICON_BYTES = 300_000
    private const val MAX_FAVICON_EDGE = 1024
    private const val FAVICON_PREFIX = "data:image/png;base64,"
    private const val CACHE_TTL_MS = 60_000L

    data class ServerStatus(
        val latencyMs: Long,
        val onlinePlayers: Int,
        val maxPlayers: Int,
        val motd: String,
        val favicon: Bitmap?,
    )

    // In-memory cache of ping results: (host:port -> Status to timestamp)
    private val statusCache = ConcurrentHashMap<String, Pair<Long, ServerStatus>>()
    // Per-key lock so ServerRow and ServerAvatar coalesce into one TCP ping.
    private val inFlightLocks = ConcurrentHashMap<String, Any>()

    fun fetchFavicon(host: String, port: Int): Bitmap? {
        val cached = statusCache["$host:$port"]?.second?.favicon
        if (cached != null) return cached
        return ping(host, port)?.favicon
    }

    /**
     * Executes one SRV-aware Server List Ping to retrieve latency, player counts, MOTD, and favicon.
     * Coalesces concurrent callers for the same host:port and caches results for 60 seconds.
     */
    fun ping(host: String, port: Int, forceFresh: Boolean = false): ServerStatus? {
        val key = "$host:$port"
        if (!forceFresh) {
            val cached = statusCache[key]
            if (cached != null && System.currentTimeMillis() - cached.first < CACHE_TTL_MS) {
                return cached.second
            }
        }

        val lock = inFlightLocks.computeIfAbsent(key) { Any() }
        synchronized(lock) {
            val now = System.currentTimeMillis()
            if (!forceFresh) {
                val cached = statusCache[key]
                if (cached != null && now - cached.first < CACHE_TTL_MS) {
                    return cached.second
                }
            }
            return try {
                val res = ServerListPing.pingWithSrv(host, port, timeoutMs = 2_500)
                val bitmap = res.favicon
                    ?.takeIf { it.startsWith(FAVICON_PREFIX) }
                    ?.substring(FAVICON_PREFIX.length)
                    ?.let { decodeFavicon(it) }
                val status = ServerStatus(
                    latencyMs = res.latencyMs.coerceAtLeast(1L),
                    onlinePlayers = res.onlinePlayers,
                    maxPlayers = res.maxPlayers,
                    motd = res.motd,
                    favicon = bitmap,
                )
                statusCache[key] = System.currentTimeMillis() to status
                status
            } catch (_: Exception) {
                null
            }
        }
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
}