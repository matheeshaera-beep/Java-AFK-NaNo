# Java AFK NaNo v4.0 Evolution: Product & Technical Proposal

> **Project:** Java AFK NaNo  
> **Platform:** Android (Kotlin JVM)  
> **Date:** November 2026  
> **Status:** Proposal & Architectural Blueprint  

---

## 1. Executive Summary & Vision

**Java AFK NaNo** is an ultra-lightweight, battery-efficient headless Minecraft Java Edition client designed specifically for mobile Android devices. It connects directly to Minecraft servers (supporting protocol versions 1.8 through 1.21.4+), authenticates, handles network protocol keep-alives, and maintains long-running AFK sessions using a foreground Android service.

The defining identity of the app is the **"NaNo" standard**:
- **Sub-3 MB APK Size:** Pure Kotlin/JVM with zero WebView, Chromium, or JNI rendering baggage.
- **Near-Zero Battery Impact:** Consumes <0.5% battery over an 8-hour overnight AFK session.
- **Pure Headless Protocol:** Receives only network frames; never renders expensive 3D voxels.

The v4.0 upgrade aims to elevate Java AFK NaNo from a simple connection tool into an **intelligent, self-preserving Minecraft companion** with visual world cues, anti-detection behavior, and survival safeguards.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    Java AFK NaNo v4.0 Architecture                          │
│                                                                             │
│   [Server List Ping]  ───►  Live Ping (24ms) + Online Players + MOTD Banner │
│   [Health/Food Pkts]  ───►  Visual Hearts HUD & Auto-Eat Trigger            │
│   [Position & Yaw]    ───►  Natural Head Swivel (±15°) + XYZ Coordinates    │
│   [Chat Stream]       ───►  Colorized Chat (§) + Macro Chips + Mentions     │
│   [Foreground Service]───►  60s Reboot Delay + Android Notification Actions │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. What Needs to Be Redesigned (UX/UI Flaws & Fixes)

### A. Server Cards (Home Screen)
- **Current Issue:** Cards are static. You cannot tell if a server is online or what its latency is without entering the session screen.
- **v4.0 Redesign:**
  1. **Live Ping Pill:** Displays TCP latency (e.g., `24ms` green / `110ms` amber / `320ms` red) and player count (`18/50`).
  2. **1-Tap Power Toggle:** Instant Connect/Disconnect power button right on the card.
  3. **MOTD Strip:** Single-line server announcement preview.
  4. **Pulsing Alive Indicator:** Retains smooth 2s breathing glow ring for online bots.

### B. Session Screen Layout
- **Current Issue:** Top status card occupies 40% of the screen height, squeezing logs/chat into a tiny scrollable area.
- **v4.0 Redesign:**
  1. **Minecraft Hearts HUD:** 10 authentic pixel-perfect red hearts & 10 food drumsticks rendered in Jetpack Compose.
  2. **Compact Metrics:** 3-column chip row for Coordinates, AFK Time, and Data Usage.
  3. **Expanded Chat Space:** 75% of screen allocated to streaming chat and logs.

### C. Quick Macro Chips Bar
- **Current Issue:** Executing frequent commands (`/afk`, `/spawn`, `/home`) requires opening the soft keyboard and typing manually every time.
- **v4.0 Redesign:** Horizontally scrolling chips directly above the input box for 1-tap command execution.

### D. Segmented Settings Sheet
- **Current Issue:** Monolithic scroll dialog with raw text inputs.
- **v4.0 Redesign:** Clean Material 3 Modal Bottom Sheet organized into 3 clear tabs: *Connection*, *AFK Automation*, and *Survival & Safety*.

---

## 3. Comprehensive 16-Feature Proposal Matrix

### 🛡️ Pillar 1: Pro AFK Behaviors & Survival Guard
1. **Natural Camera Swivel (Yaw/Pitch Oscillation)** *(Effort: Low | Impact: Huge)*:
   - Gently sweeps camera by ±5° to ±18° with randomized intervals (every 12–28s). Fools anti-cheat plugins looking for static angles. Cost: 1 packet (12 bytes) every 20s.
2. **Auto-Eat & Starvation Guard** *(Effort: Medium | Impact: Huge)*:
   - Reads `food = r.readVarInt()`. When hunger < 14, automatically sends hotbar right-click interaction to eat food.
3. **Sneak / Un-sneak Micro-Pulse** *(Effort: Low | Impact: High)*:
   - Transmits `ENTITY_ACTION` packet (sneak for 400ms, then stand) periodically to visually indicate active presence to players.
4. **Emergency Disconnect on Damage** *(Effort: Low | Impact: Critical)*:
   - Cleanly disconnects if health drops below configurable threshold (e.g. < 4 hearts) to save equipment.

### 🎮 Pillar 2: Visual HUD & Chat Superpowers
5. **Authentic Minecraft Hearts & Food Drumsticks HUD** *(Effort: Low | Impact: Huge)*:
   - Displays 10 red hearts and 10 food drumsticks in Compose updating live with `PLAY_S2C_UPDATE_HEALTH`.
6. **Quick Command Macro Chips Bar** *(Effort: Low | Impact: High)*:
   - Horizontally scrolling chips (`/afk`, `/spawn`, `/home`, `/repair`, `/heal`, `/tpa yes`).
7. **Minecraft Color-Coded Chat (§ Formatting)** *(Effort: Medium | Impact: High)*:
   - Converts standard Minecraft section formatting codes into Compose `AnnotatedString`.
8. **Live XYZ Coordinates & Cardinal Compass** *(Effort: Low | Impact: Medium)*:
   - Displays coordinates (`124, 68, -312`) and cardinal orientation (North/South/East/West).

### 🔔 Pillar 3: Background Reliability, Reconnects & Alerts
9. **Intelligent Server Restart Grace Period** *(Effort: Low | Impact: High)*:
   - 45–60s grace delay on reboot kicks to prevent rate limit firewall bans.
10. **Chat Mention & Direct Message Push Notifications** *(Effort: Medium | Impact: High)*:
    - Vibrate phone and post heads-up notification when your username is mentioned or whispered.
11. **Notification Action Buttons ([Stop All], [Reconnect])** *(Effort: Low | Impact: High)*:
    - Interactive controls attached directly to the ongoing Android foreground notification.
12. **Night AFK Scheduled Window** *(Effort: Medium | Impact: Medium)*:
    - Automatic connection windows (e.g. 11:30 PM to 6:30 AM).

### 🧰 Pillar 4: Multi-Bot Management & Presets
13. **JSON & QR Code Server Config Backup/Share** *(Effort: Low | Impact: High)*:
    - Export and import configs via JSON or QR code.
14. **1-Tap Popular Server Presets** *(Effort: Low | Impact: Medium)*:
    - Presets for Hypixel, 2b2t, DonutSMP, and Aternos.
15. **Batch Master Actions (Start All / Stop All)** *(Effort: Low | Impact: Medium)*:
    - Single tap master controls in the drawer or top bar.
16. **Per-Server Custom Accent Color & Tags** *(Effort: Low | Impact: Low)*:
    - Tag servers with custom badges.

---

## 4. Technical Feasibility & Engine Architecture

| Feature | Primary File Affected | Mechanism | Overhead |
| :--- | :--- | :--- | :--- |
| **Natural Head Swivel** | `BotSession.kt` | `scheduleSwivel()` timer sending `sendLook()` | 12 bytes / 20s (0% CPU) |
| **Auto-Eat** | `BotSession.kt` | Check `food < 14` in `PLAY_S2C_UPDATE_HEALTH` | Event-driven (0 polling) |
| **Minecraft Hearts HUD** | `MainActivity.kt` | Compose `Row` mapping `health` (0..20) | Pure UI, 0 KB assets |
| **Quick Macro Bar** | `MainActivity.kt` | `LazyRow` calling `session.sendChat()` | Minimal Compose state |
| **Live Ping on Card** | `ServerPinger.kt` | TCP handshake + status JSON parse | One-shot async coroutine |
| **Emergency Disconnect** | `BotSession.kt` | Trigger `stop()` if `health < minThreshold` | 3 lines of Kotlin |
| **Database Migration** | `AfkDb.kt` | Room Migration 7 → 8 adding default columns | 100% backward compatible |

---

## 5. Recommended Implementation Roadmap

- **Phase 1 (Quick Wins):** Natural Camera Swivel, Minecraft Hearts & Food HUD, Quick Command Macro Chips, Emergency Health Disconnect.
- **Phase 2 (Card & Connection Polish):** Live Ping & Player Count Pills on Cards, 1-Tap Power Toggle on Cards, Notification Action Buttons, Intelligent 60s Server Reboot Delay.
- **Phase 3 (Survival & Utility):** Auto-Eat Interaction, Color-Coded Minecraft Chat, JSON Server Export/Import.
