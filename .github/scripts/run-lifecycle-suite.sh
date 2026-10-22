#!/usr/bin/env bash
# CI emulator only. Each stateful suite owns its device and keeps all phases together.
set -euo pipefail
suite="${1:-}"
phase="${2:-}"
case "$suite:$phase" in
  production:build|upgrade:build|production:test|upgrade:test) ;;
  *) echo "Usage: $0 production|upgrade build|test" >&2; exit 2 ;;
esac
test "$#" -eq 2
SDK_DIR="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
test -n "$SDK_DIR"
printf 'sdk.dir=%s\n' "$SDK_DIR" > local.properties

# Compile before boot; don't spend emulator CPU time compiling unrelated modules.
if [ "$phase" = build ]; then
  case "$suite" in
    production) ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest --stacktrace ;;
    upgrade) bash scripts/app-apk-upgrade.sh build ;;
  esac
  exit
fi

# Timed log groups make install vs instrumentation costs visible in the job log.
run_step() {
  local label="$1" started status=0
  shift
  started=$SECONDS
  echo "::group::$label"
  "$@" || status=$?
  echo "::endgroup::"
  printf '%s: %ss (exit %s)\n' "$label" "$((SECONDS - started))" "$status"
  return "$status"
}
case "$suite" in
  production)
    # Use the APKs already built before boot rather than invoking Gradle again.
    run_step 'Install production APK' adb install -r -t app/build/outputs/apk/debug/app-debug.apk
    run_step 'Install production test APK' adb install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
    run_step 'Production process-death journeys' bash scripts/m13-process-death.sh
    ;;
  upgrade)
    run_step 'Signed APK update and backup rehearsal' bash scripts/app-apk-upgrade.sh test
    ;;
esac
