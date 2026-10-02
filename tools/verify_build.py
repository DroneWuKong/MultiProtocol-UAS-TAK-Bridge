#!/usr/bin/env python3
"""Fail CI if a no-op launcher produces no tests or APK (September 2026 regression)."""
from pathlib import Path
import xml.etree.ElementTree as ET
from zipfile import ZipFile

root = Path(__file__).resolve().parent.parent
reports = list((root / 'app/build/test-results/testDebugUnitTest').glob('TEST-*.xml'))
assert reports, 'Missing native test reports: Gradle may not have run'
suites = [ET.parse(p).getroot() for p in reports]
expected = {'MainActivityStartupTest', 'TelemetryCompatibilityTest', 'ToolsActionsTest',
            'BridgeLifecycleTest', 'TakTransportTest', 'ConnectionProfileMigrationTest'}
assert expected <= {s.attrib['name'].split('.')[-1] for s in suites}, 'Required regression suite missing'
for suite in suites:
    assert int(suite.get('tests', 0)) > 0, f'Empty suite: {suite.get("name")}'
    assert all(int(suite.get(k, 0)) == 0 for k in ('errors', 'failures', 'skipped')), suite.attrib
apk = root / 'app/build/outputs/apk/debug/app-debug.apk'
with ZipFile(apk) as z:
    assert z.testzip() is None, 'Corrupt APK'
    assert {'AndroidManifest.xml', 'classes.dex', 'assets/tools/tools_offline.html',
            'assets/replay/mav2-gps.hex'} <= set(z.namelist()), 'APK missing code or required assets'
assert (root / 'app/build/reports/lint-results-debug.html').stat().st_size > 0, 'No lint evidence'
print(f'Native build verified: {sum(int(s.get("tests", 0)) for s in suites)} tests, APK {apk.stat().st_size:,} bytes')
