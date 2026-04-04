# Traffic Blocker

An Android VPN-based traffic blocker that lets you control internet access per app — either block all traffic or block specific domains using customizable blocklists.

## Features

- **Per-app blocking** — Select which apps to block from a list of installed apps
- **Two blocking modes per app:**
  - **Block All Traffic** — Blocks every DNS query, effectively cutting off internet for the app
  - **Block Domains** — Blocks only domains from your configured blocklists
- **Domain blocklists** — Add blocklist URLs (plain domain lists or hosts-file format), the app downloads and parses them automatically
- **DNS-level blocking** — Intercepts DNS queries via a local VPN, no root required
- **Per-app UID identification** — Different apps can have different blocking rules simultaneously
- **Persistent service** — Survives app removal from recents, system kills, and device reboots
- **Dark mode** — System default, light, or dark theme
- **No ads, no tracking, no internet permission abuse** — The VPN only intercepts DNS traffic for selected apps

## How It Works

Traffic Blocker uses Android's VPN API to intercept DNS queries from selected apps:

1. A local VPN tunnel is created that only routes DNS traffic (to `8.8.8.8` / `8.8.4.4`)
2. Apps are scoped using `addAllowedApplication()` so only selected apps are affected
3. DNS queries are parsed from the tunnel packets
4. For **Block All** apps: every query returns `0.0.0.0` (no internet)
5. For **Block Domains** apps: queries matching the blocklist return `0.0.0.0`, others are forwarded to upstream DNS
6. Source app identification uses `/proc/net/udp` UID lookup

Non-DNS traffic is never routed through the VPN — apps with "Block Domains" mode can still access non-blocked sites at full speed.

## Screenshots

| Home | App Picker | Blocklists | Settings |
|------|-----------|------------|----------|
| Blocking targets with per-app mode chips | Select/unselect apps with search | Add blocklist URLs | Theme, polling, permissions |

## Requirements

- Android 8.0 (API 26) or higher
- VPN permission (prompted on first use)
- Usage Stats permission (for foreground app detection)
- Battery optimization exemption (recommended for reliable background operation)

## Building

```bash
# Clone the repository
git clone https://github.com/akash705/Traffic-Blocker.git
cd Traffic-Blocker

# Build debug APK
./gradlew assembleDebug

# Build release APK (requires signing config)
./gradlew assembleRelease
```

The debug APK will be at `app/build/outputs/apk/debug/app-debug.apk`.

## Supported Blocklist Formats

The app supports two common blocklist formats:

**Plain domain list** (one domain per line):
```
ads.example.com
tracker.example.net
malware.example.org
```

**Hosts file format**:
```
0.0.0.0 ads.example.com
127.0.0.1 tracker.example.net
```

Lines starting with `#` or `!` are treated as comments. `www.` prefixes are automatically stripped for matching.

### Popular Blocklists

- [StevenBlack/hosts](https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts) — Unified hosts file with ads, malware, and tracking domains
- [Energized Protection](https://energized.pro/) — Multiple tiers of domain blocking
- [OISD](https://oisd.nl/) — Curated blocklist balancing blocking and usability

## Architecture

```
com.akash.apptrafficblocker/
├── data/
│   ├── AppDatabase.kt          # Room database (block events + blocklist sources)
│   ├── BlocklistRepository.kt  # Download, parse, and store blocklists
│   └── PrefsManager.kt         # SharedPreferences for settings
├── service/
│   ├── BlockerService.kt       # VPN foreground service
│   ├── DnsPacketProxy.kt       # DNS interception + blocking engine
│   ├── AppWatchdog.kt          # Foreground app detection
│   └── PacketTunnel.kt         # Drop-all packet tunnel (legacy)
├── ui/
│   ├── home/                   # Main screen with targets + status
│   ├── picker/                 # App selection with search
│   ├── blocklist/              # Blocklist URL management
│   ├── settings/               # Theme, polling, permissions
│   └── theme/                  # Material3 theming
└── receiver/
    └── BootReceiver.kt         # Auto-start on boot
```

## Tech Stack

- **Language:** Kotlin
- **UI:** Jetpack Compose + Material3
- **Database:** Room
- **Navigation:** Jetpack Navigation Compose
- **Architecture:** MVVM with StateFlow
- **Min SDK:** 26 (Android 8.0)
- **Target SDK:** 35

## License

MIT License. See [LICENSE](LICENSE) for details.
