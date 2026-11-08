#!/usr/bin/env python3
"""Aggregate final acceptance evidence without copying personal app data."""
from pathlib import Path
import hashlib
import json
import re
import xml.etree.ElementTree as ET
from datetime import datetime, timezone

root = Path(__file__).resolve().parent.parent
out = root / 'artifacts/qa/final'
out.mkdir(parents=True, exist_ok=True)
unit = {'tests': 0, 'failures': 0, 'errors': 0, 'skipped': 0}
for file in (root / 'app/build/test-results/testDebugUnitTest').glob('TEST-*.xml'):
    suite = ET.parse(file).getroot()
    for key in unit:
        unit[key] += int(suite.get(key, 0))
lint = ET.parse(root / 'app/build/reports/lint-results-debug.xml').getroot()
lint_counts = {}
for issue in lint.findall('issue'):
    severity = issue.get('severity')
    lint_counts[severity] = lint_counts.get(severity, 0) + 1

def instrumentation(variant):
    file = root / f'artifacts/qa/{variant}/instrumentation.txt'
    text = file.read_text() if file.exists() else ''
    match = re.search(r'^OK \((\d+) tests?\)', text, re.MULTILINE)
    return {'passed': bool(match), 'tests': int(match[1]) if match else 0}

apks = {}
variants = [(variant, f'app-{abi}-{variant}' + ('-unsigned' if variant == 'release' else '') + '.apk')
            for variant in ('debug', 'release', 'qa', 'benchmark')
            for abi in ('arm64-v8a', 'armeabi-v7a', 'x86', 'x86_64')]
for variant, name in variants:
    path = root / f'app/build/outputs/apk/{variant}/{name}'
    apks[name] = {'path': str(path.relative_to(root)), 'bytes': path.stat().st_size,
                     'sha256': hashlib.sha256(path.read_bytes()).hexdigest()}
    if variant == 'release':
        import zipfile
        with zipfile.ZipFile(path) as apk:
            dex = b''.join(apk.read(n) for n in apk.namelist() if re.fullmatch(r'classes\d*\.dex', n))
        apks[name]['demoEntryRemoved'] = '体验校园助手'.encode() not in dex

offline_file = root / 'artifacts/qa/offline/offline-result.json'
offline = json.loads(offline_file.read_text()) if offline_file.exists() else {}
logs = {name: 'BUILD SUCCESSFUL' in (out / f'{name}.txt').read_text() if (out / f'{name}.txt').exists() else False for name in ['build', 'lint']}
version = re.search(r'versionName\s*=\s*"([^"]+)"', (root / 'app/build.gradle.kts').read_text())[1]
report = {'version': version, 'generatedAt': datetime.now(timezone.utc).isoformat(), 'build': logs,
          'unit': unit, 'lint': lint_counts, 'qa': instrumentation('qa'), 'benchmark': instrumentation('benchmark'),
          'offline': offline, 'apks': apks,
          'scope': 'emulator and local builds; physical device, release signing and live school services not revalidated'}
report['passed'] = all(logs.values()) and unit['tests'] > 0 and unit['errors'] == unit['failures'] == unit['skipped'] == 0 and not any(lint_counts.get(x, 0) for x in ['Error', 'Fatal']) and report['qa']['passed'] and report['benchmark']['passed'] and offline.get('cachedCourseVisible', False) and offline.get('weekendPreferenceSurvivedProcessDeath', False) and all(data.get('demoEntryRemoved', True) for data in apks.values())
(out / 'summary.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
print(json.dumps({'passed': report['passed'], 'unit': unit, 'lint': lint_counts, 'qa': report['qa'], 'benchmark': report['benchmark']}, ensure_ascii=False))
if not report['passed']:
    raise SystemExit('Final checks are incomplete or failed; see artifacts/qa/final/summary.json')
