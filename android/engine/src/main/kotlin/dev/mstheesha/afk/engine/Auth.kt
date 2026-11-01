package dev.mstheesha.afk.engine

import java.util.concurrent.atomic.AtomicBoolean

/**
 * Microsoft device-code -> Xbox -> XSTS -> Minecraft -> session join.
 *
 * Blocking; always called on the engine's blocking executor. Tokens are handed
 * to [TokenStore] so the Android app can keep them in the Keystore.
 */
internal class MicrosoftAuth(
    private val store: TokenStore?,
    private val onDeviceCode: (url: String, code: String) -> Unit,
) {
    companion object {
        const val CLIENT_ID = "00000000441cc96b"
        private const val SCOPE = "service::user.auth.xboxlive.com::MBI_SSL"
        private const val DEVICE_CODE_URL = "https://login.live.com/oauth20_connect.srf"
        private const val TOKEN_URL = "https://login.live.com/oauth20_token.srf"
        private const val XBL_URL = "https://user.auth.xboxlive.com/user/authenticate"
        private const val XSTS_URL = "https://xsts.auth.xboxlive.com/xsts/authorize"
        private const val MC_LOGIN_URL = "https://api.minecraftservices.com/authentication/login_with_xbox"
        private const val PROFILE_URL = "https://api.minecraftservices.com/minecraft/profile"
        private const val JOIN_URL = "https://sessionserver.mojang.com/session/minecraft/join"
    }

    class AuthException(message: String, val kind: Kind = Kind.UNKNOWN) : Exception(message) {
        enum class Kind { EXPIRED, NO_XBOX, CHILD, RATE_LIMIT, UNKNOWN }
    }

    private val refreshed = AtomicBoolean(false)

    @Volatile
    var accessToken: String = ""
        private set
    @Volatile
    var profileUuid: String = ""
        private set
    @Volatile
    var profileName: String = ""
        private set

    /** Returns a valid Minecraft access token, refreshing or signing in as needed. */
    fun ensureAccessToken(): String {
        val token = obtainAccessToken()
        ensureProfile()
        if (profileName.isBlank() || profileUuid.isBlank()) {
            throw AuthException("Minecraft profile unavailable; sign in again")
        }
        return token
    }

    private fun obtainAccessToken(): String {
        if (accessToken.isNotBlank()) return accessToken
        val saved = try {
            store?.load()
        } catch (e: Exception) {
            null
        }
        if (saved != null && saved.accessToken.isNotBlank() &&
            saved.expiresAtMs > System.currentTimeMillis() + 60_000
        ) {
            accessToken = saved.accessToken
            profileUuid = saved.uuid
            profileName = saved.name
            return accessToken
        }
        if (saved != null && saved.refreshToken.isNotBlank() && refreshed.compareAndSet(false, true)) {
            try {
                val ms = refresh(saved.refreshToken)
                finishLogin(ms, saved.refreshToken)
                return accessToken
            } catch (e: Exception) {
                // Expired or revoked refresh token: fall through to device code.
            }
        }
        val ms = deviceCodeFlow()
        finishLogin(ms, ms.refreshToken)
        return accessToken
    }

    /**
     * Older 2.4 imports sometimes carry a token without the profile. Login Start
     * must announce the profile's real name and UUID, so fill them in once.
     */
    private fun ensureProfile() {
        if (profileName.isNotBlank() && profileUuid.isNotBlank()) return
        try {
            val (id, name) = minecraftProfile(accessToken)
            profileUuid = id
            profileName = name
            val current = store ?: return
            val saved = current.load() ?: return
            current.save(saved.copy(accessToken = accessToken, uuid = id, name = name))
        } catch (_: Exception) {
            // Leave blank; ensureAccessToken decides whether that is fatal.
        }
    }

    /** Joins the server session with the hashed server id. */
    fun joinSession(serverHash: String) {
        val token = ensureAccessToken()
        val uuid = profileUuid.ifBlank { throw AuthException("missing Minecraft profile") }
            .replace("-", "") // Mojang expects the dashless form
        val body = Json.write(
            mapOf(
                "accessToken" to token,
                "selectedProfile" to uuid,
                "serverId" to serverHash,
            ),
        )
        try {
            Http.postJson(JOIN_URL, body)
        } catch (e: Http.HttpException) {
            if (e.code == 403) throw AuthException("Session join rejected (auth expired?)", AuthException.Kind.EXPIRED)
            throw AuthException("Session join failed: HTTP ${e.code}")
        }
    }

    fun clear() {
        accessToken = ""
        profileUuid = ""
        profileName = ""
        try {
            store?.clear()
        } catch (_: Exception) {
        }
    }

    private data class MsToken(val accessToken: String, val refreshToken: String, val expiresIn: Long)

    private fun deviceCodeFlow(): MsToken {
        val response = Http.postForm(
            DEVICE_CODE_URL,
            mapOf(
                "client_id" to CLIENT_ID,
                "scope" to SCOPE,
                "response_type" to "device_code",
            ),
        )
        val root = Json.parse(response).asObj() ?: throw AuthException("bad device-code response")
        val deviceCode = root.str("device_code") ?: throw AuthException("no device code")
        val userCode = root.str("user_code") ?: ""
        val url = root.str("verification_uri") ?: "https://www.microsoft.com/link"
        var interval = (root.num("interval") ?: 5L).coerceIn(2, 30)
        val deadline = System.currentTimeMillis() + (root.num("expires_in") ?: 900L) * 1000
        onDeviceCode(url, userCode)
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(interval * 1000)
            try {
                return pollToken(deviceCode)
            } catch (e: PendingException) {
                if (e.slowDown) interval = (interval + 5).coerceAtMost(30)
            }
        }
        throw AuthException("Device code expired; sign in again", AuthException.Kind.EXPIRED)
    }

    private class PendingException(val slowDown: Boolean) : Exception()

    private fun pollToken(deviceCode: String): MsToken {
        val text = try {
            Http.postForm(
                TOKEN_URL,
                mapOf(
                    "client_id" to CLIENT_ID,
                    "device_code" to deviceCode,
                    "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
                ),
            )
        } catch (e: Http.HttpException) {
            val error = runCatching { Json.parse(e.body).asObj()?.str("error") }.getOrNull()
            when (error) {
                "authorization_pending" -> throw PendingException(false)
                "slow_down" -> throw PendingException(true)
                "expired_token" -> throw AuthException("Device code expired", AuthException.Kind.EXPIRED)
                "authorization_declined" -> throw AuthException("Sign-in declined", AuthException.Kind.EXPIRED)
                else -> throw AuthException("Sign-in failed: ${error ?: "HTTP ${e.code}"}")
            }
        }
        return parseToken(text, "")
    }

    private fun refresh(refreshToken: String): MsToken {
        val text = try {
            Http.postForm(
                TOKEN_URL,
                mapOf(
                    "client_id" to CLIENT_ID,
                    "refresh_token" to refreshToken,
                    "grant_type" to "refresh_token",
                    "scope" to SCOPE,
                ),
            )
        } catch (e: Http.HttpException) {
            throw AuthException("Token refresh failed", AuthException.Kind.EXPIRED)
        }
        return parseToken(text, refreshToken)
    }

    private fun parseToken(text: String, previousRefresh: String): MsToken {
        val root = Json.parse(text).asObj() ?: throw AuthException("bad token response")
        val token = root.str("access_token") ?: throw AuthException("no access token")
        val refresh = root.str("refresh_token") ?: previousRefresh
        val expires = root.num("expires_in") ?: 3600L
        return MsToken(token, refresh, expires)
    }

    private fun finishLogin(ms: MsToken, refreshToken: String) {
        val xbl = xboxAuthenticate(ms.accessToken)
        val xsts = xstsAuthorize(xbl.token)
        val identity = "XBL3.0 x=${xsts.uhs};${xsts.token}"
        val mc = minecraftLogin(identity)
        val profile = minecraftProfile(mc)
        accessToken = mc
        profileUuid = profile.first
        profileName = profile.second
        try {
            store?.save(
                StoredTokens(
                    refreshToken = refreshToken,
                    accessToken = mc,
                    expiresAtMs = System.currentTimeMillis() + (ms.expiresIn - 120).coerceAtLeast(60) * 1000,
                    uuid = profile.first,
                    name = profile.second,
                ),
            )
        } catch (_: Exception) {
        }
    }

    private class XblToken(val token: String, val uhs: String)

    private fun xboxAuthenticate(msAccessToken: String): XblToken {
        val body = Json.write(
            mapOf(
                "Properties" to mapOf(
                    "AuthMethod" to "RPS",
                    "SiteName" to "user.auth.xboxlive.com",
                    "RpsTicket" to "d=$msAccessToken",
                ),
                "RelyingParty" to "http://auth.xboxlive.com",
                "TokenType" to "JWT",
            ),
        )
        val root = Json.parse(Http.postJson(XBL_URL, body)).asObj()
            ?: throw AuthException("bad Xbox response")
        val token = root.str("Token") ?: throw AuthException("no Xbox token")
        val uhs = xboxUserHash(root) ?: throw AuthException("no Xbox user hash")
        return XblToken(token, uhs)
    }

    private fun xstsAuthorize(xblToken: String): XblToken {
        val body = Json.write(
            mapOf(
                "Properties" to mapOf(
                    "SandboxId" to "RETAIL",
                    "UserTokens" to listOf(xblToken),
                ),
                "RelyingParty" to "rp://api.minecraftservices.com/",
                "TokenType" to "JWT",
            ),
        )
        val text = try {
            Http.postJson(XSTS_URL, body)
        } catch (e: Http.HttpException) {
            val xerr = runCatching {
                Json.parse(e.body).asObj()?.num("XErr")
            }.getOrNull()
            throw when (xerr) {
                2148916233L -> AuthException(
                    "This Microsoft account has no Xbox profile. Create one at xbox.com first.",
                    AuthException.Kind.NO_XBOX,
                )
                2148916238L -> AuthException(
                    "This account is managed by a family group and cannot sign in here.",
                    AuthException.Kind.CHILD,
                )
                else -> AuthException("Xbox sign-in failed (XErr ${xerr ?: e.code})")
            }
        }
        val root = Json.parse(text).asObj() ?: throw AuthException("bad XSTS response")
        val token = root.str("Token") ?: throw AuthException("no XSTS token")
        val uhs = xboxUserHash(root) ?: throw AuthException("no XSTS user hash")
        return XblToken(token, uhs)
    }

    private fun xboxUserHash(root: Map<String, Any?>): String? {
        val claims = root.obj("DisplayClaims") ?: return null
        val xui = claims.arr("xui") ?: return null
        return xui.firstOrNull()?.asObj()?.str("uhs")
    }

    private fun minecraftLogin(identityToken: String): String {
        val body = Json.write(mapOf("identityToken" to identityToken))
        val text = try {
            Http.postJson(MC_LOGIN_URL, body)
        } catch (e: Http.HttpException) {
            if (e.code == 429) throw AuthException("Minecraft login rate limited", AuthException.Kind.RATE_LIMIT)
            throw AuthException("Minecraft login failed: HTTP ${e.code}")
        }
        return Json.parse(text).asObj()?.str("access_token")
            ?: throw AuthException("no Minecraft access token")
    }

    private fun minecraftProfile(accessToken: String): Pair<String, String> {
        val text = Http.get(PROFILE_URL, bearer = accessToken)
        val root = Json.parse(text).asObj() ?: throw AuthException("bad profile response")
        val id = root.str("id") ?: throw AuthException("profile has no id")
        val name = root.str("name") ?: throw AuthException("profile has no name")
        return id to name
    }
}
