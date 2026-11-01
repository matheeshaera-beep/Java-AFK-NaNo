package dev.mstheesha.afk.engine

/**
 * Decides which host/port to dial. Only `_minecraft._tcp` SRV records are
 * honoured, and only for a bare hostname on the default port, matching the
 * vanilla client. The cache is shared by the blocking connect pool.
 */
internal object HostPolicy {
    private const val DEFAULT_PORT = 25565
    private const val POSITIVE_TTL_MS = 5 * 60_000L
    private const val NEGATIVE_TTL_MS = 60_000L

    private class Entry(val host: String, val port: Int, val expiresAt: Long)

    private val cache = HashMap<String, Entry>()

    data class Target(val host: String, val port: Int)

    @Synchronized
    fun resolve(host: String, port: Int): Target {
        val name = host.trim()
        if (port != DEFAULT_PORT || name.isEmpty() || isLiteral(name)) {
            return Target(name, port)
        }
        val key = name.lowercase()
        val now = System.currentTimeMillis()
        cache[key]?.let { if (it.expiresAt > now) return Target(it.host, it.port) }

        val srv = Dns.resolveSrv(name)
        val entry = if (srv != null) {
            Entry(srv.host, srv.port, now + POSITIVE_TTL_MS)
        } else {
            Entry(name, port, now + NEGATIVE_TTL_MS)
        }
        cache[key] = entry
        return Target(entry.host, entry.port)
    }

    /** True for IPv4/IPv6 literals and single-label hosts, which never use SRV. */
    fun isLiteral(host: String): Boolean {
        if (host.equals("localhost", ignoreCase = true)) return true
        if (host.indexOf(':') >= 0) return true
        if (!host.contains('.')) return true
        val parts = host.split('.')
        if (parts.size == 4 && parts.all { it.isNotEmpty() && it.all(Char::isDigit) }) {
            return parts.all { (it.toIntOrNull() ?: 256) in 0..255 }
        }
        return false
    }

    @Synchronized
    fun clear() = cache.clear()
}
