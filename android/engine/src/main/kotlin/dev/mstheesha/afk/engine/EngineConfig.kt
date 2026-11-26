package dev.mstheesha.afk.engine

/** How much chat a bot keeps. Filtering happens at the frame layer. */
enum class ChatMode { ALL, COMMANDS_ONLY, MESSAGES_ONLY, HIDDEN }

/** Connection phase, mirrored to the UI. */
enum class Phase { CONNECTING, LOGGING_IN, CONFIGURING, PLAYING, RECONNECTING, STOPPED }

/** Anti-AFK activity strategy (all zero-world-model, jittered timing). */
enum class AntiAfkMode { OFF, JUMP, LOOK, MIX }

/** Automatic chat/command trigger when inbound chat contains [match] (case-insensitive). */
data class ChatTrigger(
    val match: String,
    val command: String,
    val delaySeconds: Int = 2,
)

/** Significant bot lifecycle/safety events delivered to [BotListener.onEvent]. */
enum class BotEventKind {
    KICKED,
    BANNED,
    DIED,
    LOW_HEALTH,
    TRANSFERRED,
    AUTO_STOPPED,
}

data class BotEvent(
    val kind: BotEventKind,
    val message: String,
    val ts: Long = System.currentTimeMillis(),
)

/**
 * Everything the engine needs to run one bot. Immutable: live edits replace the
 * whole object through [AfkEngine.updateBot].
 */
data class BotConfig(
    val id: Long,
    val host: String,
    val port: Int = 25565,
    val username: String,
    val onlineMode: Boolean = false,
    val viewDistance: Int = 2,
    val chatMode: ChatMode = ChatMode.ALL,
    val commands: List<String> = emptyList(),
    val commandDelaySeconds: Int = 5,
    val loopCommand: Boolean = false,
    val autoJump: Boolean = true,
    val protocolOverride: Int = 0,
    val accountKey: String = "default",
    val antiAfkMode: AntiAfkMode? = null,
    val autoRespawn: Boolean = true,
    /** Disconnect when health drops to or below this value (0 = disabled, 2..18 = 1..9 hearts). */
    val lowHealthDisconnect: Float = 0f,
    val chatTriggers: List<ChatTrigger> = emptyList(),
    /** Automatically stop this bot after N minutes of session time (0 = disabled). */
    val autoStopMinutes: Int = 0,
) {
    fun effectiveAntiAfk(): AntiAfkMode =
        antiAfkMode ?: if (autoJump) AntiAfkMode.JUMP else AntiAfkMode.OFF
}

/** One immutable view of a bot, pushed to the UI on every meaningful change. */
data class BotSnapshot(
    val phase: Phase = Phase.CONNECTING,
    val detail: String = "",
    val connected: Boolean = false,
    val username: String = "",
    val protocol: Int = 0,
    val versionName: String = "",
    val bytesIn: Long = 0,
    val bytesOut: Long = 0,
    val health: Float = 20f,
    val food: Int = 20,
    val kicks: Int = 0,
    val reconnectInMs: Long = 0,
    val pingMs: Int = -1,
)

data class ChatLine(
    val seq: Int,
    val ts: Long,
    val type: String,
    val sender: String?,
    val text: String,
    val styledText: String = text,
)

/** Callbacks arrive on the engine thread; implementations must not block. */
interface BotListener {
    fun onSnapshot(snapshot: BotSnapshot)
    fun onLog(line: String)
    fun onChat(line: ChatLine)
    fun onAuthRequired(url: String, code: String)
    fun onEvent(event: BotEvent) = Unit
}

/** Tokens persisted by the host platform (Android Keystore in the app). */
data class StoredTokens(
    val refreshToken: String = "",
    val accessToken: String = "",
    val expiresAtMs: Long = 0L,
    val uuid: String = "",
    val name: String = "",
)

interface TokenStore {
    fun load(): StoredTokens?
    fun save(tokens: StoredTokens)
    fun clear()

    fun load(accountKey: String): StoredTokens? = load()
    fun save(accountKey: String, tokens: StoredTokens) = save(tokens)
    fun clear(accountKey: String) = clear()
}
