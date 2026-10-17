#!/usr/bin/env bash
# Only the dedicated synthetic test host on the CI emulator; no installed Masroof identity.
set -euo pipefail
HOST="com.baraa.masroof.beta.upgrade.rehearsal"
TEST="${HOST}.test"
CLASS="com.baraa.masroof.lab.upgrade.BetaApkUpgradeTest"
SDK_DIR="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"
TOOLS="$(find "$SDK_DIR/build-tools" -mindepth 1 -maxdepth 1 -type d | sort -V | tail -n 1)"
OUT="beta-upgrade-diagnostics"
mkdir -p "$OUT"
before="lab/upgrade-host/build/outputs/apk/before/debug/upgrade-host-before-debug.apk"
after="lab/upgrade-host/build/outputs/apk/after/debug/upgrade-host-after-debug.apk"

verify_apk() {
  local apk="$1" version="$2" package certificate
  package=$("$TOOLS/aapt" dump badging "$apk")
  printf '%s\n' "$package" > "$OUT/package-$version.txt"
  printf '%s\n' "$package" | grep -F "package: name='$HOST' versionCode='$version'"
  if printf '%s\n' "$package" | grep -F "uses-permission: name='android.permission.READ_SMS'"; then exit 1; fi
  "$TOOLS/apksigner" verify --verbose --print-certs "$apk" > "$OUT/certificate-$version.txt"
  certificate=$(sed -nE 's/^(Signer #1 |V2 Signer: )certificate SHA-256 digest: ([a-fA-F0-9]{64})$/\2/p' "$OUT/certificate-$version.txt")
  test -n "$certificate"
  printf '%s\n' "$certificate"
  sha256sum "$apk" | tee "$OUT/apk-sha-$version.txt"
}
verify_apk "$before" 1
verify_apk "$after" 2
diff <(grep 'certificate SHA-256 digest:' "$OUT/certificate-1.txt") <(grep 'certificate SHA-256 digest:' "$OUT/certificate-2.txt")

instrument() {
  local method="$1" output
  output=$(adb shell am instrument -w -r -e class "$CLASS#$method" "$TEST/androidx.test.runner.AndroidJUnitRunner")
  printf '%s\n' "$output" | tee "$OUT/$method.txt"
  case "$output" in *"OK (1 test)"*) ;; *) exit 1 ;; esac
}

# These fixed identities can never match the production application.
case "$HOST" in com.baraa.masroof.beta.upgrade.rehearsal) ;; *) exit 1 ;; esac
rehearsal_serial="$(adb get-serialno | tr -d '\r')"
case "$rehearsal_serial" in emulator-*) ;; *) echo "This rehearsal requires the isolated CI emulator" >&2; exit 1 ;; esac
export ANDROID_SERIAL="$rehearsal_serial"
test "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" = "1"
adb install -r -t "$before"
adb install -r -t lab/upgrade-host/build/outputs/apk/androidTest/before/debug/upgrade-host-before-debug-androidTest.apk
instrument seedVersionOne
adb shell dumpsys package "$HOST" | grep 'versionCode=' | tee "$OUT/installed-before.txt"
# No clear/uninstall/downgrade flag: this must be a real signed package replacement.
printf '%s\n' 'adb install -r -t isolated host versionCode=2; no clear, uninstall or downgrade flag' > "$OUT/install-update.txt"
adb install -r -t "$after" | tee -a "$OUT/install-update.txt"
adb install -r -t lab/upgrade-host/build/outputs/apk/androidTest/after/debug/upgrade-host-after-debug-androidTest.apk
instrument verifyVersionTwo
adb shell dumpsys package "$HOST" | grep 'versionCode=' | tee "$OUT/installed-after.txt"
adb pull "/sdcard/Android/data/$HOST/files/upgrade-rehearsal/." "$OUT/"
test -s "$OUT/before.json" && test -s "$OUT/after.json"
python3 scripts/check-beta-upgrade.py "$OUT"
echo "Synthetic Beta signed APK update 1-to-2, financial state, encrypted key continuity and measured performance passed"
