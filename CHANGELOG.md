# Changelog

## [Unreleased]

### Recent updates

- New "Recent updates" screen (Settings → Recent updates) lists what changed in each weekly
  dictionary release - added/modified counts per dictionary and per language, newest first.
  Backed by a `changelog.json` SumatoraIndex now publishes with every release (see
  changelog-pipeline.md); `DictionaryUpdateChecker` fetches and sha256-verifies it alongside the
  existing manifest check (same 7-day background schedule, or manual "Check Now"), independent of
  whether the user has any packs installed.

## [0.5.0-beta3] - 2026-07-22

### Language packs

- Non-English gloss/example dictionaries (German, Russian, Spanish, Dutch, Hungarian, Swedish,
  French, Slovenian) are no longer bundled in the app - only English still ships in the APK
  (shrinking it by ~52MB), and every other language is now a downloadable pack through the
  existing suffix/names optional-pack machinery (`OptionalDictionaryCatalog`). Install them from
  "Manage dictionaries".
- The search screen's language picker now updates immediately when a pack is installed from
  "Manage dictionaries" (a separate Activity), instead of requiring the search screen to be
  recreated first - `BaseQueryFragmentModel.installedDictionaries` is now backed by a Room
  Flowable instead of a one-shot query built once in `onCreateOptionsMenu`. The picker also gained
  a "More languages…" entry, shown whenever the catalog has a gloss pack that isn't installed yet,
  that jumps straight to "Manage dictionaries".
- Fixed the backup-language fallback being judged for a whole entry at once instead of per sense:
  a partially-translated entry either showed every sense in the main language or every sense in
  the backup language - and the search-result card was grayed out or not as a single unit - even
  when only some of its senses actually had a main-language translation. Each sense now resolves
  its own gloss independently, main language first and backup only for the senses that
  specifically lack it, both in the search-result list (`DictionarySearchQueryTool`'s precomputed
  render payload, merged per sense in `PersistentDatabaseComponent.mergeSenseGroups`) and in the
  entry detail sheet (`PersistentDatabaseComponent.fetchEntryDetail`, which previously had no
  fallback indicator at all). Senses that fell back are now dimmed individually instead of
  graying the whole search-result card - most noticeable with a language like French whose JMdict
  translation coverage (~6% of senses) is far sparser than English's (~88%), where most results
  are a genuine mix of translated and backup-language senses within the same entry.

### App icon

- Shrunk the adaptive icon artwork (~12%, to a 60dp diameter) so it clears the ~66dp safe-circle
  that many launcher mask shapes crop to - the previous artwork sat at a 68.4dp diameter against
  the 108dp canvas with almost no margin, reportedly clipped on some devices (e.g. Pixel 8 Pro).

### Dictionary downloads

- Fixed a failed optional-pack download (bad network, checksum mismatch, storage full) silently
  reverting to "not installed" with no explanation - `DictionaryDownloadCompleteReceiver` used to
  delete the in-progress row on failure, the same as on success, so the row just quietly dropped
  off the "Downloading…" state. It now persists a "Download failed · tap to retry" state instead
  (`RemoteDictionaryObject.failed`, schema v12), with a matching retry affordance in
  "Manage dictionaries", and posts a notification on both success and failure for fresh installs
  (previously only a background update to an already-installed pack posted one). The app now also
  requests the notification permission from "Manage dictionaries" so those notifications aren't
  silently dropped on Android 13+.
- Note: `DownloadManager` itself still has no timeout and can sit paused indefinitely with no
  network at all, showing "Downloading…" the whole time - a genuinely stuck download (as opposed
  to one that fails outright) still isn't distinguishable from a slow one. Not addressed here.

### Copy/paste

- Fixed copy/paste not really working from entries: no `TextView` in the app ever enabled text
  selection, and there was no clipboard code anywhere, so long-press never did anything.
  Entry detail (headword, glosses, examples, forms table, notes/xrefs/language-source boxes) is
  now selectable via native long-press, coexisting with the existing tap-to-open-kanji-detail and
  tap-to-follow-xref spans. Search-result rows use a long-press-to-copy affordance instead of
  native selection (copies headword + reading + glosses to the clipboard) since a `RecyclerView`
  row can be rebound out from under an in-progress selection, and the row itself is already a
  tap target for opening the entry.

### Theming

- Fixed the Tags screen's hamburger menu icon being invisible in dark mode ("black on black") -
  `TagsFragment` set it from the raw drawable resource, which has a hardcoded black fill, instead
  of tinting it to the theme-aware `colorOnSurfaceVariant` the way every sibling screen
  (Home/Bookmarks via `BaseFragment`, Settings) already does. Untinted, it rendered fine against
  light mode's near-white toolbar but disappeared against dark mode's near-black one.

## [0.5.0-beta2] - 2026-07-20

### Search and bookmarks

- Fixed entries staying permanently blank, and sluggish scrolling, when fast-scrolling through a
  very large result set (e.g. a bookmark list of several thousand entries): dozens of rows
  binding at once each queued their own live per-row database fetch, and those fetches piled up
  contending over the single database connection. Every search tier (bookmark/tag listing,
  exact/prefix/substring, gloss reverse-search, deinflection, proper nouns, and the bookmark
  import preview) now assembles a row's full display payload as part of the same query that finds
  it, so scrolling never triggers a separate per-row fetch. This replaces the per-row live fetch
  and its LRU cache introduced in 0.5.0-beta1.
- Fixed search getting progressively slower the further down the results you scrolled: the fix
  above computed every matched row's full display payload (headword, furigana, senses, tags)
  eagerly for an *entire* search tier the moment it ran, so a broad tier (a short/common query, or
  a large bookmark list) paid that cost for thousands of rows at once instead of just the ~50 about
  to be shown. The real root cause turned out to be a missing database index
  (`Sense.sense_group_id`, now shipped in dictionaries-v12 - bundled core.db updated accordingly),
  which made per-row rendering expensive in the first place. With that fixed, rendering is now
  bounded to roughly a page at a time (`DictionarySearchQueryTool.backfillRenderJson`), and finding
  matches (cheap) is fully decoupled from rendering them (bounded).
  Measured on an emulator against the real dictionary, for a one-character kana query matching
  13,142 entries: eager whole-tier rendering (the pre-fix approach, reconstructed for comparison)
  took **4638ms**; bounded rendering of the same tier's first page took **34ms** - about **135x**
  faster for reaching a broad tier, with every further page through it costing ~34ms instead of
  being free-but-already-paid-for upfront. See `SearchTierPerformanceTest`, which keeps these
  numbers honest going forward.

## [0.5.0-beta1] - 2026-07-19

The biggest release since the initial JMdict-based app: the dictionary engine moved to
[SumatoraIndex](https://github.com/HappyPeng2x/SumatoraIndex)'s normalized schema, entry
display was redesigned around it, and dictionary packs are no longer static APK assets.

### Dictionary engine and search

- Migrated to SumatoraIndex's normalized schema (v2) - replaces the old JMdict-derived tables.
- Exact/prefix/substring search matching is now deterministic per entry.
- Search results show the specific matched form (not just the entry's default headword), with
  furigana, and pair a kana-only match with its kanji spelling when one exists.
- Entries now show a forms table for multi-reading words, per-sense example sentences,
  sense-restriction labels, and every valid reading (not just the one that matched).
- Fixed blank senses when an entry mixes gloss languages per sense.
- Fixed slow, blank-then-appear rendering when scrolling through many search results: three
  missing indices caused full-table-scan query plans, and a GLOB pattern built in SQL (instead of
  bound as a plain parameter) defeated SQLite's prefix-seek optimization on nearly every
  prefix/substring search. Search-result rendering also now batches its per-row queries into a
  handful of consolidated round trips instead of one query per gloss/furigana/form, and caches
  computed row summaries.

### Redesigned entry display

- Entry cards and tags redesigned to match Jitendex's layout: rounded tag pills with
  human-readable labels, pipe-separated glosses, a detail bottom sheet.

### Dictionary distribution

- The core dictionary and the language packs it needs can now update themselves in the
  background instead of only ever coming from a fresh APK install.
- Two optional packs - substring search and proper names (JMnedict) - are downloadable on demand
  instead of being bundled, keeping the base install smaller.
- The Manage Dictionaries screen (Settings) was redesigned: every pack (bundled or optional) now
  shows in one grouped list with a single combined "up to date"/"update ready" status instead of
  a status per pack, and downloads in progress are visible instead of silently disappearing from
  the list until they finish.
- Fixed dictionary update downloads silently failing to install.
- Fixed the substring-search/proper-names packs offering a stale, wrong-repo download URL
  (pinned to an old pre-SumatoraIndex release) whenever they're installed before the first
  background manifest check completes, which could pair them with a bundled core dictionary from
  an incompatible build.

### Other

- Restored the "Display log" button in Settings (broken since an earlier navigation rewrite).
- Fixed a guard against exceeding SQLite's limit on simultaneously attached databases.
- Removed the legacy JavaFX desktop module - desktop development continues in a separate
  Rust/GTK4 rewrite.

## [0.4.7.6] and earlier

See git history.
