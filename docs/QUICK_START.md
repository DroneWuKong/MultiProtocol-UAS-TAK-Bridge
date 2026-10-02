# TAK Bridge Quick Start

Prismo / TAK Bridge · Development build 0.4.0

## 01. Start here

![Actual software-rendered Android screen; no USB source connected.](guide-assets/map-controls.png)

### 1 / Try the screen

Install the development APK on Android 8.0+ with USB host support. Open Map and tap Software demo. Watch the fresh, stale and no-fix states. The demo produces local previews only. Tap Stop demo before connecting live output.

### 2 / Match an existing stream

Use a USB data cable and a source already providing compatible aircraft GPS. Open Connection settings. Check the generic profile, protocol, baud, DTR and callsign against that existing interface. Close the panel.

### 3 / Connect and confirm

Tap Connect, select a serial port if prompted and grant Android USB access. Wait for fresh coordinates and a valid fix. A byte counter or heartbeat alone does not prove GPS.

### Know where to look

Tap the coordinate to cycle display formats. + / − zoom; the follow button recentres. Map and Tools are in the bottom navigation. Stop ends the session; switching apps leaves it running.

> **Device-side setup** — This guide begins with an existing compatible stream. Device-side feature discovery and activation are outside its scope.

## 02. Verify the complete path

### 4 / Choose a TAK destination

**Local client:** Enable Local TAK in Connection settings before connecting USB. Configure the receiver for 239.2.3.1:6969. Check network isolation and multicast filtering if nothing arrives.

**Server:** Enter the administrator-provided host and port (TCP default 8087). For TLS, enable TLS, import the client .p12 and any required CA through Certificates, then tap Send to TAK and unlock. The host must match the server certificate. Passwords must be entered again after the process ends.

### 5 / Verify the receiving client

Confirm the intended callsign and changing position in TAK. CoT counters show socket writes; they do not confirm client display. GPS STALE means publication has paused because the last position is more than five seconds old.

| If you see… | Check… |
| --- | --- |
| No USB serial interface | Data cable, Android host/OTG and selected source interface. |
| Bytes but no GPS | Actual upstream GPS stream, protocol, baud and valid fix. |
| No TAK marker | Group/port or server settings, receiving client and network filtering. |
| TLS rejected | Password, certificate dates, CA trust and hostname/IP match. |

### Use Tools when planning

Open Tools → All tools for searchable RF calculations, terrain paths, mesh scenarios and configuration drafts. Calculators and catalogs are bundled. Map imagery and online elevation need a network; valid HGT or WGS84 GeoTIFF data support offline terrain calculations.

> **Keep estimates in context** — RF and coverage results depend on your inputs; they are not field measurements. Check generated configuration text before applying it elsewhere.

### Stop, reconnect and share evidence

Use Stop or the service notification to end the session. Cable loss clears the position and stops the bridge; reconnect explicitly. Diagnostics exports counters without coordinates or credentials. Read the User Manual for the detailed workflows.

Development build 0.4.0. Physical USB/GPS/TAK acceptance remains pending; see V1_READINESS.md. Simulated positions never go to a network.
