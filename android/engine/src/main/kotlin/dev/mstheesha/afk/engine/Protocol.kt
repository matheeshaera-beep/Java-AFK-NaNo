package dev.mstheesha.afk.engine

import java.math.BigInteger
import java.security.MessageDigest
import java.util.UUID

/** Protocol numbers and the small set of version thresholds the engine needs. */
internal object Protocol {
    const val V1_8 = 47
    const val V1_9 = 107
    const val V1_12_2 = 340
    const val V1_13 = 393
    const val V1_14 = 477
    const val V1_16 = 735
    const val V1_16_2 = 751
    const val V1_17 = 755
    const val V1_19 = 759
    const val V1_19_1 = 760
    const val V1_19_3 = 761
    const val V1_19_4 = 762
    const val V1_20 = 763
    const val V1_20_2 = 764
    const val V1_20_3 = 765
    const val V1_20_5 = 766
    const val V1_21 = 767
    const val V1_21_2 = 768
    const val V1_21_9 = 773
    const val V1_21_11 = 774

    fun offlineUuid(name: String): UUID =
        UUID.nameUUIDFromBytes("OfflinePlayer:$name".toByteArray(Charsets.UTF_8))

    /** Parses a dashed or dashless (Mojang profile) UUID; null when malformed. */
    fun uuidFromString(value: String): UUID? {
        val hex = value.replace("-", "")
        if (hex.length != 32) return null
        return try {
            UUID(
                java.lang.Long.parseUnsignedLong(hex.substring(0, 16), 16),
                java.lang.Long.parseUnsignedLong(hex.substring(16), 16),
            )
        } catch (_: NumberFormatException) {
            null
        }
    }

    /** Minecraft's signed hex SHA-1 used by session join. */
    fun serverHash(serverId: String, secret: ByteArray, publicKey: ByteArray): String {
        val md = MessageDigest.getInstance("SHA-1")
        md.update(serverId.toByteArray(Charsets.UTF_8))
        md.update(secret)
        md.update(publicKey)
        return BigInteger(md.digest()).toString(16)
    }

    fun uuidNoDashes(uuid: UUID): String = uuid.toString().replace("-", "")
}
