#!/usr/bin/env bash
# New production replica only, synthetic test UID. No real inbox or production identity.
set -euo pipefail
APP_ID="com.baraa.masroof.lab.beta.test"
RUNNER="$APP_ID/androidx.test.runner.AndroidJUnitRunner"
CLASS="com.baraa.masroof.lab.beta.processdeath.ReplicaProcessDeathTest"
case "$(adb get-serialno | tr -d '\r')" in emulator-*) ;; *) echo 'Requires CI emulator' >&2; exit 1;; esac
instrument() {
  local output
  output=$(adb shell am instrument -w -r -e class "$CLASS#$1" "$RUNNER")
  printf '%s\n' "$output"
  case "$output" in *'OK (1 test)'*) ;; *) exit 1;; esac
}
# Instrumentation ends before force-stop, so neither phase can continue in the old process.
adb shell pm clear "$APP_ID"
instrument armCapturedBeforeProcessing
adb shell am force-stop "$APP_ID"
instrument resumeCaptureThroughDefaultHostWorkerFactory
instrument armCommittedFinancialHistory
adb shell am force-stop "$APP_ID"
instrument resumeCommittedHistoryAndResetOnlyLab
mkdir -p beta-replica-diagnostics
adb pull "/sdcard/Android/data/$APP_ID/files/replica-process-death.txt" beta-replica-diagnostics/
echo 'Replica capture, cold worker, replay, financial reopen and isolated reset passed'
