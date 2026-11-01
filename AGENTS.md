# AGENTS.md

Guidance for working in this repository.

## Layout

```
android/                 Gradle project (Gradle 9.7.1, AGP 9.4.0, Kotlin 2.4.20)
  engine/                pure-JVM Minecraft protocol client (:engine)
  app/                   Compose UI + Room + foreground service (:app)
tools/gen_packet_ids.py  regenerates engine PacketIds.kt from minecraft-data
docs/                    design + phase plan for the 3.0 rewrite
dist/                    built APKs (git-ignored)
```

`android/engine` must never import `android.*`. It is a plain JVM module and
its tests run on the desktop JVM (`./gradlew :engine:test`).

## Engine rules

- One daemon thread (`afk-engine`) owns one `Selector`, one command queue and
  one timer heap for **all** bots. Never block that thread.
- DNS, socket connect and HTTP auth go through the single bounded blocking
  executor.
- Per bot: non-blocking `SocketChannel`, TCP_NODELAY + SO_KEEPALIVE, 4 KiB
  read / 1 KiB write buffers that grow and shrink, two AES/CFB8 ciphers, log
  ring capped at 100 lines.
- Shared per engine: writer scratch buffer, `Inflater`, `Deflater(level 1)`,
  16 KiB scratch array.
- Frame decoder: 256 KiB frame cap, 4 MiB inflated cap, 16 reads per selector
  event, per-state `boolean[256]` allowlist.
- Packet IDs are data, not code: edit `tools/gen_packet_ids.py`, regenerate
  `PacketIds.kt`, and commit the result. Never hand-edit `PacketIds.kt`.
- Chat is intentionally sent unsigned (signing fields zeroed). Do not try to
  implement Mojang chat signing here.

## App rules

- `EngineHost` owns the single `AfkEngine`; `ServerSession` is a thin
  `StateFlow` adapter. No polling loops, no local HTTP bridge.
- Tokens live in `KeystoreTokenStore` (AES-GCM, Android Keystore). Never log or
  persist them in plaintext. `allowBackup` stays `false`.
- Keep the existing visual design; new settings belong in the server settings
  sheet and must be persisted through `AfkDb` migrations.

## Build

```sh
export JAVA_HOME=/path/to/jdk-21
export ANDROID_HOME=/path/to/android-sdk
cd android
./gradlew :engine:test
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease   # requires android/keystore.properties
```

- `compileSdk 37`, `buildToolsVersion "37.0.0"` is pinned on purpose.
- Release signing reads `android/keystore.properties`; without it the release
  build falls back to the debug key. The 3.0 key is different from 2.4, so a
  signature-mismatch install needs `adb uninstall dev.mstheesha.afk.java`.
- Repositories point at the Tencent mirror first because `dl.google.com` and
  `plugins.gradle.org` are unreachable from the build machine; official
  repositories remain as fallbacks.

## Version support

`PacketIds.kt` covers releases 1.8 through 26.1 (46 protocols). 26.2 / 26.3
have no upstream packet data yet; adding them is a generator change plus a
regenerated table.
