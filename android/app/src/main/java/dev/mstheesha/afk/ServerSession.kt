package dev.mstheesha.afk

import android.content.Context
import dev.mstheesha.afk.engine.BotConfig
import dev.mstheesha.afk.engine.BotListener
import dev.mstheesha.afk.engine.BotSnapshot
import dev.mstheesha.afk.engine.ChatMode
import dev.mstheesha.afk.engine.ChatLine
import dev.mstheesha.afk.engine.Phase
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * Per-server view of one engine bot. The engine pushes [BotSnapshot], log and
 * chat callbacks straight into these StateFlows from its own thread, so the UI
 * has nothing to poll: every collector sees the latest value immediately.
 */
class ServerSession(private val context: Context, val serverId: Long) {

    private val scope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default + CoroutineExceptionHandler { _, e ->
            android.util.Log.w("ServerSession", "unhandled: ${e.message}")
        },
    )

    private val _state = MutableStateFlow("disconnected")
    private val _detail = MutableStateFlow("")
    private val _logs = MutableStateFlow<List<LogLine>>(emptyList())
    private val _authRequired = MutableStateFlow<Pair<String, String>?>(null)
    private val _chat = MutableStateFlow<List<ChatLine>>(emptyList())

    val state = _state
    val detail = _detail
    val logs = _logs
    val authRequired = _authRequired
    val chat = _chat

    // Session-owned counters: survive UI navigation. Time accrues only while
    // 'connected'; reset only on Stop.
    private val _afkSeconds = MutableStateFlow(0L)
    val afkSeconds: StateFlow<Long> = _afkSeconds
    private val _sessionDataBytes = MutableStateFlow(0L)
    val sessionDataBytes: StateFlow<Long> = _sessionDataBytes
    private var connectedAtMs = 0L
    private var accumulatedMs = 0L

    private val _bytesIn = MutableStateFlow(0L)
    val bytesIn: StateFlow<Long> = _bytesIn
    private val _bytesOut = MutableStateFlow(0L)
    val bytesOut: StateFlow<Long> = _bytesOut

    @Volatile
    var kickedCount = 0
        private set

    private var lastAuthLabel = ""
    private var lastServerRef = ""
    private var config: BotConfig? = null
    private var registered = false

    /** All state transitions go through here for connected-time accounting. */
    private fun setState(s: String) {
        if (_state.value == s) return
        val now = System.currentTimeMillis()
        if (_state.value == "connected" && connectedAtMs > 0) {
            accumulatedMs += now - connectedAtMs
            connectedAtMs = 0
        }
        _state.value = s
        if (s == "connected" && connectedAtMs == 0L) connectedAtMs = now
        refreshCounters()
    }

    /** Recomputes the visible counters; the UI calls this from its 1 s ticker. */
    fun refreshCounters() {
        val now = System.currentTimeMillis()
        val extra = if (_state.value == "connected" && connectedAtMs > 0) now - connectedAtMs else 0
        _afkSeconds.value = (accumulatedMs + extra) / 1000
    }

    data class LogLine(val id: Long, val text: String)

    private var nextLocalLogId = -1L

    private fun pushLog(line: String) {
        val prev = _logs.value
        _logs.value = (prev + LogLine(nextLocalLogId--, line)).takeLast(500)
    }

    private fun pushChat(line: ChatLine) {
        val prev = _chat.value
        _chat.value = (prev + line).takeLast(500)
    }

    // ------------------------------------------------------------------ listener

    private val listener = object : BotListener {
        override fun onSnapshot(snapshot: BotSnapshot) = applySnapshot(snapshot)

        override fun onLog(line: String) {
            pushLog(line)
        }

        override fun onChat(line: ChatLine) {
            pushChat(line)
        }

        override fun onAuthRequired(url: String, code: String) {
            setState("authenticating")
            _detail.value = "Sign in required"
            pushLog("Microsoft sign-in needed: $url code $code")
            _authRequired.value = url to code
        }
    }

    private fun applySnapshot(snapshot: BotSnapshot) {
        _bytesIn.value = snapshot.bytesIn
        _bytesOut.value = snapshot.bytesOut
        _sessionDataBytes.value = snapshot.bytesIn + snapshot.bytesOut
        kickedCount = snapshot.kicks
        when (snapshot.phase) {
            Phase.STOPPED -> {
                setState("disconnected")
                _detail.value = if (snapshot.detail.isNotBlank() && snapshot.detail != "Stopped") {
                    snapshot.detail
                } else {
                    ""
                }
                _authRequired.value = null
            }
            Phase.PLAYING -> {
                setState("connected")
                _detail.value = buildString {
                    append("Online as ${snapshot.username.ifBlank { "bot" }}")
                    if (lastAuthLabel.isNotBlank()) append(" · $lastAuthLabel")
                    if (lastServerRef.isNotBlank()) append(" · $lastServerRef")
                }
                if (_authRequired.value != null) _authRequired.value = null
            }
            Phase.RECONNECTING -> {
                setState("reconnecting")
                val secs = (snapshot.reconnectInMs + 999) / 1000
                val reason = snapshot.detail.ifBlank { "disconnected" }
                _detail.value = "Reconnecting in ${secs}s — $reason"
            }
            Phase.CONNECTING, Phase.LOGGING_IN, Phase.CONFIGURING -> {
                if (_state.value != "authenticating") setState("connecting")
                _detail.value = when {
                    _authRequired.value != null -> "Sign in required"
                    snapshot.phase == Phase.LOGGING_IN -> "Logging in…"
                    else -> snapshot.detail.ifBlank { "Connecting to server…" }
                }
            }
        }
    }

    // ============ PUBLIC API ============

    fun start(configJson: String) {
        val json = JSONObject(configJson)
        lastAuthLabel = if (json.optString("auth") == "microsoft") "Microsoft account" else "Offline mode"
        lastServerRef = json.optString("host") + ":" + json.optInt("port", 25565)
        accumulatedMs = 0
        connectedAtMs = 0
        _afkSeconds.value = 0
        _sessionDataBytes.value = 0
        _bytesIn.value = 0
        _bytesOut.value = 0
        _authRequired.value = null
        val botConfig = parseConfig(json)
        config = botConfig
        if (!registered) {
            EngineHost.register(serverId, listener)
            registered = true
        }
        setState("connecting")
        _detail.value = "Connecting to server…"
        EngineHost.engine().startBot(botConfig)
    }

    private fun parseConfig(json: JSONObject): BotConfig {
        val commands = json.optString("chatCommand")
            .split('\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        return BotConfig(
            id = serverId,
            host = json.optString("host"),
            port = json.optInt("port", 25565),
            username = json.optString("username").ifBlank { "AFKBot" },
            onlineMode = json.optString("auth") == "microsoft",
            viewDistance = json.optInt("viewDistance", 2).coerceIn(2, 12),
            chatMode = chatModeOf(json.optString("chatMode", "enabled")),
            commands = commands,
            commandDelaySeconds = json.optInt("commandDelaySeconds", 5).coerceIn(1, 3600),
            loopCommand = json.optBoolean("loopCommand", false),
            autoJump = json.optBoolean("autoJump", true),
            protocolOverride = json.optInt("protocolOverride", 0).coerceIn(0, 1_000_000),
        )
    }

    private fun chatModeOf(value: String): ChatMode = when (value) {
        "commandsOnly" -> ChatMode.COMMANDS_ONLY
        "messagesOnly" -> ChatMode.MESSAGES_ONLY
        "hidden" -> ChatMode.HIDDEN
        else -> ChatMode.ALL
    }

    fun stop() {
        EngineHost.engine().stopBot(serverId)
        setState("disconnected")
        _detail.value = ""
        accumulatedMs = 0
        connectedAtMs = 0
        _afkSeconds.value = 0
        _sessionDataBytes.value = 0
        _bytesIn.value = 0
        _bytesOut.value = 0
        _authRequired.value = null
        pushLog("Stopped")
    }

    fun reconnect() {
        EngineHost.engine().reconnect(serverId)
        setState("connecting")
        _detail.value = "Connecting to server…"
    }

    fun sendChat(message: String, onError: (String) -> Unit = {}) {
        val text = message.trim()
        if (text.isEmpty()) return
        if (_state.value != "connected") {
            onError("Not connected")
            return
        }
        EngineHost.engine().sendChat(serverId, text)
        // Echo locally: the server does not send our own message back, and 2.4
        // always showed it as <You>.
        pushChat(
            ChatLine(
                seq = nextLocalLogId--.toInt(),
                ts = System.currentTimeMillis(),
                type = "chat",
                sender = "You",
                text = text,
            ),
        )
    }

    /**
     * Pushes live settings to a running bot (chat visibility, commands, delays,
     * view distance, auto-jump). The engine keeps the whole immutable config,
     * so this replaces it atomically.
     */
    fun pushLiveConfig(
        chatCommand: String,
        delaySeconds: Int,
        viewDistance: Int,
        chatMode: String,
        autoJump: Boolean = config?.autoJump ?: true,
        loopCommand: Boolean = config?.loopCommand ?: false,
    ) {
        val current = config ?: return
        val next = current.copy(
            commands = chatCommand.split('\n').map { it.trim() }.filter { it.isNotEmpty() },
            commandDelaySeconds = delaySeconds.coerceIn(1, 3600),
            viewDistance = viewDistance.coerceIn(2, 12),
            chatMode = chatModeOf(chatMode),
            autoJump = autoJump,
            loopCommand = loopCommand,
        )
        config = next
        EngineHost.engine().updateBot(next)
    }

    /** Chat arrives live through the listener; kept for call-site compatibility. */
    fun setChatPolling(enabled: Boolean) = Unit

    fun refreshChatNow() = Unit

    fun clearToken() {
        scope.launch {
            EngineHost.clearTokens()
            pushLog("Saved login token cleared")
        }
    }

    fun dispose() {
        EngineHost.engine().stopBot(serverId)
        EngineHost.unregister(serverId)
        registered = false
        setState("disconnected")
        _detail.value = ""
        accumulatedMs = 0
        connectedAtMs = 0
        _afkSeconds.value = 0
        _sessionDataBytes.value = 0
        _authRequired.value = null
    }
}
