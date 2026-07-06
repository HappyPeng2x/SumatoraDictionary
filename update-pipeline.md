# Dictionary Update Pipeline (design notes)

Not yet implemented. Written up after the schema v2 migration so it can be picked up later.

**Order of work:** Phase 0b (suffix/names as GitHub Release downloads, per the schema v2 migration
plan) → this (generalizes Phase 0b to *every* pack, plus lets already-bundled packs like
core/gloss update without an APK reinstall) → desktop parity.

## Motivation

JMdict/JMnedict/KANJIDIC2/Tatoeba get updated upstream over time, and SumatoraIndex rebuilds
packs from them. Right now the only way to get a newer dictionary is to ship a new APK with new
bundled assets. The goal: let the app check for and install a newer pack on its own, for every
pack type (not just the optional suffix/names packs Phase 0b already needs download support for).

## What's already there, dormant

Found while investigating this - the app already has half of this built and never wired up:

- `PersistentDatabaseInitialization.java` already writes a `PersistentSetting` called
  `REPOSITORY_URL` (`Settings.REPOSITORY_URL`), defaulting to
  `https://sumatora.happypeng.org/dictionaries/v4/dictionaries.xml` - but nothing ever reads it
  back. The app only ever parses the *bundled* `dictionaries.xml` asset.
- `RemoteDictionaryObject.download()` wraps `android.app.DownloadManager` but has no caller.
- `InstalledDictionary.isSuperiorVersion()` already compares `(version, date)` against another
  `InstalledDictionary` - this is exactly the comparison a remote-update check needs, just
  currently only used when reconciling against the bundled asset manifest.
- `RemoteDictionaryObjectDao`/`AssetDictionaryObjectDao`/`LocalDictionaryObjectDao` Room DAOs
  already exist for tracking these three dictionary-source kinds.

So this is less "design something new" and more "finish connecting what's there."

## Decisions made

- **Hosting: GitHub Releases, unified.** Every pack (core, gloss_{lang}, pitch, kanji,
  examples_{lang}, suffix, names) becomes a versioned Release asset - most naturally on
  **SumatoraIndex** (the repo that produces them), since that's where a new release gets cut
  whenever the pipeline rebuilds. Not `sumatora.happypeng.org` (the existing dormant default) -
  moving everything to GitHub keeps one hosting story consistent with the Phase 0b decision.
- **Update trigger: automatic background check via WorkManager**, with a manual "Check Now"
  button in Settings kept regardless (auto-check shouldn't remove the ability to force it).

## Manifest

GitHub release-asset URLs embed the release tag (`.../releases/download/{tag}/{filename}`), so a
manifest telling the app "here's the current version" needs a URL that does *not* change every
release. Keep `dictionaries.xml` as a plain file served via
`raw.githubusercontent.com/.../main/dictionaries.xml` (always reflects the latest commit on the
default branch), whose entries point *at* the versioned release-asset URLs. Cutting a new release
becomes: publish pack assets to a new release, update this one file, commit. That's a small,
scriptable step in SumatoraIndex's release process - worth writing a helper script there, but it's
a process detail, not an app concern.

Add a **SHA-256 field per `<dictionary>` entry** in the manifest schema. Nothing today validates a
downloaded file before `ATTACH`ing it as SQLite; a truncated/corrupted download would otherwise
risk a crash rather than a clean "download failed, retry."

## Download + install flow

1. New dependency: `androidx.work:work-runtime` (not currently in the project).
2. A `PeriodicWorkRequest` (weekly is a reasonable default - JMdict doesn't need daily
   granularity), constrained to Wi-Fi + not-low-battery given pack sizes (core alone is
   ~94M compressed), enqueued via `enqueueUniquePeriodicWork` so re-scheduling on app start is
   idempotent.
3. The worker: fetch the manifest -> compare each `(type, lang, version, date)` against
   `InstalledDictionary` using the existing `isSuperiorVersion()` check -> for anything newer,
   persist a `RemoteDictionaryObject` row and call `.download(downloadManager, ...)`.
4. A **manifest-registered** `BroadcastReceiver` for `DownloadManager.ACTION_DOWNLOAD_COMPLETE`
   (not just runtime-registered, so it still fires if the app isn't open) verifies the checksum,
   decompresses to a **new, version-suffixed filename**, updates `InstalledDictionary`/marks the
   new file "pending", and posts a notification ("Dictionary update ready - restart Sumatora to
   apply").
5. **Never hot-swap a live-attached SQLite file.** The dictionary files are `ATTACH`ed to Room's
   live connection; overwriting one in place risks corruption or a crash mid-query. The actual
   swap (detach old alias if attached / delete old file / promote the new file's
   `InstalledDictionary` row to active) happens in the existing `initializeDatabase()`
   reconciliation step on the next cold start, before anything gets attached for that session.
   This means "restart the app to get the update" - a normal, well-understood UX for content
   updates, not a limitation worth engineering around for v1.

## Settings UI

Extend the existing per-pack list (`DictionaryObjectViewHolder` already renders one row per
dictionary object) with current version/date (via each pack's `BuildMetadata`, same mechanism
`DictionaryControlInfo` already uses for the core pack), a manual "Check Now" action, and download
progress.

## Explicitly out of scope for v1

- **Binary diffing between versions.** Full-file re-download per update is simple and safe;
  diffing (bsdiff/courgette-style) would cut bandwidth for routine word-count-only updates but
  needs SumatoraIndex to also publish diff artifacts and the app to bundle a patch-apply library.
  Worth reconsidering only if update frequency/bandwidth complaints make it necessary - mitigate
  first by having SumatoraIndex release on a deliberately spaced cadence (e.g. monthly) rather
  than every upstream JMdict update.
- **SumatoraIndex's release automation itself** (cutting releases, updating the manifest file,
  computing checksums) is a prerequisite for this feature but is a SumatoraIndex/process concern,
  not app code.
