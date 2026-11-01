# Java AFK 3.0 — lightweight pure-Kotlin engine

Date: 2026-10-30
Status: approved for autonomous execution by the task brief (no interactive approval gate)

## Intent

Replace the embedded Node.js 18 + mineflayer + localhost HTTP bridge with a
from-scratch Kotlin Minecraft Java Edition protocol client that has **no world
model**. The app keeps the Java AFK look and feature set, but each bot becomes a
few kilobytes of state on one shared non-blocking I/O thread.

The why: today every bot starts a V8 isolate, mineflayer, a 50 ms physics loop
and a full chunk/entity world. That costs hundreds of MB per bot, heats the
phone, burns data and produces a 65 MB APK. The new engine connects,
authenticates, answers keep-alives, reads chat, occasionally jumps, reconnects,
and drops every other packet at the frame layer before parsing.

## Goals

- G1 release APK contains no `lib/` and no Node assets.
- G2 APK at least 85% smaller than 64.9 MB (target < 8 MB).
- G3 5 idle bots in the JVM harness: < 150 KB heap per bot after GC.
- G4 exactly one engine thread + one blocking executor for all bots.
- G5 engine thread blocked in `select()` between keep-alives; no polling loops.
- G6 join/survive/chat/commands/reconnect/respawn on 1.8.9, 1.12.2, 1.16.5,
  1.19.4, 1.20.4, 1.20.6 and the latest release.
- G7 bytes/hour per bot at view distance 2, compared with the old app estimate.
- G8 clean repo: no unreferenced files, UnusedResources clean, no unused deps,
  README/AGENTS.md updated, fresh-clone build passes.
- G9 reviewer approves the diff.

## Non-goals / anti-goals

- No inventory/GUI windows, entity/block tracking, physics, mining, proxies,
  licensing, resource-pack cache, low-RAM toggle, packet tracing, compat toggles.
- No secure chat signing. Chat is sent unsigned (1.19+ signing fields zeroed).
  Servers enforcing secure profiles may reject chat but not commands. Report it,
  do not solve it.
- No redesign of the Java AFK UI.

## Approaches considered

1. **Keep Node, trim mineflayer plugins.** Smallest code change, but cannot meet
   G1/G2/G3 and keeps a native runtime and per-bot V8 cost. Rejected.
2. **Use a third-party Java protocol library (MCProtocolLib).** Less code, but it
   builds a world/protocol object graph, adds a large dependency, and does not
   give the frame-level allowlist or the memory profile required. Rejected.
3. **From-scratch minimal client (chosen).** One selector thread, one command
   queue, one timer heap, shared compressor/writer, per-bot 4 KB/1 KB shrinking
   buffers, per-state `boolean[256]` allowlist, data-driven packet IDs. This is
   the only approach that meets every goal.

## Module layout

```
android/
  settings.gradle.kts        include(":app", ":engine")
  engine/                    pure-JVM Kotlin module, no android.* imports
    src/main/kotlin/dev/mstheesha/afk/engine/
      Engine.kt              singleton: thread, selector, command inbox, timers
      EngineConfig.kt        data classes for bot config / snapshots
      Connection.kt          per-bot channel, buffers, ciphers, frame decoder
      PacketReader.kt        zero-copy (array,start,end) reader
      PacketWriter.kt        shared growable writer
      Compression.kt         shared Inflater / Deflater(1) / 16 KB scratch
      PacketIds.kt           GENERATED packet-id table + version lookup
      Protocol.kt            version constants and field helpers
      BotSession.kt          state machine, keep-alive, chat, position, reconnect
      HostPolicy.kt          shared connect gate + rate-limit penalty
      ServerListPing.kt      SLP version detect + favicon
      Dns.kt                 DoH SRV lookup + plain fallback
      Auth.kt                offline UUID, Microsoft device-code chain, session join
      TokenStore.kt          interface implemented by the app (Keystore)
      ChatText.kt            JSON chat + NBT chat -> plain text, key translation
      LogRing.kt             bounded log/chat rings, password masking
    src/test/kotlin/...      unit tests + fake server + 5-bot harness
  app/                       Android app, depends on :engine
tools/gen_packet_ids.py      generator (minecraft-data -> PacketIds.kt)
tools/README.md              one-line update instructions
```

## Engine interfaces

```kotlin
object AfkEngine {
    const val MAX_BOTS = 5
    fun start()
    fun shutdown()
    fun createBot(config: BotConfig, listener: BotListener): BotHandle
    fun activeCount(): Int
    fun shrinkLogs()
    var tokenStore: TokenStore
    var authEvents: AuthEvents   // device-code callback to the UI
}

data class BotConfig(
    val id: Long, val host: String, val port: Int,
    val username: String, val onlineMode: Boolean,
    val viewDistance: Int = 2,
    val chatMode: ChatMode = ChatMode.ALL,
    val commands: List<String> = emptyList(),   // max 5
    val commandDelaySeconds: Int = 5,
    val loopCommand: Boolean = false,
    val autoJump: Boolean = true,
    val protocolOverride: Int = 0,
)

interface BotListener {
    fun onSnapshot(s: BotSnapshot)
    fun onLog(line: String)
    fun onChat(line: ChatLine)
    fun onAuthRequired(url: String, code: String, message: String)
}
```

The app adapter turns listener callbacks into `StateFlow` and keeps the existing
`ServerSession` public API, so the Compose screens stay almost unchanged.

## Concurrency model

- One daemon thread `afk-engine`, one `Selector`, one
  `ConcurrentLinkedQueue<Command>` inbox, one `PriorityQueue<Timer>`.
- Every timer carries `(bot, epoch)`. Cancelling a bot's timers increments its
  epoch; stale timers are skipped when popped. No per-bot scheduler.
- One single-thread `ThreadPoolExecutor` `afk-blocking` for DNS/SRV, TCP connect
  and auth HTTP. It never touches the selector or protocol state directly; it
  posts results back to the engine inbox.
- UI threads only submit commands and read `StateFlow` snapshots.
- Channel registration always happens on the engine thread (blocking thread
  posts a `Register` command and wakes the selector).

## Frame decoder

Per connection: `SocketChannel` (TCP_NODELAY, SO_KEEPALIVE), `byte[4096]` read
buffer, `byte[1024]` write buffer, two AES/CFB8 ciphers.

Read path:
1. Read bytes; if encryption is active, decrypt the newly read slice in place
   (AES/CFB8 is a stream cipher, so decrypting on arrival is safe).
2. Parse frame length VarInt. Length > 256 KB: skip that many bytes without
   buffering (zero allocation). Negative/oversized VarInt: disconnect.
3. Compression: read data-length VarInt; `0` means the rest is raw; otherwise
   inflate into the shared 16 KB scratch (grow up to 4 MB cap). Inflated length
   mismatch or > 4 MB: disconnect.
4. Read packet id VarInt; 0..255 else disconnect.
5. `allowlist[state][id]` false: advance cursor, zero allocation.
6. true: dispatch a zero-copy `PacketReader(buf, start, end)`.
7. At most 16 frames per selector event; then return to `select()`.
8. Compact after processing; if the buffer grew past 32 KB and is drained,
   reset to 4096. Write buffer resets to 1024 after a >32 KB burst.

Note: the brief lists "length -> decompress -> decrypt". The protocol encrypts
the whole stream including the length prefix, so the correct order is
decrypt -> length -> decompress -> packet id. This is an intentional correction.

## Packet allowlist

Per state, a `boolean[256]` built once per protocol version. Play state accepts
only: keep-alive, join game, disconnect, player position (combined and, where
present, separate position/look), update health, respawn, death combat,
chat/system/disguised/profileless chat, ping, resource pack, cookie request,
transfer, start-configuration. Config state accepts: finish, known packs,
keep-alive, ping, cookie, resource pack, disconnect. Login accepts: disconnect,
encryption request, success, compression, plugin request, cookie request.
Everything else is dropped at the frame layer.

Chat mode filters at ingestion: `HIDDEN` does not put chat ids in the allowlist
at all; `COMMANDS_ONLY` only allows system chat; `MESSAGES_ONLY` only allows
player/disguised/profileless chat; `ALL` allows both.

## Packet IDs

`tools/gen_packet_ids.py` reads PrismarineJS `minecraft-data` (`dataPaths.json`,
`protocolVersions.json`, `data/pc/<version>/protocol.json`) and emits
`PacketIds.kt` for every release-like version from 1.8 (protocol 47) to the
latest release in the data set. Only the needed logical packets are emitted.
The generated table is committed; the generator and one README line are kept for
future Minecraft updates. No hand-written per-version IDs.

The generator handles name aliases across versions: `success`/`login_success`,
`compress`/`set_compression`, `login`/`join_game`, `kick_disconnect`/`disconnect`,
`chat`/`chat_message`, `resource_pack_send`/`add_resource_pack`,
`profileless_chat`/`disguised_chat`, `settings`/`client_information`.

## Authentication

- Offline: UUID = `UUID.nameUUIDFromBytes("OfflinePlayer:<name>")`.
- Microsoft: device-code flow (client id `00000000441cc96b`, live.com) ->
  `user.auth.xboxlive.com` -> `xsts.auth.xboxlive.com` -> `login_with_xbox` ->
  `/minecraft/profile` -> `sessionserver.mojang.com/session/minecraft/join` on
  Encryption Request. RSA-1024 keypair, AES/CFB8 shared secret.
- `HttpURLConnection` only, 1 MB response cap, 15 s connect / 30 s read.
- Tokens stored by the app in Android Keystore AES-GCM, in
  `noBackupFilesDir`; never in `toString()` or logs. `allowBackup=false`.
- Migration: try to import `files/ms_token.json`; otherwise log a one-time
  "sign in again" message. The existing device-code dialog is kept.
- Auth expiry phrases force a refresh on the next attempt.

## Version detection and DNS

Server List Ping before login: handshake (protocol -1) + status request, parse
`version.protocol` and `version.name`, cache per host:port for the session. Use
`version.protocol` directly when it maps to a table; otherwise map the name;
otherwise fail with a readable "unsupported protocol" message. Manual override
is supported in `BotConfig` for tests (the current UI has no field, so no UI
change).

SRV: DoH `https://cloudflare-dns.com/dns-query?name=_minecraft._tcp.<host>&type=SRV`
with `Accept: application/dns-json`; choose lowest priority, strip trailing dot,
fall back to a plain connect on any failure. Handshake still uses the original
host/port.

## Behaviour kept / adopted

Kept from Java AFK: server cards/dashboard, chat tab, per-server on-join
commands (max 5, 1 s after every join/reconnect), looping command with delay,
per-server chat visibility, health/hunger log every 30 s with damage log
rate-limited to 5 s, reconnect backoff with 20% jitter and 60 s stable reset,
ban detection.

Adopted from EasyAFK: per-host 6 s connect gate, rate-limit phrase detection
with 20 s doubling to 300 s penalty that decays after 10 min, auth-expired
phrases, 3 identical config-stage kicks -> stop with "check version", 15 s
handshake / 40 s login timeouts, readable disconnect reasons, anti-AFK jump
(y+0.42 then back after 250 ms, random 75–180 s, default on), password masking,
foreground service auto-stop 4 s after the last bot, wake+wifi lock only while
active, notification throttled to one update per 2 s and only on text change,
"Stop all" action, `onTrimMemory` log shrink, per-bot bytes in/out, hide mode
filtered at ingestion.

Dropped: Node, mineflayer, JNI/C++, localhost bridge/polling, inventory/GUI,
entity/block tracking, physics, "items mined"/"Window closed" logs, proxies,
licensing, resource-pack cache, low-RAM toggle, packet tracing, compat toggles,
EasyAFK UI.

## UI changes (minimal)

- Inventory bottom sheet and its models removed (feature dropped).
- Chat visibility chips gain "Messages only"; existing "Enabled/Commands only/
  Hidden" stay.
- Server settings gain "Auto-jump" (default on) and "Repeat command" (default
  off) switches. Command field accepts up to 5 lines.
- Data-used card shows the total plus a small `↓in ↑out` line.
- No other visual redesign.

## Risks and mitigations

- **Unsigned chat** is rejected by secure-profile servers. Accepted limit.
- **1.21.2+ position packet field order** differs; parse by protocol threshold.
- **1.20.5+ chat/disconnect are NBT**, not JSON; a small NBT text reader handles
  both. Older versions use JSON.
- **Resource packs**: decline (no download/cache); forced packs may kick. Log it.
- **Auth without real credentials** cannot be end-to-end tested; the chain is
  unit-tested against a local mock HTTP server.
- **No adb device**: on-device measurement is a manual 5-line checklist.
- **Bandwidth**: view distance 2 still receives chunks; they are discarded at
  the frame layer. Chunk Batch Received (1.20.2+) is evaluated only if it does
  not cause kicks; adopt only with evidence.

## Acceptance

`./gradlew :engine:test assembleRelease` passes; release APK has no `lib/`, is
< 8 MB, version 3.0 (code 11); JVM harness shows 5 bots on 2 threads with
< 150 KB/bot heap delta; real-server smoke/soak passes; reviewer approves.
