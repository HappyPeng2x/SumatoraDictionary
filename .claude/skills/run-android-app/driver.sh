#!/usr/bin/env bash
# Driver for building, launching, and driving the Sumatora Android app on a
# headless emulator in this container. See SKILL.md for the full walkthrough.
set -euo pipefail

SDK="${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}"
export PATH="$PATH:$SDK/platform-tools:$SDK/emulator"

AVD="Medium_Phone_API_36.1"
PKG="org.happypeng.sumatora.android.sumatoradictionary"
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
SHOT_DIR="${SCREENSHOT_DIR:-/tmp/shots}"
mkdir -p "$SHOT_DIR"

cmd="${1:-help}"
[ $# -gt 0 ] && shift

case "$cmd" in
  start-emulator)
    # -no-snapshot forces a fresh boot of the OS, but the AVD's userdata
    # partition (installed apps, settings) still persists across restarts -
    # see Gotchas in SKILL.md if `install` fails with a signature mismatch.
    nohup emulator -avd "$AVD" -no-window -no-audio -no-boot-anim \
      -gpu swiftshader_indirect -no-snapshot > /tmp/emulator.log 2>&1 &
    disown
    echo "emulator starting (pid $!) - log at /tmp/emulator.log"
    ;;

  wait-boot)
    timeout 180 adb wait-for-device
    for _ in $(seq 1 60); do
      boot=$(adb shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')
      if [ "$boot" = "1" ]; then
        echo "booted"
        adb devices -l
        exit 0
      fi
      sleep 5
    done
    echo "TIMEOUT waiting for sys.boot_completed" >&2
    exit 1
    ;;

  build)
    (cd "$REPO_ROOT" && ./gradlew :app:assembleDebug -q)
    find "$REPO_ROOT/app/build/outputs/apk/debug" -iname "*.apk"
    ;;

  install)
    apk=$(find "$REPO_ROOT/app/build/outputs/apk/debug" -iname "*.apk" | head -1)
    if [ -z "$apk" ]; then
      echo "no debug APK found under app/build/outputs/apk/debug - run '$0 build' first" >&2
      exit 1
    fi
    # A prior differently-signed install (e.g. a release build tried earlier
    # in the same AVD) makes a plain install fail with
    # INSTALL_FAILED_UPDATE_INCOMPATIBLE. Recover by uninstalling first.
    if ! adb install -r "$apk"; then
      echo "install failed, retrying after uninstall (likely signature mismatch)" >&2
      adb uninstall "$PKG" || true
      adb install -r "$apk"
    fi
    ;;

  launch)
    activity="${1:-.activity.MainActivity}"
    adb shell am start -n "$PKG/$activity"
    ;;

  size)
    # Real device pixel resolution - tap/coordinates below must be in THIS
    # space, not whatever a screenshot viewer downscaled a .png to.
    adb shell wm size
    ;;

  tap)
    if [ $# -lt 2 ]; then echo "usage: $0 tap X Y  (real device pixels - see '$0 size')" >&2; exit 1; fi
    adb shell input tap "$1" "$2"
    ;;

  text)
    # ASCII only - adb's `input text` cannot type Japanese/kana/kanji.
    adb shell input text "$1"
    ;;

  key)
    # Common codes: 4=BACK, 66=ENTER, 3=HOME
    adb shell input keyevent "$1"
    ;;

  screenshot)
    name="${1:-shot-$(date +%s)}"
    adb shell screencap -p /sdcard/_driver_shot.png
    adb pull /sdcard/_driver_shot.png "$SHOT_DIR/$name.png" > /dev/null
    echo "screenshot: $SHOT_DIR/$name.png"
    ;;

  logcat)
    # Recent app logs, filtered to this package's process.
    adb logcat -d --pid="$(adb shell pidof -s "$PKG")" 2>/dev/null | tail -n "${1:-100}"
    ;;

  stop)
    adb emu kill || true
    ;;

  help|*)
    cat <<EOF
usage: driver.sh <command> [args]

  start-emulator          boot the headless AVD ($AVD) in the background
  wait-boot               block until sys.boot_completed=1 (~30s-5min)
  build                   ./gradlew :app:assembleDebug, prints the APK path
  install                 adb install the built debug APK (auto-recovers
                           from a stale differently-signed install)
  launch [activity]       am start (default: .activity.MainActivity)
  size                    print real device resolution (wm size) - use
                           this to compute tap/text coordinates
  tap X Y                 adb shell input tap - X Y are REAL device
                           pixels, NOT a downscaled screenshot preview
  text STRING             adb shell input text (ASCII only, no kana/kanji)
  key KEYCODE             adb shell input keyevent (4=BACK 66=ENTER 3=HOME)
  screenshot [name]       capture + pull to \$SCREENSHOT_DIR/<name>.png
                           (SCREENSHOT_DIR defaults to /tmp/shots)
  logcat [lines]          tail this app's recent logcat (default 100 lines)
  stop                    kill the emulator
EOF
    ;;
esac
