package dev.mstheesha.afk.engine

import java.nio.channels.SelectionKey
import java.nio.channels.SocketChannel
import java.security.KeyFactory
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import java.util.UUID
import javax.crypto.Cipher

/**
 * One bot: login, configuration, play, keep-alive, chat, commands, jump and
 * reconnect. Every method runs on the engine thread; the only exception is
 * [auth], which is only touched by the blocking pool.
 */
internal class BotSession(
    val id: Long,
    var config: BotConfig,
    private val engine: AfkEngine,
    private val listener: BotListener,
) {
    var phase = Phase.CONNECTING
        private set
    var detail = ""
        private set
    var connected = false
        private set
    var protocol = 0
        private set
    var versionName = ""
        private set
    var kicks = 0
        private set
    var health = 20f
        private set
    var food = 20
        private set
    var reconnectInMs = 0L
        private set

    /** Bumped whenever the bot is stopped/restarted; stale timers compare it. */
    var epoch = 0
    var key: SelectionKey? = null
    var conn: Connection? = null
        private set
    @Volatile
    var auth: MicrosoftAuth? = null
    var authPending = false

    /** Authenticated Minecraft profile, resolved before the socket opens in online mode. */
    @Volatile
    private var authName = ""
    @Volatile
    private var authUuid = ""

    /** Name actually announced in Login Start (engine thread only). */
    private var sentLoginName = ""

    private var table: PacketIds? = null
    private var allow: Array<BooleanArray>? = null
    private var configFrames = 0
    private var lastX = 0.0
    private var lastY = 0.0
    private var lastZ = 0.0
    private var lastYaw = 0f
    private var lastPitch = 0f
    private var hasPosition = false
    private var commandIndex = 0
    private var chatSeq = 0
    private var backoffMs = 2_000L
    private var lastInboundMs = 0L
    private var stopped = false
    private var timersScheduled = false
    private var bytesInTotal = 0L
    private var bytesOutTotal = 0L
    private val logRing = LogRing(100)
    private val random = SecureRandom()

    // ---------------------------------------------------------------- lifecycle

    fun start() {
        epoch++
        stopped = false
        kicks = 0
        backoffMs = 2_000L
        commandIndex = 0
        hasPosition = false
        timersScheduled = false
        phase = Phase.CONNECTING
        detail = "Resolving"
        publish()
        engine.connect(this)
    }

    fun stop() {
        if (stopped) return
        stopped = true
        epoch++
        closeConnection()
        phase = Phase.STOPPED
        detail = "Stopped"
        connected = false
        publish()
    }

    fun update(newConfig: BotConfig) {
        val old = config
        config = newConfig
        if (old.chatMode != newConfig.chatMode) buildAllowlist()
        if (!old.autoJump && newConfig.autoJump) scheduleJump()
        if (old.commands != newConfig.commands || old.loopCommand != newConfig.loopCommand) {
            commandIndex = 0
            if (newConfig.commands.isNotEmpty() && phase == Phase.PLAYING) scheduleCommand(1_000)
        }
        publish()
    }

    fun reconnectNow() {
        if (stopped) return
        epoch++
        closeConnection()
        start()
    }

    /** Shared Microsoft session; created once and reused across reconnects. */
    @Synchronized
    fun authSession(): MicrosoftAuth {
        auth?.let { return it }
        val created = MicrosoftAuth(engine.tokenStore) { url, code ->
            engine.notifyAuthCode(this, url, code)
        }
        auth = created
        return created
    }

    /**
     * Records the signed-in profile. Called from the blocking pool before the
     * socket is opened, so Login Start can carry the real name and UUID.
     */
    fun resolveProfile(name: String, uuid: String) {
        authName = name
        authUuid = uuid
    }

    /** Name sent in Login Start: the signed-in profile in online mode. */
    private fun loginName(): String =
        if (config.onlineMode && authName.isNotBlank()) authName else config.username

    // ---------------------------------------------------------------- transport

    fun onConnected(channel: SocketChannel, protocol: Int, version: String) {
        this.protocol = protocol
        this.versionName = version.ifBlank {
            PacketTable.forProtocol(protocol)?.version ?: "protocol $protocol"
        }
        table = PacketTable.forProtocol(protocol)
        buildAllowlist()
        configFrames = 0
        val c = Connection(channel, this)
        conn = c
        lastInboundMs = System.currentTimeMillis()
        c.state = ConnState.HANDSHAKE
        c.send(id(P.HANDSHAKE_C2S_SET_PROTOCOL)) {
            it.writeVarInt(protocol)
            it.writeString(config.host)
            it.writeShort(config.port)
            it.writeVarInt(2) // login
        }
        c.state = ConnState.LOGIN
        sendLoginStart(c)
        phase = Phase.LOGGING_IN
        detail = versionName
        log("Connecting to ${config.host}:${config.port} as ${loginName()} ($versionName)")
        publish()
    }

    fun onConnectFailed(message: String) {
        if (stopped) return
        log("Connect failed: $message")
        if (message.startsWith("Unsupported server version")) {
            stopped = true
            epoch++
            closeConnection()
            phase = Phase.STOPPED
            detail = message.take(200)
            connected = false
            publish()
            engine.notifyStopped(this)
            return
        }
        scheduleReconnect(message)
    }

    fun onDisconnected(reason: String) {
        if (stopped) return
        log("Disconnected: $reason")
        scheduleReconnect(reason)
    }

    fun onIoError(error: Throwable) {
        if (stopped) return
        log("Error: ${error.message ?: error.javaClass.simpleName}")
        scheduleReconnect(error.message ?: "I/O error")
    }

    fun onAuthJoined(secret: ByteArray, encryptedSecret: ByteArray, encryptedToken: ByteArray) {
        authPending = false
        val c = conn ?: return
        sendEncryptionResponse(c, secret, encryptedSecret, encryptedToken)
    }

    fun onAuthFailed(message: String) {
        authPending = false
        log("Sign-in failed: $message")
        val permanent = listOf("expired", "declined", "no xbox", "family group", "rate limited")
            .any { message.lowercase().contains(it) }
        if (permanent) {
            stopped = true
            epoch++
            closeConnection()
            phase = Phase.STOPPED
            detail = message.take(160)
            connected = false
            publish()
            engine.notifyStopped(this)
        } else {
            scheduleReconnect(message)
        }
    }

    fun touchInbound() {
        lastInboundMs = System.currentTimeMillis()
    }

    fun checkTimeout() {
        if (phase != Phase.PLAYING || lastInboundMs == 0L) return
        if (System.currentTimeMillis() - lastInboundMs > 90_000) {
            onDisconnected("Timed out")
        }
    }

    // ---------------------------------------------------------------- outbound

    fun sendChat(text: String) {
        val c = conn ?: return
        if (c.state != ConnState.PLAY || text.isBlank()) return
        if (text.startsWith("/")) sendCommand(c, text.substring(1)) else sendMessage(c, text)
        log("> $text")
    }

    private fun sendMessage(c: Connection, message: String) {
        if (!has(P.PLAY_C2S_CHAT)) return
        val now = System.currentTimeMillis()
        c.send(id(P.PLAY_C2S_CHAT)) {
            it.writeString(message)
            when {
                protocol < 759 -> Unit
                protocol == 759 -> {
                    it.writeLong(now)
                    it.writeLong(0L)
                    it.writeByteArray(ByteArray(0))
                    it.writeBoolean(false)
                }
                protocol == 760 -> {
                    it.writeLong(now)
                    it.writeLong(0L)
                    it.writeByteArray(ByteArray(0))
                    it.writeBoolean(false)
                    it.writeVarInt(0)
                    it.writeBoolean(false)
                }
                protocol < 773 -> {
                    it.writeLong(now)
                    it.writeLong(0L)
                    it.writeBoolean(false) // no signature
                    it.writeVarInt(0) // offset
                    it.writeBytes(ByteArray(3)) // acknowledgement
                }
                else -> {
                    it.writeLong(now)
                    it.writeLong(0L)
                    it.writeBoolean(false)
                    it.writeVarInt(0)
                    it.writeBytes(ByteArray(3))
                    it.writeByte(0) // checksum
                }
            }
        }
    }

    private fun sendCommand(c: Connection, command: String) {
        if (!has(P.PLAY_C2S_CHAT_COMMAND)) return
        val now = System.currentTimeMillis()
        c.send(id(P.PLAY_C2S_CHAT_COMMAND)) {
            it.writeString(command)
            when {
                protocol < 759 -> Unit
                protocol == 759 -> {
                    it.writeLong(now)
                    it.writeLong(0L)
                    it.writeVarInt(0) // argument signatures
                    it.writeBoolean(false) // no preview
                }
                protocol == 760 -> {
                    it.writeLong(now)
                    it.writeLong(0L)
                    it.writeVarInt(0)
                    it.writeBoolean(false)
                    it.writeVarInt(0)
                    it.writeBoolean(false)
                }
                protocol < 766 -> {
                    it.writeLong(now)
                    it.writeLong(0L)
                    it.writeVarInt(0)
                    it.writeVarInt(0)
                    it.writeBytes(ByteArray(3))
                }
                else -> Unit // 1.20.5+: command only
            }
        }
    }

    private fun sendLoginStart(c: Connection) {
        // Online mode must announce the signed-in profile: the proxy asks the
        // session server about this exact name, and the join was made for this
        // profile's UUID.
        val name = loginName()
        sentLoginName = name
        val uuid = if (config.onlineMode) {
            Protocol.uuidFromString(authUuid) ?: Protocol.offlineUuid(name)
        } else {
            Protocol.offlineUuid(name)
        }
        c.send(id(P.LOGIN_C2S_LOGIN_START)) {
            it.writeString(name)
            when {
                protocol == 759 -> it.writeBoolean(false) // no profile signature
                protocol == 760 -> {
                    it.writeBoolean(false)
                    it.writeBoolean(true)
                    it.writeUuid(uuid)
                }
                protocol >= 761 -> it.writeUuid(uuid)
            }
        }
    }

    private fun sendSettings(c: Connection) {
        val slot = if (c.state == ConnState.CONFIG) P.CONFIG_C2S_SETTINGS else P.PLAY_C2S_SETTINGS
        if (!has(slot)) return
        c.send(id(slot)) {
            it.writeString("en_US")
            it.writeByte(config.viewDistance.coerceIn(2, 16))
            if (protocol < 107) {
                it.writeByte(0) // chat flags
            } else {
                it.writeVarInt(0)
            }
            it.writeBoolean(true) // chat colors
            it.writeByte(0x7F) // all skin parts
            if (protocol >= 107) it.writeVarInt(1) // right hand
            if (protocol >= 759) {
                it.writeBoolean(false) // text filtering
                it.writeBoolean(true) // server listing
            }
            if (protocol >= 768) it.writeVarInt(0) // particle status: all
        }
    }

    private fun sendEncryptionResponse(
        c: Connection,
        secret: ByteArray,
        encryptedSecret: ByteArray,
        encryptedToken: ByteArray,
    ) {
        c.send(id(P.LOGIN_C2S_ENCRYPTION_RESPONSE)) {
            it.writeByteArray(encryptedSecret)
            it.writeByteArray(encryptedToken)
        }
        c.enableEncryption(secret)
        detail = "Encrypted"
        publish()
        log("Encryption response sent")
    }

    private fun sendPosition(x: Double, y: Double, z: Double, onGround: Boolean) {
        val c = conn ?: return
        if (c.state != ConnState.PLAY || !has(P.PLAY_C2S_POSITION)) return
        c.send(id(P.PLAY_C2S_POSITION)) {
            it.writeDouble(x)
            it.writeDouble(y)
            it.writeDouble(z)
            if (protocol >= 768) it.writeByte(if (onGround) 1 else 0)
            else it.writeBoolean(onGround)
        }
    }

    private fun sendFlying(onGround: Boolean) {
        val c = conn ?: return
        if (c.state != ConnState.PLAY || !has(P.PLAY_C2S_FLYING)) return
        c.send(id(P.PLAY_C2S_FLYING)) {
            if (protocol >= 768) it.writeByte(if (onGround) 1 else 0)
            else it.writeBoolean(onGround)
        }
    }

    // ---------------------------------------------------------------- inbound

    fun allows(state: ConnState, packetId: Int): Boolean {
        val table = allow ?: return false
        return table[state.ordinal][packetId]
    }

    /** @return false when frame processing should pause (auth in flight). */
    fun handle(state: ConnState, packetId: Int, r: PacketReader): Boolean {
        touchInbound()
        val c = conn ?: return false
        if (authPending) return false
        when (state) {
            ConnState.HANDSHAKE, ConnState.STATUS -> Unit
            ConnState.LOGIN -> handleLogin(c, packetId, r)
            ConnState.CONFIG -> handleConfig(c, packetId, r)
            ConnState.PLAY -> handlePlay(c, packetId, r)
        }
        return !stopped && conn != null
    }

    private fun handleLogin(c: Connection, packetId: Int, r: PacketReader) {
        when (packetId) {
            id(P.LOGIN_S2C_DISCONNECT) -> handleKick(ChatText.fromJson(r.readString(1 shl 20)))
            id(P.LOGIN_S2C_ENCRYPTION_REQUEST) -> handleEncryption(c, r)
            id(P.LOGIN_S2C_SUCCESS) -> handleLoginSuccess(c, r)
            id(P.LOGIN_S2C_COMPRESS) -> {
                val threshold = r.readVarInt()
                c.setCompression(threshold)
                log("Compression enabled (threshold $threshold)")
            }
            id(P.LOGIN_S2C_PLUGIN_REQUEST) -> {
                val messageId = r.readVarInt()
                r.readString(1 shl 16)
                if (has(P.LOGIN_C2S_PLUGIN_RESPONSE)) {
                    c.send(id(P.LOGIN_C2S_PLUGIN_RESPONSE)) {
                        it.writeVarInt(messageId)
                        it.writeBoolean(false) // not understood
                    }
                }
            }
            id(P.LOGIN_S2C_COOKIE_REQUEST) -> {
                // Cookies are optional; answer "no payload" so the proxy does
                // not wait for a response it will never get.
                val key = r.readString(1 shl 16)
                if (has(P.LOGIN_C2S_COOKIE_RESPONSE)) {
                    c.send(id(P.LOGIN_C2S_COOKIE_RESPONSE)) {
                        it.writeString(key)
                        it.writeBoolean(false)
                    }
                }
            }
            else -> Unit
        }
    }

    private fun handleLoginSuccess(c: Connection, r: PacketReader) {
        val uuid = if (protocol >= 735) r.readUuid().toString() else r.readString(64)
        val name = r.readString(64)
        detail = name
        connected = true
        log("Logged in as $name ($uuid)")
        if (protocol >= 764) {
            if (has(P.LOGIN_C2S_LOGIN_ACKNOWLEDGED)) {
                c.send(id(P.LOGIN_C2S_LOGIN_ACKNOWLEDGED))
            }
            c.state = ConnState.CONFIG
            phase = Phase.CONFIGURING
            sendSettings(c)
        } else {
            c.state = ConnState.PLAY
            phase = Phase.PLAYING
            sendSettings(c)
            schedulePlayTimers()
        }
        publish()
    }

    private fun handleEncryption(c: Connection, r: PacketReader) {
        val request = LoginPacketCodec.readEncryptionRequest(protocol, r)
        val publicKey = request.publicKey
        val verifyToken = request.verifyToken
        val shouldAuthenticate = request.shouldAuthenticate
        log("Online mode: server requests authentication = $shouldAuthenticate (protocol $protocol)")

        val secret = ByteArray(16).also(random::nextBytes)
        val key = KeyFactory.getInstance("RSA").generatePublic(X509EncodedKeySpec(publicKey))
        val rsa = Cipher.getInstance("RSA/ECB/PKCS1Padding")
        rsa.init(Cipher.ENCRYPT_MODE, key)
        val encryptedSecret = rsa.doFinal(secret)
        rsa.init(Cipher.ENCRYPT_MODE, key)
        val encryptedToken = rsa.doFinal(verifyToken)

        if (config.onlineMode && shouldAuthenticate) {
            // hasJoined is keyed by the name announced in Login Start, so a join
            // under a different profile can only 204. Reconnect with the profile.
            if (authName.isBlank() || sentLoginName != authName) {
                log("Online mode: announced name does not match the signed-in profile; reconnecting")
                scheduleReconnect("Signed-in profile not ready")
                return
            }
            val hash = Protocol.serverHash(request.serverId, secret, publicKey)
            authPending = true
            detail = "Signing in"
            publish()
            log("Joining Minecraft session…")
            val accepted = engine.dispatch {
                try {
                    authSession().joinSession(hash)
                    engine.notifyAuthJoined(this, secret, encryptedSecret, encryptedToken)
                } catch (e: Exception) {
                    engine.notifyAuthFailed(this, e.message ?: "sign-in failed")
                }
            }
            if (!accepted) onAuthFailed("Engine is busy")
        } else {
            if (shouldAuthenticate) {
                log("Online mode is off for this server; the proxy will reject an unauthenticated join")
            } else if (config.onlineMode) {
                log("Server does not require authentication; replying without a session join")
            }
            sendEncryptionResponse(c, secret, encryptedSecret, encryptedToken)
        }
    }

    private fun handleConfig(c: Connection, packetId: Int, r: PacketReader) {
        // Registry data is the bulk of the config phase; log the interesting
        // packets only so the ring stays readable.
        if (configFrames < 8 && packetId != id(P.CONFIG_S2C_REGISTRY_DATA)) {
            configFrames++
            log("Config packet id=$packetId len=${r.remaining()}")
        }
        when (packetId) {
            id(P.CONFIG_S2C_DISCONNECT) -> handleKick(readComponent(r))
            id(P.CONFIG_S2C_FINISH) -> {
                if (has(P.CONFIG_C2S_FINISH)) {
                    c.send(id(P.CONFIG_C2S_FINISH))
                }
                c.state = ConnState.PLAY
                phase = Phase.PLAYING
                detail = "Joining"
                schedulePlayTimers()
                publish()
            }
            id(P.CONFIG_S2C_KEEP_ALIVE) -> {
                val value = r.readLong()
                if (has(P.CONFIG_C2S_KEEP_ALIVE)) {
                    c.send(id(P.CONFIG_C2S_KEEP_ALIVE)) { it.writeLong(value) }
                }
            }
            id(P.CONFIG_S2C_PING) -> {
                val value = r.readInt()
                if (has(P.CONFIG_C2S_PONG)) {
                    c.send(id(P.CONFIG_C2S_PONG)) { it.writeInt(value) }
                }
            }
            id(P.CONFIG_S2C_COOKIE_REQUEST) -> {
                val key = r.readString(1 shl 16)
                if (has(P.CONFIG_C2S_COOKIE_RESPONSE)) {
                    c.send(id(P.CONFIG_C2S_COOKIE_RESPONSE)) {
                        it.writeString(key)
                        it.writeBoolean(false)
                    }
                }
            }
            id(P.CONFIG_S2C_SELECT_KNOWN_PACKS) -> {
                // The server will not send the registry (and therefore never
                // finishes configuration) until the client answers. We ship no
                // data packs, so an empty selection is the correct answer.
                if (has(P.CONFIG_C2S_SELECT_KNOWN_PACKS)) {
                    c.send(id(P.CONFIG_C2S_SELECT_KNOWN_PACKS)) { it.writeVarInt(0) }
                }
            }
            id(P.CONFIG_S2C_RESOURCE_PACK) -> acceptResourcePack(c, r, true)
            id(P.CONFIG_S2C_CODE_OF_CONDUCT) -> {
                if (has(P.CONFIG_C2S_ACCEPT_CODE_OF_CONDUCT)) {
                    c.send(id(P.CONFIG_C2S_ACCEPT_CODE_OF_CONDUCT))
                }
            }
            else -> Unit
        }
    }

    private fun handlePlay(c: Connection, packetId: Int, r: PacketReader) {
        when (packetId) {
            id(P.PLAY_S2C_KEEP_ALIVE) -> {
                if (has(P.PLAY_C2S_KEEP_ALIVE)) {
                    if (protocol >= 340) {
                        val value = r.readLong()
                        c.send(id(P.PLAY_C2S_KEEP_ALIVE)) { it.writeLong(value) }
                    } else {
                        val value = r.readVarInt()
                        c.send(id(P.PLAY_C2S_KEEP_ALIVE)) { it.writeVarInt(value) }
                    }
                }
            }
            id(P.PLAY_S2C_JOIN_GAME) -> {
                phase = Phase.PLAYING
                detail = "In game"
                connected = true
                commandIndex = 0
                scheduleCommand(1_500)
                publish()
            }
            id(P.PLAY_S2C_DISCONNECT) -> handleKick(readComponent(r))
            id(P.PLAY_S2C_POSITION) -> handlePosition(c, r)
            id(P.PLAY_S2C_UPDATE_HEALTH) -> {
                health = r.readFloat()
                food = r.readVarInt()
                publish()
            }
            id(P.PLAY_S2C_DEATH_COMBAT) -> {
                val e = epoch
                engine.schedule(this, 1_000) {
                    if (epoch == e && phase == Phase.PLAYING && has(P.PLAY_C2S_CLIENT_COMMAND)) {
                        conn?.send(id(P.PLAY_C2S_CLIENT_COMMAND)) { it.writeVarInt(0) }
                    }
                }
            }
            id(P.PLAY_S2C_PING) -> {
                val value = r.readInt()
                if (has(P.PLAY_C2S_PONG)) {
                    c.send(id(P.PLAY_C2S_PONG)) { it.writeInt(value) }
                }
            }
            id(P.PLAY_S2C_RESOURCE_PACK) -> acceptResourcePack(c, r, false)
            id(P.PLAY_S2C_START_CONFIGURATION) -> {
                if (has(P.PLAY_C2S_CONFIGURATION_ACK)) {
                    c.send(id(P.PLAY_C2S_CONFIGURATION_ACK))
                }
                c.state = ConnState.CONFIG
                phase = Phase.CONFIGURING
                detail = "Reconfiguring"
                sendSettings(c)
                publish()
            }
            id(P.PLAY_S2C_CHAT) -> handlePlayerChat(r)
            id(P.PLAY_S2C_SYSTEM_CHAT) -> handleSystemChat(r)
            id(P.PLAY_S2C_DISGUISED_CHAT) -> handleDisguisedChat(r)
            else -> Unit
        }
    }

    private fun handlePosition(c: Connection, r: PacketReader) {
        var teleportId = 0
        var x: Double
        var y: Double
        var z: Double
        var yaw: Float
        var pitch: Float
        var flags: Int
        if (protocol >= 768) {
            teleportId = r.readVarInt()
            x = r.readDouble()
            y = r.readDouble()
            z = r.readDouble()
            val dx = r.readDouble()
            val dy = r.readDouble()
            val dz = r.readDouble()
            yaw = r.readFloat()
            pitch = r.readFloat()
            flags = r.readInt()
            if (hasPosition) {
                if (flags and 0x01 != 0) x = lastX + dx
                if (flags and 0x02 != 0) y = lastY + dy
                if (flags and 0x04 != 0) z = lastZ + dz
                if (flags and 0x08 != 0) yaw = lastYaw + yaw
                if (flags and 0x10 != 0) pitch = lastPitch + pitch
            }
        } else {
            x = r.readDouble()
            y = r.readDouble()
            z = r.readDouble()
            yaw = r.readFloat()
            pitch = r.readFloat()
            flags = if (protocol >= 763) r.readInt() else r.readByte().toInt()
            if (hasPosition) {
                if (flags and 0x01 != 0) x += lastX
                if (flags and 0x02 != 0) y += lastY
                if (flags and 0x04 != 0) z += lastZ
                if (flags and 0x08 != 0) yaw += lastYaw
                if (flags and 0x10 != 0) pitch += lastPitch
            }
            if (protocol >= 107) {
                teleportId = r.readVarInt()
                if (protocol in 755..761) r.readBoolean() // dismount vehicle
            }
        }
        lastX = x
        lastY = y
        lastZ = z
        lastYaw = yaw
        lastPitch = pitch
        hasPosition = true
        if (protocol >= 107 && teleportId != 0 && has(P.PLAY_C2S_TELEPORT_CONFIRM)) {
            c.send(id(P.PLAY_C2S_TELEPORT_CONFIRM)) { it.writeVarInt(teleportId) }
        }
    }

    // ---------------------------------------------------------------- chat

    private fun handlePlayerChat(r: PacketReader) {
        if (protocol < 759) {
            // Pre-1.19 chat packet: JSON + position (0 chat, 1 system, 2 action bar).
            val text = ChatText.fromJson(r.readString(1 shl 20))
            val position = r.readByte().toInt()
            when (config.chatMode) {
                ChatMode.COMMANDS_ONLY -> if (position != 1) return
                ChatMode.MESSAGES_ONLY -> if (position != 0) return
                ChatMode.HIDDEN -> return
                ChatMode.ALL -> Unit
            }
            if (text.isNotBlank()) emitChat(if (position == 0) "chat" else "system", null, text)
            return
        }
        val text = parsePlayerChat(r)
        if (text.isBlank() || config.chatMode == ChatMode.COMMANDS_ONLY) return
        emitChat("chat", null, text)
    }

    private fun parsePlayerChat(r: PacketReader): String = when {
        protocol == 759 -> {
            val signed = r.readString(1 shl 20)
            val unsigned = if (r.readBoolean()) r.readString(1 shl 20) else null
            unsigned ?: signed
        }
        protocol == 760 -> {
            if (r.readBoolean()) r.skip(256)
            r.readUuid()
            val signatureLength = r.readVarInt()
            if (signatureLength > 0) r.skip(signatureLength)
            r.readString(1 shl 20)
        }
        protocol < 770 -> {
            r.readUuid()
            r.readVarInt()
            if (r.readBoolean()) r.skip(256)
            r.readString(1 shl 20)
        }
        else -> {
            r.readVarInt()
            r.readUuid()
            r.readVarInt()
            if (r.readBoolean()) r.skip(256)
            r.readString(1 shl 20)
        }
    }

    private fun handleSystemChat(r: PacketReader) {
        val text = when {
            protocol == 759 -> {
                val json = r.readString(1 shl 20)
                r.readVarInt() // chat type
                ChatText.fromJson(json)
            }
            protocol < 766 -> ChatText.fromJson(r.readString(1 shl 20))
            else -> ChatText.fromNbt(r, false)
        }
        if (config.chatMode == ChatMode.MESSAGES_ONLY || config.chatMode == ChatMode.HIDDEN) return
        emitChat("system", null, text)
    }

    private fun handleDisguisedChat(r: PacketReader) {
        if (config.chatMode == ChatMode.COMMANDS_ONLY || config.chatMode == ChatMode.HIDDEN) return
        val text: String
        val sender: String?
        if (protocol < 765) {
            text = ChatText.fromJson(r.readString(1 shl 20))
            r.readVarInt()
            sender = ChatText.fromJson(r.readString(1 shl 20))
        } else {
            text = ChatText.fromNbt(r, false)
            r.readVarInt()
            sender = ChatText.fromNbt(r, false)
        }
        if (text.isNotBlank()) emitChat("chat", sender.ifBlank { null }, text)
    }

    private fun emitChat(type: String, sender: String?, text: String) {
        val line = ChatLine(++chatSeq, System.currentTimeMillis(), type, sender, text)
        try {
            listener.onChat(line)
        } catch (_: Exception) {
        }
    }

    // ---------------------------------------------------------------- resource packs

    private fun acceptResourcePack(c: Connection, r: PacketReader, configState: Boolean) {
        var uuid: UUID? = null
        var hash = ""
        when {
            protocol >= 765 -> {
                uuid = r.readUuid()
                r.readString(1 shl 20) // url
                hash = r.readString(1 shl 20)
            }
            else -> {
                r.readString(1 shl 20) // url
                hash = r.readString(1 shl 20)
            }
        }
        // Declining gets most bots kicked; claim success without downloading.
        respondPack(c, configState, uuid, hash, 3) // accepted
        respondPack(c, configState, uuid, hash, 0) // successfully loaded
    }

    private fun respondPack(
        c: Connection,
        configState: Boolean,
        uuid: UUID?,
        hash: String,
        result: Int,
    ) {
        val slot = if (configState) P.CONFIG_C2S_RESOURCE_PACK_RECEIVE else P.PLAY_C2S_RESOURCE_PACK_RECEIVE
        if (!has(slot)) return
        c.send(id(slot)) {
            when {
                protocol >= 765 -> it.writeUuid(uuid ?: UUID(0L, 0L))
                protocol < 755 -> it.writeString(hash)
            }
            it.writeVarInt(result)
        }
    }

    // ---------------------------------------------------------------- kick / reconnect

    private fun readComponent(r: PacketReader): String = when {
        protocol >= 766 -> ChatText.fromNbt(r, false)
        else -> ChatText.fromJson(r.readString(1 shl 20))
    }

    private fun handleKick(reason: String) {
        val text = reason.trim().ifBlank { "Disconnected" }
        log("Kicked: $text")
        connected = false
        val lower = text.lowercase()
        if (BAN_MARKERS.any { lower.contains(it) }) {
            stopped = true
            epoch++
            closeConnection()
            phase = Phase.STOPPED
            detail = text.take(200)
            publish()
            engine.notifyStopped(this)
            return
        }
        val rateLimited = RATE_LIMIT_MARKERS.any { lower.contains(it) }
        scheduleReconnect(text, if (rateLimited) 60_000L else -1L)
    }

    private fun scheduleReconnect(reason: String, fixedDelay: Long = -1L) {
        if (stopped) return
        closeConnection()
        kicks++
        phase = Phase.RECONNECTING
        detail = reason.take(160)
        connected = false
        val delay = if (fixedDelay > 0) fixedDelay else backoffMs
        backoffMs = (backoffMs * 2).coerceAtMost(60_000L)
        reconnectInMs = delay
        publish()
        val e = epoch
        engine.schedule(this, delay) {
            if (epoch == e && !stopped) {
                reconnectInMs = 0
                phase = Phase.CONNECTING
                detail = "Reconnecting"
                publish()
                engine.connect(this)
            }
        }
    }

    private fun closeConnection() {
        authPending = false
        key?.cancel()
        key = null
        conn?.close()
        conn = null
    }

    // ---------------------------------------------------------------- timers

    private fun schedulePlayTimers() {
        if (stopped || timersScheduled) return
        timersScheduled = true
        val e = epoch
        engine.schedule(this, 60_000) {
            if (epoch == e && phase == Phase.PLAYING) backoffMs = 2_000L
        }
        engine.schedule(this, 30_000) {
            if (epoch == e) {
                checkTimeout()
                engine.schedule(this, 30_000) { if (epoch == e) checkTimeoutLoop() }
            }
        }
        scheduleJump()
    }

    private fun checkTimeoutLoop() {
        if (stopped) return
        checkTimeout()
        val e = epoch
        engine.schedule(this, 30_000) { if (epoch == e) checkTimeoutLoop() }
    }

    private fun scheduleJump() {
        if (stopped) return
        val e = epoch
        engine.schedule(this, 15_000) {
            if (epoch != e || stopped) return@schedule
            if (!config.autoJump || phase != Phase.PLAYING) {
                scheduleJump()
                return@schedule
            }
            if (hasPosition) {
                sendPosition(lastX, lastY + 0.42, lastZ, false)
                engine.schedule(this, 350) {
                    if (epoch == e && phase == Phase.PLAYING) {
                        sendPosition(lastX, lastY, lastZ, true)
                    }
                }
            } else {
                sendFlying(false)
                engine.schedule(this, 350) {
                    if (epoch == e && phase == Phase.PLAYING) sendFlying(true)
                }
            }
            scheduleJump()
        }
    }

    private fun scheduleCommand(delayMs: Long) {
        if (stopped || config.commands.isEmpty()) return
        val e = epoch
        engine.schedule(this, delayMs) {
            if (epoch != e || phase != Phase.PLAYING) return@schedule
            val list = config.commands
            if (list.isEmpty()) return@schedule
            if (commandIndex >= list.size) {
                if (!config.loopCommand) return@schedule
                commandIndex = 0
            }
            val command = list[commandIndex]
            commandIndex++
            if (command.isNotBlank()) sendChat(command)
            val hasMore = config.loopCommand || commandIndex < list.size
            if (hasMore) scheduleCommand(config.commandDelaySeconds.coerceAtLeast(1) * 1_000L)
        }
    }

    // ---------------------------------------------------------------- plumbing

    private fun buildAllowlist() {
        val table = table ?: return
        val lists = Array(ConnState.values().size) { BooleanArray(256) }
        fun mark(state: ConnState, slot: Int) {
            val packetId = table.id(slot)
            if (packetId in 0..255) lists[state.ordinal][packetId] = true
        }
        for (slot in intArrayOf(
            P.LOGIN_S2C_DISCONNECT,
            P.LOGIN_S2C_ENCRYPTION_REQUEST,
            P.LOGIN_S2C_SUCCESS,
            P.LOGIN_S2C_COMPRESS,
            P.LOGIN_S2C_PLUGIN_REQUEST,
            P.LOGIN_S2C_COOKIE_REQUEST,
        )) mark(ConnState.LOGIN, slot)

        for (slot in intArrayOf(
            P.CONFIG_S2C_DISCONNECT,
            P.CONFIG_S2C_FINISH,
            P.CONFIG_S2C_KEEP_ALIVE,
            P.CONFIG_S2C_PING,
            P.CONFIG_S2C_CUSTOM_PAYLOAD,
            P.CONFIG_S2C_RESOURCE_PACK,
            P.CONFIG_S2C_REMOVE_RESOURCE_PACK,
            P.CONFIG_S2C_COOKIE_REQUEST,
            P.CONFIG_S2C_TRANSFER,
            P.CONFIG_S2C_REGISTRY_DATA,
            P.CONFIG_S2C_FEATURE_FLAGS,
            P.CONFIG_S2C_SELECT_KNOWN_PACKS,
            P.CONFIG_S2C_CODE_OF_CONDUCT,
        )) mark(ConnState.CONFIG, slot)

        for (slot in intArrayOf(
            P.PLAY_S2C_KEEP_ALIVE,
            P.PLAY_S2C_JOIN_GAME,
            P.PLAY_S2C_DISCONNECT,
            P.PLAY_S2C_POSITION,
            P.PLAY_S2C_UPDATE_HEALTH,
            P.PLAY_S2C_RESPAWN,
            P.PLAY_S2C_DEATH_COMBAT,
            P.PLAY_S2C_PING,
            P.PLAY_S2C_RESOURCE_PACK,
            P.PLAY_S2C_REMOVE_RESOURCE_PACK,
            P.PLAY_S2C_COOKIE_REQUEST,
            P.PLAY_S2C_TRANSFER,
            P.PLAY_S2C_START_CONFIGURATION,
            P.PLAY_S2C_CHUNK_BATCH_START,
        )) mark(ConnState.PLAY, slot)

        val mode = config.chatMode
        if (mode != ChatMode.HIDDEN) mark(ConnState.PLAY, P.PLAY_S2C_CHAT)
        if (mode == ChatMode.ALL || mode == ChatMode.COMMANDS_ONLY) {
            mark(ConnState.PLAY, P.PLAY_S2C_SYSTEM_CHAT)
        }
        if (mode == ChatMode.ALL || mode == ChatMode.MESSAGES_ONLY) {
            mark(ConnState.PLAY, P.PLAY_S2C_DISGUISED_CHAT)
        }
        allow = lists
    }

    private fun id(slot: Int): Int = table?.id(slot) ?: -1
    private fun has(slot: Int): Boolean = (table?.id(slot) ?: -1) in 0..255

    fun snapshot(): BotSnapshot {
        val c = conn
        if (c != null) {
            bytesInTotal = c.bytesIn
            bytesOutTotal = c.bytesOut
        }
        return BotSnapshot(
            phase = phase,
            detail = detail,
            connected = connected,
            username = loginName(),
            protocol = protocol,
            versionName = versionName,
            bytesIn = bytesInTotal,
            bytesOut = bytesOutTotal,
            health = health,
            food = food,
            kicks = kicks,
            reconnectInMs = reconnectInMs,
        )
    }

    private fun publish() = engine.publish(id, snapshot())

    /** Called by the engine's 1 s tick so byte counters stay fresh. */
    internal fun pushNow() = publish()

    private fun log(line: String) {
        logRing.add(line)
        try {
            listener.onLog(line)
        } catch (_: Exception) {
        }
    }

    /** Engine-thread entry point for lines produced on the blocking pool. */
    internal fun logExternal(line: String) = log(line)

    fun recentLog(): List<String> = logRing.toList()

    private companion object {
        val BAN_MARKERS = listOf(
            "banned", "ban ", "blacklist", "not whitelisted", "not allowed on this server",
        )
        val RATE_LIMIT_MARKERS = listOf(
            "rate limit", "too fast", "slow down", "wait before", "throttl",
        )
    }
}
