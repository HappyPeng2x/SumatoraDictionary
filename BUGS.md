# Known Bugs

## EntryDetailScreenshotTest hang (resolved)

**Status:** resolved as of 2026-07-19, re-enabled (`@Ignore` removed). The render() hang reported
on 2026-07-12 no longer reproduces - most likely fixed as a side effect of that day's
search-result performance work, which cut `fetchEntryDetail`'s per-entry query count roughly in
half and fixed several missing-index full-table-scan query plans elsewhere in the same DB
connection. The one remaining failure once render() was confirmed fast and correct was in the
test itself, not the app: it anchored its second screenshot's scroll on
`entry_detail_examples_header`, which is only visible for entries with entry-level "fallback"
examples (see `PersistentDatabaseComponent.fetchEntryDetail`'s `fallbackExamples`) - an entry like
掛ける, where every example now resolves to a specific sense and renders inline
(`EntryDetailBottomSheet.buildSenses`), legitimately leaves that section `GONE`. Fixed by swiping
the scroll container directly instead of anchoring on a section that isn't always present.

## RecyclerView crash: "Cannot call this method while RecyclerView is computing a layout or scrolling"

**Status:** fixed in `BaseFragment.java` (2026-07-11) - `submitList()` is now deferred via
`RecyclerView.post()` when `isComputingLayout()` is true, per the smaller-patch direction below.
The underlying legacy Paging 2 -> Paging 3 migration described below is still worth doing at some
point but is no longer blocking. Section kept for context on the original repro/diagnosis.

**Symptom:** the app process crashes with:

```
java.lang.IllegalStateException: Cannot call this method while RecyclerView is computing a
layout or scrolling ...RecyclerView{...} app:id/dictionary_bookmark_fragment_recyclerview,
adapter:...DictionaryPagedListAdapter, layout:...LinearLayoutManager
	at androidx.recyclerview.widget.RecyclerView.assertNotInLayoutOrScroll(RecyclerView.java:3051)
	at androidx.recyclerview.widget.RecyclerView$RecyclerViewDataObserver.onItemRangeInserted(...)
	at androidx.recyclerview.widget.RecyclerView$AdapterDataObservable.notifyItemRangeInserted(...)
	at androidx.recyclerview.widget.RecyclerView$Adapter.notifyItemRangeInserted(...)
	at androidx.recyclerview.widget.AdapterListUpdateCallback.onInserted(...)
	at androidx.recyclerview.widget.BatchingListUpdateCallback.dispatchLastEvent(...)
	at androidx.recyclerview.widget.DiffUtil$DiffResult.dispatchUpdatesTo(...)
	at androidx.paging.PagedStorageDiffHelper.dispatchDiff(...)
	at androidx.paging.AsyncPagedListDiffer.latchPagedList(...)
	at androidx.paging.AsyncPagedListDiffer$2$1.run(...)
	at android.os.Handler.handleCallback(...)
```

**How to reproduce:** on an emulator/device running API 36 (`Medium_Phone_API_36.1` AVD used
here), type a search term into the search box, press the IME action button, then close the soft
keyboard shortly after. Timing-sensitive - reproduced consistently in this environment, may not
reproduce on all devices/API levels.

**Likely cause:** `BaseFragment.java` subscribes `queryFragmentModel.getPagedListObservable()` and
calls `pagedListAdapter.submitList(l)` directly (`DictionaryPagedListAdapter` extends the legacy
`androidx.paging.PagedListAdapter`). `submitList` schedules an async `DiffUtil` computation
(`AsyncPagedListDiffer`) that later dispatches `notifyItemRangeInserted` on the main thread's
message queue. If that dispatch lands while `RecyclerView` is mid-layout (e.g. during the
resize/relayout triggered by the IME closing), `RecyclerView` throws rather than deferring the
update.

**Suggested fix directions (not investigated further - out of scope for the schema v2 migration):**
- Migrate `DictionaryPagedListAdapter`/`BaseQueryFragmentModel` from the legacy `androidx.paging`
  (Paging 2) `PagedListAdapter`/`LivePagedListBuilder` APIs to Paging 3
  (`androidx.paging.PagingDataAdapter` + `Pager`), which handles this kind of diff/layout race
  internally.
- Or, as a smaller patch: guard the `submitList` call so it's deferred (e.g.
  `recyclerView.post { pagedListAdapter.submitList(l) }`) when the RecyclerView is currently
  computing a layout or scrolling (`recyclerView.isComputingLayout`).

**Where found:** while manually verifying the schema v2 migration (see git history around
2026-07-06) using an instrumented diagnostic harness
(`app/src/androidTest/.../diagnostic/SearchListScreenshotTest.java`) on a real emulator.
