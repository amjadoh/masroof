#!/usr/bin/env bash
set -eu

# Each complete suite runs once on its assigned emulator. Keep failure status and
# screenshot collection in one shell; the emulator action isolates script lines.
case "${1:-}" in
  app) smoke_task=":app:connectedDebugAndroidTest" ;;
  beta) smoke_task=":lab:beta:connectedDebugAndroidTest" ;;
  *) echo "Usage: $0 app|beta" >&2; exit 2 ;;
esac
task_sdk_dir="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
test -n "$task_sdk_dir"
printf 'sdk.dir=%s\n' "$task_sdk_dir" > local.properties

# Existing notPackage arguments keep process-death phases out of both suites.
smoke_status=0
./gradlew "$smoke_task" --no-parallel --stacktrace || smoke_status=$?
if [ "$1" = app ]; then
  mkdir -p m19-statement-screenshots
  adb pull /sdcard/m19-statement-screenshots/. m19-statement-screenshots || true
else
  mkdir -p beta-dashboard-screenshots
  adb pull /sdcard/beta-dashboard-screenshots/. beta-dashboard-screenshots || true
fi
if [ "$smoke_status" -ne 0 ]; then exit "$smoke_status"; fi
if [ "$1" = app ]; then
  find m19-statement-screenshots -name 'm19-*.png' | grep -q .
fi
