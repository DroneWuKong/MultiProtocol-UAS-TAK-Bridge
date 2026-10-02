# Hardware-neutral naming

App-owned descriptions, documentation, connection profiles, USB picker labels,
catalog display names, VTX profile labels and RF planner presets use generic
roles, protocols, bands and profile IDs. They do not advertise manufacturers or
hardware product names. USB devices display VID:PID and serial-port number
instead of the device's self-reported product string.

Public guides cover the bridge's operation with an existing supported telemetry
stream. They omit device-side feature discovery, activation menus and
controller-specific enablement recipes. Support remains in the implementation.

The telemetry parsers, USB IDs, baud/DTR settings, polling rules, RF numerical
values, waveform compatibility groups and source data remain functional. Old
persisted connection keys are accepted on upgrade and new saves use generic
keys. Derived catalog IDs are local profile references, not firmware targets.

Exact upstream identifiers are retained where changing them would misidentify
hardware or lose source attribution:

- FC `target` keys, catalog `firmware` identifiers and `layout_file` names;
- pinned source URLs and paths, source revisions and blob hashes;
- legacy persisted-profile aliases;
- third-party dependencies, protocol names, notices and licenses.

These strings identify interoperability or provenance; they are not product
endorsements. A profile label alone does not establish hardware compatibility.
VTX profiles require matching frequency/power tables and source verification.
RF planner profiles retain the previous assumptions; their generic names do not
establish interoperability with arbitrary devices.

The current tree is updated. Existing commits, tags, releases and previously
distributed APKs/documents are historical records and are not rewritten by this
change. Renamed setup/evidence documents are linked from the current README.

Regenerate the printable guide with `python3 tools/generate_quick_start.py`
(ReportLab required). Run the existing browser and Android test suites after
changing presentation or profile keys.
