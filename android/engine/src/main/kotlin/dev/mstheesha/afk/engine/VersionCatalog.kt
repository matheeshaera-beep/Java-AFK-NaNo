package dev.mstheesha.afk.engine

/**
 * Human-facing release catalog built on top of [PacketTable].
 *
 * [PacketIds.kt] is generated and only exposes a private `byVersionName` map, so the
 * release names are mirrored here in their original (oldest-first) order. Protocols are
 * still resolved through the public [PacketTable] API, which keeps this file honest if
 * the generated table ever drops a version.
 */
object VersionCatalog {
    data class Release(val name: String, val protocol: Int)

    /** Release names copied from PacketIds.kt's `byVersionName` map, oldest first. */
    private val releaseNames: List<String> = listOf(
        "1.8",
        "1.9",
        "1.9.2",
        "1.9.4",
        "1.10.2",
        "1.11",
        "1.11.2",
        "1.12",
        "1.12.1",
        "1.12.2",
        "1.13",
        "1.13.1",
        "1.13.2",
        "1.14",
        "1.14.1",
        "1.14.3",
        "1.14.4",
        "1.15",
        "1.15.1",
        "1.15.2",
        "1.16",
        "1.16.1",
        "1.16.2",
        "1.16.3",
        "1.16.5",
        "1.17",
        "1.17.1",
        "1.18.1",
        "1.18.2",
        "1.19",
        "1.19.2",
        "1.19.3",
        "1.19.4",
        "1.20.1",
        "1.20.2",
        "1.20.4",
        "1.20.6",
        "1.21.1",
        "1.21.3",
        "1.21.4",
        "1.21.5",
        "1.21.6",
        "1.21.8",
        "1.21.10",
        "1.21.11",
        "26.1",
    )

    /** Distinct releases, oldest first, one per protocol (aliases collapsed). */
    val releases: List<Release> = buildList {
        val seen = HashSet<Int>()
        for (name in releaseNames) {
            val protocol = PacketTable.protocolForName(name) ?: continue
            if (PacketTable.forProtocol(protocol) == null) continue
            if (seen.add(protocol)) add(Release(name, protocol))
        }
    }.sortedBy { it.protocol }

    private val nameByProtocol: Map<Int, String> =
        releases.associate { it.protocol to it.name }

    /** Release name for a protocol, or null when unsupported. */
    fun nameOf(protocol: Int): String? = nameByProtocol[protocol]

    /** Greatest supported protocol <= [protocol]; if none, the smallest supported. Null only if the table is empty. */
    fun nearestSupported(protocol: Int): Int? =
        releases.lastOrNull { it.protocol <= protocol }?.protocol
            ?: releases.firstOrNull()?.protocol

    /** Default protocol when auto-detection fails (the newest supported one). */
    val latestProtocol: Int = PacketTable.latest.protocol
}
