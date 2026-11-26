package dev.mstheesha.afk

import android.content.Context
import dev.mstheesha.afk.engine.AfkEngine
import dev.mstheesha.afk.engine.BotListener
import dev.mstheesha.afk.engine.StoredTokens
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Owns the ONE engine for the whole process. Every [ServerSession] registers a
 * listener before starting its bot; the engine thread looks them up by id when
 * it has something to report. No polling, no local socket, no Node runtime.
 */
object EngineHost {

    private val listeners = ConcurrentHashMap<Long, BotListener>()
    private val emptyAccounts = MutableStateFlow<List<AccountInfo>>(emptyList())

    @Volatile
    private var engineInstance: AfkEngine? = null

    @Volatile
    private var tokenStore: KeystoreTokenStore? = null

    @Synchronized
    fun init(context: Context) {
        if (engineInstance != null) return
        val store = KeystoreTokenStore(context)
        tokenStore = store
        engineInstance = AfkEngine(
            listenerFactory = { id -> listeners[id] ?: NoopListener },
            tokenStore = store,
        )
    }

    fun engine(): AfkEngine = engineInstance
        ?: throw IllegalStateException("EngineHost.init() was not called")

    fun accountsFlow(): StateFlow<List<AccountInfo>> = tokenStore?.accounts ?: emptyAccounts

    fun register(id: Long, listener: BotListener) {
        listeners[id] = listener
    }

    fun unregister(id: Long) {
        listeners.remove(id)
    }

    fun clearTokens(accountKey: String = KeystoreTokenStore.DEFAULT_KEY) {
        tokenStore?.clear(accountKey)
    }

    fun signInAccount(
        accountKey: String,
        onCode: (url: String, code: String) -> Unit,
        onResult: (Result<StoredTokens>) -> Unit,
    ) {
        engine().signInAccount(accountKey, onCode, onResult)
    }

    fun setUiVisible(visible: Boolean) {
        engineInstance?.setUiVisible(visible)
    }

    fun setNetworkAvailable(available: Boolean) {
        engineInstance?.setNetworkAvailable(available)
    }

    fun trimMemory() {
        engineInstance?.trimMemory()
    }

    private object NoopListener : BotListener {
        override fun onSnapshot(snapshot: dev.mstheesha.afk.engine.BotSnapshot) = Unit
        override fun onLog(line: String) = Unit
        override fun onChat(line: dev.mstheesha.afk.engine.ChatLine) = Unit
        override fun onAuthRequired(url: String, code: String) = Unit
    }
}
