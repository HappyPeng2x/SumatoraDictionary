---
name: run-android-app
description: Build, install, launch, and drive the Sumatora Android app on a headless emulator in this container. Use when asked to run the app, start the emulator, install a debug/release build, take a screenshot of the UI, tap through a screen, or check logcat.
---

Sumatora is an Android app (package `org.happypeng.sumatora.android.sumatoradictionary`,
launcher `.activity.MainActivity`). There is no window for you to see directly -
drive it with the adb-based CLI driver at
`.claude/skills/run-android-app/driver.sh`, which boots a headless AVD, builds/installs
the app, and exposes `tap`/`text`/`key`/`screenshot`/`logcat` primitives. Each adb
command is a fast, independent shell call - no REPL/tmux wrapping needed, just run
the driver commands in sequence.

All paths below are relative to the repo root.

## Prerequisites

Already provisioned in this container - nothing to install:

- Android SDK at `~/Android/Sdk` (`platform-tools/adb`, `emulator/emulator`). Not on
  `PATH` by default and `ANDROID_HOME`/`ANDROID_SDK_ROOT` are unset - the driver adds
  `platform-tools` and `emulator` to `PATH` itself, so you don't need to.
- An AVD registered as `Medium_Phone_API_36.1` (`emulator -list-avds` to confirm; the
  `.avd` directory is named `Medium_Phone.avd` but the registered AVD id differs -
  always get the real name from `-list-avds`, not the directory name).
- Gradle wrapper + dependency cache already populated (`./gradlew :app:assembleDebug`
  needed no network in this session).

## Run (agent path)

```bash
D=.claude/skills/run-android-app/driver.sh

$D start-emulator     # boots headless, backgrounded
$D wait-boot           # blocks until sys.boot_completed=1 (took ~30s in this session)

$D build               # ./gradlew :app:assembleDebug, prints the APK path
$D install              # adb install -r (auto-recovers from a stale signature mismatch)

$D launch               # am start MainActivity
$D size                  # prints e.g. "Physical size: 1080x2400" - use this to compute
                          # tap coordinates; a screenshot viewer may show the image
                          # downscaled, but tap/screenshot both operate in real pixels
$D key 4                 # dismiss the keyboard (KEYCODE_BACK)
$D screenshot 01-main     # -> /tmp/shots/01-main.png (SCREENSHOT_DIR overrides the dir)

$D tap 196 146            # e.g. open the language picker next to the search box
$D screenshot 02-popup

$D text "mizu"             # ASCII only - cannot type kana/kanji this way
$D key 66                   # submit (KEYCODE_ENTER)
$D screenshot 03-results

$D logcat 100                # tail this app's recent logcat
$D stop                       # kill the emulator when done
```

Then actually open the screenshot files under `/tmp/shots/` and look at them -
a blank or unchanged frame means the tap/text didn't land.

### Commands

| command | what it does |
|---|---|
| `start-emulator` | boot the headless AVD in the background |
| `wait-boot` | block until the device is booted |
| `build` | `./gradlew :app:assembleDebug`, prints the APK path |
| `install` | `adb install -r` the built debug APK |
| `launch [activity]` | `am start` (default `.activity.MainActivity`; other exported activities include `.activity.DictionariesManagementActivity`) |
| `size` | print real device resolution (`wm size`) |
| `tap X Y` | `input tap` - X Y are real device pixels |
| `text STRING` | `input text` (ASCII only) |
| `key KEYCODE` | `input keyevent` (4=BACK, 66=ENTER, 3=HOME) |
| `screenshot [name]` | capture + pull to `$SCREENSHOT_DIR/<name>.png` (default `/tmp/shots`) |
| `logcat [lines]` | tail this app's recent logcat (default 100 lines) |
| `stop` | kill the emulator |

## Run (human path)

Open the project in Android Studio and hit Run - meaningless in a headless
container, included only for completeness.

## Test

```bash
./gradlew :app:compileDebugKotlin :app:compileDebugJavaWithJavac   # typecheck, fast
./gradlew :app:compileDebugAndroidTestJavaWithJavac                 # androidTest compiles
```

Actual instrumentation tests (`connectedDebugAndroidTest`) need the emulator up
(`start-emulator` + `wait-boot` first) and take several minutes - prefer driving the
app directly with the driver for a quick check unless you specifically need the
instrumentation suite.

## Gotchas

- **Tap/screenshot coordinates are real device pixels, not the screenshot preview
  size.** `screencap` captures at full resolution (1080x2400 on this AVD), but an
  image viewer may show it downscaled (e.g. "displayed at 900x2000, multiply by
  1.20"). Forgetting that multiplier sends `tap` to the wrong element - it doesn't
  error, it just silently taps nothing or the wrong row. Run `$D size` once and do
  the math, or read the scale note the image viewer gives you.
- **`emulator -list-avds` name != the `.avd` directory name.** This container's AVD
  directory is `~/.android/avd/Medium_Phone.avd` but the id `emulator` actually wants
  is `Medium_Phone_API_36.1`. Get it from `-list-avds`, never guess from the folder.
- **`-no-snapshot` only skips the boot snapshot - the AVD's userdata partition
  (installed apps, app data, language settings) persists across `stop`/`start-emulator`
  cycles.** A debug build installed after a differently-signed build (e.g. a release
  APK) was ever installed on the same AVD fails with
  `INSTALL_FAILED_UPDATE_INCOMPATIBLE`; `install` already retries with
  `adb uninstall` automatically. Conversely, don't be surprised when app state (e.g.
  a previously downloaded dictionary pack, a previously selected language) is still
  there on a fresh `start-emulator` - it's the same disk image.
- **`input text` cannot type Japanese.** It's ASCII only. For a Japanese dictionary
  app this means you can only exercise romaji/English-side search flows this way, not
  actual kana/kanji queries - fine for smoke-testing UI, not for testing IME-specific
  behavior.
- **The app's first cold start does real work** (decompressing bundled dictionary
  assets into internal storage) - `launch` returns immediately but the UI may take a
  few seconds to become responsive; a short `sleep` between `launch` and the first
  `tap`/`screenshot` avoids racing it.
- **Tapping a popup-menu anchor twice in a row closes it, it doesn't reopen it.**
  E.g. the language picker text next to the search box: tap once to open the popup,
  but if you tap that same spot again (to "retry" after a screenshot that looked
  blank, say) you dismiss it instead - PopupMenu treats a second tap on the anchor as
  an outside touch and closes. Confirmed with `adb shell uiautomator dump` +
  `dumpsys window` that the tap coordinates and focused window were correct both
  times; the popup just toggles. If a screenshot right after a tap looks unchanged,
  don't tap the same spot again to "retry" - screenshot again first (it may just have
  been a rendering race) or explicitly check state with `uiautomator dump` before
  deciding whether to tap.

## Troubleshooting

- **`adb: command not found`**: you're not using the driver - `adb`/`emulator` aren't
  on `PATH` by default in this container. Either use `$D ...` (which sets `PATH`
  itself) or `export PATH="$PATH:$HOME/Android/Sdk/platform-tools:$HOME/Android/Sdk/emulator"`
  first.
- **`INSTALL_FAILED_UPDATE_INCOMPATIBLE`**: signature mismatch against whatever is
  already installed on this AVD (see Gotchas). `$D install` already handles this by
  uninstalling first; if calling `adb install` directly, do the same.
  `adb uninstall org.happypeng.sumatora.android.sumatoradictionary` then reinstall.
- **`wait-boot` times out**: check `/tmp/emulator.log` for a crash (missing KVM/
  virtualization support would show up here); this container had working
  virtualization and booted in ~30s with no extra flags.
