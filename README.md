# Java AFK NaNo v3.0

Java AFK NaNo is a lightweight Android edition of [Java AFK](https://github.com/matheeshaera-beep/Java-AFK). It keeps Minecraft Java Edition accounts online while using a much smaller, native Kotlin protocol engine instead of an embedded Node.js runtime. The v3.0 release APK is about 2.5 MB.

The app is built in Kotlin for Android. Its protocol engine is pure Kotlin/JVM: it uses no Node.js, V8, JNI, or native libraries. It detects the server's Minecraft Java protocol version automatically and supports Minecraft 1.8 through 26.1. Microsoft device-code sign-in stores tokens in Android Keystore; offline usernames are also supported.

## How this differs from Java AFK v2.4

| Java AFK v2.4 has; NaNo v3.0 does not | Java AFK NaNo v3.0 has; Java AFK v2.4 does not |
| --- | --- |
| A Bedrock Edition engine and a headless Go command-line client | A pure Kotlin/JVM Minecraft Java protocol engine, with no Node.js, V8, JNI, or native libraries |
| The Java Edition Mineflayer bot running on an embedded Node.js runtime | Automatic Java protocol-version detection, manual version override, and support for versions 1.8-26.1 |
| Up to 5 simultaneous bots | Up to 6 simultaneous bots |
| A first-run copy of the embedded bot runtime and protocol data (about 120 MB) | An APK around 2.5 MB and about 3 MB of app storage |
| Support limited to the shipped 64-bit Android ABIs | Builds for Android ABIs without shipping native engine libraries |
| A saved Microsoft token (the v2.4 README does not specify Android Keystore storage) | Microsoft tokens stored in Android Keystore, plus optional anti-AFK auto-jump and repeating commands |
| Device-keystore-backed Microsoft token storage | Microsoft tokens stored in Android Keystore |

Both editions include a server list, Microsoft and offline sign-in, per-server logs and chat, reconnect handling, foreground service support, and health/hunger monitoring. See the [original Java AFK project](https://github.com/matheeshaera-beep/Java-AFK) for the full edition.

## Download

Download and install the [Java AFK NaNo v3.0 APK](https://github.com/matheeshaera-beep/Java-AFK-NaNo/releases/download/v3.0/JavaAFK-NaNo-3.0.apk). Android 15 or newer is required. Version 3.0 uses a new release signing key, so uninstall the older Java AFK app before installing this APK.

## Build from source

Requirements: JDK 21 and the Android SDK (compile SDK 37, build tools 37.0.0).

```sh
cd android
./gradlew :engine:test
./gradlew :app:assembleDebug
./gradlew :app:assembleRelease
```

The release build requires the release signing configuration. Do not commit signing keys or `android/keystore.properties`.

## License

Personal project â€” all rights reserved unless stated otherwise.