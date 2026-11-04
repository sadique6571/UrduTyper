#!/usr/bin/env bash
set -euo pipefail
mkdir -p android-diagnostics
adb logcat -c
adb logcat -v threadtime > android-diagnostics/logcat.txt 2>&1 &
LOGCAT_PID=$!
collect_diagnostics() {
  result=$?
  trap - EXIT
  kill "$LOGCAT_PID" 2>/dev/null || true
  timeout 15s adb shell dumpsys activity activities > android-diagnostics/activities.txt 2>&1 || true
  timeout 15s adb shell dumpsys meminfo me.nomi.urdutyper > android-diagnostics/memory.txt 2>&1 || true
  timeout 15s adb pull /sdcard/Pictures/UrduTyper-ui ui-screenshots || true
  exit "$result"
}
trap collect_diagnostics EXIT
timeout --signal=TERM --kill-after=15s 8m ./gradlew :presentation:connectedDebugAndroidTest --stacktrace --no-daemon --max-workers=2
