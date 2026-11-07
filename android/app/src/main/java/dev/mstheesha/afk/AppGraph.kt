package dev.mstheesha.afk

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

object AppGraph {
    /** States in which a bot is running or trying to run. */
    private val ACTIVE_STATES = setOf("connecting", "connected", "reconnecting", "authenticating")

    private val sessions = mutableMapOf<Long, ServerSession>()
    private val sessionJobs = mutableMapOf<Long, Job>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _activeCount = MutableStateFlow(0)
    val activeCount: Flow<Int> get() = _activeCount

    /** True while the UI is in the foreground. The session poll loop uses a
     *  fast interval then, and a slow one when the screen is off. */
    @Volatile
    var uiVisible = false

    // Display names for sessions, keyed by server id. The notification reads
    // these to show which server(s) are actually running.
    private val serverNames = mutableMapOf<Long, String>()
    private val _namesTick = MutableStateFlow(0)
    val namesTick: Flow<Int> get() = _namesTick

    /** Remembered from UI surfaces that know the entity (list rows, session). */
    fun noteServerName(id: Long, name: String) = synchronized(sessions) {
        if (serverNames[id] != name) {
            serverNames[id] = name
            _namesTick.value++
        }
    }

    /** Names of sessions that are running or trying to run. Used as the
     *  notification text. */
    fun activeServerNames(): List<String> = synchronized(sessions) {
        sessions.filter { (_, s) -> s.state.value in ACTIVE_STATES }
            .map { (id, _) -> serverNames[id] ?: "Server $id" }
    }

    /**
     * True while at least one bot is connecting, connected, reconnecting or
     * waiting for sign-in. This is the single liveness signal for the
     * foreground service, the notification and the wake lock: when it is
     * false nothing should be left running.
     */
    fun hasActiveSessions(): Boolean = synchronized(sessions) {
        sessions.values.any { it.state.value in ACTIVE_STATES }
    }

    lateinit var db: AfkDatabase
        private set

    fun init(context: android.content.Context) {
        if (!::db.isInitialized) db = AfkDatabase.get(context)
        EngineHost.init(context)
    }

    /** Returns the shared ServerSession for a server (never starts a Node per server). */
    fun sessionFor(serverId: Long): ServerSession = synchronized(sessions) {
        sessions.getOrPut(serverId) {
            val session = ServerSession(context(), serverId)
            sessionJobs[serverId] = scope.launch(Dispatchers.Default) {
                session.state.collect { recomputeActiveCount() }
            }
            session
        }
    }
    fun removeSession(serverId: Long) {
        val session = synchronized(sessions) {
            sessions.remove(serverId)?.also { sessionJobs.remove(serverId)?.cancel() }
        }
        session?.dispose()
        recomputeActiveCount()
    }

    /** Disconnects every session (each posts stop to the bridge and goes
     *  disconnected). Used by the notification's force-stop action so no
     *  session is left showing a stale Connected state. */
    fun stopAllSessions() {
        val all = synchronized(sessions) { sessions.values.toList() }
        all.forEach { it.stop() }
    }

    /** Reconnects all sessions currently running or trying to run. */
    fun reconnectAllSessions() {
        val active = synchronized(sessions) {
            sessions.values.filter { it.state.value in ACTIVE_STATES }
        }
        active.forEach { it.reconnect() }
    }

    /**
     * A session starts being "active" when it leaves 'disconnected' (a start or
     * reconnect was requested) and returns to idle once stopped/disconnected.
     * 'error' is terminal (the bot is not connected and nothing retries), so it
     * does not keep the service alive.
     */
    private fun recomputeActiveCount() {
        val n = synchronized(sessions) { sessions.values.count { it.state.value in ACTIVE_STATES } }
        _activeCount.value = n
        // State transitions also change the notification's server-name list.
        _namesTick.value++
    }

    private fun context(): android.content.Context =
        appContext ?: throw IllegalStateException("AppGraph not initialized with a context")

    private var appContext: android.content.Context? = null
        private set

    fun setContext(ctx: android.content.Context) {
        appContext = ctx.applicationContext
    }
}