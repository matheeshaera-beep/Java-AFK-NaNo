package dev.mstheesha.afk

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
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

    private val _health = MutableStateFlow(20f)
    val health: StateFlow<Float> = _health
    private val _food = MutableStateFlow(20)
    val food: StateFlow<Int> = _food

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
        checkMentionNotification(line)
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
        _health.value = snapshot.health
        _food.value = snapshot.food
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
                    if (lastAuthLabel.isNotBlank()) append(" \u00B7 $lastAuthLabel")
                    if (lastServerRef.isNotBlank()) append(" \u00B7 $lastServerRef")
                }
                if (_authRequired.value != null) _authRequired.value = null
            }
            Phase.RECONNECTING -> {
                setState("reconnecting")
                val secs = (snapshot.reconnectInMs + 999) / 1000
                val reason = snapshot.detail.ifBlank { "disconnected" }
                _detail.value = "Reconnecting in ${secs}s \u2014 $reason"
            }
            Phase.CONNECTING, Phase.LOGGING_IN, Phase.CONFIGURING -> {
                if (_state.value != "authenticating") setState("connecting")
                _detail.value = when {
                    _authRequired.value != null -> "Sign in required"
                    snapshot.phase == Phase.LOGGING_IN -> "Logging in\u2026"
                    else -> snapshot.detail.ifBlank { "Connecting to server\u2026" }
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
        _detail.value = "Connecting to server\u2026"
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
            disconnectOnLowHealth = json.optBoolean("disconnectOnLowHealth", false),
            lowHealthThreshold = json.optDouble("lowHealthThreshold", 6.0).toFloat(),
            disconnectOnDeath = json.optBoolean("disconnectOnDeath", false),
            disconnectOnKick = json.optBoolean("disconnectOnKick", false),
            notifyOnMention = json.optBoolean("notifyOnMention", true),
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
        _detail.value = "Connecting to server\u2026"
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
     * view distance, auto-jump, safety disconnects). The engine keeps the whole
     * immutable config, so this replaces it atomically.
     */
    fun pushLiveConfig(
        chatCommand: String,
        delaySeconds: Int,
        viewDistance: Int,
        chatMode: String,
        autoJump: Boolean = config?.autoJump ?: true,
        loopCommand: Boolean = config?.loopCommand ?: false,
        disconnectOnLowHealth: Boolean = config?.disconnectOnLowHealth ?: false,
        lowHealthThreshold: Float = config?.lowHealthThreshold ?: 6f,
        disconnectOnDeath: Boolean = config?.disconnectOnDeath ?: false,
        disconnectOnKick: Boolean = config?.disconnectOnKick ?: false,
        notifyOnMention: Boolean = config?.notifyOnMention ?: true,
    ) {
        val current = config ?: return
        val next = current.copy(
            commands = chatCommand.split('\n').map { it.trim() }.filter { it.isNotEmpty() },
            commandDelaySeconds = delaySeconds.coerceIn(1, 3600),
            viewDistance = viewDistance.coerceIn(2, 12),
            chatMode = chatModeOf(chatMode),
            autoJump = autoJump,
            loopCommand = loopCommand,
            disconnectOnLowHealth = disconnectOnLowHealth,
            lowHealthThreshold = lowHealthThreshold,
            disconnectOnDeath = disconnectOnDeath,
            disconnectOnKick = disconnectOnKick,
            notifyOnMention = notifyOnMention,
        )
        config = next
        EngineHost.engine().updateBot(next)
    }

    private fun checkMentionNotification(line: ChatLine) {
        val conf = config ?: return
        if (!conf.notifyOnMention) return
        val myName = conf.username.trim()
        if (myName.length < 3) return
        // Do not notify on our own echoes
        if (line.sender != null && line.sender.equals(myName, ignoreCase = true)) return
        if (line.sender == "You") return

        val isWhisper = line.type == "whisper"
        val isMention = line.text.contains(myName, ignoreCase = true)
        if (!isWhisper && !isMention) return

        postMentionNotification(line)
    }

    private fun postMentionNotification(line: ChatLine) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                MENTION_CHANNEL_ID,
                "Mentions & Whispers",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Notifications when your username is mentioned in server chat"
                enableVibration(true)
            }
            nm.createNotificationChannel(channel)
        }

        val openApp = PendingIntent.getActivity(
            context,
            serverId.toInt(),
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val title = if (line.type == "whisper") {
            line.sender?.let { "<$it> whispered" } ?: "Direct whisper received"
        } else {
            line.sender?.let { "<$it> mentioned you" } ?: "Mentioned in server chat"
        }

        val notification = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(context, MENTION_CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }
            .setContentTitle(title)
            .setContentText(line.text)
            .setSmallIcon(R.drawable.ic_stat_afk)
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()

        nm.notify((1000 + serverId).toInt(), notification)
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

    companion object {
        const val MENTION_CHANNEL_ID = "afk_mentions"
    }
}
