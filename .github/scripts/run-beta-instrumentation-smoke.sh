#!/usr/bin/env bash
set -eu

# The emulator action runs separate script lines in separate shells. Keep the
# Gradle status and capture cleanup in this one process, including on failure.
task_sdk_dir="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
test -n "$task_sdk_dir"
printf 'sdk.dir=%s\n' "$task_sdk_dir" > local.properties

# Existing notPackage arguments keep process-death phases out of this suite.
smoke_status=0
./gradlew :lab:beta:connectedDebugAndroidTest :app:connectedDebugAndroidTest :lab:android:connectedDebugAndroidTest --no-parallel --stacktrace || smoke_status=$?
mkdir -p m19-statement-screenshots beta-dashboard-screenshots
adb pull /sdcard/m19-statement-screenshots/. m19-statement-screenshots || true
adb pull /sdcard/beta-dashboard-screenshots/. beta-dashboard-screenshots || true
adb pull /sdcard/Android/data/com.baraa.masroof.lab.beta.test/files/m0-entry/. beta-dashboard-screenshots || true
if [ "$smoke_status" -ne 0 ]; then exit "$smoke_status"; fi
find m19-statement-screenshots -name 'm19-*.png' | grep -q .
