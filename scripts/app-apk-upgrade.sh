#!/usr/bin/env bash
# Emulator-only update of the real application identity; never uninstall between phases.
set -euo pipefail
phase="${1:-test}"
root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root"
case "$phase" in
  build)
    baseline_dir="$root/app/build/upgrade-baseline"
    mkdir -p "$baseline_dir"
    git archive b76c1afc | tar -x -C "$baseline_dir"
    cp local.properties "$baseline_dir/local.properties"
    ./gradlew -p "$baseline_dir" :app:assembleDebug -PappVersionCode=900001 --stacktrace
    ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest -PappVersionCode=900002 --stacktrace
    ;;
  test)
    case "$(adb get-serialno | tr -d '\r')" in emulator-*) ;; *) echo 'Requires an isolated test emulator' >&2; exit 1;; esac
    runner='com.baraa.masroof.test/com.baraa.masroof.instrumentation.MasroofAndroidTestRunner'
    class='com.baraa.masroof.instrumentation.upgrade.AppUpgradePreservationTest'
    diagnostics='app/build/reports/apk-upgrade'
    mkdir -p "$diagnostics"
    adb install -r -t app/build/upgrade-baseline/app/build/outputs/apk/debug/app-debug.apk
    adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
    adb shell pm clear com.baraa.masroof
    instrument() {
      local output
      if [[ -n "${CI_DEVICE_BUNDLE:-}" ]]; then
        python3 scripts/ci_device.py phase --case "$class#$1"
        return
      fi
      output=$(adb shell am instrument -w -r -e clearPackageData false -e class "$class#$1" "$runner")
      printf '%s\n' "$output"
      printf '%s\n' "$output" > "$diagnostics/$1.txt"
      case "$output" in *'OK (1 test)'*) ;; *) exit 1;; esac
    }
    instrument seedBeforeUpdate
    adb shell am force-stop com.baraa.masroof
    adb install -r -t app/build/outputs/apk/debug/app-debug.apk
    instrument verifyAfterUpdate
    ;;
  *) echo 'Usage: scripts/app-apk-upgrade.sh build|test' >&2; exit 2 ;;
esac
