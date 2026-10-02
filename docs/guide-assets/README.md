# Guide screenshots

These images depict actual software-rendered application layouts and bundled assets.
They are illustrations of operation, not physical hardware acceptance evidence.

| File | Capture source | Scenario |
| --- | --- | --- |
| `map-controls.png` | `MainActivityStartupTest.bottomTabsKeepIconsLabelsAndContentSeparate` | Android app layout, no connected source |
| `connection-settings.png` | `MainActivityStartupTest.connectionSettingsRemainReachableAtPhoneLandscapeAndTabletSizes` | Actual expanded Android settings, 393 × 800 dp |
| `tool-library.png` | `tools/ui-tests/check.cjs` | Searchable tool library, 393 × 852 CSS px |
| `terrain-offline.png` | `tools/ui-tests/check.cjs` | Points on an offline grid; no map imagery or terrain result implied |
| `mesh-plan-example.png` | `tools/ui-tests/functional.cjs` | Three synthetic planning nodes with controlled elevation data; not field measurements |

The browser captures serve the exact APK assets at its Android asset origin.
External tile requests are blocked. The mesh example uses test elevation inputs.
Refresh the files from passing UI artifacts after a visible UI change and regenerate
both PDFs with `python3 tools/generate_guides.py`.
