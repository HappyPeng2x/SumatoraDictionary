# Changelog

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
