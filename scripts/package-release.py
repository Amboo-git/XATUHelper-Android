#!/usr/bin/env python3
"""Package an installable optimized APK without treating a development key as a release identity."""
from pathlib import Path
import hashlib
import json
import os
import re
import shutil
import subprocess
import zipfile

root = Path(__file__).resolve().parent.parent
config = (root / 'app/build.gradle.kts').read_text()
version = re.search(r'versionName\s*=\s*"([^"]+)"', config)[1]
version_code = int(re.search(r'versionCode\s*=\s*(\d+)', config)[1])
sdk = Path((root / 'local.properties').read_text().split('sdk.dir=', 1)[1].splitlines()[0])
build_tools = sdk / 'build-tools/36.0.0'
source = root / 'app/build/outputs/apk/release/app-release-unsigned.apk'
out = root / 'dist' / version
out.mkdir(parents=True, exist_ok=True)
with zipfile.ZipFile(source) as apk:
    dex = b''.join(apk.read(name) for name in apk.namelist() if re.fullmatch(r'classes\d*\.dex', name))
for forbidden in ['体验校园助手', '周次校准（可选）', '第一周周一 · yyyy-MM-dd']:
    if forbidden.encode() in dex:
        raise SystemExit('Production APK still contains removed login UI: ' + forbidden)
commit = subprocess.check_output(['git', 'rev-parse', '--short=7', 'HEAD'], cwd=root, text=True).strip()
if f'{version} ({commit})'.encode() not in dex:
    raise SystemExit('APK version/commit does not match HEAD; rebuild after committing before packaging.')

unsigned = out / f'XATUHelper-{version}-unsigned.apk'
shutil.copy2(source, unsigned)
keystore = os.environ.get('RELEASE_KEYSTORE')
if keystore:
    signing = 'release-key-provided-via-environment'
    alias = os.environ['RELEASE_KEY_ALIAS']
    os.environ['RELEASE_STORE_PASSWORD']  # Required; do not print it or put it into a command argument.
    signing_args = ['--ks', keystore, '--ks-key-alias', alias, '--ks-pass', 'env:RELEASE_STORE_PASSWORD']
    if os.environ.get('RELEASE_KEY_PASSWORD'):
        signing_args += ['--key-pass', 'env:RELEASE_KEY_PASSWORD']
    signed = out / f'XATUHelper-{version}.apk'
else:
    signing = 'local-development-key-not-a-production-release-key'
    key = Path(os.environ.get('ANDROID_USER_HOME', str(Path.home() / '.android'))) / 'debug.keystore'
    signing_args = ['--ks', str(key), '--ks-key-alias', 'androiddebugkey', '--ks-pass', 'pass:android', '--key-pass', 'pass:android']
    signed = out / f'XATUHelper-{version}-devsigned.apk'
subprocess.run([str(build_tools / 'apksigner'), 'sign', *signing_args, '--out', str(signed), str(unsigned)], check=True)
verification = subprocess.check_output([str(build_tools / 'apksigner'), 'verify', '--verbose', '--print-certs', str(signed)], text=True)
(out / 'signature-verification.txt').write_text(verification)
badging = subprocess.check_output([str(build_tools / 'aapt'), 'dump', 'badging', str(signed)], text=True)
assert "name='cn.edu.xatu.helper'" in badging
assert f"versionCode='{version_code}'" in badging and f"versionName='{version}'" in badging
assert 'application-debuggable' not in badging
(out / 'apk-metadata.txt').write_text(badging)
files = {file.name: {'bytes': file.stat().st_size, 'sha256': hashlib.sha256(file.read_bytes()).hexdigest()} for file in [signed, unsigned]}
(out / 'SHA256SUMS').write_text(''.join(f"{data['sha256']}  {name}\n" for name, data in files.items()))
(out / 'package.json').write_text(json.dumps({'version': version, 'versionCode': version_code, 'commit': commit, 'signing': signing,
    'demoLoginRemoved': True, 'firstWeekLoginRemoved': True, 'debuggable': False, 'files': files}, ensure_ascii=False, indent=2) + '\n')
print(json.dumps({'version': version, 'signing': signing, 'apk': str(signed)}, ensure_ascii=False))
