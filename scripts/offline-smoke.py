#!/usr/bin/env python3
"""Emulator-only manual-clock-independent offline/process-death and appearance checks."""
import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument('--adb', default=os.environ.get('ADB', 'adb'))
parser.add_argument('--package', default='com.xatune.app.qa')
args = parser.parse_args()
if not os.environ.get('ANDROID_SERIAL'):
    devices = subprocess.check_output([args.adb, 'devices'], text=True, timeout=15)
    emulators = [line.split()[0] for line in devices.splitlines() if line.startswith('emulator-') and line.split()[1:] == ['device']]
    if len(emulators) != 1:
        raise SystemExit('Set ANDROID_SERIAL to exactly one emulator.')
    os.environ['ANDROID_SERIAL'] = emulators[0]
root = Path(__file__).resolve().parent.parent
out = root / 'artifacts/qa/offline'
out.mkdir(parents=True, exist_ok=True)
def adb(*parts):
    return subprocess.check_output([args.adb, *parts], text=True, timeout=30)
if adb('shell', 'getprop', 'ro.kernel.qemu').strip() != '1':
    raise SystemExit('This smoke check only operates on an emulator.')
def nodes():
    adb('shell', 'uiautomator', 'dump', '/sdcard/xatu-milestone-window.xml')
    return ET.fromstring(adb('shell', 'cat', '/sdcard/xatu-milestone-window.xml')).iter('node')
def find(text, timeout=15):
    until = time.monotonic() + timeout
    while time.monotonic() < until:
        for node in nodes():
            if text in (node.get('text', ''), node.get('content-desc', '')):
                return node
        time.sleep(.25)
    raise AssertionError('Missing UI text: ' + text)
def tap(text):
    node = find(text)
    left, top, right, bottom = map(int, re.findall(r'\d+', node.get('bounds')))
    adb('shell', 'input', 'tap', str((left + right)//2), str((top + bottom)//2))
def screenshot(name):
    with (out/name).open('wb') as stream:
        subprocess.run([args.adb, 'exec-out', 'screencap', '-p'], stdout=stream, check=True, timeout=15)

original_font = adb('shell', 'settings', 'get', 'system', 'font_scale').strip()
original_night = adb('shell', 'cmd', 'uimode', 'night').strip().split()[-1]
original_wifi = adb('shell', 'settings', 'get', 'global', 'wifi_on').strip()
original_data = adb('shell', 'settings', 'get', 'global', 'mobile_data').strip()
try:
    adb('shell', 'am', 'start', '-W', '-n', args.package + '/com.xatune.app.MainActivity')
    current = list(nodes())
    labels = {n.get('text', '') for n in current}
    if '体验校园助手 · 调试版' in labels:
        tap('体验校园助手 · 调试版')
    elif '示例数据 · 未连接学校账号' not in labels:
        raise AssertionError('Use the demo fixture, not a personal account, for this smoke check.')
    tap('首页')
    find('今天的安排')
    tap('课程表')
    find('高等数学')
    # Persist a real setting across process death as well as the timetable itself.
    current = list(nodes())
    if any(n.get('content-desc') == '隐藏周末' for n in current):
        tap('隐藏周末')
    find('显示周末')
    print('Seeded and displayed fixture timetable.', flush=True)
    adb('shell', 'am', 'force-stop', args.package)
    adb('shell', 'svc', 'wifi', 'disable')
    adb('shell', 'svc', 'data', 'disable')
    cold = adb('shell', 'am', 'start', '-W', '-n', args.package + '/com.xatune.app.MainActivity')
    find('今天的安排')
    tap('课程表')
    find('高等数学')
    find('显示周末')
    tap('显示周末')
    screenshot('offline-process-restart.png')
    evidence = {'coldLaunch': cold, 'wifiOn': adb('shell', 'settings', 'get', 'global', 'wifi_on').strip(), 'mobileData': adb('shell', 'settings', 'get', 'global', 'mobile_data').strip(), 'cachedCourseVisible': True, 'weekendPreferenceSurvivedProcessDeath': True}
    (out/'offline-result.json').write_text(json.dumps(evidence, ensure_ascii=False, indent=2))
    print('Offline process-death recovery passed: ' + cold.replace('\n', ' '), flush=True)
    adb('shell', 'cmd', 'uimode', 'night', 'yes')
    time.sleep(1)
    find('高等数学')
    screenshot('timetable-dark.png')
    adb('shell', 'settings', 'put', 'system', 'font_scale', '1.3')
    time.sleep(1)
    find('高等数学')
    screenshot('timetable-dark-large-text.png')
    print('Captured dark theme and 1.3x font screenshots.', flush=True)
finally:
    if original_font == 'null':
        adb('shell', 'settings', 'delete', 'system', 'font_scale')
    else:
        adb('shell', 'settings', 'put', 'system', 'font_scale', original_font)
    adb('shell', 'cmd', 'uimode', 'night', original_night)
    adb('shell', 'svc', 'wifi', 'enable' if original_wifi == '1' else 'disable')
    adb('shell', 'svc', 'data', 'enable' if original_data == '1' else 'disable')
