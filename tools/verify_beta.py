#!/usr/bin/env python3
"""Verify the beta artifact's identity, non-debuggable manifest, signature and checksum."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import xml.etree.ElementTree as ET


def verify(apk, expected_certificate=None):
    analyzer = shutil.which('apkanalyzer')
    if not analyzer:
        raise SystemExit('apkanalyzer is required from Android SDK command-line tools')
    sdk = Path(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT') or Path(analyzer).resolve().parents[3])
    signers = sorted((sdk / 'build-tools').glob('*/apksigner'), key=lambda p: tuple(int(n) for n in re.findall(r'\d+', p.parent.name)))
    if not signers:
        raise SystemExit('Android build-tools/apksigner is required')
    manifest = subprocess.check_output([analyzer, 'manifest', 'print', str(apk)], text=True)
    xml = ET.fromstring(manifest)
    ns = '{http://schemas.android.com/apk/res/android}'
    assert xml.get('package') == 'com.dronewukong.takbridge.beta', 'Wrong beta application ID'
    assert xml.get(ns + 'versionName') == '0.5.0-beta.1', 'Wrong beta version'
    assert xml.get(ns + 'versionCode') == '8', 'Wrong version code'
    assert xml.find('application').get(ns + 'debuggable', 'false') == 'false', 'Debuggable APK cannot be published as this beta'
    assert xml.find('uses-sdk').get(ns + 'minSdkVersion') == '26', 'Unexpected minimum Android version'
    signature = subprocess.check_output([str(signers[-1]), 'verify', '--verbose', '--print-certs', str(apk)], text=True)
    matches = re.findall(r'Signer #\d+ certificate SHA-256 digest: ([a-fA-F0-9]+)', signature)
    assert len(matches) == 1, 'Expected exactly one signing certificate'
    certificate = matches[0].lower()
    if expected_certificate:
        assert certificate == expected_certificate.replace(':', '').lower(), 'Signing identity changed'
    return {'application_id': xml.get('package'), 'version': xml.get(ns + 'versionName'),
            'version_code': int(xml.get(ns + 'versionCode')), 'debuggable': False,
            'min_sdk': 26, 'bytes': apk.stat().st_size,
            'sha256': hashlib.sha256(apk.read_bytes()).hexdigest(), 'certificate_sha256': certificate}


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk', type=Path)
    parser.add_argument('--expected-certificate')
    args = parser.parse_args()
    print(json.dumps(verify(args.apk, args.expected_certificate), indent=2))
