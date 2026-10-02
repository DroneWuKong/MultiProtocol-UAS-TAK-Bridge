# MultiProtocol UAS TAK Bridge

**Drone GPS → MGRS → TAK**

Standalone Android app that reads supported GPS telemetry from a flight controller or transmitter over USB — MAVLink, MSP, or GHST — converts coordinates to MGRS, and pushes Cursor on Target (CoT) events to TAK (ATAK/WinTAK/iTAK) in real time.

Part of the [AI Wingman](https://github.com/DroneWuKong/Ai-Project) ecosystem, but runs independently with zero dependencies on other Wingman components.

Hardware descriptions use generic profiles; exact interoperability keys and source
attribution are retained. See [naming scope](docs/NAMING_POLICY.md).

## Current development build: 0.3.0

A foreground service owns active USB/TAK sessions. Switching apps or closing the
screen leaves the session running; use **STOP** or the notification to end it.
A cable disconnect clears the position and stops the session. Reconnect explicitly.

**SOFTWARE DEMO** works without USB: recorded-format MAVLink goes through the same
parser and CoT formatter, with fresh/stale/no-fix phases and a clearly labeled local
preview. It never sends simulated aircraft positions to a network. **DIAGNOSTICS**
shows/export counters and connection stages without coordinates or credentials.

See [v1 readiness, release signing and remaining device checks](docs/V1_READINESS.md).

## USB controller setup

Version 0.2 adds USB controller USB profiles with Android permission handling and DTR.
Start with **MAVLink transcode** on controller and **USB controller · MAVLink transcode** in the
app (115200, DTR on). See [the ordered setup and diagnostics](docs/USB_CONTROLLER_SETUP.md).
The alternate GHST profile decodes actual GHST GPS types and reports RC-only
streams separately. Software compatibility is tested; direct controller USB GPS still
requires the physical acceptance check in that guide.

## What It Does

```
Drone FC ──USB OTG──→ Protocol Auto-Detect ──→ Coordinate Formatter ──→ CoT Formatter ──→ TAK
                       ├─ MAVLink v1/v2          (MGRS/DD/DMS/UTM display)                ├─ Multicast UDP (239.2.3.1:6969)
                       ├─ MSP v1/v2              Live Map with                         ├─ TAK Server TCP
                       └─ GHST/CRSF              Drone Marker + Trail                  └─ TAK Server TLS (.p12 cert)
```

1. **Connects** to a flight controller via USB OTG (phone → FC or phone → transmitter)
2. **Auto-detects** protocol: MAVLink v1/v2, MSP v1/v2, or GHST/CRSF
3. **Parses** supported GPS messages from correctly configured telemetry sources
4. **Converts** lat/lon to MGRS using NGA's official library
5. **Displays** live map with drone marker (heading rotation), breadcrumb trail, coordinate readout (tap to cycle MGRS / Lat-Lon DD / Lat-Lon DMS / UTM), fix quality, satellites, altitude, speed
6. **Pushes** CoT events to ATAK/WinTAK/iTAK via configured multicast, TAK Server TCP, or TAK Server TLS
7. **Tools tab** — the Forge RF tools suite (Channel Planner, Range Estimator, Fresnel Zone, Harmonics, Dipole Length, VTX Config, FC Matcher, ELRS, etc.) runs in an embedded WebView with bundled libraries and catalogs. Online tiles/elevation need network access; local DEMs support offline terrain calculations

## Supported Protocols

| Protocol | Firmware | GPS Behavior | Default Baud |
|----------|----------|--------------|--------------|
| MAVLink v1/v2 | PX4, ArduPilot | Requires upstream GPS stream | 115200 |
| MSP v1/v2 | Betaflight, iNav | Polled at 2Hz only with Direct FC profile | 115200 |
| GHST/CRSF | Via compatible telemetry mirror | Native GHST / separate CRSF GPS frames | 115200 |

Auto-detect passively feeds all three parsers; decoded GPS locks the protocol. Manual override is available. Only the explicit Direct FC MSP profile sends GPS read requests.

## Hardware targets (physical acceptance pending)

- **Flight Controllers:** Compatible USB telemetry interfaces emitting the supported message formats; firmware configuration matters
- **USB Chips:** CDC ACM and supported USB-to-serial interfaces
- **Radio Link:** USB controller MAVLink/GHST and GHST/CRSF telemetry mirrors are compatibility targets, not proof of an aircraft GPS downlink
- **Ground Station:** Any Android 8.0+ with USB OTG
- **Map Tiles:** OpenStreetMap via OSMDroid (no API key required, works offline with cached tiles)

## Connection Methods

### Direct to Flight Controller (Recommended for demos)
Phone USB-C → OTG adapter → FC USB port. Simplest path — no radio config needed. FC must have GPS module with satellite fix.

### Through Transmitter (For live flight telemetry)
Phone USB-C → OTG adapter → Transmitter USB port. Requires EdgeTX USB serial mode set to VCP/Debug with telemetry mirroring enabled on the external module.

## TAK Integration

### Multicast (No Server)
The **Local TAK** checkbox enables UDP output to `239.2.3.1:6969` (default on). Configure ATAK to receive that group/port. Wi-Fi isolation, multicast filtering, VPNs and client settings can prevent delivery. A sent counter proves a socket write, not that ATAK displayed the marker.

### TAK Server (TCP)
Enter your TAK Server IP and port (default 8087). Auto-reconnect with linear backoff on disconnect.

### TAK Server (TLS)
Check TLS, open **CERTIFICATES**, import the client `.p12` with its password, and import the server CA `.pem`/`.crt` if needed. The client chain supplies CA trust when no separate CA is imported; otherwise system trust is used. Enter a hostname (or IP) present in the server certificate. Click **TAK** to unlock/connect. Passwords remain in memory only and must be entered again after the process ends. Bad passwords, expired certificates, untrusted servers and hostname mismatches are rejected.

### CoT Details
- **Type:** `a-f-A-M-H-Q` (Friendly, Air, Military, Rotary-wing, UAV)
- **UID:** Persistent per callsign
- **Update Rate:** 1Hz
- **Stale:** 30 seconds
- **CE90:** Estimated from HDOP
- **Detail:** Callsign, speed, heading, fix quality, satellite count

## Map Features

- **Dark/tactical map** — inverted OpenStreetMap tiles
- **Drone marker** — teal chevron that rotates with heading
- **Breadcrumb trail** — teal line showing flight path (500 point history, 2m minimum spacing)
- **Auto-follow** — map tracks drone position, tap map to pan freely, tap ◎ to re-center
- **Coordinate overlay** — large monospace coordinate displayed over map; tap it (or the format label) to cycle MGRS → Lat/Lon DD → Lat/Lon DMS → UTM. Selection persists across launches; CoT always transmits in decimal degrees regardless of display format.

## Architecture

```
com.dronewukong.takbridge/
├── mavlink/
│   ├── GpsPosition.kt          # Source-agnostic GPS data class
│   ├── MavlinkGpsParser.kt     # MAVLink v1/v2 GPS extraction
│   ├── MspGpsParser.kt         # MSP v1/v2 GPS extraction (Betaflight/iNav)
│   ├── GhstPassthrough.kt      # CRC-checked GHST and CRSF GPS parsers
│   └── ProtocolRouter.kt       # Auto-detect + route to correct parser
├── mgrs/
│   └── CoordinateFormatter.kt  # Multi-format coordinate display (MGRS/DD/DMS/UTM) over NGA MGRS lib
├── cot/
│   ├── CotTypes.kt             # MIL-STD-2525 type codes + multicast group/ports
│   └── CotFormatter.kt         # CoT XML event builder
├── tak/
│   ├── TakProtocol.kt          # TAK Protocol v1 framing + ATAK-CIV endpoint/group constants
│   └── TakInboundMonitor.kt    # Listens on multicast for inbound CoT (emergency, SPI, peer discovery)
├── transport/
│   ├── UsbSerialTransport.kt   # USB serial connection manager
│   ├── TakSender.kt            # Multicast + TCP + TLS output
│   ├── TakConfig.kt            # TAK output configuration
│   └── ConfigStore.kt          # SharedPreferences persistence
└── ui/
    ├── MainActivity.kt         # Hosts the bottom-nav shell + map logic (Map / Tools tabs)
    ├── MapFragment.kt          # Map tab — inflates the map layout owned by MainActivity
    ├── ToolsFragment.kt        # Tools tab — Forge RF tools suite in an offline WebView
    └── bridge/
        └── WingmanJsBridge.kt  # JS ↔ Android bridge (GPS location into WebView tools)
```

The app is a dual-tab Activity: a bottom navigation bar switches between the **Map** tab and the **Tools** tab.

## Building

Standard Android Studio project. Clone, open, sync Gradle, build.

```bash
./gradlew testDebugUnitTest assembleDebug
```

## Dependencies

- [usb-serial-for-android](https://github.com/mik3y/usb-serial-for-android) — USB serial driver
- [NGA MGRS](https://ngageoint.github.io/mgrs-android/) — Official MGRS coordinate library (mil.nga.mgrs)
- [OSMDroid](https://github.com/osmdroid/osmdroid) — OpenStreetMap tiles (no API key)
- AndroidX / Material Components / Kotlin Coroutines

## Quick Start Guide

See [`docs/TAK_Bridge_Quick_Start.pdf`](docs/TAK_Bridge_Quick_Start.pdf) for a printable 2-page guide covering setup, connection methods, TAK output, and troubleshooting.

## What This Is NOT

This is not a flight controller. This is not a GCS. This is not ATAK. This is a **bridge** — it reads position data and relays it to TAK. It does not command the drone, change flight modes, or modify parameters. controller/passive profiles only receive; the explicit direct-FC MSP profile sends GPS read requests. Stale or invalid GPS is not published.

## Visual Identity

Dark theme. Teal (`#4ECDC4`) accent. Monospace. Like the rest of the AI Wingman family.

---

*Buddy up.*

### Phone Tools UI

Version 0.2.2 adds one searchable tool chooser, a responsive high-contrast layout,
map loading/error states and repaired native tabs. The map engine and calculator
assets are bundled; map imagery and online elevation require internet.
See [UI repair and verification](docs/UI_REPAIR_2026-09-29.md) for the browser and
Android regression checks.
