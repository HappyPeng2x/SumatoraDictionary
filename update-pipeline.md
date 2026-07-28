# Dictionary Update Pipeline

**Status: implemented**, as of `v0.5.0-beta4`. Originally written up as a not-yet-built design
after the schema v2 migration; superseded piece by piece over several releases without this doc
being kept in sync, so treat everything below as a description of what actually ships, not a
plan. See `~/Code/SumatoraIndex`'s `release-pipeline.md` for the publishing side (how
`dictionaries.xml` and its release assets get produced/updated) - this doc only covers the
app-side consumption of that manifest.

## Motivation

JMdict/JMnedict/KANJIDIC2/Tatoeba get updated upstream over time, and SumatoraIndex rebuilds
packs from them. The app checks for and installs a newer pack on its own, for every pack type
(not just the optional suffix/names packs that needed download support first).

## Hosting: SumatoraIndex

Every pack (core, gloss_{lang}, pitch, kanji, examples_{lang}, suffix, names, plus web-search/
web-gloss packs used by the PWA client) is a versioned Release asset on
**[SumatoraIndex](https://github.com/HappyPeng2x/SumatoraIndex)**, the repo that produces them -
not `SumatoraDictionary`, so every independent client (Android, desktop, PWA) fetches from one
shared place instead of coupling to whichever client repo happened to host things first.
`R.string.dictionaries_url` (`app/src/main/res/values/strings.xml`) points at
`https://raw.githubusercontent.com/HappyPeng2x/SumatoraIndex/master/dictionaries.xml` - a stable
URL that always reflects the latest commit on SumatoraIndex's default branch, independent of
whatever release tag is current. That URL is also the default written into the `REPOSITORY_URL`
`PersistentSetting` on first run (`PersistentDatabaseInitialization.java`); it's user-editable
from Settings, which is what a self-hosted mirror or a testing manifest would override.

## Manifest

SumatoraIndex's live `dictionaries.xml` has more fields than the app actually reads.
`BaseDictionaryObject.fromXML()` (`db/tools/BaseDictionaryObject.java`) only parses:

- Repository-level, once per manifest: `version`, `date`. **The whole manifest shares one
  version/date pair, not one per pack** - every `<dictionary>` entry inherits the `<repository>`
  element's `version`/`date` regardless of when that individual pack last actually changed.
- Per `<dictionary>` element: `uri`, `description`, `type`, `lang` (defaults to `""`), `sha256`
  (defaults to `""`, which skips checksum verification for that entry - see below).

`plain_uri`, `plain_sha256`, `changelog`, and `changelog_sha256` - all present in SumatoraIndex's
current manifest - are silently ignored by the parser; there's no app code path that fetches an
uncompressed pack or surfaces a changelog. Adding either is a matter of extending
`BaseDictionaryObject.fromXML()` and whatever consumes the result, not a manifest-schema change.

## How it works today

**Startup wiring.** `DictionaryApplication.onCreate()` calls
`DictionaryUpdateWorker.enqueuePeriodic(this)`, which schedules a `PeriodicWorkRequest` (7 days,
`NetworkType.UNMETERED` + `setRequiresBatteryNotLow(true)`) via
`enqueueUniquePeriodicWork(..., ExistingPeriodicWorkPolicy.KEEP, ...)` - `KEEP` means this
schedule survives repeated `onCreate()` calls without resetting. WorkManager is initialized with
a Hilt-aware `WorkerFactory` (the default `androidx.startup` WorkManager initializer is disabled
in `AndroidManifest.xml` in favor of this manual one).

**Manual "Check Now".** Lives in `DictionariesManagementActivity.kt` (not Settings), as a button
that first probes real socket capability (see "Network-blocked detection" below), then calls
`DictionaryUpdateWorker.enqueueNow(this)` - a separate `OneTimeWorkRequest`
(`NetworkType.CONNECTED`, looser than the periodic job's `UNMETERED`) run via
`enqueueUniqueWork(..., ExistingWorkPolicy.REPLACE, ...)`. Both the periodic and manual requests
run the same `DictionaryUpdateWorker.doWork()`. The Activity observes
`DictionaryUpdateWorker.manualCheckStatus()` (a `LiveData<List<WorkInfo>>`) to drive the
button's spinner.

**Check flow.** `doWork()` reads `Settings.REPOSITORY_URL` (falling back to
`R.string.dictionaries_url`) and delegates to `DictionaryUpdateChecker.checkAndEnqueue()`:

1. `RemoteManifestFetcher.fetch()` GETs the manifest via plain `HttpURLConnection` (15s
   connect/read timeout), parsed via `BaseDictionaryObject.fromXML` into `RemoteDictionaryObject`
   rows. Any failure aborts the check for this cycle - no retry/backoff, it just waits for the
   next scheduled run.
2. The fetched manifest overwrites the `CachedManifestEntry` table - this cache is what
   `OptionalDictionaryCatalog.resolve()` reads later to version-match not-yet-installed optional
   packs against whatever core is currently installed.
3. For each remote entry, looks up the matching `InstalledDictionary` by `(type, lang)` and
   **skips anything not already installed** - the update checker never auto-installs a pack the
   user hasn't opted into; that's a separate, explicit "install this optional pack" flow through
   `OptionalDictionaryCatalog`/`DictionariesManagementActivity`.
4. Compares via `isSuperiorVersion()` (`version > other.version || (version >= other.version &&
   date > other.date)`), skips if a pending update at least as new is already queued, and skips
   if a download for that `(type, lang)` is already in flight.
5. Otherwise calls `RemoteDictionaryObject.download()` and persists the row, inside a try/catch -
   one pack's `download()` throwing doesn't stop the rest of the manifest from being checked.

**Download.** `RemoteDictionaryObject.download()` uses `android.app.DownloadManager` (not OkHttp
or a plain HTTP client), destination `<externalFilesDir>/downloads/<type>-<lang>.db.gz`,
`setAllowedOverRoaming(false)`. A `downloadId < 0` result throws `IllegalStateException` instead
of silently persisting a broken row (fixed alongside the network-blocked detection below - this
used to be a silent failure mode).

**Network-blocked detection.** GrapheneOS's per-app Network permission toggle doesn't flow
through `checkSelfPermission()` - it drops the process from the `inet` supplementary group at
launch instead, which made `DownloadManager.enqueue()` (and a stuck `WorkManager` job whose
`NetworkType.CONNECTED` constraint reads as satisfied system-wide even though this app can't
actually reach it) fail with no usable signal. `DictionariesManagementActivity.kt` has a private
`hasSocketCapability()` check (opens/closes a throwaway `DatagramSocket`) used before both a
manual pack download and a manual "Check Now" tap; on failure it shows a blocking dialog that
deep-links to the app's system Network setting.

**Known gap:** this probe only guards the two Activity-initiated paths above - `checkAndEnqueue()`
(shared by the periodic worker and the manual "Check Now" worker) has no equivalent pre-check
before `RemoteDictionaryObject.download()`, so on a device with Network access blocked, a
`DownloadManager.enqueue()` failure still throws `IllegalStateException` there. That per-entry
call *is* now wrapped in try/catch (see below), so one pack failing to enqueue no longer aborts
every other pack in the same manifest - it just skips that one pack and keeps going - but there's
still no proactive check, so a fully network-blocked device silently fails every entry one at a
time instead of degrading with a single clear signal up front.

**Checksum verification.** `DictionaryDownloadCompleteReceiver.verifyChecksum()` computes the
downloaded file's SHA-256 and compares it (case-insensitively) against the manifest's `sha256`
for that entry. If `sha256` was empty in the manifest, verification is skipped and the download
is trusted as-is.

**Install / download-complete handling.** `DictionaryDownloadCompleteReceiver` is
manifest-registered (not runtime-registered, so it still fires if the app process was killed
mid-download) for `DownloadManager.ACTION_DOWNLOAD_COMPLETE`, and `exported="true"` since
`DownloadManager` delivers that broadcast at the system uid. After checksum verification:

- A **brand-new** pack (no existing `InstalledDictionary` row - e.g. the first time an optional
  language is installed) decompresses immediately, inserts into `InstalledDictionary`, and
  attaches it live.
- An **update to an already-installed** pack decompresses under a version-suffixed filename and
  stashes it as `pendingFile`/`pendingVersion`/`pendingDate` on the existing row, deferring the
  actual swap - see below.
- On failure, the `RemoteDictionaryObject` row is kept (not deleted) with `downloadId = -1,
  failed = true`, so the Manage Dictionaries screen can offer "Download failed · tap to retry".
- A notification is posted on both success and failure (`dictionary_updates` channel):
  "Dictionary update ready - restart Sumatora to apply" for a pending update, a plain completion
  notification for a fresh optional-pack install, or a failure notification.

**Never hot-swap a live-attached SQLite file.** `PersistentDatabaseInitialization
.initializeDatabase()` calls `promotePendingUpdate()` for every `InstalledDictionary` with
`hasPendingUpdate()` (i.e. `pendingFile != null`) *before* anything gets attached for that
session - deletes the old file and promotes the pending one to `file`/`version`/`date`. This
means "restart the app to get the update," which is why the notification says exactly that.

**Catch-up check on APK upgrade.** `PersistentDatabaseComponent.checkAppUpgrade()` runs after
`initializeDatabase()` on every cold start: reads the `lastSeenVersionCode` `PersistentSetting`,
compares it against `BuildConfig.VERSION_CODE`, and if they differ (and this isn't a first
install, where `lastSeenVersionCode` is still unset) calls `DictionaryUpdateWorker.enqueueNow()`
- the same one-time request the manual "Check Now" button uses. This closes the gap where bundled
packs (core/kanji/pitch/gloss_eng/tatoeba_eng) jump to the new version immediately on every APK
upgrade (they're just re-extracted from assets), but *optional* downloaded packs would otherwise
sit stale until the 7-day periodic worker or a manual check happened to run.

**Settings UI.** `DictionariesManagementActivity.kt` + `DictionaryManagementRenderer.kt`: one
grouped list (by type, with a header when more than one language variant of a type is present),
each row showing version/date or a "Downloading…"/"Download failed · tap to retry" caption, a
per-row trailing control (spinner/retry/delete/install depending on state), a combined
"Up to date" / "Update ready · restart to apply" status pill at the top, and the "Check for
updates" button. The whole screen is reactive - a `MediatorLiveData` over the installed/remote/
cached-manifest Room DAOs - so it updates itself if a background download completes while some
other screen is in the foreground.

## Optional-pack fallback URLs (`OptionalDictionaryCatalog`)

Before the first successful manifest fetch (freshest on first app launch), suffix/names/optional
gloss+examples packs resolve through a fallback URL built as
`https://github.com/HappyPeng2x/SumatoraIndex/releases/download/dictionaries-v{N}/...`, where `N`
is the **currently installed core pack's version**, not a hardcoded pin - this self-corrects as
the bundled core version moves forward across app releases instead of drifting stale (it used to
be pinned to a fixed `dictionaries-v8` URL on the old `SumatoraDictionary` repo; fixed in commit
`2495677`). Once a manifest fetch succeeds, `OptionalDictionaryCatalog.resolve()` prefers the
freshly cached manifest entries instead, when their `(version, date)` match the installed core.

## Explicitly out of scope

- **Binary diffing between versions.** Full-file re-download per update is simple and safe;
  diffing (bsdiff/courgette-style) would cut bandwidth for routine word-count-only updates but
  needs SumatoraIndex to also publish diff artifacts and the app to bundle a patch-apply library.
  No code for this exists anywhere in the app. Worth reconsidering only if update
  frequency/bandwidth complaints make it necessary - mitigated today by SumatoraIndex releasing
  on a deliberately spaced cadence (monthly) rather than every upstream JMdict update.
- **Per-pack changelog display.** The manifest already carries `changelog`/`changelog_sha256`
  (see "Manifest" above); nothing in the app fetches or shows it yet.
