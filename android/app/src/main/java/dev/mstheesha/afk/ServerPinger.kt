package dev.mstheesha.afk

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import dev.mstheesha.afk.engine.ServerListPing
import java.io.File
import java.security.MessageDigest

/**
 * Fetches server favicons using `:engine`'s [ServerListPing] (including SRV
 * support) and caches them on disk in `cacheDir/favicons` so restarts don't
 * re-ping every server on every launch.
 */
object ServerPinger {
    private const val MAX_FAVICON_CHARS = 400_000
    private const val MAX_FAVICON_BYTES = 300_000
    private const val MAX_FAVICON_EDGE = 1024
    private const val DISK_TTL_MS = 24 * 60 * 60_000L

    fun fetchFavicon(context: Context, host: String, port: Int): Bitmap? {
        val cacheFile = cacheFileFor(context, host, port)
        if (cacheFile.isFile && System.currentTimeMillis() - cacheFile.lastModified() < DISK_TTL_MS) {
            runCatching { BitmapFactory.decodeFile(cacheFile.absolutePath) }.getOrNull()?.let { return it }
        }
        return try {
            val result = ServerListPing.ping(host, port, timeoutMs = 3_000)
            val dataUrl = result.favicon ?: return null
            val prefix = "data:image/png;base64,"
            val base64 = if (dataUrl.startsWith(prefix)) dataUrl.substring(prefix.length) else return null
            val rawBytes = decodeBytes(base64) ?: return null
            val bmp = decodeBitmap(rawBytes) ?: return null
            runCatching {
                cacheFile.parentFile?.mkdirs()
                cacheFile.writeBytes(rawBytes)
            }
            bmp
        } catch (_: Exception) {
            // Fall back to stale disk cache if server is temporarily unreachable.
            if (cacheFile.isFile) runCatching { BitmapFactory.decodeFile(cacheFile.absolutePath) }.getOrNull()
            else null
        }
    }

    private fun decodeBytes(base64: String): ByteArray? {
        if (base64.length > MAX_FAVICON_CHARS) return null
        val bytes = Base64.decode(base64, Base64.DEFAULT)
        if (bytes.size > MAX_FAVICON_BYTES) return null
        return bytes
    }

    private fun decodeBitmap(bytes: ByteArray): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth !in 1..MAX_FAVICON_EDGE) return null
        if (bounds.outHeight !in 1..MAX_FAVICON_EDGE) return null
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun cacheFileFor(context: Context, host: String, port: Int): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("${host.lowercase()}:$port".toByteArray(Charsets.UTF_8))
        val hex = digest.take(12).joinToString("") { "%02x".format(it) }
        return File(File(context.applicationContext.cacheDir, "favicons"), "$hex.png")
    }
}