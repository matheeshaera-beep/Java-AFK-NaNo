<div align="center">

# Java AFK NaNo 
**Lightweight, Pure Kotlin Android Minecraft AFK Bot**

[![Platform](https://img.shields.io/badge/Platform-Android-green.svg)]()
[![Language](https://img.shields.io/badge/Language-Kotlin-purple.svg)]()
[![Size](https://img.shields.io/badge/APK-~15MB-blue.svg)]()

</div>

## Overview
Java AFK NaNo is the lightweight, battery-optimized edition of Java AFK. Stripped of all embedded runtimes and native JNI dependencies, it offers a blazing-fast, pure Kotlin implementation for users who need reliable AFK capabilities without the overhead of heavy background engines.

## Core Capabilities
- **Zero Native Dependencies:** No embedded Node.js, V8, or Go binaries.
- **Pure Kotlin Stack:** Modern, idiomatic Android development using Coroutines.
- **Ultra-Low Overhead:** Minimal RAM footprint and optimized for extended background execution.

## Ecosystem Comparison
NaNo is designed as a lightweight alternative to the core Java AFK application.

| Feature | Java AFK  | Java AFK NaNo |
| :--- | :--- | :--- |
| **Primary Language** | C / C++ (NDK) | Kotlin |
| **Bot Engine** | Node.js (Mineflayer) + Go | Pure Kotlin (MCProtocolLib) |
| **Runtime Dependencies** | Embedded Node, V8, Go | None (Native Android) |
| **APK Footprint** | Large (~150MB+) | Small (~15MB) |
| **Battery Efficiency** | Moderate | High |
| **Target Audience** | Advanced users needing Mineflayer/Go features | Users prioritizing battery life and minimal footprint |

## Interface

<div align="center">
  <img src="./assets/screenshots/nano-ui.png" alt="Nano UI" width="30%" hspace="10"/>
  <img src="./assets/screenshots/nano-usage.png" alt="Resource Usage" width="30%" hspace="10"/>
  <img src="./assets/screenshots/nano-settings.png" alt="Settings" width="30%" />
</div>

## Build Instructions
1. Clone the repository.
2. Open in Android Studio (Latest stable release).
3. Sync Gradle dependencies.
4. Build and run directly on your device. No NDK or external toolchains required.
   
