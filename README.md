# Java AFK NaNo

A lightweight, pure Kotlin Android edition of Java AFK. Engineered without embedded Node.js, V8, or native JNI dependencies, NaNo provides a highly efficient and battery-friendly AFK experience for Android devices.

## Core Features

* **Zero Native Dependencies:** Stripped of heavy embedded runtimes (Node.js/V8) and JNI bridges.
* **Pure Kotlin Implementation:** Built entirely using modern Kotlin, ensuring easier debugging and maintenance.
* **Minimal Footprint:** Significantly reduced APK size and lower RAM consumption.
* **Efficient Protocol Handling:** Utilizes native Java Edition protocol implementations.

## Architecture Comparison

This project includes two distinct editions. Choose the version that best fits your hardware capabilities.

| Feature |    Java AFK | Java AFK NaNo |
| :--- | :--- | :--- |
| **Core Language** | C / C++ (NDK) | Kotlin |
| **Bot Engines** | Node.js (Mineflayer), Go (Gophertunnel) | Pure Kotlin implementation |
| **Dependencies** | Embedded Node.js, V8, JNI | Zero native dependencies |
| **APK Footprint** | Large (includes bundled runtimes) | Small / Minimal |
| **Best For** | Advanced scripting, cross-version support | Low-end devices, battery efficiency |

## Screenshots

<div align="center">
  <img src="./assets/screenshots/nano-ui.png" alt="Nano UI" width="32%" />
  <img src="./assets/screenshots/nano-usage.png" alt="Lightweight Usage" width="32%" />
  <img src="./assets/screenshots/nano-settings.png" alt="Settings" width="32%" />
</div>

## Build Instructions

### Prerequisites
* Android Studio (Latest stable release)
* Kotlin 1.9+

### Compilation
1. Clone the repository.
2. Open the project in Android Studio.
3. Sync Gradle dependencies.
4. Build and run directly on your device or emulator.
