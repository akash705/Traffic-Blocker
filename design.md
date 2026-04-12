# AppTrafficBlocker — Technical Design Document

**Platform:** Android (Samsung One UI)
**Language:** Kotlin
**Min SDK:** 26 (Android 8.0)
**Target SDK:** 35 (Android 15)
**Version:** 1.2.0
**Status:** Draft

---

## Table of Contents

1. [Overview](#1-overview)
2. [Goals & Non-Goals](#2-goals--non-goals)
3. [Architecture](#3-architecture)
4. [Component Design](#4-component-design)
5. [Data Flow](#5-data-flow)
6. [UI Screens](#6-ui-screens)
7. [API & Service Design](#7-api--service-design)
8. [Permissions](#8-permissions)
9. [Samsung One UI Considerations](#9-samsung-one-ui-considerations)
10. [Edge Cases & Limitations](#10-edge-cases--limitations)
11. [Open Questions](#11-open-questions)

---

## 1. Overview

AppTrafficBlocker is a personal-use Android app that blocks internet traffic for a user-selected app whenever that app is in the foreground. It achieves this without root by using Android's `VpnService` API with `VpnService.Builder.addAllowedApplication()` to scope a drop-only tunnel exclusively to the target app.

**Background traffic** is handled separately by the user via Samsung's native per-app data restriction (Settings → Apps → [App] → Data usage → Allow background data). This app only concerns itself with foreground traffic, which keeps the implementation simple: the tunnel exists solely to drop packets, with no forwarding or relay required.

### Core User Story

> As a user, I want to select an app (e.g. Instagram) and ensure it cannot make any network requests while I am actively using it — so I can use it offline or prevent background calls — without needing to toggle airplane mode manually.

---

## 2. Goals & Non-Goals

### Goals
- Block all network traffic (Wi-Fi + mobile data) for a specific app while it is in the foreground
- Work without root on Samsung One UI devices
- Run as a persistent background service with minimal battery impact
- Provide a simple UI to select target apps and toggle blocking on/off
- Survive Samsung's aggressive battery optimisation (Doze, App Standby)

### Non-Goals
- Deep packet inspection or traffic logging
- Blocking background traffic — handled by Samsung's native per-app data restriction
- SSL decryption / MITM
- Packet forwarding / relay (tunnel is drop-only)
- Supporting multiple simultaneous blocked apps (v1)
- Being a general-purpose firewall

---

## 3. Architecture

```
┌─────────────────────────────────────────────────────┐
│                    USER INTERFACE                    │
│         MainActivity  ·  AppPickerScreen             │
│         StatusWidget  ·  SettingsScreen              │
└────────────────────────┬────────────────────────────┘
                         │ starts / binds
┌────────────────────────▼────────────────────────────┐
│              BlockerService (Foreground)             │
│                                                      │
│   ┌──────────────────┐   ┌────────────────────────┐ │
│   │  VpnService      │   │  AppWatchdog           │ │
│   │  (PacketTunnel)  │◄──│  (UsageStatsManager)   │ │
│   │                  │   │                        │ │
│   │  addAllowed      │   │  polls foreground app  │ │
│   │  Application()   │   │  → open/close tunnel   │ │
│   │  drop-only       │   │                        │ │
│   │  no relay        │   │                        │ │
│   └──────────────────┘   └────────────────────────┘ │
└──────────────────────────┬──────────────────────────┘
                           │
┌──────────────────────────▼──────────────────────────┐
│                   Data Layer                         │
│   SharedPreferences (settings)                      │
│   Room DB (blocked app config, logs)                │
└─────────────────────────────────────────────────────┘
```

### Layer Summary

| Layer | Responsibility |
|---|---|
| UI Layer | User configuration, status display |
| BlockerService | Orchestrates VPN + watchdog lifecycle |
| PacketTunnel | Drop-only tun loop — reads and discards all packets; no forwarding, no UID inspection |
| AppWatchdog | Polls foreground app; triggers tunnel open/close in BlockerService |
| Data Layer | Persists user config and optional block logs |

### VPN Scoping — Key Design Decision

`VpnService.Builder.addAllowedApplication(targetPackage)` routes **only the target app's traffic** through the VPN tunnel at the OS/kernel level. All other apps continue to use the real network interface unaffected.

The tunnel is **drop-only** — every packet that arrives is discarded. No forwarding, no relay, no TCP state machine. Background traffic for the target app is separately restricted via Samsung's native per-app data setting, so there is nothing to forward when the app is not in the foreground.

- No packet-level UID inspection required
- No relay or protected-socket forwarding
- No risk of accidentally blocking unrelated apps
- `PacketTunnel` only ever sees packets from the target app — every packet is dropped
- Tunnel open = blocking active; tunnel closed = target app has no network access at all (background restricted by OS)

---

## 4. Component Design

### 4.1 BlockerService

A `Service` running as a foreground service. Owns both `PacketTunnel` and `AppWatchdog`. Acts as the coordinator.

```kotlin
class BlockerService : VpnService() {

    private lateinit var tunnel: PacketTunnel
    private lateinit var watchdog: AppWatchdog
    private var vpnInterface: ParcelFileDescriptor? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification())
        val targetPackage = intent?.getStringExtra(KEY_TARGET_PACKAGE) ?: return START_NOT_STICKY

        watchdog = AppWatchdog(this, targetPackage) { isTargetInForeground ->
            if (isTargetInForeground) openTunnel(targetPackage) else closeTunnel()
        }
        watchdog.start()
        return START_STICKY
    }

    private fun openTunnel(targetPackage: String) {
        if (vpnInterface != null) return          // already open
        vpnInterface = buildVpnInterface(targetPackage)
        tunnel = PacketTunnel(vpnInterface!!)
        tunnel.start()
        updateState(isBlocking = true)
    }

    private fun closeTunnel() {
        tunnel?.stop()
        vpnInterface?.close()
        vpnInterface = null
        updateState(isBlocking = false)
    }

    private fun buildVpnInterface(targetPackage: String): ParcelFileDescriptor {
        return Builder()
            .addAddress("10.0.0.2", 32)
            .addRoute("0.0.0.0", 0)
            .addDnsServer("8.8.8.8")
            .addDnsServer("1.1.1.1")
            .addAllowedApplication(targetPackage)   // ← only target app's traffic enters tunnel
            .setSession("AppTrafficBlocker")
            .setBlocking(false)
            .establish()!!
    }

    override fun onDestroy() {
        watchdog.stop()
        closeTunnel()
    }
}
```

---

### 4.2 PacketTunnel

Reads raw IP packets from the VPN tun interface and drops every one of them. Because `addAllowedApplication()` scopes the tunnel to the target app only, every packet here belongs to that app. The tunnel is only opened when the target app is in the foreground, so dropping is always the correct action — there is nothing to forward.

```kotlin
class PacketTunnel(
    private val vpnFd: ParcelFileDescriptor
) {
    private var job: Job? = null

    fun start() {
        job = CoroutineScope(Dispatchers.IO).launch {
            val inputStream = FileInputStream(vpnFd.fileDescriptor)
            val buffer = ByteArray(MAX_PACKET_SIZE)

            while (isActive) {
                val length = inputStream.read(buffer)
                if (length <= 0) continue
                // Drop — do nothing. Tunnel is only open when target app is in foreground.
            }
        }
    }

    fun stop() { job?.cancel() }

    companion object {
        const val MAX_PACKET_SIZE = 32767
    }
}
```

No `setBlocking()`, no `forwardPacket()`, no protected sockets. Open tunnel = blocking. Closed tunnel = no blocking.

---

### 4.3 AppWatchdog

Polls `UsageStatsManager` every second to detect foreground app changes. Emits a callback when the target app enters or leaves the foreground. The callback drives tunnel open/close in `BlockerService` — not a blocking flag inside the tunnel.

```kotlin
class AppWatchdog(
    private val context: Context,
    private val targetPackage: String,
    private val onStateChange: (Boolean) -> Unit  // true = target in foreground → open tunnel
) {
    private var job: Job? = null
    private var lastState = false

    fun start() {
        job = CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                val foreground = getForegroundPackage()
                val isTarget = foreground == targetPackage
                if (isTarget != lastState) {
                    lastState = isTarget
                    onStateChange(isTarget)
                }
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private fun getForegroundPackage(): String? {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE)
                as UsageStatsManager
        val now = System.currentTimeMillis()
        val stats = usm.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            now - 2000,
            now
        )
        return stats?.maxByOrNull { it.lastTimeUsed }?.packageName
    }

    fun stop() { job?.cancel() }

    companion object {
        const val POLL_INTERVAL_MS = 1000L
    }
}
```

---

### 4.4 Data Layer

**SharedPreferences** — lightweight config:

| Key | Type | Description |
|---|---|---|
| `pref_target_package` | String | Package name of the blocked app |
| `pref_service_enabled` | Boolean | Global on/off toggle |
| `pref_block_on_launch` | Boolean | Auto-start service on device boot |

**Room Database** — optional block event log:

```kotlin
@Entity(tableName = "block_events")
data class BlockEvent(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val packageName: String,
    val eventType: String,       // "BLOCKED" | "UNBLOCKED"
    val timestamp: Long
)
```

---

## 5. Data Flow

### 5.1 Service Start Flow

```
User taps "Enable" in UI
        │
        ▼
Check VPN permission granted?
   No → Launch VpnService.prepare() intent → user confirms
   Yes ↓
Check PACKAGE_USAGE_STATS granted?
   No → Open Special App Access settings
   Yes ↓
Start BlockerService via Intent (pass targetPackage)
        │
        ▼
BlockerService.onCreate()
   → Instantiate AppWatchdog for targetPackage
   → Start watchdog polling loop
        │
        ▼
Service running — persistent notification shown
Tunnel not yet open — waiting for target app to come to foreground
Other apps unaffected — their traffic never enters the tunnel
```

---

### 5.2 Packet Blocking Flow

```
AppWatchdog polls every 1s
        │
        ├── foreground == targetPackage?
        │         YES → BlockerService.openTunnel()
        │                    │
        │                    ▼
        │             establish() with addAllowedApplication()
        │             PacketTunnel.start() — drop loop running
        │             All target app packets → silently dropped
        │
        └── foreground != targetPackage?
                  YES → BlockerService.closeTunnel()
                             │
                             ▼
                       vpnInterface.close()
                       Target app has no foreground traffic
                       Background traffic already restricted
                       by Samsung per-app data setting
```

No UID lookup. No packet forwarding. Tunnel open = block. Tunnel closed = no foreground traffic (background handled by OS).

---

### 5.3 Boot Auto-Start Flow

```
Device boots
     │
     ▼
BootReceiver (BOOT_COMPLETED broadcast)
     │
     ▼
Read pref_block_on_launch from SharedPreferences
     │
     ├── true → start BlockerService
     └── false → do nothing
```

---

## 6. UI Screens

### 6.1 Home Screen (`MainActivity`)

```
┌──────────────────────────────────┐
│  AppTrafficBlocker          ⚙️   │
├──────────────────────────────────┤
│                                  │
│   Blocking                       │
│   ┌──────────────────────────┐   │
│   │  🔴  Instagram           │   │
│   │  com.instagram.android   │   │
│   │              [Change]    │   │
│   └──────────────────────────┘   │
│                                  │
│   Status                         │
│   ● Active — foreground detected │
│                                  │
│   ┌──────────────────────────┐   │
│   │   DISABLE BLOCKER        │   │  ← toggle button
│   └──────────────────────────┘   │
│                                  │
│   Last blocked: 2 mins ago       │
│                                  │
└──────────────────────────────────┘
```

**States:**
- `IDLE` — service off, no app selected
- `READY` — app selected, service off
- `ACTIVE_WATCHING` — service on, target app not in foreground
- `ACTIVE_BLOCKING` — service on, target app in foreground (red indicator)

---

### 6.2 App Picker Screen

```
┌──────────────────────────────────┐
│  ← Select App to Block           │
├──────────────────────────────────┤
│  🔍 Search apps...               │
├──────────────────────────────────┤
│  📷 Camera                       │
│  com.sec.android.app.camera      │
├──────────────────────────────────┤
│  📘 Facebook                     │
│  com.facebook.katana             │
├──────────────────────────────────┤
│  📸 Instagram          ✓ selected│
│  com.instagram.android           │
├──────────────────────────────────┤
│  🎵 Spotify                      │
│  com.spotify.music               │
└──────────────────────────────────┘
```

- Shows only **user-installed apps** (excludes system)
- Search filters by app name
- Tapping selects and returns to Home

---

### 6.3 Settings Screen

```
┌──────────────────────────────────┐
│  ← Settings                      │
├──────────────────────────────────┤
│  Auto-start on boot      [  ON ] │
│                                  │
│  Persistent notification  [  ON ]│
│                                  │
│  Show block log          [ OFF ] │
│                                  │
│  Poll interval                   │
│  ○ 500ms  ● 1s  ○ 2s            │
│                                  │
│  Permissions                     │
│  ✅ VPN                          │
│  ✅ Usage Stats                  │
│  ✅ Battery Optimization exempt  │
│                                  │
│  [  Request Missing Permissions ]│
└──────────────────────────────────┘
```

---

### 6.4 Persistent Notification

```
┌──────────────────────────────────┐
│ 🔴 AppTrafficBlocker             │
│ Blocking: Instagram              │
│ [Pause]            [Stop]        │
└──────────────────────────────────┘
```

Required to keep the foreground service alive on Samsung One UI.

---

## 7. API & Service Design

### 7.1 BlockerService Intent API

All communication with `BlockerService` is via `Intent` extras.

| Action | Extra Keys | Description |
|---|---|---|
| `ACTION_START` | `KEY_TARGET_PACKAGE: String` | Start VPN + watchdog for target app |
| `ACTION_STOP` | — | Stop service, tear down VPN |
| `ACTION_PAUSE` | — | Stop watchdog; close tunnel; suspend blocking temporarily |
| `ACTION_RESUME` | — | Restart watchdog; re-enable blocking |

```kotlin
// Start
Intent(context, BlockerService::class.java).apply {
    action = BlockerService.ACTION_START
    putExtra(BlockerService.KEY_TARGET_PACKAGE, "com.instagram.android")
}.also { startForegroundService(it) }

// Stop
Intent(context, BlockerService::class.java).apply {
    action = BlockerService.ACTION_STOP
}.also { startService(it) }
```

---

### 7.2 Service → UI Communication

Use a `StateFlow` via bound service to push status updates to the UI.

```kotlin
data class BlockerState(
    val isRunning: Boolean,
    val isBlocking: Boolean,
    val targetPackage: String?,
    val lastBlockedAt: Long?
)

// In BlockerService — expose via StateFlow
private val _state = MutableStateFlow(BlockerState(...))
val state: StateFlow<BlockerState> = _state.asStateFlow()
```

---

### 7.3 BootReceiver

```kotlin
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        if (prefs.getBoolean(KEY_AUTO_START, false)) {
            val target = prefs.getString(KEY_TARGET_PACKAGE, null) ?: return
            Intent(context, BlockerService::class.java).apply {
                action = BlockerService.ACTION_START
                putExtra(BlockerService.KEY_TARGET_PACKAGE, target)
            }.also { context.startForegroundService(it) }
        }
    }
}
```

---

## 8. Permissions

| Permission | Type | Why Needed |
|---|---|---|
| `BIND_VPN_SERVICE` | Normal | Declare VpnService capability |
| `FOREGROUND_SERVICE` | Normal | Run persistent foreground service |
| `RECEIVE_BOOT_COMPLETED` | Normal | Auto-start on reboot |
| `PACKAGE_USAGE_STATS` | Special (manual grant) | Detect foreground app |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Special (manual grant) | Survive Samsung Doze |

**Permission grant flow:**
1. On first launch, check each permission
2. Show rationale dialog for `PACKAGE_USAGE_STATS` (most users won't know what it is)
3. Deep-link directly to the relevant Settings page for each missing permission
4. Re-check on resume

---

## 9. Samsung One UI Considerations

Samsung's One UI has aggressive background process management that requires special handling.

### 9.1 Battery Optimisation
- Request exemption via `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
- Without this, Samsung will kill the service within minutes
- Guide the user to **Settings → Battery → Background usage limits** and ensure the app is excluded

### 9.2 Auto-Start Permission
- Samsung has a proprietary **Auto-start** permission not in AOSP
- Some One UI versions require the user to manually enable this at **Settings → Apps → [App] → Battery → Allow background activity**
- Show a one-time prompt guiding the user to enable this

### 9.3 Adaptive Battery
- One UI's Adaptive Battery may restrict `UsageStatsManager` polling
- Mitigation: keep the foreground service notification visible and active
- Consider `AccessibilityService` as a fallback for foreground detection (more reliable but requires broader permission)

### 9.4 VPN Slot
- Android allows only one active VPN at a time
- If Samsung's built-in VPN or Knox is active, `VpnService.establish()` will fail
- Detect this case and show a clear error message

### 9.5 `addAllowedApplication()` and Samsung-specific apps
- System apps and apps running in Samsung Secure Folder use separate user profiles
- `addAllowedApplication()` only scopes traffic within the primary user profile
- Apps inside Secure Folder will not be affected by the tunnel — surface a clear message if user selects such an app

---

## 10. Edge Cases & Limitations

| Scenario | Behaviour |
|---|---|
| User switches away from target app quickly | 1s poll delay means ~1s of unblocked traffic; acceptable for v1 |
| Target app uses certificate pinning | Packets still blocked at IP level; pinning is irrelevant |
| Target app detects VPN / proxy | App may show a warning or refuse to load; out of scope |
| Another VPN is active | `establish()` returns null; show error, prompt user to disconnect other VPN |
| Device reboots mid-session | BootReceiver handles restart if auto-start is enabled |
| Target app is uninstalled | Service continues running; watchdog never matches; no harm |
| User revokes Usage Stats permission post-launch | Watchdog fails silently; add a periodic permission re-check |
| Samsung One UI kills service | Foreground service + battery exemption minimises this; add a `JobScheduler` watchdog as backup |
| Target app runs in Secure Folder | Separate user profile — `addAllowedApplication()` scope does not reach it; show unsupported message |
| User selects a system app | `addAllowedApplication()` may throw `PackageManager.NameNotFoundException` for some system packages; validate on app selection |

---

## 11. Open Questions

| # | Question | Notes |
|---|---|---|
| 1 | Is `AccessibilityService` a better foreground detector than `UsageStatsManager`? | More reliable on OneUI, but intrusive permission; evaluate in testing |
| 2 | Should we log blocked sessions for the user to review? | Privacy concern if logs persist; opt-in only; log tunnel open/close events not individual packets |
| 3 | Multi-app blocking in v2? | Call `addAllowedApplication()` once per target package on the builder before `establish()` — straightforward extension |
| 4 | Should the user be prompted to disable background data in Samsung settings as part of onboarding? | Yes — deep-link to Settings → Apps → [App] → Data usage so setup is one flow |

---

*Document version 1.2 — simplified to drop-only tunnel; background traffic delegated to Samsung per-app data restriction; no packet forwarding or relay.*
