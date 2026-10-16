#!/usr/bin/env bash
# Isolated Beta test APK only; no production database or real device inbox.
set -euo pipefail
APP_ID="com.baraa.masroof.lab.beta.test"
RUNNER="${APP_ID}/androidx.test.runner.AndroidJUnitRunner"
CLASS="com.baraa.masroof.lab.beta.processdeath.BetaHistoryProcessDeathTest"
instrument() {
  local output
  output=$(adb shell am instrument -w -r -e betaResume "$2" -e class "${CLASS}#$1" "$RUNNER")
  printf '%s\n' "$output"
  case "$output" in *"OK (1 test)"*) ;; *) exit 1 ;; esac
}
journey() {
  adb shell pm clear "$APP_ID"
  instrument "$1" false
  adb shell am force-stop "$APP_ID"
  instrument "$2" true
}
journey armDuringComparison resumeDuringComparison
journey armAfterComparisonBeforeCheckpoint resumeAfterComparisonBeforeCheckpoint
echo "Beta process-death journeys passed"
