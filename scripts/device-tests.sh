#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")/.."
variant="${1:-qa}"
adb_bin="${ADB:-adb}"
# A connected phone must not make emulator-only acceptance ambiguous.
if [[ -z "${ANDROID_SERIAL:-}" ]]; then
  emulator_serials=$("$adb_bin" devices | awk '$1 ~ /^emulator-/ && $2 == "device" { print $1 }')
  if [[ -z "$emulator_serials" || "$emulator_serials" == *$'\n'* ]]; then
    echo "Set ANDROID_SERIAL to exactly one emulator." >&2
    exit 2
  fi
  export ANDROID_SERIAL="$emulator_serials"
fi
if [[ "$("$adb_bin" shell getprop ro.kernel.qemu | tr -d '\r')" != "1" ]]; then
  echo "Select exactly one emulator. This script only resets data on emulators." >&2
  exit 2
fi
case "$variant" in
  qa|debug)
    variant=qa
    package=cn.edu.xatu.helper.qa
    test_package=cn.edu.xatu.helper.qa.test
    ./gradlew :app:assembleQa :app:assembleQaAndroidTest --console plain
    test_apk=app/build/outputs/apk/androidTest/qa/app-qa-androidTest.apk
    ;;
  benchmark)
    package=cn.edu.xatu.helper.benchmark
    test_package=cn.edu.xatu.helper.performance
    ./gradlew :app:assembleBenchmark :performance:assembleBenchmark --console plain
    test_apk=performance/build/outputs/apk/benchmark/performance-benchmark.apk
    ;;
  *) echo "Usage: ADB=/path/to/adb $0 [qa|benchmark]" >&2; exit 2 ;;
esac
"$adb_bin" install -r "app/build/outputs/apk/$variant/app-$variant.apk"
"$adb_bin" install -r "$test_apk"
"$adb_bin" shell pm clear "$package"
mkdir -p "artifacts/qa/$variant"
"$adb_bin" shell am instrument -w -r "$test_package/androidx.test.runner.AndroidJUnitRunner" | tee "artifacts/qa/$variant/instrumentation.txt"
grep -Eq '^OK \([0-9]+ tests?\)' "artifacts/qa/$variant/instrumentation.txt"
if [[ "$variant" == qa ]]; then
    "$adb_bin" pull "/sdcard/Android/data/$package/files/." "artifacts/qa/$variant/"
else
    "$adb_bin" pull "/sdcard/Android/data/$test_package/files/." "artifacts/qa/$variant/"
fi
