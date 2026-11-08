#!/bin/bash
# Explicitly targeted physical-device QA; never clears the production package or changes device radios.
set -euo pipefail
cd "$(dirname "$0")/.."
adb_bin="${ADB:-adb}"
serial="${1:?Usage: ADB=/path/to/adb scripts/physical-tests.sh DEVICE_SERIAL}"
export ANDROID_SERIAL="$serial"
if [[ "$("$adb_bin" get-state)" != device ]]; then exit 2; fi
if [[ "$("$adb_bin" shell getprop ro.kernel.qemu | tr -d '\r')" == 1 ]]; then
  echo "Use device-tests.sh for emulators." >&2
  exit 2
fi
package=cn.edu.xatu.helper.qa
test_package=cn.edu.xatu.helper.qa.test
out=artifacts/qa/physical
mkdir -p "$out"
./gradlew :app:assembleQa :app:assembleQaAndroidTest --offline --console plain --max-workers=1
{
  "$adb_bin" shell getprop ro.product.manufacturer
  "$adb_bin" shell getprop ro.product.model
  "$adb_bin" shell getprop ro.build.version.release
  "$adb_bin" shell getprop ro.build.version.sdk
  "$adb_bin" shell wm size
  "$adb_bin" shell wm density
  "$adb_bin" shell settings get system font_scale
} > "$out/device.txt"
device_abi=$("$adb_bin" shell getprop ro.product.cpu.abi | tr -d '\r')
"$adb_bin" install -r "app/build/outputs/apk/qa/app-$device_abi-qa.apk"
"$adb_bin" install -r app/build/outputs/apk/androidTest/qa/app-qa-androidTest.apk
# Tests reset their own fixtures using app permissions; MIUI may forbid shell pm clear.
"$adb_bin" shell am instrument -w -r "$test_package/androidx.test.runner.AndroidJUnitRunner" | tee "$out/instrumentation.txt"
"$adb_bin" pull "/sdcard/Android/data/$package/files/." "$out/"
grep -Eq '^OK \([0-9]+ tests?\)' "$out/instrumentation.txt"
