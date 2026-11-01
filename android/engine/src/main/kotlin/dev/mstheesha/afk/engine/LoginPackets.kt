package dev.mstheesha.afk.engine

/** Parsed `login` encryption request. */
internal class EncryptionRequest(
    val serverId: String,
    val publicKey: ByteArray,
    val verifyToken: ByteArray,
    val shouldAuthenticate: Boolean,
)

/**
 * Wire layout of the few login packets the engine reads by hand. Kept separate
 * from [BotSession] so the layouts can be tested per protocol.
 */
internal object LoginPacketCodec {
    /**
     * 1.20.5 (766) added the trailing `shouldAuthenticate` flag; older servers
     * always expect a session join, so the flag defaults to true there.
     */
    fun readEncryptionRequest(protocol: Int, r: PacketReader): EncryptionRequest {
        val serverId = r.readString(64)
        val publicKey = r.readByteArray(1024)
        val verifyToken = r.readByteArray(1024)
        val shouldAuthenticate = if (protocol >= Protocol.V1_20_5) r.readBoolean() else true
        return EncryptionRequest(serverId, publicKey, verifyToken, shouldAuthenticate)
    }
}
