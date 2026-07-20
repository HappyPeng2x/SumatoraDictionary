#!/usr/bin/env bash
# Installs the (debug-signed, see app/build.gradle) minified release APK on the running emulator
# and confirms MainActivity actually launches, instead of just checking that assembleRelease
# compiles. This is what catches R8 silently stripping something at runtime rather than failing
# the build - the release build shipped a crash-on-launch once before (Room's _Impl classes got
# stripped) while assembleRelease itself succeeded.
#
# Not inlined in ci.yml: reactivecircus/android-emulator-runner's `script:` input runs each line
# of a multi-line block as its OWN separate shell invocation, not as one continuous script -
# variables and multi-line for/if constructs don't survive across lines there. Running this as a
# single `bash check-release-launch.sh` command sidesteps that entirely.
set -euo pipefail

PKG=org.happypeng.sumatora.android.sumatoradictionary
LOGFILE=release-launch-logcat.txt

adb uninstall "$PKG" || true
adb install -r app/build/outputs/apk/release/*.apk
adb logcat -c
adb shell am start -W -n "$PKG/.activity.MainActivity"

resumed=0
for i in $(seq 1 30); do
  adb logcat -d > "$LOGFILE"
  if grep -q "FATAL EXCEPTION" "$LOGFILE"; then
    echo "Release build crashed on launch:"
    cat "$LOGFILE"
    exit 1
  fi
  if adb shell dumpsys activity activities | grep -qE "(m|top)ResumedActivity.*MainActivity"; then
    resumed=1
    break
  fi
  sleep 2
done
adb logcat -d > "$LOGFILE"

if [ "$resumed" != "1" ]; then
  echo "Release build's MainActivity never became resumed within the timeout:"
  cat "$LOGFILE"
  exit 1
fi
echo "Release build (minified, debug-signed) launched MainActivity successfully."
