#!/bin/bash
# Emulator-only final acceptance. Production app data is never cleared.
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p artifacts/qa/final
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease :app:assembleBenchmark :performance:assembleBenchmark --offline --console plain --max-workers=1 2>&1 | tee artifacts/qa/final/build.txt
# Lint must run after kapt has finished writing every variant's generated sources.
./gradlew :app:lintDebug --offline --console plain --max-workers=1 2>&1 | tee artifacts/qa/final/lint.txt
scripts/device-tests.sh qa 2>&1 | tee artifacts/qa/final/device.txt
scripts/device-tests.sh benchmark 2>&1 | tee artifacts/qa/final/benchmark.txt
python3 scripts/offline-smoke.py --adb "${ADB:-adb}" 2>&1 | tee artifacts/qa/final/offline.txt
python3 scripts/final-report.py
