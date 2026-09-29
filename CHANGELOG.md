# Changelog

## 0.2.2 — Phone Tools redesign (2026-09-29)

- Replace duplicate scrolling tool bars with one searchable tool library.
- Rebuild the phone layout with readable contrast, larger inputs, responsive
  columns, a map-first terrain screen and consistent teal styling.
- Fix malformed report strings that broke the entire calculator script and
  leaked report styling into the page; separate HTML, CSS and JavaScript.
- Bundle Leaflet, GeoTIFF and icon assets, use an HTTPS asset origin, and show
  map tile failures with a retry action. Add an Android file picker for DEMs.
- Fix crowded native tab icons/labels and apply system/keyboard insets once.
- Close the tool chooser before leaving Tools with Android Back.
- Correct the range calculator's mW-to-dBm and MHz/km path-loss unit errors.
- Add browser navigation/layout/calculator checks and native navigation checks.

## 0.2.1 — Fix immediate launch crash (2026-09-29)

- Reproduce and fix the startup `NullPointerException`: activity startup tried
  to bind map controls before the map fragment had created its view.
- Bind from `MapFragment.onViewCreated` using that fragment's root view.
- Reuse Android-restored fragments after activity recreation instead of adding
  a second map/tools pair.
- Add three Robolectric tests using the real manifest/layout: cold launch,
  Connect without USB, and recreation with Map/Tools navigation.
- Version code 3, compatible with installing over the previous debug build.

## 0.2.0 — TAC.CTRL USB compatibility (2026-09-29)

- TAC MAVLink/GHST profiles, configurable DTR, Android permission/retry handling,
  serial-port selection and disconnect/session cleanup.
- Correct GHST GPS framing, CRC, units and fix flags; separate CRSF GPS decoding.
- CRC-checked MAVLink 1/2, zero-truncated/signed frames and source isolation.
- RC-only versus GPS diagnostics and fresh-position-only CoT publication.
- Software wire fixtures, a complete Gradle wrapper and Android build CI.
- Repair pre-existing build errors in the CoT caller/comment, MSP constant,
  launcher icon and TAK reconnect coroutine; guard optional phone GPS access.

Hardware aircraft-GPS-through-TAC-USB acceptance remains pending; see
[setup and test steps](docs/TAC_CTRL_SETUP.md).

All notable changes to the MultiProtocol UAS TAK Bridge, most recent first.

## Unreleased

### Added
- **TAK / CoT layer upgrade** — borrowed ATAK-CIV (GPLv3) schemas and added a
  `tak/` package: `TakProtocol.kt` (TAK Protocol v1 framing, contact-endpoint and
  group/role constants) and `TakInboundMonitor.kt`, which listens on multicast for
  inbound CoT events (emergency `b-a*` beacons, sensor SPI, and peer/contact
  discovery from `<contact endpoint=…>`). (0662b6c)
- **Tools tab** — the Forge RF tools suite (Channel Planner, Range Estimator,
  Fresnel Zone, Harmonics, Dipole Length, VTX Config, FC Matcher, ELRS, etc.)
  embedded as an offline WebView. A "Use GPS" bridge (`WingmanJsBridge`) injects the
  device fix into the calculators. The app is now a dual-tab Activity (Map / Tools)
  driven by a bottom navigation bar. (cb40138)
- **Tap-to-cycle coordinate formats** — the map coordinate readout cycles through
  MGRS → Lat/Lon DD → Lat/Lon DMS → UTM on tap (`CoordinateFormatter`). The chosen
  format persists across launches; CoT always transmits in decimal degrees
  regardless of the display format. (87da206)

### Fixed
- Added `fitsSystemWindows` so the UI no longer overlaps the phone status bar. (2d12f8d)

### Changed
- Removed Orqa branding from docs (README controller note; CoT wire-format `takv`
  device example). (6aaaaf2)
