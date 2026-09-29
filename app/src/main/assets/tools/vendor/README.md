# Bundled Tools runtime

The Tools page does not require a CDN to load its navigation, icons, calculators
or map engine. Map imagery, address search and online elevation still use the
providers selected in `tools-core.js`.

| Library | Pinned npm package | License | Included assets |
| --- | --- | --- | --- |
| Leaflet | `leaflet@1.9.4` | BSD-2-Clause | Distribution JS/CSS and map images |
| GeoTIFF.js | `geotiff@2.1.3` | MIT | `dist-browser/geotiff.js` |
| Phosphor icons | `@phosphor-icons/web@2.1.2` | MIT | Regular CSS + WOFF2 |

Each directory contains its upstream LICENSE. Assets came from the matching
npm package tarball. Phosphor's CSS font source list is narrowed to the bundled
WOFF2; alternate unused font formats are omitted. Leaflet and GeoTIFF JS are
unmodified upstream distribution files. `SHA256SUMS` records packaged bytes.

Do not inject Android markup by replacing every `</head>` in a source document:
report-generator JavaScript also contains that string. Keep the page, styling,
navigation and calculator JavaScript in their separate files, and run
`tools/ui-tests` after changes.
