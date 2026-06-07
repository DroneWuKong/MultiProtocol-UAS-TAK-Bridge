# Changelog

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
