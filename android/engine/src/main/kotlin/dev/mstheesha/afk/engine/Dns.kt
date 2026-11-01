package dev.mstheesha.afk.engine

/** SRV lookup through Cloudflare's DNS-over-HTTPS JSON API. */
internal object Dns {
    data class Srv(val host: String, val port: Int)

    private const val ENDPOINT = "https://cloudflare-dns.com/dns-query"

    /**
     * Returns the best `_minecraft._tcp` target, or null when the server has no
     * SRV record or the lookup fails (callers then use the original host/port).
     */
    fun resolveSrv(host: String): Srv? {
        return try {
            val url = "$ENDPOINT?name=" + java.net.URLEncoder.encode("_minecraft._tcp.$host", "UTF-8") +
                "&type=SRV"
            val json = Http.get(url, headers = mapOf("Accept" to "application/dns-json"))
            val root = Json.parse(json).asObj() ?: return null
            val answers = root.arr("Answer") ?: return null
            data class Record(val priority: Int, val weight: Int, val port: Int, val target: String)

            val records = answers.mapNotNull { entry ->
                val data = (entry.asObj() ?: return@mapNotNull null).str("data") ?: return@mapNotNull null
                val parts = data.trim().split(Regex("\\s+"))
                if (parts.size < 4) return@mapNotNull null
                Record(
                    priority = parts[0].toIntOrNull() ?: return@mapNotNull null,
                    weight = parts[1].toIntOrNull() ?: return@mapNotNull null,
                    port = parts[2].toIntOrNull() ?: return@mapNotNull null,
                    target = parts[3].trimEnd('.'),
                )
            }
            records.minWithOrNull(compareBy<Record>({ it.priority }, { -it.weight }))
                ?.takeIf { it.target.isNotBlank() && it.port in 1..65535 }
                ?.let { Srv(it.target, it.port) }
        } catch (e: Exception) {
            null
        }
    }
}
