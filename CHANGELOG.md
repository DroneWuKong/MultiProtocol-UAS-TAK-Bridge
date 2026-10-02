# Changelog

## 0.4.0 — Prismo interface and guides (2026-10-02)

- Prismo charcoal, teal and cream styling across Map and Tools, with larger controls, a restrained map palette and named connection-state accessibility descriptions.
- Scrollable connection settings keep Connect, Send to TAK, Software demo and Diagnostics available. Settings expansion survives activity recreation; map controls return when it closes.
- Illustrated eleven-page user manual and redesigned two-page quick-start, with searchable Markdown copies and reproducible PDF source.
- Public guides describe existing streams and generic bridge workflows; device-side feature activation stays out of scope.
- Added Android layout checks for portrait phones, landscape and tablets, including reachability of lower settings and expansion restoration.
- Telemetry decoders, protocol behavior, USB matching and RF calculations are retained.


## Unreleased - Hardware-neutral naming (2026-10-02)

- Omit device-side activation recipes from the README and public guides;
  retain support in the code and document bridge operation for existing streams.
- Replace hardware manufacturer/product descriptions with generic USB, radio,
  video, VTX and catalog profiles; keep telemetry, USB IDs and RF values intact.
- Preserve saved connection-profile selections through an upgrade migration.
- Rename USB setup/evidence documents and refresh the printable quick-start.
- Retain exact upstream target/layout keys, pinned sources and license notices;
  document the boundary in `docs/NAMING_POLICY.md`.

## 0.3.0 — Reliability foundation (2026-09-30)

- Restore the empty Unix Gradle launcher and require actual native test/APK/lint evidence in CI.
- Move USB parsing, GPS freshness, MSP polling and CoT output into a foreground-service session. Closing the activity leaves it running; Stop and USB loss clear live state.
- Add a local-only MAVLink software demo, connection diagnostics/export and a Local TAK multicast toggle.
- Complete password-protected PKCS12 import, CA import and TLS hostname checks; remove obsolete plaintext password preferences.
- Serialize network writes, isolate connection generations, detect idle TCP EOF, reconnect and discard expired queued events.
- Use an ephemeral UDP source port so ATAK can listen on 6969 on the same phone.
- Add lifecycle/replay/socket/mTLS regression tests and explicit release-signing requirements.

## 0.2.3 — Tools functional audit (2026-09-29)

- Repair all 13 Tools panels, native clipboard/export, offline RX/TX catalogs, VTX presets, coordinate conversion, terrain imports/calculations and mesh failure cases.
- Browser checks cover 20 functional groups and 39 navigation checks. See docs/TOOLS_AUDIT_2026-09-29.md.


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

## 0.2.0 — USB controller USB compatibility (2026-09-29)

- controller MAVLink/GHST profiles, configurable DTR, Android permission/retry handling,
  serial-port selection and disconnect/session cleanup.
- Correct GHST GPS framing, CRC, units and fix flags; separate CRSF GPS decoding.
- CRC-checked MAVLink 1/2, zero-truncated/signed frames and source isolation.
- RC-only versus GPS diagnostics and fresh-position-only CoT publication.
- Software wire fixtures and Android build CI. The Unix Gradle launcher was later found empty and restored in 0.3.0.
- Repair pre-existing build errors in the CoT caller/comment, MSP constant,
  launcher icon and TAK reconnect coroutine; guard optional phone GPS access.

Hardware aircraft-GPS-through-controller-USB acceptance remains pending; see
[setup and test steps](docs/USB_CONTROLLER_SETUP.md).

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
- Removed manufacturer branding from docs (README controller note; CoT wire-format `takv`
  device example). (6aaaaf2)
