package dev.mstheesha.afk

import android.content.Context
import dev.mstheesha.afk.engine.AntiAfkMode
import dev.mstheesha.afk.engine.BotConfig
import dev.mstheesha.afk.engine.BotEvent
import dev.mstheesha.afk.engine.BotEventKind
import dev.mstheesha.afk.engine.BotListener
import dev.mstheesha.afk.engine.BotSnapshot
import dev.mstheesha.afk.engine.ChatLine
import dev.mstheesha.afk.engine.ChatMode
import dev.mstheesha.afk.engine.ChatTrigger
import dev.mstheesha.afk.engine.Phase
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Per-server view of one engine bot. The engine pushes [BotSnapshot], log,
 * chat, and safety event callbacks into StateFlows from its own thread.
 * Logs and chat are backed by bounded rings to avoid double-copy GC churn.
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

    val state: StateFlow<String> = _state
    val detail: StateFlow<String> = _detail
    val logs: StateFlow<List<LogLine>> = _logs
    val authRequired = _authRequired
    val chat: StateFlow<List<ChatLine>> = _chat

    // Live vitals & connection telemetry from BotSnapshot (zero extra packets).
    private val _health = MutableStateFlow(20f)
    val health: StateFlow<Float> = _health
    private val _food = MutableStateFlow(20)
    val food: StateFlow<Int> = _food
    private val _pingMs = MutableStateFlow(-1)
    val pingMs: StateFlow<Int> = _pingMs
    private val _versionName = MutableStateFlow("")
    val versionName: StateFlow<String> = _versionName

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

    private val _kickedCount = MutableStateFlow(0)
    val kickedCount: StateFlow<Int> = _kickedCount

    private var serverDisplayName = "Server $serverId"
    private var lastAuthLabel = ""
    private var lastServerRef = ""
    private var alertsMask = 0
    private var alertKeyword = ""
    private var webhookUrl = ""
    private var config: BotConfig? = null
    private var registered = false

    // Bounded ring buffers (avoid double list copy on every inbound line).
    private val logBuffer = ArrayDeque<LogLine>(MAX_LOG_LINES)
    private val chatBuffer = ArrayDeque<ChatLine>(MAX_CHAT_LINES)
    private var logDirty = false
    private var chatDirty = false
    private var nextLocalLogId = -1L

    data class LogLine(val id: Long, val text: String, val ts: Long = System.currentTimeMillis())

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

    /** Recomputes visible counters and flushes any background-buffered chat/logs when UI returns. */
    fun refreshCounters() {
        val now = System.currentTimeMillis()
        val extra = if (_state.value == "connected" && connectedAtMs > 0) now - connectedAtMs else 0
        _afkSeconds.value = (accumulatedMs + extra) / 1000
        flushBuffersIfDirty()
    }

    fun flushBuffersIfDirty() {
        if (logDirty) {
            synchronized(logBuffer) {
                logDirty = false
                _logs.value = ArrayList(logBuffer)
            }
        }
        if (chatDirty) {
            synchronized(chatBuffer) {
                chatDirty = false
                _chat.value = ArrayList(chatBuffer)
            }
        }
    }

    fun trimMemory() {
        synchronized(logBuffer) {
            while (logBuffer.size > 100) logBuffer.removeFirst()
            _logs.value = ArrayList(logBuffer)
            logDirty = false
        }
        synchronized(chatBuffer) {
            while (chatBuffer.size > 100) chatBuffer.removeFirst()
            _chat.value = ArrayList(chatBuffer)
            chatDirty = false
        }
    }

    fun clearLogs() {
        synchronized(logBuffer) {
            logBuffer.clear()
            logDirty = false
            _logs.value = emptyList()
        }
    }

    fun clearChat() {
        synchronized(chatBuffer) {
            chatBuffer.clear()
            chatDirty = false
            _chat.value = emptyList()
        }
    }

    private fun pushLog(line: String) {
        synchronized(logBuffer) {
            if (logBuffer.size >= MAX_LOG_LINES) logBuffer.removeFirst()
            logBuffer.addLast(LogLine(nextLocalLogId--, line))
            if (AppGraph.uiVisible) {
                logDirty = false
                _logs.value = ArrayList(logBuffer)
            } else {
                logDirty = true
            }
        }
    }

    private fun pushChat(line: ChatLine) {
        synchronized(chatBuffer) {
            if (chatBuffer.size >= MAX_CHAT_LINES) chatBuffer.removeFirst()
            chatBuffer.addLast(line)
            if (AppGraph.uiVisible) {
                chatDirty = false
                _chat.value = ArrayList(chatBuffer)
            } else {
                chatDirty = true
            }
        }
    }

    // ------------------------------------------------------------------ listener

    private val listener = object : BotListener {
        override fun onSnapshot(snapshot: BotSnapshot) = applySnapshot(snapshot)

        override fun onLog(line: String) {
            pushLog(line)
        }

        override fun onChat(line: ChatLine) {
            pushChat(line)
            checkChatAlerts(line)
        }

        override fun onAuthRequired(url: String, code: String) {
            setState("authenticating")
            _detail.value = "Sign in required"
            pushLog("Microsoft sign-in needed: $url code $code")
            _authRequired.value = url to code
        }

        override fun onEvent(event: BotEvent) {
            handleBotEvent(event)
        }
    }

    private fun checkChatAlerts(line: ChatLine) {
        val mask = alertsMask
        if (mask == 0 || line.sender == "You") return
        val lower = line.text.lowercase()
        if ((mask and AlertFlags.WHISPER) != 0 &&
            (lower.contains("whispers to you") || lower.contains("-> me]") || lower.contains("-> you]"))
        ) {
            AlertNotifier.notify(context, serverDisplayName, "Whisper", line.text, webhookUrl, 0x1E88E5)
            return
        }
        val kw = alertKeyword.trim()
        if ((mask and AlertFlags.KEYWORD) != 0 && kw.isNotEmpty() &&
            line.text.contains(kw, ignoreCase = true)
        ) {
            AlertNotifier.notify(context, serverDisplayName, "Chat match ($kw)", line.text, webhookUrl, 0x8E24AA)
        }
    }

    private fun handleBotEvent(event: BotEvent) {
        val mask = alertsMask
        when (event.kind) {
            BotEventKind.KICKED -> {
                if ((mask and AlertFlags.KICK) != 0) {
                    AlertNotifier.notify(context, serverDisplayName, "Kicked", event.message, webhookUrl, 0xFB8C00)
                }
            }
            BotEventKind.BANNED -> {
                if ((mask and AlertFlags.KICK) != 0) {
                    AlertNotifier.notify(context, serverDisplayName, "Disconnected / Banned", event.message, webhookUrl, 0xD32F2F)
                }
            }
            BotEventKind.DIED -> {
                if ((mask and AlertFlags.DEATH) != 0) {
                    AlertNotifier.notify(context, serverDisplayName, "Bot died", event.message, webhookUrl, 0xD32F2F)
                }
            }
            BotEventKind.LOW_HEALTH -> {
                if ((mask and AlertFlags.LOW_HEALTH) != 0) {
                    AlertNotifier.notify(context, serverDisplayName, "Low health safety stop", event.message, webhookUrl, 0xE53935)
                }
            }
            BotEventKind.AUTO_STOPPED -> {
                AlertNotifier.notify(context, serverDisplayName, "Auto-stop reached", event.message, webhookUrl, 0x43A047)
            }
            BotEventKind.TRANSFERRED -> Unit
        }
    }

    private fun applySnapshot(snapshot: BotSnapshot) {
        _bytesIn.value = snapshot.bytesIn
        _bytesOut.value = snapshot.bytesOut
        _sessionDataBytes.value = snapshot.bytesIn + snapshot.bytesOut
        _health.value = snapshot.health
        _food.value = snapshot.food
        _pingMs.value = snapshot.pingMs
        if (snapshot.versionName.isNotBlank()) _versionName.value = snapshot.versionName
        _kickedCount.value = snapshot.kicks
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
                val reason = snapshot.detail.ifBlank { "disconnected" }
                _detail.value = if (snapshot.reconnectInMs > 0) {
                    val secs = (snapshot.reconnectInMs + 999) / 1000
                    "Reconnecting in ${secs}s — $reason"
                } else {
                    reason
                }
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

    fun start(server: ServerEntity) {
        serverDisplayName = server.name
        lastAuthLabel = if (server.onlineMode) "Microsoft" else "Offline"
        lastServerRef = "${server.host}:${server.port}"
        alertsMask = server.alertsMask
        alertKeyword = server.alertKeyword
        webhookUrl = server.webhookUrl
        accumulatedMs = 0
        connectedAtMs = 0
        _afkSeconds.value = 0
        _sessionDataBytes.value = 0
        _bytesIn.value = 0
        _bytesOut.value = 0
        _health.value = 20f
        _food.value = 20
        _pingMs.value = -1
        _authRequired.value = null
        val botConfig = toBotConfig(server)
        config = botConfig
        if (!registered) {
            EngineHost.register(serverId, listener)
            registered = true
        }
        setState("connecting")
        _detail.value = "Connecting to server…"
        EngineHost.engine().startBot(botConfig)
    }

    fun pushLiveSettings(server: ServerEntity) {
        serverDisplayName = server.name
        alertsMask = server.alertsMask
        alertKeyword = server.alertKeyword
        webhookUrl = server.webhookUrl
        val current = config ?: return
        val next = toBotConfig(server).copy(
            host = current.host,
            port = current.port,
            username = current.username,
            onlineMode = current.onlineMode,
            protocolOverride = current.protocolOverride,
        )
        config = next
        EngineHost.engine().updateBot(next)
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
        _pingMs.value = -1
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
        pushChat(
            ChatLine(
                seq = nextLocalLogId--.toInt(),
                ts = System.currentTimeMillis(),
                type = "out",
                sender = "You",
                text = text,
                styledText = text,
            ),
        )
    }

    fun clearToken(accountKey: String = KeystoreTokenStore.DEFAULT_KEY) {
        scope.launch {
            EngineHost.clearTokens(accountKey)
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

    companion object {
        private const val MAX_LOG_LINES = 300
        private const val MAX_CHAT_LINES = 300

        fun toBotConfig(s: ServerEntity): BotConfig {
            val commands = s.chatCommand
                .split('\n')
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            val antiAfk = when (s.antiAfkMode.lowercase()) {
                "off" -> AntiAfkMode.OFF
                "look" -> AntiAfkMode.LOOK
                "mix" -> AntiAfkMode.MIX
                else -> if (s.autoJump) AntiAfkMode.JUMP else AntiAfkMode.OFF
            }
            return BotConfig(
                id = s.id,
                host = s.host,
                port = s.port,
                username = s.username.ifBlank { "AFKBot" },
                onlineMode = s.onlineMode,
                viewDistance = s.viewDistance.coerceIn(2, 12),
                chatMode = chatModeOf(s.chatMode),
                commands = commands,
                commandDelaySeconds = s.commandDelaySeconds.coerceIn(1, 3600),
                loopCommand = s.loopCommand,
                autoJump = antiAfk != AntiAfkMode.OFF,
                protocolOverride = s.protocolOverride.coerceIn(0, 1_000_000),
                accountKey = s.accountKey.ifBlank { KeystoreTokenStore.DEFAULT_KEY },
                antiAfkMode = antiAfk,
                autoRespawn = s.autoRespawn,
                lowHealthDisconnect = s.lowHealthDisconnect.coerceIn(0, 18).toFloat(),
                chatTriggers = parseTriggers(s.chatTriggersJson),
                autoStopMinutes = s.autoStopMinutes.coerceIn(0, 1440),
            )
        }

        private fun chatModeOf(value: String): ChatMode = when (value) {
            "commandsOnly" -> ChatMode.COMMANDS_ONLY
            "messagesOnly" -> ChatMode.MESSAGES_ONLY
            "hidden" -> ChatMode.HIDDEN
            else -> ChatMode.ALL
        }

        fun parseTriggers(json: String): List<ChatTrigger> {
            if (json.isBlank()) return emptyList()
            return runCatching {
                val arr = JSONArray(json)
                buildList(arr.length()) {
                    for (i in 0 until arr.length()) {
                        val obj = arr.optJSONObject(i) ?: continue
                        val m = obj.optString("m").trim()
                        val c = obj.optString("c").trim()
                        if (m.isNotEmpty() && c.isNotEmpty()) {
                            add(ChatTrigger(m, c, obj.optInt("d", 2).coerceIn(0, 120)))
                        }
                    }
                }
            }.getOrDefault(emptyList())
        }

        fun serializeTriggers(triggers: List<ChatTrigger>): String {
            if (triggers.isEmpty()) return ""
            val arr = JSONArray()
            for (t in triggers) {
                arr.put(
                    JSONObject()
                        .put("m", t.match.trim())
                        .put("c", t.command.trim())
                        .put("d", t.delaySeconds.coerceIn(0, 120)),
                )
            }
            return arr.toString()
        }
    }
}
