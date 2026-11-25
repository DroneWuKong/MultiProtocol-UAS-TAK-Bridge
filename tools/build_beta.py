#!/usr/bin/env python3
"""Build a signed, checked beta with credentials kept outside the checkout."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
KEYS = ('TAK_BETA_KEYSTORE', 'TAK_BETA_STORE_PASSWORD', 'TAK_BETA_KEY_ALIAS', 'TAK_BETA_KEY_PASSWORD')
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--credentials', type=Path, help='Owner-only JSON file containing the four TAK_BETA_* values; otherwise use environment')
args = parser.parse_args()
env = dict(os.environ)
if args.credentials:
    path = args.credentials.resolve(strict=True)
    if path.is_relative_to(ROOT):
        parser.error('Keep signing credentials outside the checkout')
    if os.name == 'posix' and path.stat().st_mode & 0o077:
        parser.error('Credential file must be owner-only (chmod 600)')
    data = json.loads(path.read_text())
    env.update({key: str(data[key]) for key in KEYS if key in data})
if not all(env.get(key) for key in KEYS):
    parser.error('All four TAK_BETA_* signing values are required; see docs/BETA_RELEASE.md')
subprocess.run([str(ROOT / 'gradlew'), 'testDebugUnitTest', 'assembleDebug', 'assembleBeta', 'lintDebug', 'lintBeta',
                '--no-daemon', '--max-workers=1', '-Dorg.gradle.jvmargs=-Xmx768m -Dfile.encoding=UTF-8',
                '-Pkotlin.compiler.execution.strategy=in-process'], cwd=ROOT, env=env, check=True)
subprocess.run([sys.executable, str(ROOT / 'tools/verify_build.py')], cwd=ROOT, check=True)
subprocess.run([sys.executable, str(ROOT / 'tools/verify_beta.py'), str(ROOT / 'app/build/outputs/apk/beta/app-beta.apk')], env=env, check=True)
