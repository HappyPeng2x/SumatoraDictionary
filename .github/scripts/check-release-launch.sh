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

# Basic functionality check: search for a common word and confirm a real dictionary entry with
# real gloss text actually renders. This exercises the full stack this build type has broken
# before - Room DAOs, Hilt injection, RxJava, Paging, JSON parsing, view binding - not just "did
# the process not crash." The search box (androidx SearchView's internal EditText,
# .../id/search_src_text - a stable library resource id, unaffected by R8/minifyEnabled) is
# focused on a fresh install, confirmed via uiautomator dump, so no tap is needed - which also
# sidesteps this needing to know the emulator's screen resolution/coordinates. "Resumed" doesn't
# mean the SearchView has actually finished taking focus yet, though, so wait for that for real
# instead of racing it (a race caught during development: input text landed nowhere because the
# dump moments later still showed the empty "Search…" hint).
dump_ui() {
  adb shell uiautomator dump /sdcard/ui_dump.xml > /dev/null
  adb pull /sdcard/ui_dump.xml ui_dump.xml > /dev/null 2>&1 || true
}

search_focused=0
for i in $(seq 1 20); do
  dump_ui
  if [ -f ui_dump.xml ] && grep -q 'search_src_text".*focused="true"' ui_dump.xml; then
    search_focused=1
    break
  fi
  sleep 1
done

if [ "$search_focused" != "1" ]; then
  echo "Search box never gained focus - can't drive a search:"
  cat ui_dump.xml 2>/dev/null || echo "(no UI dump captured)"
  adb logcat -d
  exit 1
fi

# Retries typing rather than assuming one `input text` call lands: seen during development to
# occasionally miss even right after the search box reports focused="true" (likely the IME
# connection settling a beat later than view focus does) - the dump used to confirm it landed
# doubles as the wait between attempts.
typed=0
for i in $(seq 1 5); do
  adb shell input text "mizu"
  sleep 1
  dump_ui
  if [ -f ui_dump.xml ] && grep -q 'text="mizu"' ui_dump.xml; then
    typed=1
    break
  fi
done

if [ "$typed" != "1" ]; then
  echo "Typing 'mizu' into the search box never landed:"
  cat ui_dump.xml 2>/dev/null || echo "(no UI dump captured)"
  adb logcat -d
  exit 1
fi

adb shell input keyevent 66

found=0
for i in $(seq 1 15); do
  dump_ui
  if [ -f ui_dump.xml ] && grep -q "water" ui_dump.xml; then
    found=1
    break
  fi
  sleep 1
done

if [ "$found" != "1" ]; then
  echo "Searching 'mizu' never rendered the expected 'water' gloss (entry: 水/みず) - basic search functionality may be broken:"
  cat ui_dump.xml 2>/dev/null || echo "(no UI dump captured)"
  adb logcat -d
  exit 1
fi
echo "Release build search returned and rendered a real dictionary entry successfully."
