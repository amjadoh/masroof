#!/usr/bin/env bash
set -eu

# Each suite (or disjoint runner shard) runs once. Keep failure status and
# screenshot collection in one shell; the emulator action isolates script lines.
case "${1:-}" in
  app) smoke_task=":app:connectedDebugAndroidTest" ;;
  beta) smoke_task=":lab:beta:connectedDebugAndroidTest" ;;
  *) echo "Usage: $0 app|beta" >&2; exit 2 ;;
esac
# Optional sharding applies only to stateless production smoke, never halt/resume.
smoke_sharded=false
if [ "$#" -eq 3 ] && [ "$1" = app ]; then
  case "$2:$3" in 0:2|1:2) ;; *) echo "Expected shard 0 or 1 of 2" >&2; exit 2 ;; esac
  smoke_sharded=true
elif [ "$#" -ne 1 ]; then
  echo "Usage: $0 app [0|1 2]|beta" >&2; exit 2
fi
task_sdk_dir="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
test -n "$task_sdk_dir"
printf 'sdk.dir=%s\n' "$task_sdk_dir" > local.properties

# Existing notPackage arguments keep process-death phases out of both suites.
smoke_status=0
if [ "$smoke_sharded" = true ]; then
  ./gradlew "$smoke_task"     "-Pandroid.testInstrumentationRunnerArguments.numShards=$3"     "-Pandroid.testInstrumentationRunnerArguments.shardIndex=$2"     --no-parallel --stacktrace || smoke_status=$?
else
  ./gradlew "$smoke_task" --no-parallel --stacktrace || smoke_status=$?
fi
if [ "$1" = app ]; then
  mkdir -p m19-statement-screenshots
  adb pull /sdcard/m19-statement-screenshots/. m19-statement-screenshots || true
else
  mkdir -p beta-dashboard-screenshots
  adb pull /sdcard/Android/data/com.baraa.masroof.lab.beta.test/files/replica-journey/. beta-dashboard-screenshots || true
fi
if [ "$smoke_status" -ne 0 ]; then exit "$smoke_status"; fi
if [ "$1" = app ] && [ "$smoke_sharded" = false ]; then
  find m19-statement-screenshots -name 'm19-*.png' | grep -q .
fi

# Sharded screenshots are required across the merged artifacts by emulator-smoke.
