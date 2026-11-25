#!/usr/bin/env python3
"""Capture the running beta unchanged, verify its installed APK, and record screenshot provenance."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = 'com.dronewukong.takbridge.beta'
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--serial', required=True)
parser.add_argument('--apk', required=True, type=Path)
parser.add_argument('--name', required=True, choices=['home','demo','connection','destination','video','diagnostics','tools'])
parser.add_argument('--scenario', required=True)
args = parser.parse_args()
adb = ['adb', '-s', args.serial]
def output(*arguments):
    return subprocess.check_output(adb + list(arguments))
def shell(*arguments):
    return output('shell', *arguments).decode().strip()
assert PACKAGE in shell('dumpsys', 'activity', 'activities').split('mResumedActivity:')[-1].splitlines()[0], 'Beta must be the resumed activity'
remote = shell('pm', 'path', PACKAGE).removeprefix('package:')
expected = hashlib.sha256(args.apk.read_bytes()).hexdigest()
with tempfile.TemporaryDirectory(prefix='tak-beta-installed-') as temp:
    installed = Path(temp) / 'installed.apk'
    subprocess.run(adb + ['pull', remote, str(installed)], check=True, capture_output=True)
    assert hashlib.sha256(installed.read_bytes()).hexdigest() == expected, 'Installed APK differs from the supplied artifact'
folder = ROOT / 'docs/guide-assets/beta-0.5.0'
folder.mkdir(parents=True, exist_ok=True)
png = output('exec-out', 'screencap', '-p')
assert png.startswith(b'\x89PNG\r\n\x1a\n')
name = args.name + '.png'
(folder / name).write_bytes(png)
manifest = folder / 'captures.json'
records = json.loads(manifest.read_text()) if manifest.exists() else {}
records[name] = {'captured_utc': datetime.now(timezone.utc).isoformat(), 'method': 'adb exec-out screencap -p; unmodified PNG',
                 'scenario': args.scenario, 'device_serial': args.serial, 'emulator': shell('getprop','ro.kernel.qemu') == '1',
                 'android_release': shell('getprop','ro.build.version.release'), 'sdk': shell('getprop','ro.build.version.sdk'),
                 'model': shell('getprop','ro.product.model'), 'application_id': PACKAGE,
                 'installed_apk_sha256': expected, 'png_sha256': hashlib.sha256(png).hexdigest()}
manifest.write_text(json.dumps(records,indent=2)+'\n')
print('Captured', name, 'from the verified installed beta APK')
