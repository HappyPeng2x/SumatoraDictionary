/* Sumatora Dictionary
        Copyright (C) 2026 Nicolas Centa

        This program is free software: you can redistribute it and/or modify
        it under the terms of the GNU General Public License as published by
        the Free Software Foundation, either version 3 of the License, or
        (at your option) any later version.

        This program is distributed in the hope that it will be useful,
        but WITHOUT ANY WARRANTY; without even the implied warranty of
        MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
        GNU General Public License for more details.

        You should have received a copy of the GNU General Public License
        along with this program.  If not, see <http://www.gnu.org/licenses/>.*/

package org.happypeng.sumatora.android.sumatoradictionary.db.tools;

import android.database.Cursor;
import android.util.Log;

import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteStatement;
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner;

import org.happypeng.sumatora.android.sumatoradictionary.component.PersistentDatabaseComponent;
import org.happypeng.sumatora.android.sumatoradictionary.db.InstalledDictionary;
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentDatabase;
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentLanguageSettings;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.testing.HiltAndroidRule;
import dagger.hilt.android.testing.HiltAndroidTest;

import static org.junit.Assert.assertTrue;

// Regression guard + living record for the "scrolling further gets slower" fix: rendering cost
// (render_json - see DictionarySearchQueryTool.backfillRenderJson) must stay bounded to roughly a
// page's worth of rows, never proportional to how many rows a search tier actually matched. Before
// this fix, every tier computed render_json for its *entire* match set eagerly (measured ~90us/row
// against the real dictionary, ~500-1000ms+ for a single broad one-character kana query), and the
// real root cause of the live-fetch jank that first motivated eager precomputation was a missing
// index (Sense.sense_group_id, shipped in dictionaries-v12) rather than the query architecture -
// see the SumatoraIndex commit "Add three missing indices found auditing the app's search/render
// query plans" and this repo's history around the same date.
//
// Read timings with: adb logcat -s SearchPerf
// Run just this class: adb shell am instrument -w -e class \
//   org.happypeng.sumatora.android.sumatoradictionary.db.tools.SearchTierPerformanceTest \
//   org.happypeng.sumatora.android.sumatoradictionary.test/androidx.test.runner.AndroidJUnitRunner
@HiltAndroidTest
@RunWith(AndroidJUnit4ClassRunner.class)
public class SearchTierPerformanceTest {
    private static final String TAG = "SearchPerf";

    // "ka" -> katakana "カ" - a common one-character prefix that matches thousands of SearchTerm
    // rows in the real dictionary (measured 5,000-12,000 depending on script/priority tier).
    private static final String BROAD_TERM = "ka";
    // A single specific entry - exact-match tier only, a handful of rows.
    private static final String NARROW_TERM = "食べる"; // 食べる
    // Mirrors BaseQueryFragmentModel.RENDER_BACKFILL_LIMIT - keep these in sync.
    private static final int BACKFILL_LIMIT = 200;

    @org.junit.Rule
    public HiltAndroidRule hiltRule = new HiltAndroidRule(this);

    @Inject
    PersistentDatabaseComponent dbComponent;

    private PersistentLanguageSettings languageSettings;
    private int refCounter = 8000;

    @Before
    public void setUp() {
        hiltRule.inject();
        Assume.assumeFalse(
                "skipped: no dictionary installed on this device",
                dbComponent.getDatabase().installedDictionaryDao().getAll().isEmpty());

        PersistentLanguageSettings settings = dbComponent.getDatabase()
                .persistentLanguageSettingsDao().getLanguageSettingsDirect(0);
        languageSettings = settings != null ? settings : defaultSettings();
    }

    private static PersistentLanguageSettings defaultSettings() {
        PersistentLanguageSettings s = new PersistentLanguageSettings();
        s.lang = PersistentLanguageSettings.LANG_DEFAULT;
        s.backupLang = null;
        return s;
    }

    private interface TimedAction {
        void run();
    }

    private static long timeMs(TimedAction action) {
        long t0 = System.nanoTime();
        action.run();
        return (System.nanoTime() - t0) / 1_000_000;
    }

    // Runs every tier for `term` against a fresh ref, mirroring BaseQueryFragmentModel's
    // Op.ExecuteSearch (match-finding only - render_json stays NULL until backfillRenderJson runs).
    private void runMatchOnly(DictionarySearchQueryTool tool, String term) {
        final PersistentDatabase database = dbComponent.getDatabase();
        int max = tool.getCount(term);
        database.runInTransaction(() -> {
            for (int i = 0; i < max; i++) {
                tool.execute(term, i, false, false);
            }
            tool.executeProperNouns(term);
            tool.executeDeinflection(term);
        });
    }

    private long countRows(int ref) {
        SupportSQLiteDatabase readable = dbComponent.getDatabase().getOpenHelper().getReadableDatabase();
        SupportSQLiteStatement stmt = readable.compileStatement(
                "SELECT COUNT(*) FROM DictionarySearchElement WHERE ref = ?");
        try {
            stmt.bindLong(1, ref);
            return stmt.simpleQueryForLong();
        } finally {
            try {
                stmt.close();
            } catch (java.io.IOException e) {
                // ignored - test-only helper statement
            }
        }
    }

    // The core invariant this whole fix rests on: bounding backfill to BACKFILL_LIMIT rows means a
    // tier matching ~100x more rows must not cost anywhere near 100x more to backfill. A ratio bound
    // (not an absolute one) so this stays meaningful across wildly different CI/device hardware,
    // while still catching the kind of blowup the pre-fix eager architecture had (see
    // testHistoricalEagerRenderWasMuchSlowerForBroadTiers for that number, measured fresh).
    @Test
    public void testBackfillCostIsBoundedByPageNotTierBreadth() {
        DictionarySearchQueryTool narrowTool = new DictionarySearchQueryTool(dbComponent, refCounter++, languageSettings);
        long narrowBackfillMs;
        long narrowRows;
        try {
            runMatchOnly(narrowTool, NARROW_TERM);
            narrowRows = countRows(narrowTool.getKey());
            narrowBackfillMs = timeMs(() -> narrowTool.backfillRenderJson(BACKFILL_LIMIT));
        } finally {
            narrowTool.delete();
            narrowTool.close();
        }

        DictionarySearchQueryTool broadTool = new DictionarySearchQueryTool(dbComponent, refCounter++, languageSettings);
        long broadBackfillMs;
        long broadRows;
        try {
            runMatchOnly(broadTool, BROAD_TERM);
            broadRows = countRows(broadTool.getKey());
            broadBackfillMs = timeMs(() -> broadTool.backfillRenderJson(BACKFILL_LIMIT));
        } finally {
            broadTool.delete();
            broadTool.close();
        }

        Log.i(TAG, String.format(java.util.Locale.US,
                "BENCH bounded_backfill narrow_rows=%d narrow_ms=%d broad_rows=%d broad_ms=%d",
                narrowRows, narrowBackfillMs, broadRows, broadBackfillMs));

        Assume.assumeTrue("skipped: broad term didn't actually match many more rows than narrow on "
                        + "this device/data - can't validate the bound without a real breadth difference",
                broadRows > narrowRows * 20);

        assertTrue("backfill cost scaled with tier breadth (" + narrowBackfillMs + "ms for " + narrowRows
                        + " rows -> " + broadBackfillMs + "ms for " + broadRows + " rows) - render_json "
                        + "backfill may no longer be bounded by LIMIT",
                broadBackfillMs <= narrowBackfillMs * 8 + 200);
    }

    // Match-finding alone (no render_json at all) should stay fast regardless of tier breadth -
    // this is the ~5us/row path, unaffected by this fix (it was already cheap; render_json was
    // the expensive part). Absolute bound is very generous (measured ~50-90ms in development)
    // to tolerate slow CI/emulator hardware while still catching a real regression (e.g. a missing
    // index making even match-finding slow).
    @Test
    public void testMatchOnlyInsertIsCheapEvenForBroadTiers() {
        DictionarySearchQueryTool tool = new DictionarySearchQueryTool(dbComponent, refCounter++, languageSettings);
        long ms;
        long rows;
        try {
            ms = timeMs(() -> runMatchOnly(tool, BROAD_TERM));
            rows = countRows(tool.getKey());
        } finally {
            tool.delete();
            tool.close();
        }

        Log.i(TAG, String.format(java.util.Locale.US, "BENCH match_only rows=%d ms=%d", rows, ms));

        assertTrue("match-only tier execution took " + ms + "ms for " + rows + " rows - expected well under 2000ms",
                ms < 2000);
    }

    // Historical baseline, for comparison only: reconstructs the pre-fix architecture (render_json
    // computed inline, per matched row, eagerly for the whole tier) using the same production
    // buildRenderJsonExpr the bounded backfill above still relies on, so the regression this test
    // class guards against has a concrete, reproducible "how bad it was" number rather than just a
    // comment. Logs only - no assertion, since how bad the *old* way is isn't something correctness
    // should hinge on, and it intentionally doesn't touch DictionarySearchElement at all.
    @Test
    public void testHistoricalEagerRenderWasMuchSlowerForBroadTiers() {
        final PersistentDatabase database = dbComponent.getDatabase();
        final SupportSQLiteDatabase db = database.getOpenHelper().getWritableDatabase();
        final List<InstalledDictionary> installed = database.installedDictionaryDao().getAll();
        final boolean glossInstalled = DictionarySearchQueryTool.isInstalled(installed, "gloss", languageSettings.lang);
        final String glossAliasOrNull = glossInstalled ? DictionarySearchQueryTool.glossAlias(languageSettings.lang) : null;

        final String renderExpr = DictionarySearchQueryTool.buildRenderJsonExpr(
                "SearchTerm.entry_id", glossAliasOrNull, null);

        // Same shape as the pre-fix SQL_QUERY_BASIC_TIER (see git history around "Precompute
        // search-result render payload at insert time" and its later bounding) - just SELECTing
        // instead of INSERTing, since this is a read-only comparison.
        final String sql = "SELECT SearchTerm.entry_id, " + renderExpr + " "
                + "FROM core.SearchTerm "
                + "JOIN core.Entry ON Entry.entry_id = SearchTerm.entry_id "
                + "LEFT JOIN core.EntryForm ON EntryForm.form_id = SearchTerm.form_id "
                + "WHERE SearchTerm.script = 'kana' AND SearchTerm.priority = 0 "
                + "AND SearchTerm.normalized GLOB ? AND SearchTerm.is_prefix_searchable = 1";

        final String katakanaPrefix = dbComponent.getRomkan().to_katakana(dbComponent.getRomkan().to_hepburn(BROAD_TERM));

        long rows = 0;
        long t0 = System.nanoTime();
        try (Cursor cur = db.query(sql, new Object[]{katakanaPrefix + "*"})) {
            while (cur.moveToNext()) {
                rows++;
            }
        }
        long ms = (System.nanoTime() - t0) / 1_000_000;

        Log.i(TAG, String.format(java.util.Locale.US,
                "BENCH historical_eager_render_per_tier rows=%d ms=%d us_per_row=%.1f",
                rows, ms, rows == 0 ? 0.0 : (ms * 1000.0 / rows)));
    }
}
