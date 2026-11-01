package dev.mstheesha.afk.engine

import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/** Small HTTP helper: HttpURLConnection only, 1 MB response cap. */
internal object Http {
    private const val MAX_BYTES = 1 shl 20
    private const val CONNECT_TIMEOUT = 15_000
    private const val READ_TIMEOUT = 30_000
    private const val USER_AGENT = "JavaAFK/3.0 (engine)"

    class HttpException(val code: Int, val body: String) : Exception("HTTP $code")

    fun postForm(url: String, fields: Map<String, String>): String {
        val body = fields.entries.joinToString("&") {
            encode(it.key) + "=" + encode(it.value)
        }.toByteArray(Charsets.UTF_8)
        return request(url, "POST", body, "application/x-www-form-urlencoded", null, emptyMap())
    }

    fun postJson(url: String, json: String): String =
        request(url, "POST", json.toByteArray(Charsets.UTF_8), "application/json", null, emptyMap())

    fun postJson(url: String, json: String, bearer: String?): String =
        request(url, "POST", json.toByteArray(Charsets.UTF_8), "application/json", bearer, emptyMap())

    fun get(url: String, bearer: String? = null, headers: Map<String, String> = emptyMap()): String =
        request(url, "GET", null, null, bearer, headers)

    private fun request(
        url: String,
        method: String,
        body: ByteArray?,
        contentType: String?,
        bearer: String?,
        headers: Map<String, String>,
    ): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = CONNECT_TIMEOUT
            conn.readTimeout = READ_TIMEOUT
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept", "application/json")
            if (bearer != null) conn.setRequestProperty("Authorization", "Bearer $bearer")
            for ((k, v) in headers) conn.setRequestProperty(k, v)
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", contentType ?: "application/octet-stream")
                conn.setFixedLengthStreamingMode(body.size)
                conn.outputStream.use { it.write(body) }
            }
            val code = conn.responseCode
            val stream: InputStream = if (code in 200..299) conn.inputStream else conn.errorStream ?: conn.inputStream
            val text = readCapped(stream)
            if (code !in 200..299) throw HttpException(code, text)
            return text
        } finally {
            conn.disconnect()
        }
    }

    private fun readCapped(stream: InputStream): String {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(8192)
        stream.use {
            while (true) {
                val n = it.read(buf)
                if (n < 0) break
                if (out.size() + n > MAX_BYTES) throw ProtocolException("HTTP response over 1 MB")
                out.write(buf, 0, n)
            }
        }
        return out.toString("UTF-8")
    }

    private fun encode(s: String): String =
        java.net.URLEncoder.encode(s, "UTF-8")
}
