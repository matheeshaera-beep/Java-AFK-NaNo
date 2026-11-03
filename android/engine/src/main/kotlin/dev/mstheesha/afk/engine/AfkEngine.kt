package dev.mstheesha.afk.engine

import java.net.InetSocketAddress
import java.nio.channels.SelectionKey
import java.nio.channels.Selector
import java.nio.channels.SocketChannel
import java.util.PriorityQueue
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ThreadFactory
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * The whole engine: one daemon thread, one [Selector], one command queue, one
 * timer heap and one bounded blocking pool for DNS/connect/auth.
 *
 * Public methods are thread-safe and never block; all socket work happens on
 * the engine thread. Listener callbacks are delivered on that same thread.
 */
class AfkEngine(
    private val listenerFactory: (Long) -> BotListener,
    internal val tokenStore: TokenStore? = null,
    private val options: Options = Options(),
) : AutoCloseable {

    data class Options(
        val connectTimeoutMs: Int = 10_000,
        val blockingThreads: Int = 4,
        val blockingQueue: Int = 64,
        /** How long a connected socket may stay pre-PLAY before reconnecting. */
        val prePlayTimeoutMs: Int = 15_000,
        /** Threads dedicated to Microsoft sign-in (device code can take minutes). */
        val authThreads: Int = 1,
        val authQueue: Int = 8,
    )

    data class Metrics(
        val bots: Int,
        val threads: Int,
        val heapUsed: Long,
        val heapMax: Long,
        val bytesIn: Long,
        val bytesOut: Long,
    )

    private sealed interface Command {
        data class Start(val config: BotConfig) : Command
        data class Stop(val id: Long) : Command
        data class Update(val config: BotConfig) : Command
        data class Chat(val id: Long, val text: String) : Command
        data class Reconnect(val id: Long) : Command
        data class Connected(
            val bot: BotSession,
            val channel: SocketChannel,
            val protocol: Int,
            val version: String,
        ) : Command
        data class ConnectFailed(val bot: BotSession, val message: String) : Command
        data class AuthJoined(
            val bot: BotSession,
            val secret: ByteArray,
            val encryptedSecret: ByteArray,
            val encryptedToken: ByteArray,
        ) : Command
        data class AuthFailed(val bot: BotSession, val message: String) : Command
        data class AuthCode(val bot: BotSession, val url: String, val code: String) : Command
        data class LogLine(val bot: BotSession, val line: String) : Command
        data class Stopped(val bot: BotSession) : Command
        data object Shutdown : Command
    }

    private class TimerTask(
        val botId: Long,
        val epoch: Int,
        val due: Long,
        val order: Long,
        val action: (BotSession) -> Unit,
    )

    private val selector: Selector = Selector.open()
    private val commands = ConcurrentLinkedQueue<Command>()
    private val bots = HashMap<Long, BotSession>()
    private val listeners = HashMap<Long, BotListener>()
    private val snapshots = ConcurrentHashMap<Long, BotSnapshot>()
    private val timers = PriorityQueue<TimerTask>(
        compareBy<TimerTask>({ it.due }, { it.order }),
    )
    private val executor: ThreadPoolExecutor
    private val authExecutor: ThreadPoolExecutor
    private var order = 0L
    private var lastPublish = 0L

    @Volatile
    private var running = true

    private val thread = Thread(::run, "afk-engine").apply { isDaemon = true }

    init {
        val counter = AtomicInteger()
        val factory = ThreadFactory { r ->
            Thread(r, "afk-io-${counter.incrementAndGet()}").apply { isDaemon = true }
        }
        executor = ThreadPoolExecutor(
            options.blockingThreads,
            options.blockingThreads,
            30,
            TimeUnit.SECONDS,
            ArrayBlockingQueue(options.blockingQueue),
            factory,
            ThreadPoolExecutor.AbortPolicy(),
        ).apply { allowCoreThreadTimeOut(true) }
        // Sign-in is split onto its own small pool: a device-code prompt can
        // block for up to 15 minutes and must not starve DNS/connect work.
        val authCounter = AtomicInteger()
        val authFactory = ThreadFactory { r ->
            Thread(r, "afk-auth-${authCounter.incrementAndGet()}").apply { isDaemon = true }
        }
        authExecutor = ThreadPoolExecutor(
            options.authThreads,
            options.authThreads,
            30,
            TimeUnit.SECONDS,
            ArrayBlockingQueue(options.authQueue),
            authFactory,
            ThreadPoolExecutor.AbortPolicy(),
        ).apply { allowCoreThreadTimeOut(true) }
        thread.start()
    }

    // ------------------------------------------------------------------ public API

    fun startBot(config: BotConfig) = post(Command.Start(config))

    fun stopBot(id: Long) = post(Command.Stop(id))

    fun updateBot(config: BotConfig) = post(Command.Update(config))

    fun sendChat(id: Long, text: String) = post(Command.Chat(id, text))

    fun reconnect(id: Long) = post(Command.Reconnect(id))

    fun snapshot(id: Long): BotSnapshot? = snapshots[id]

    fun snapshots(): Map<Long, BotSnapshot> = HashMap(snapshots)

    fun metrics(): Metrics {
        val runtime = Runtime.getRuntime()
        var inBytes = 0L
        var outBytes = 0L
        for (snapshot in snapshots.values) {
            inBytes += snapshot.bytesIn
            outBytes += snapshot.bytesOut
        }
        return Metrics(
            bots = snapshots.size,
            threads = Thread.activeCount(),
            heapUsed = runtime.totalMemory() - runtime.freeMemory(),
            heapMax = runtime.maxMemory(),
            bytesIn = inBytes,
            bytesOut = outBytes,
        )
    }

    override fun close() {
        post(Command.Shutdown)
        try {
            thread.join(3_000)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
        if (thread.isAlive) thread.interrupt()
        executor.shutdownNow()
        authExecutor.shutdownNow()
        try {
            selector.close()
        } catch (_: Exception) {
        }
    }

    // ------------------------------------------------------------------ engine thread

    private fun run() {
        while (running) {
            try {
                drainCommands()
                if (!running) break
                val now = System.currentTimeMillis()
                if (now - lastPublish >= 1_000) {
                    lastPublish = now
                    for (bot in bots.values) bot.pushNow()
                }
                selector.select(nextTimeout().toLong())
                val keys = selector.selectedKeys()
                val iterator = keys.iterator()
                while (iterator.hasNext()) {
                    val key = iterator.next()
                    iterator.remove()
                    val bot = key.attachment() as? BotSession ?: continue
                    if (!key.isValid) continue
                    try {
                        if (key.isReadable) readBot(bot)
                        if (key.isValid && key.isWritable) writeBot(bot)
                    } catch (e: Throwable) {
                        bot.onIoError(e)
                    }
                }
                runTimers()
            } catch (e: InterruptedException) {
                break
            } catch (e: Throwable) {
                // A single bad bot must never kill the engine thread.
                if (!running) break
            }
        }
        shutdownAll()
    }

    private fun drainCommands() {
        while (true) {
            val command = commands.poll() ?: return
            try {
                handle(command)
            } catch (e: Throwable) {
                // Ignore malformed commands; the bot will reconnect or stay stopped.
            }
        }
    }

    private fun handle(command: Command) {
        when (command) {
            is Command.Start -> startBotNow(command.config)
            is Command.Stop -> stopBotNow(command.id)
            is Command.Update -> bots[command.config.id]?.update(command.config)
            is Command.Chat -> {
                val bot = bots[command.id] ?: return
                bot.sendChat(command.text)
                refreshInterest(bot)
            }
            is Command.Reconnect -> bots[command.id]?.reconnectNow()
            is Command.Connected -> attach(command)
            is Command.ConnectFailed -> {
                if (bots[command.bot.id] === command.bot) command.bot.onConnectFailed(command.message)
            }
            is Command.AuthJoined -> {
                if (bots[command.bot.id] === command.bot) {
                    command.bot.onAuthJoined(
                        command.secret,
                        command.encryptedSecret,
                        command.encryptedToken,
                    )
                }
            }
            is Command.AuthFailed -> {
                if (bots[command.bot.id] === command.bot) command.bot.onAuthFailed(command.message)
            }
            is Command.AuthCode -> {
                if (bots[command.bot.id] !== command.bot) return
                val listener = listeners[command.bot.id] ?: return
                try {
                    listener.onAuthRequired(command.url, command.code)
                } catch (_: Exception) {
                }
            }
            is Command.LogLine -> {
                if (bots[command.bot.id] === command.bot) command.bot.logExternal(command.line)
            }
            is Command.Stopped -> {
                if (bots[command.bot.id] !== command.bot) return
                bots.remove(command.bot.id)
                command.bot.key?.cancel()
                command.bot.key = null
                listeners.remove(command.bot.id)
            }
            is Command.Shutdown -> running = false
        }
    }

    private fun startBotNow(config: BotConfig) {
        bots[config.id]?.stop()
        bots.remove(config.id)
        listeners.remove(config.id)
        val listener = try {
            listenerFactory(config.id)
        } catch (e: Exception) {
            return
        }
        val bot = BotSession(config.id, config, this, listener)
        bots[config.id] = bot
        listeners[config.id] = listener
        snapshots[config.id] = bot.snapshot()
        bot.start()
    }

    private fun stopBotNow(id: Long) {
        val bot = bots.remove(id) ?: return
        bot.stop()
        bot.key?.cancel()
        bot.key = null
        listeners.remove(id)
    }

    private fun attach(command: Command.Connected) {
        val bot = command.bot
        if (bots[bot.id] !== bot) {
            try {
                command.channel.close()
            } catch (_: Exception) {
            }
            return
        }
        try {
            val key = command.channel.register(selector, SelectionKey.OP_READ, bot)
            bot.key = key
            bot.onConnected(command.channel, command.protocol, command.version)
            refreshInterest(bot)
        } catch (e: Exception) {
            try {
                command.channel.close()
            } catch (_: Exception) {
            }
            bot.onConnectFailed(e.message ?: "connect failed")
        }
    }

    private fun readBot(bot: BotSession) {
        val connection = bot.conn ?: return
        var reads = 0
        var eof = false
        while (reads < 16) {
            val read = connection.readFromChannel()
            if (read < 0) {
                eof = true
                break
            }
            if (read == 0) break
            reads++
        }
        // Decode everything already buffered before acting on EOF: a final
        // chat line or kick reason must not be dropped just because the peer
        // closed right after sending it.
        var frames = 0
        while (frames < 512) {
            val n = connection.processFrames(16)
            frames += n
            // Fewer than a full batch means the buffer is empty (or the bot
            // paused for auth); nothing left to do until the next read.
            if (n < 16) break
        }
        if (eof) {
            // A kick already scheduled a reconnect and closed the connection.
            if (bot.conn === connection) bot.onDisconnected("connection closed")
            return
        }
        refreshInterest(bot)
    }

    private fun writeBot(bot: BotSession) {
        val connection = bot.conn ?: return
        if (connection.flushPending()) {
            bot.key?.let { key ->
                if (key.isValid) key.interestOps(SelectionKey.OP_READ)
            }
        }
    }

    private fun refreshInterest(bot: BotSession) {
        val key = bot.key ?: return
        if (!key.isValid) return
        val write = bot.conn?.hasPendingWrite() == true
        key.interestOps(SelectionKey.OP_READ or if (write) SelectionKey.OP_WRITE else 0)
    }

    private fun nextTimeout(): Int {
        val now = System.currentTimeMillis()
        val due = timers.peek()?.due ?: return 250
        return (due - now).coerceIn(1L, 250L).toInt()
    }

    private fun runTimers() {
        val now = System.currentTimeMillis()
        while (true) {
            val task = timers.peek() ?: return
            if (task.due > now) return
            timers.poll()
            val bot = bots[task.botId] ?: continue
            if (bot.epoch != task.epoch) continue
            try {
                task.action(bot)
            } catch (e: Throwable) {
                bot.onIoError(e)
            }
            refreshInterest(bot)
        }
    }

    private fun shutdownAll() {
        running = false
        for (bot in bots.values.toList()) bot.stop()
        bots.clear()
        listeners.clear()
        timers.clear()
    }

    // ------------------------------------------------------------------ internal API

    internal fun publish(id: Long, snapshot: BotSnapshot) {
        snapshots[id] = snapshot
        val listener = listeners[id] ?: return
        try {
            listener.onSnapshot(snapshot)
        } catch (_: Exception) {
        }
    }

    internal fun schedule(bot: BotSession, delayMs: Long, action: (BotSession) -> Unit) {
        if (!running) return
        timers.add(TimerTask(bot.id, bot.epoch, System.currentTimeMillis() + delayMs, order++, action))
        selector.wakeup()
    }

    internal val prePlayTimeoutMs: Int get() = options.prePlayTimeoutMs

    internal fun connect(bot: BotSession) {
        if (!running || bots[bot.id] !== bot) return
        if (!bot.config.onlineMode) {
            openSocket(bot)
            return
        }
        // Sign-in can block for minutes while waiting for a device code. Run it
        // on the auth pool so other servers keep connecting, then hand off to
        // the socket pool once the profile is known.
        try {
            authExecutor.execute {
                try {
                    val session = bot.authSession()
                    val cached = session.accessToken.isNotBlank()
                    if (!cached) post(Command.LogLine(bot, "Signing in with Microsoft…"))
                    session.ensureAccessToken()
                    bot.resolveProfile(session.profileName, session.profileUuid)
                    if (!cached) post(Command.LogLine(bot, "Signed in as ${session.profileName}"))
                } catch (e: Exception) {
                    post(Command.AuthFailed(bot, e.message ?: "sign-in failed"))
                    return@execute
                }
                if (running && bots[bot.id] === bot) openSocket(bot)
            }
        } catch (e: RejectedExecutionException) {
            post(Command.ConnectFailed(bot, "Engine is busy"))
        }
    }

    private fun openSocket(bot: BotSession) {
        try {
            executor.execute { connectNow(bot) }
        } catch (e: RejectedExecutionException) {
            post(Command.ConnectFailed(bot, "Engine is busy"))
        }
    }

    private fun connectNow(bot: BotSession) {
        if (!running || bots[bot.id] !== bot) return
        try {
            val target = HostPolicy.resolve(bot.config.host, bot.config.port)
            var pingVersion = ""
            var protocol = if (bot.config.protocolOverride > 0) {
                bot.config.protocolOverride
            } else {
                val ping = try {
                    ServerListPing.ping(target.host, target.port, options.connectTimeoutMs)
                } catch (e: Exception) {
                    null
                }
                pingVersion = ping?.versionName ?: ""
                ping?.protocol?.takeIf { it > 0 } ?: PacketTable.latest.protocol
            }
            if (PacketTable.forProtocol(protocol) == null) {
                val fallback = VersionCatalog.nearestSupported(protocol)
                if (fallback == null) {
                    post(
                        Command.ConnectFailed(
                            bot,
                            "Unsupported server version (protocol $protocol). " +
                                "Supported: ${PacketTable.supportedVersions}",
                        ),
                    )
                    return
                }
                val label = VersionCatalog.nameOf(fallback) ?: "protocol $fallback"
                post(
                    Command.LogLine(
                        bot,
                        "Server reports protocol $protocol, which is unsupported; " +
                            "using $fallback ($label)",
                    ),
                )
                protocol = fallback
                pingVersion = label
            }
            val channel = SocketChannel.open()
            channel.configureBlocking(true)
            try {
                channel.socket().tcpNoDelay = true
                channel.socket().keepAlive = true
                channel.socket().connect(
                    InetSocketAddress(target.host, target.port),
                    options.connectTimeoutMs,
                )
            } catch (e: Exception) {
                try {
                    channel.close()
                } catch (_: Exception) {
                }
                throw e
            }
            channel.configureBlocking(false)
            post(Command.Connected(bot, channel, protocol, pingVersion))
        } catch (e: Exception) {
            post(Command.ConnectFailed(bot, e.message ?: e.javaClass.simpleName))
        }
    }

    internal fun dispatch(task: () -> Unit): Boolean {
        return try {
            executor.execute(task)
            true
        } catch (e: RejectedExecutionException) {
            false
        }
    }

    internal fun notifyAuthCode(bot: BotSession, url: String, code: String) =
        post(Command.AuthCode(bot, url, code))

    internal fun notifyAuthJoined(
        bot: BotSession,
        secret: ByteArray,
        encryptedSecret: ByteArray,
        encryptedToken: ByteArray,
    ) = post(Command.AuthJoined(bot, secret, encryptedSecret, encryptedToken))

    internal fun notifyAuthFailed(bot: BotSession, message: String) =
        post(Command.AuthFailed(bot, message))

    internal fun notifyStopped(bot: BotSession) = post(Command.Stopped(bot))

    private fun post(command: Command) {
        commands.add(command)
        selector.wakeup()
    }
}
