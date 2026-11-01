// GENERATED FILE - DO NOT EDIT.
// Source: PrismarineJS minecraft-data (pc protocol data).
// Regenerate with: python tools/gen_packet_ids.py
package dev.mstheesha.afk.engine

/** Flat slot indices into [PacketIds.ids]. */
object P {
    const val PLAY_S2C_KEEP_ALIVE = 0
    const val PLAY_S2C_JOIN_GAME = 1
    const val PLAY_S2C_DISCONNECT = 2
    const val PLAY_S2C_POSITION = 3
    const val PLAY_S2C_UPDATE_HEALTH = 4
    const val PLAY_S2C_RESPAWN = 5
    const val PLAY_S2C_DEATH_COMBAT = 6
    const val PLAY_S2C_CHAT = 7
    const val PLAY_S2C_SYSTEM_CHAT = 8
    const val PLAY_S2C_DISGUISED_CHAT = 9
    const val PLAY_S2C_PING = 10
    const val PLAY_S2C_RESOURCE_PACK = 11
    const val PLAY_S2C_REMOVE_RESOURCE_PACK = 12
    const val PLAY_S2C_COOKIE_REQUEST = 13
    const val PLAY_S2C_TRANSFER = 14
    const val PLAY_S2C_START_CONFIGURATION = 15
    const val PLAY_S2C_CHUNK_BATCH_START = 16
    const val PLAY_C2S_KEEP_ALIVE = 17
    const val PLAY_C2S_CHAT = 18
    const val PLAY_C2S_CHAT_COMMAND = 19
    const val PLAY_C2S_SETTINGS = 20
    const val PLAY_C2S_POSITION = 21
    const val PLAY_C2S_POSITION_LOOK = 22
    const val PLAY_C2S_LOOK = 23
    const val PLAY_C2S_FLYING = 24
    const val PLAY_C2S_PONG = 25
    const val PLAY_C2S_RESOURCE_PACK_RECEIVE = 26
    const val PLAY_C2S_CUSTOM_PAYLOAD = 27
    const val PLAY_C2S_TELEPORT_CONFIRM = 28
    const val PLAY_C2S_CLIENT_COMMAND = 29
    const val PLAY_C2S_TICK_END = 30
    const val PLAY_C2S_CONFIGURATION_ACK = 31
    const val LOGIN_S2C_DISCONNECT = 32
    const val LOGIN_S2C_ENCRYPTION_REQUEST = 33
    const val LOGIN_S2C_SUCCESS = 34
    const val LOGIN_S2C_COMPRESS = 35
    const val LOGIN_S2C_PLUGIN_REQUEST = 36
    const val LOGIN_S2C_COOKIE_REQUEST = 37
    const val LOGIN_C2S_LOGIN_START = 38
    const val LOGIN_C2S_ENCRYPTION_RESPONSE = 39
    const val LOGIN_C2S_PLUGIN_RESPONSE = 40
    const val LOGIN_C2S_COOKIE_RESPONSE = 41
    const val LOGIN_C2S_LOGIN_ACKNOWLEDGED = 42
    const val CONFIG_S2C_DISCONNECT = 43
    const val CONFIG_S2C_FINISH = 44
    const val CONFIG_S2C_KEEP_ALIVE = 45
    const val CONFIG_S2C_PING = 46
    const val CONFIG_S2C_CUSTOM_PAYLOAD = 47
    const val CONFIG_S2C_RESOURCE_PACK = 48
    const val CONFIG_S2C_REMOVE_RESOURCE_PACK = 49
    const val CONFIG_S2C_COOKIE_REQUEST = 50
    const val CONFIG_S2C_TRANSFER = 51
    const val CONFIG_S2C_REGISTRY_DATA = 52
    const val CONFIG_S2C_FEATURE_FLAGS = 53
    const val CONFIG_S2C_SELECT_KNOWN_PACKS = 54
    const val CONFIG_C2S_SETTINGS = 55
    const val CONFIG_C2S_FINISH = 56
    const val CONFIG_C2S_KEEP_ALIVE = 57
    const val CONFIG_C2S_PONG = 58
    const val CONFIG_C2S_RESOURCE_PACK_RECEIVE = 59
    const val CONFIG_C2S_CUSTOM_PAYLOAD = 60
    const val CONFIG_C2S_SELECT_KNOWN_PACKS = 61
    const val CONFIG_C2S_COOKIE_RESPONSE = 62
    const val CONFIG_S2C_CODE_OF_CONDUCT = 63
    const val CONFIG_C2S_ACCEPT_CODE_OF_CONDUCT = 64
    const val STATUS_S2C_SERVER_INFO = 65
    const val STATUS_S2C_PING = 66
    const val STATUS_C2S_PING_START = 67
    const val STATUS_C2S_PING = 68
    const val HANDSHAKE_C2S_SET_PROTOCOL = 69
    const val COUNT = 70
}

/** Packet ids for one protocol version; -1 means the packet does not exist there. */
class PacketIds internal constructor(
    val protocol: Int,
    val version: String,
    private val ids: IntArray,
) {
    fun id(slot: Int): Int = ids[slot]
    fun has(slot: Int): Boolean = ids[slot] >= 0
}

object PacketTable {
    private val v1_8 = PacketIds(47, "1.8", intArrayOf(0, 1, 64, 8, 6, 7, -1, 2, -1, -1, -1, 72, -1, -1, -1, -1, -1, 0, 1, -1, 21, 4, 6, 5, 3, -1, 25, 23, -1, 22, -1, -1, 0, 1, 2, 3, -1, -1, 0, 1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0, 1, 0, 1, 0))
    private val v1_9 = PacketIds(107, "1.9", intArrayOf(31, 35, 26, 46, 62, 51, -1, 15, -1, -1, -1, 50, -1, -1, -1, -1, -1, 11, 2, -1, 4, 12, 13, 14, 15, -1, 22, 9, 0, 3, -1, -1, 0, 1, 2, 3, -1, -1, 0, 1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0, 1, 0, 1, 0))
    private val v1_9_2 = v1_9
    private val v1_9_4 = v1_9
    private val v1_10_2 = v1_9
    private val v1_11 = v1_9
    private val v1_11_2 = v1_9
    private val v1_12 = PacketIds(335, "1.12", intArrayOf(31, 35, 26, 46, 64, 52, -1, 15, -1, -1, -1, 51, -1, -1, -1, -1, -1, 12, 3, -1, 5, 14, 15, 16, 13, -1, 24, 10, 0, 4, -1, -1, 0, 1, 2, 3, -1, -1, 0, 1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0, 1, 0, 1, 0))
    private val v1_12_1 = PacketIds(338, "1.12.1", intArrayOf(31, 35, 26, 47, 65, 53, -1, 15, -1, -1, -1, 52, -1, -1, -1, -1, -1, 11, 2, -1, 4, 13, 14, 15, 12, -1, 24, 9, 0, 3, -1, -1, 0, 1, 2, 3, -1, -1, 0, 1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0, 1, 0, 1, 0))
    private val v1_12_2 = v1_12_1
    private val v1_13 = PacketIds(393, "1.13", intArrayOf(33, 37, 27, 50, 68, 56, -1, 14, -1, -1, -1, 55, -1, -1, -1, -1, -1, 14, 2, -1, 4, 16, 17, 18, 15, -1, 29, 10, 0, 3, -1, -1, 0, 1, 2, 3, 4, -1, 0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0, 1, 0, 1, 0))
    private val v1_13_1 = v1_13
    private val v1_13_2 = v1_13
    private val v1_14 = PacketIds(477, "1.14", intArrayOf(32, 37, 26, 53, 72, 58, -1, 14, -1, -1, -1, 57, -1, -1, -1, -1, -1, 15, 3, -1, 5, 17, 18, 19, 20, -1, 31, 11, 0, 4, -1, -1, 0, 1, 2, 3, 4, -1, 0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0, 1, 0, 1, 0))
    private val v1_14_1 = v1_14
    private val v1_14_3 = v1_14
    private val v1_14_4 = v1_14
    private val v1_15 = PacketIds(573, "1.15", intArrayOf(33, 38, 27, 54, 73, 59, -1, 15, -1, -1, -1, 58, -1, -1, -1, -1, -1, 15, 3, -1, 5, 17, 18, 19, 20, -1, 31, 11, 0, 4, -1, -1, 0, 1, 2, 3, 4, -1, 0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0, 1, 0, 1, 0))
    private val v1_15_1 = v1_15
    private val v1_15_2 = v1_15
    private val v1_16 = PacketIds(735, "1.16", intArrayOf(32, 37, 26, 53, 73, 58, -1, 14, -1, -1, -1, 57, -1, -1, -1, -1, -1, 16, 3, -1, 5, 18, 19, 20, 21, -1, 32, 11, 0, 4, -1, -1, 0, 1, 2, 3, 4, -1, 0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0, 1, 0, 1, 0))
    private val v1_16_1 = v1_16
    private val v1_16_2 = PacketIds(751, "1.16.2", intArrayOf(31, 36, 25, 52, 73, 57, -1, 14, -1, -1, -1, 56, -1, -1, -1, -1, -1, 16, 3, -1, 5, 18, 19, 20, 21, -1, 33, 11, 0, 4, -1, -1, 0, 1, 2, 3, 4, -1, 0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0, 1, 0, 1, 0))
    private val v1_16_3 = v1_16_2
    private val v1_16_5 = v1_16_2
    private val v1_17 = PacketIds(755, "1.17", intArrayOf(33, 38, 26, 56, 82, 61, -1, 15, -1, -1, 48, 60, -1, -1, -1, -1, -1, 15, 3, -1, 5, 17, 18, 19, 20, 29, 33, 10, 0, 4, -1, -1, 0, 1, 2, 3, 4, -1, 0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0, 1, 0, 1, 0))
    private val v1_17_1 = v1_17
    private val v1_18_1 = v1_17
    private val v1_18_2 = v1_17
    private val v1_19 = PacketIds(759, "1.19", intArrayOf(30, 35, 23, 54, 82, 59, -1, 48, 95, -1, 45, 58, -1, -1, -1, -1, -1, 17, 4, 3, 7, 19, 20, 21, 22, 31, 35, 12, 0, 6, -1, -1, 0, 1, 2, 3, 4, -1, 0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0, 1, 0, 1, 0))
    private val v1_19_2 = PacketIds(760, "1.19.2", intArrayOf(32, 37, 25, 57, 85, 62, -1, 51, 98, -1, 47, 61, -1, -1, -1, -1, -1, 18, 5, 4, 8, 20, 21, 22, 23, 32, 36, 13, 0, 7, -1, -1, 0, 1, 2, 3, 4, -1, 0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0, 1, 0, 1, 0))
    private val v1_19_3 = PacketIds(761, "1.19.3", intArrayOf(31, 36, 23, 56, 83, 61, -1, 49, 96, 24, 46, 60, -1, -1, -1, -1, -1, 17, 5, 4, 7, 19, 20, 21, 22, 31, 36, 12, 0, 6, -1, -1, 0, 1, 2, 3, 4, -1, 0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0, 1, 0, 1, 0))
    private val v1_19_4 = PacketIds(762, "1.19.4", intArrayOf(35, 40, 26, 60, 87, 65, -1, 53, 100, 27, 50, 64, -1, -1, -1, -1, -1, 18, 5, 4, 8, 20, 21, 22, 23, 32, 36, 13, 0, 7, -1, -1, 0, 1, 2, 3, 4, -1, 0, 1, 2, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0, 1, 0, 1, 0))
    private val v1_20_1 = v1_19_4
    private val v1_20_2 = PacketIds(764, "1.20.2", intArrayOf(36, 41, 27, 62, 89, 67, -1, 55, 103, 28, 51, 66, -1, -1, -1, 101, 13, 20, 5, 4, 9, 22, 23, 24, 25, 35, 39, 15, 0, 8, -1, 11, 0, 1, 2, 3, 4, -1, 0, 1, 2, -1, 3, 1, 2, 3, 4, 0, 6, -1, -1, -1, 5, 7, -1, 0, 2, 3, 4, 5, 1, -1, -1, -1, -1, 0, 1, 0, 1, 0))
    private val v1_20_4 = PacketIds(765, "1.20.4", intArrayOf(36, 41, 27, 62, 91, 69, -1, 55, 105, 28, 51, 68, 67, -1, -1, 103, 13, 21, 5, 4, 9, 23, 24, 25, 26, 36, 40, 16, 0, 8, -1, 11, 0, 1, 2, 3, 4, -1, 0, 1, 2, -1, 3, 1, 2, 3, 4, 0, 7, 6, -1, -1, 5, 8, -1, 0, 2, 3, 4, 5, 1, -1, -1, -1, -1, 0, 1, 0, 1, 0))
    private val v1_20_6 = PacketIds(766, "1.20.6", intArrayOf(38, 43, 29, 64, 93, 71, -1, 57, 108, 30, 53, 70, 69, 22, 115, 105, 13, 24, 6, 4, 10, 26, 27, 28, 29, 39, 43, 18, 0, 9, -1, 12, 0, 1, 2, 3, 4, 5, 0, 1, 2, 4, 3, 2, 3, 4, 5, 1, 9, 8, 0, 11, 7, 12, 14, 0, 3, 4, 5, 6, 2, 7, 1, -1, -1, 0, 1, 0, 1, 0))
    private val v1_21_1 = v1_20_6
    private val v1_21_3 = PacketIds(768, "1.21.3", intArrayOf(39, 44, 29, 66, 98, 76, -1, 59, 115, 30, 55, 75, 74, 22, 122, 112, 13, 26, 7, 5, 12, 28, 29, 30, 31, 41, 45, 20, 0, 10, 11, 14, 0, 1, 2, 3, 4, 5, 0, 1, 2, 4, 3, 2, 3, 4, 5, 1, 9, 8, 0, 11, 7, 12, 14, 0, 3, 4, 5, 6, 2, 7, 1, -1, -1, 0, 1, 0, 1, 0))
    private val v1_21_4 = PacketIds(769, "1.21.4", intArrayOf(39, 44, 29, 66, 98, 76, -1, 59, 115, 30, 55, 75, 74, 22, 122, 112, 13, 26, 7, 5, 12, 28, 29, 30, 31, 43, 47, 20, 0, 10, 11, 14, 0, 1, 2, 3, 4, 5, 0, 1, 2, 4, 3, 2, 3, 4, 5, 1, 9, 8, 0, 11, 7, 12, 14, 0, 3, 4, 5, 6, 2, 7, 1, -1, -1, 0, 1, 0, 1, 0))
    private val v1_21_5 = PacketIds(770, "1.21.5", intArrayOf(38, 43, 28, 65, 97, 75, -1, 58, 114, 29, 54, 74, 73, 21, 122, 111, 12, 26, 7, 5, 12, 28, 29, 30, 31, 43, 47, 20, 0, 10, 11, 14, 0, 1, 2, 3, 4, 5, 0, 1, 2, 4, 3, 2, 3, 4, 5, 1, 9, 8, 0, 11, 7, 12, 14, 0, 3, 4, 5, 6, 2, 7, 1, -1, -1, 0, 1, 0, 1, 0))
    private val v1_21_6 = PacketIds(771, "1.21.6", intArrayOf(38, 43, 28, 65, 97, 75, -1, 58, 114, 29, 54, 74, 73, 21, 122, 111, 12, 27, 8, 6, 13, 29, 30, 31, 32, 44, 48, 21, 0, 11, 12, 15, 0, 1, 2, 3, 4, 5, 0, 1, 2, 4, 3, 2, 3, 4, 5, 1, 9, 8, 0, 11, 7, 12, 14, 0, 3, 4, 5, 6, 2, 7, 1, -1, -1, 0, 1, 0, 1, 0))
    private val v1_21_8 = v1_21_6
    private val v1_21_10 = PacketIds(773, "1.21.10", intArrayOf(43, 48, 32, 70, 102, 80, -1, 63, 119, 33, 59, 79, 78, 21, 127, 116, 12, 27, 8, 6, 13, 29, 30, 31, 32, 44, 48, 21, 0, 11, 12, 15, 0, 1, 2, 3, 4, 5, 0, 1, 2, 4, 3, 2, 3, 4, 5, 1, 9, 8, 0, 11, 7, 12, 14, 0, 3, 4, 5, 6, 2, 7, 1, 19, 9, 0, 1, 0, 1, 0))
    private val v1_21_11 = v1_21_10
    private val v26_1 = PacketIds(775, "26.1", intArrayOf(44, 49, 32, 72, 104, 82, -1, 65, 121, 33, 61, 81, 80, 21, 129, 118, 12, 28, 9, 7, 14, 30, 31, 32, 33, 45, 49, 22, 0, 12, 13, 16, 0, 1, 2, 3, 4, 5, 0, 1, 2, 4, 3, 2, 3, 4, 5, 1, 9, 8, 0, 11, 7, 12, 14, 0, 3, 4, 5, 6, 2, 7, 1, 19, 9, 0, 1, 0, 1, 0))

    private val byProtocol: Map<Int, PacketIds> = mapOf(
        47 to v1_8,
        107 to v1_9,
        109 to v1_9_2,
        110 to v1_9_4,
        210 to v1_10_2,
        315 to v1_11,
        316 to v1_11_2,
        335 to v1_12,
        338 to v1_12_1,
        340 to v1_12_2,
        393 to v1_13,
        401 to v1_13_1,
        404 to v1_13_2,
        477 to v1_14,
        480 to v1_14_1,
        490 to v1_14_3,
        498 to v1_14_4,
        573 to v1_15,
        575 to v1_15_1,
        578 to v1_15_2,
        735 to v1_16,
        736 to v1_16_1,
        751 to v1_16_2,
        753 to v1_16_3,
        754 to v1_16_5,
        755 to v1_17,
        756 to v1_17_1,
        757 to v1_18_1,
        758 to v1_18_2,
        759 to v1_19,
        760 to v1_19_2,
        761 to v1_19_3,
        762 to v1_19_4,
        763 to v1_20_1,
        764 to v1_20_2,
        765 to v1_20_4,
        766 to v1_20_6,
        767 to v1_21_1,
        768 to v1_21_3,
        769 to v1_21_4,
        770 to v1_21_5,
        771 to v1_21_6,
        772 to v1_21_8,
        773 to v1_21_10,
        774 to v1_21_11,
        775 to v26_1,
    )

    private val byVersionName: Map<String, Int> = mapOf(
        "1.8" to 47,
        "1.9" to 107,
        "1.9.2" to 109,
        "1.9.4" to 110,
        "1.10.2" to 210,
        "1.11" to 315,
        "1.11.2" to 316,
        "1.12" to 335,
        "1.12.1" to 338,
        "1.12.2" to 340,
        "1.13" to 393,
        "1.13.1" to 401,
        "1.13.2" to 404,
        "1.14" to 477,
        "1.14.1" to 480,
        "1.14.3" to 490,
        "1.14.4" to 498,
        "1.15" to 573,
        "1.15.1" to 575,
        "1.15.2" to 578,
        "1.16" to 735,
        "1.16.1" to 736,
        "1.16.2" to 751,
        "1.16.3" to 753,
        "1.16.5" to 754,
        "1.17" to 755,
        "1.17.1" to 756,
        "1.18.1" to 757,
        "1.18.2" to 758,
        "1.19" to 759,
        "1.19.2" to 760,
        "1.19.3" to 761,
        "1.19.4" to 762,
        "1.20.1" to 763,
        "1.20.2" to 764,
        "1.20.4" to 765,
        "1.20.6" to 766,
        "1.21.1" to 767,
        "1.21.3" to 768,
        "1.21.4" to 769,
        "1.21.5" to 770,
        "1.21.6" to 771,
        "1.21.8" to 772,
        "1.21.10" to 773,
        "1.21.11" to 774,
        "26.1" to 775,
    )

    fun forProtocol(protocol: Int): PacketIds? = byProtocol[protocol]

    fun protocolForName(name: String): Int? = byVersionName[name]

    val latest: PacketIds = v26_1
    val supportedVersions: String = "1.8 - 26.1"
}
