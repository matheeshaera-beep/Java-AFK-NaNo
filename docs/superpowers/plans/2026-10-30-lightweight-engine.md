# Plan — Java AFK 3.0 lightweight engine

Design: `docs/superpowers/specs/2026-10-30-lightweight-engine-design.md`
Branch: `lightweight-engine` (tag `pre-lightweight` = `93d6df8`)

Each phase ends in a commit. Build/test after every phase. No interactive
approval gates; rulings are recorded here.

## Phase 0 — toolchain

- [x] JDK 21 at `E:\AndroidDev\jdk\jdk-21.0.12.1+1`
- [x] cmdline-tools 13114758 extracted to `E:\AndroidDev\sdk\cmdline-tools\latest`
- [x] platform-tools, `platforms;android-37`, `build-tools;37.0.0`
- [x] Gradle 9.7.1 distribution pre-seeded in wrapper cache
- [ ] `gradlew :app:assembleDebug` on untouched tree (baseline)

Ruling: keep the repo's declared stack (Gradle 9.7.1 / AGP 9.4.0 / Kotlin
2.4.20). The machine had only Gradle 9.1.0 + AGP 9.0.1 cached; network access to
services.gradle.org and dl.google.com works, so the declared versions are
fetched instead of downgrading `compileSdk`.

Ruling: `dl.google.com`, `maven.google.com` and `plugins.gradle.org` are NOT
reachable from this machine (TCP timeouts), while the Tencent public mirror
(`https://mirrors.cloud.tencent.com/nexus/repository/maven-public/`) proxies
all three. The mirror is listed first in `pluginManagement` and
`dependencyResolutionManagement`, with the official repositories kept as
fallbacks. Cost if wrong: on a network where the mirror is blocked but Google
is not, resolution falls through to `google()`/`mavenCentral()` anyway.

Ruling: no JDK 17 is installed, so `:engine` compiles with the JDK 21 toolchain
and emits Java 17 bytecode (`jvmTarget = JVM_17` + `sourceCompatibility`).
Cost if wrong: the module cannot be built on a JDK 17-only machine until a
`jvmToolchain(17)` is restored there.

## Phase 1 — packet id generator

- [x] `tools/gen_packet_ids.py`: read minecraft-data `dataPaths.json`,
      `protocolVersions.json`, `data/pc/<v>/protocol.json`
- [x] emit `android/engine/src/main/kotlin/.../PacketIds.kt` (version ->
      logical name -> id) for every release 1.8..latest, aliases resolved
- [x] `tools/README.md` one-liner
- [x] verify a sample of ids by hand against protocol snapshots

Acceptance: `PacketIds.table` covers 47, 340, 754, 759, 762, 766, 767, 769,
773, 774, 775+ and every id resolves in the matching protocol JSON.

Ruling: the generated table covers 46 protocols (1.8 – 26.1). 26.2/26.3 are
absent because PrismarineJS `minecraft-data` has no packet data for them yet;
the generator will pick them up when upstream adds them.

## Phase 2 — engine core (pure JVM)

- [x] `Engine.kt`, `EngineConfig.kt`, `LogRing.kt`
- [x] `PacketReader.kt` / `PacketWriter.kt` / `Compression.kt`
- [x] `Connection.kt` frame decoder + allowlist
- [x] `BotSession.kt` state machine: login, config, play, keep-alive, position,
      respawn, chat, commands, jump, reconnect/backoff, ban + rate-limit detect
- [x] `HostPolicy.kt`, `ServerListPing.kt`, `Dns.kt`, `ChatText.kt`, `Auth.kt`
- [x] unit tests: varint, frame decoder (raw + compressed + encrypted), NBT/JSON
      chat, offline UUID, SLP parse, SRV parse, auth chain against mock server
- [x] fake server test: full 1.20.6 join, keep-alive, chat, kick, reconnect

Acceptance: `:engine:test` green; no `android.*` in `:engine`. Result: 21 tests,
0 failures (ChatText 6, Compression 3, FakeServer 1, PacketIo 5, PacketTable 6).

## Phase 3 — app integration

- [x] `settings.gradle.kts` include `:engine`; app depends on it
- [x] `ServerSession.kt` -> engine adapter, same public API, StateFlow
- [x] `AfkService.kt` locks/notification/auto-stop rewired
- [x] `AfkDb.kt` migration for autoJump/loopCommand + chat mode MESSAGES_ONLY
- [x] UI: remove inventory sheet, add chips/switches, bytes line
- [x] `AndroidManifest` `allowBackup=false`, Application for `onTrimMemory`
- [x] `KeystoreTokenStore` + migration from `ms_token.json`

Acceptance: `:app:assembleDebug` green, UI diff limited to the agreed list.

## Phase 4 — real-server verification

- [x] on-device smoke test against a local 1.20.6 server (adb reverse): ping
      auto-detect -> 766, login, config, play, system chat received, chat sent
      (0x06, 35 B unsigned payload), keep-alive echo, kick -> reconnect
- [ ] join/survive/chat/command/reconnect on as many of 1.8.9, 1.12.2, 1.16.5,
      1.19.4, 1.20.4, 1.20.6, latest as reachable
- [ ] 5-bot JVM harness: heap/bot, thread count, bytes/hour

Acceptance: harness numbers recorded in this file; failures documented.

## Phase 5 — cleanup

- [x] `git rm` nodejs-project, cpp, jniLibs, bridge code, unused deps/resources
- [x] R8 minify + shrink, `stripReleaseNativeLibs` task deleted
- [x] README + AGENTS.md rewritten for the new architecture
- [ ] `./gradlew clean :engine:test assembleRelease` from a fresh clone

Acceptance: release APK has no `lib/`, < 8 MB, no unused deps/resources.

## Phase 6 — release

- [x] versionName 3.0, versionCode 11, **new** release key (2.4 used the debug key)
- [x] `apksigner verify` + `aapt dump badging`
- [x] copy to `dist/JavaAFK-3.0.apk`
- [x] report: size before/after, heap, threads, bytes/hour, removed UI, deleted
      groups, limits, install steps

## Measurements

| metric | 2.4 (Node) | 3.0 (engine) |
|---|---|---|
| release APK | 64.9 MB | 2.44 MB |
| process threads (1 bot, UI open) | not measured | 37 |
| total PSS (1 bot, UI open) | not measured | 81–91 MB |
| CPU (1 bot, UI open) | not measured | 3.3–4.1% |
| Java heap (1 bot) | not measured | 12 MB |
| bytes/hour/bot | not measured | 12 KB on a keep-alive-only local server |

