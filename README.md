# Traffic Blocker

A no-root Android app that blocks internet access per app using a local VPN. Choose between blocking **all traffic** or only **specific domains** from customizable blocklists — no ads, no tracking, no data leaves your device.

## Download

Grab the latest signed APK from [Releases](https://github.com/akash705/Traffic-Blocker/releases).

## Features

- **Per-app traffic control** — Select which apps to block from a list of all installed apps
- **Two blocking modes per app:**
  - **Block All Traffic** — Blocks every DNS query, completely cutting off internet for the app
  - **Block Domains Only** — Blocks only domains matching your configured blocklists, everything else works normally
- **Background data blocking** — Optionally block an app's network access when it's not in the foreground, preventing background ad loading, telemetry, and data usage
- **Custom domain blocklists** — Add your own blocklist URLs (plain domain lists or hosts-file format). The app downloads and parses them automatically
- **DNS query log** — Real-time log of all DNS queries showing blocked vs allowed domains per app
- **Blocking profiles** — Save and switch between different blocking configurations for quick toggling
- **Quick Settings tile** — Start/stop the blocker from the notification shade
- **Launcher shortcuts** — Long-press the app icon for Start/Stop shortcuts, compatible with Samsung Routines automation
- **Persistent service** — Survives app removal from recents, system kills, and device reboots
- **Auto-start on boot** — Optionally resume blocking when the device restarts
- **Dark mode** — System default, light, or dark theme
- **No ads, no tracking, no internet permission abuse** — The VPN only intercepts DNS traffic for selected apps

## How It Works

Traffic Blocker uses Android's VPN API to intercept DNS queries from selected apps:

1. A local VPN tunnel is created that only routes DNS traffic (to `8.8.8.8` / `8.8.4.4`)
2. Apps are scoped using `addAllowedApplication()` so only selected apps are affected
3. DNS queries are intercepted and parsed from the tunnel packets
4. For **Block All** apps: every DNS query returns `0.0.0.0` / `::` (no internet)
5. For **Block Domains** apps: queries matching the blocklist are blocked, others are forwarded to upstream DNS
6. **Background blocking**: when enabled, all DNS from the app is blocked while it's not in the foreground
7. Known DoH/DoT providers are blocked to prevent DNS bypass via encrypted DNS

Non-DNS traffic is never routed through the VPN — apps with "Block Domains" mode can still access non-blocked sites at full speed.

## Blocklists

### Recommended: Hagezi DNS Blocklists

We recommend using the excellent blocklists by [Hagezi](https://github.com/hagezi/dns-blocklists) — huge thanks to him for maintaining comprehensive, regularly updated domain lists.

Popular picks from Hagezi:

| List | Domains | Description |
|------|---------|-------------|
| [Light](https://raw.githubusercontent.com/hagezi/dns-blocklists/main/domains/light.txt) | ~80K | Basic ad/tracker blocking with minimal false positives |
| [Normal](https://raw.githubusercontent.com/hagezi/dns-blocklists/main/domains/multi.txt) | ~180K | Balanced blocking for daily use |
| [Pro](https://raw.githubusercontent.com/hagezi/dns-blocklists/main/domains/pro.txt) | ~310K | Aggressive blocking for advanced users |
| [Ultimate](https://raw.githubusercontent.com/hagezi/dns-blocklists/main/domains/ultimate.txt) | ~400K | Maximum blocking coverage |

Just copy any of the URLs above and paste it into the app's **Domain Blocklists** screen.

### Custom Blocklists

You can also add your own blocklist URLs. The app supports two common formats:

**Plain domain list** (one domain per line):
```
ads.example.com
tracker.example.net
```

**Hosts file format**:
```
0.0.0.0 ads.example.com
127.0.0.1 tracker.example.net
```

Lines starting with `#` or `!` are treated as comments. `www.` prefixes are automatically stripped.

### Other Popular Blocklists

- [StevenBlack/hosts](https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts) — Unified hosts with ads, malware, and trackers
- [OISD](https://oisd.nl/) — Curated blocklist balancing blocking and usability

## Requirements

- Android 8.0 (API 26) or higher
- VPN permission (prompted on first use)
- Usage Stats permission (for foreground app detection)
- Battery optimization exemption (recommended for reliable background operation)

## Building

```bash
git clone https://github.com/akash705/Traffic-Blocker.git
cd Traffic-Blocker

# Debug APK
./gradlew assembleDebug

# Release APK (requires keystore.properties)
./gradlew assembleRelease
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
