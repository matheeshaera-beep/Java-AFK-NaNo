package dev.mstheesha.afk

import android.content.Context
import android.content.Intent
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
    val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _activeCount = MutableStateFlow(0)
    val activeCount: Flow<Int> get() = _activeCount

    @Volatile
    var uiVisible: Boolean = false
        set(value) {
            field = value
            EngineHost.setUiVisible(value)
            if (value) {
                val all = synchronized(sessions) { sessions.values.toList() }
                all.forEach { it.flushBuffersIfDirty() }
            }
        }

    private val serverNames = mutableMapOf<Long, String>()
    private val _namesTick = MutableStateFlow(0)
    val namesTick: Flow<Int> get() = _namesTick

    fun noteServerName(id: Long, name: String) = synchronized(sessions) {
        if (serverNames[id] != name) {
            serverNames[id] = name
            _namesTick.value++
        }
    }

    fun activeServerNames(): List<String> = synchronized(sessions) {
        sessions.filter { (_, s) -> s.state.value in ACTIVE_STATES }
            .map { (id, _) -> serverNames[id] ?: "Server $id" }
    }

    fun hasActiveSessions(): Boolean = synchronized(sessions) {
        sessions.values.any { it.state.value in ACTIVE_STATES }
    }

    fun totalSessionBytes(): Long = synchronized(sessions) {
        sessions.values.sumOf { it.sessionDataBytes.value }
    }

    lateinit var db: AfkDatabase
        private set

    fun init(context: Context) {
        appContext = context.applicationContext
        Prefs.init(context)
        if (!::db.isInitialized) db = AfkDatabase.get(context)
        EngineHost.init(context)
        AlertNotifier.ensureChannel(context)
    }

    fun sessionFor(serverId: Long): ServerSession = synchronized(sessions) {
        sessions.getOrPut(serverId) {
            val session = ServerSession(context(), serverId)
            sessionJobs[serverId] = scope.launch(Dispatchers.Default) {
                session.state.collect { recomputeActiveCount() }
            }
            session
        }
    }

    fun startServer(ctx: Context, server: ServerEntity): Boolean {
        val cap = Prefs.maxBots(ctx)
        if (cap > 0 && _activeCount.value >= cap) {
            val existing = sessionFor(server.id)
            if (existing.state.value !in ACTIVE_STATES) return false
        }
        noteServerName(server.id, server.name)
        val session = sessionFor(server.id)
        session.start(server)
        ctx.startForegroundService(
            Intent(ctx, AfkService::class.java).setAction(AfkService.ACTION_START),
        )
        return true
    }

    fun removeSession(serverId: Long) {
        val session = synchronized(sessions) {
            sessions.remove(serverId)?.also { sessionJobs.remove(serverId)?.cancel() }
        }
        session?.dispose()
        recomputeActiveCount()
    }

    fun stopAllSessions() {
        val all = synchronized(sessions) { sessions.values.toList() }
        all.forEach { it.stop() }
    }

    fun reconnectAllSessions() {
        val active = synchronized(sessions) {
            sessions.values.filter { it.state.value in ACTIVE_STATES }
        }
        active.forEach { it.reconnect() }
    }

    fun trimMemory() {
        EngineHost.trimMemory()
        val all = synchronized(sessions) { sessions.values.toList() }
        all.forEach { it.trimMemory() }
    }

    private fun recomputeActiveCount() {
        val n = synchronized(sessions) { sessions.values.count { it.state.value in ACTIVE_STATES } }
        _activeCount.value = n
        _namesTick.value++
    }

    private fun context(): Context =
        appContext ?: throw IllegalStateException("AppGraph not initialized with a context")

    private var appContext: Context? = null

    fun setContext(ctx: Context) {
        appContext = ctx.applicationContext
    }
}