# Tools functional audit — 0.2.3

The 0.2.2 navigation checks did not establish that every calculator and action worked. This audit exercises all 13 panels and fixes failures in the underlying calculations, generated commands, imported data, and Android actions.

## Coverage

| Tool | Verified behavior |
| --- | --- |
| Terrain link | API elevation parsing, explicit service failure, local HGT and WGS84 GeoTIFF import, malformed/projected file rejection, known FSPL and diffraction values, coordinates, map layers, trace, overlay, waypoint profiles, repeater calculations, undo, clear, HTML export |
| Mesh planner | Three-node analysis, power changes including 0 dBm, zero margin, node removal and GCS outage, profile/trace, layers/overlay, report export, unavailable terrain, undo/clear |
| Link range | All five presets, 1000 mW = 30 dBm, known 164 km free-space budget, invalid/empty inputs |
| Fresnel clearance | 1000 m at 1000 MHz: 8.7 m midpoint radius, 5.2 m clearance, 7.5 m quarter-path radius; invalid position |
| Harmonics | Harmonic frequencies, GNSS frequency proximity, all preset categories, invalid frequency and reversed band limits |
| Antenna length | 1000 MHz/VF 1: 75 mm quarter-wave and 150 mm half-wave; inches conversion, six presets, invalid velocity factor |
| Channel planner | Same-channel conflict, separated channels, six-pilot limit, add/remove |
| Closest channel | 5800 MHz = F4; Raceband nearest = R5/5806; band filtering and invalid input |
| VTX configuration | Ten source-backed model/region variants; band count, frequency count, power encoding, UART selection, full clipboard text |
| VTX table | SmartAudio 2.0 indices, SmartAudio 2.1 dBm, Tramp mW, CUSTOM frequency bands, no-band rejection, clipboard |
| FC matcher | Real MCU/GYRO/board_name syntax, exact target matching, unknown text, clear |
| ExpressLRS reference | Static panel, four resource destinations, repaired supported-hardware link |
| MafiaLRS catalog | 222 RX and 106 TX entries offline, search, manufacturer filter, selection, firmware/layout details, clipboard |

## Fixes

- Android clipboard uses the native clipboard service. Copy failures are visible. The table copy callback no longer reads an expired global `event`.
- Reports use Android's Create Document picker and write UTF-8 HTML to the selected URI; browser builds download a standalone HTML file. Report markup drops copied interactive handlers. Export from a selected tool hash and cancel/retry are covered in Android tests.
- Removed broken map-cache buttons whose handlers did not exist. Local elevation imports remain available; bulk offline map caching is not implemented.
- Bundled the missing Forge target catalog. The tool is labeled a target catalog, not a firmware generator; it does not build or flash firmware. Derived catalog IDs are distinguished from firmware/layout names.
- Replaced invented VTX model power tables with ten explicitly sourced presets. Removed nonexistent commands, UART pin unmapping, and the AUX selector which generated no configuration. Unsupported models are not represented by guessed tables.
- Generic VTX tables correctly distinguish protocol power encodings and use CUSTOM bands, so filtering/reordering bands does not send the wrong hardware band number. Generic power examples still require a matching hardware table.
- Numeric calculators reject empty/out-of-range inputs and clear previous results. Mesh uses the selected power, respects zero values, invalidates results after edits, and does not silently promote another node to GCS.
- Replaced the inaccurate approximate MGRS conversion with bundled `mgrs@2.1.0`. Tested the Eiffel Tower reference and southern-hemisphere DMS. Place search runs on Enter, not each keystroke.
- Elevation APIs require complete finite data, have bounded timeouts, and never substitute zero-height terrain on failure. Mesh marks unevaluated terrain as unknown and does not call those links connected.
- Corrected single knife-edge diffraction to the ITU approximation and included a standard 4/3 effective Earth radius. GeoTIFF imports enforce WGS84 geographic coordinates and reject nodata/nonfinite elevations. File/raster sizes are bounded.
- Cleared stale results when inputs, points, or DEMs change; guarded in-flight results against edits. Waypoint profile indices no longer change when the distance chart is sorted.
- Frequency matching no longer treats radios hundreds of MHz apart as compatible. Non-mesh peer nodes are not modeled as relay links. Compatibility and radio sensitivity/rate presets remain planning assumptions.
- Free-space overlays and traces explicitly state that they exclude terrain.

## Evidence

- `tools/ui-tests/check.cjs`: all 13 panels at 360, 393, and 800 px; 39 navigation checks, no clipped controls and no uncaught script errors.
- `tools/ui-tests/functional.cjs`: 20 scenario groups covering the matrix above, no uncaught script errors. Uses deterministic elevation fixtures, real binary HGT/GeoTIFF files, bundled parsers, and browser clipboard/download APIs. It does not impersonate a physical radio.
- Android: 26 tests pass, including four native clipboard/report tests and the existing startup/telemetry suite. Tests run under Robolectric SDK 35, not a physical phone or emulator.
- Debug build and lint pass; lint reports 0 errors and 81 pre-existing warnings.
- Live HTTP checks on 2026-09-29: USGS EPQS returned 1892.482788086 m at 39.5/-104.5; Open-Elevation returned 50.822224 m at 48.8582/2.2945. ExpressLRS Product Finder, Configurator instructions, and Signal Health pages returned HTTP 200. These service checks do not guarantee continuing availability on a phone's network.

Run browser checks with `npm ci && npx playwright install chromium && npm test` in `tools/ui-tests`. CI runs both UI suites before the Android build. Run Android checks with `./gradlew testDebugUnitTest assembleDebug lintDebug` using JDK 17.

## Sources and limits

VTX data is pinned to [Betaflight firmware-presets revision 06c42ca](https://github.com/betaflight/firmware-presets/tree/06c42cade636be6eeff62f3f5d55cccd67a2d729/presets/4.3/vtx). Each preset links to its individual source; source blob hashes were checked before extraction. The upstream GPL license is bundled. Command/table semantics: [Betaflight VTX documentation](https://betaflight.com/docs/wiki/guides/current/VTX).

Catalog: `DroneWuKong/droneclear_Forge`, `forge-source/forge_database.json`, blob `efafdad617b549125328ee262f154d7e28f13bf2`. Only identification/source/firmware/layout fields are bundled; unrelated compliance, price, and performance claims are excluded.

Diffraction reference: [ITU-R P.526](https://www.itu.int/rec/R-REC-P.526). This is a sampled, single dominant-edge planning model. It does not resolve every obstruction, vegetation, buildings, multipath, rain, antenna pattern, or actual throughput. GeoTIFF imports support geographic EPSG:4326 only. Map tiles and address lookup need internet; a covering local DEM enables terrain calculations offline.

No physical TAC.CTRL, VTX, flight controller, phone installation, or over-the-air test was performed. Generated CLI was checked against source-backed formats and tables; it was not applied to hardware. The previous TAC.CTRL telemetry limitations still apply.
