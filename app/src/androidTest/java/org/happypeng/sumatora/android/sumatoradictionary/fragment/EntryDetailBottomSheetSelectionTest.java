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

package org.happypeng.sumatora.android.sumatoradictionary.fragment;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import org.happypeng.sumatora.android.sumatoradictionary.R;
import org.happypeng.sumatora.android.sumatoradictionary.activity.MainActivity;
import org.happypeng.sumatora.android.sumatoradictionary.component.PersistentDatabaseComponent;
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentLanguageSettings;
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.DictionarySearchQueryTool;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.inject.Inject;

import dagger.hilt.android.testing.HiltAndroidRule;
import dagger.hilt.android.testing.HiltAndroidTest;

import static org.junit.Assert.assertTrue;

// Regression coverage for: no TextView in the entry detail sheet ever enabled text selection, so
// long-press did nothing (see CHANGELOG's Unreleased "Copy/paste" entry). Unlike search-result
// rows (see SearchResultCopyTest), this view isn't inside a RecyclerView, so native selection was
// the fix here rather than a copy button - see EntryDetailBottomSheet.render()'s
// setTextIsSelectable() calls.
@HiltAndroidTest
@RunWith(AndroidJUnit4ClassRunner.class)
public class EntryDetailBottomSheetSelectionTest {

    @Rule(order = 0)
    public HiltAndroidRule hiltRule = new HiltAndroidRule(this);

    @Rule(order = 1)
    public ActivityTestRule<MainActivity> activityRule =
            new ActivityTestRule<>(MainActivity.class, false, false);

    @Inject
    PersistentDatabaseComponent dbComponent;

    @Before
    public void setUp() {
        hiltRule.inject();
        Assume.assumeFalse(
                "skipped: no dictionary installed on this device",
                dbComponent.getDatabase().installedDictionaryDao().getAll().isEmpty());
    }

    // Resolves 掛ける's entry/form id by running a real search instead of hardcoding a snapshot -
    // ids aren't stable across a dictionary rebuild. Mirrors EntryDetailScreenshotTest's helper of
    // the same name (see its comment for why - BUGS.md history around 2026-07-11).
    private long[] findKakeruEntryAndForm() {
        PersistentLanguageSettings settings = dbComponent.getDatabase()
                .persistentLanguageSettingsDao().getLanguageSettingsDirect(0);
        if (settings == null) {
            settings = new PersistentLanguageSettings();
            settings.lang = PersistentLanguageSettings.LANG_DEFAULT;
        }

        DictionarySearchQueryTool tool = new DictionarySearchQueryTool(dbComponent, 9501, settings);
        try {
            int max = tool.getCount("掛ける");
            for (int i = 0; i < max; i++) {
                tool.execute("掛ける", i, false, false);
            }

            android.database.Cursor cur = dbComponent.getDatabase().getOpenHelper().getReadableDatabase().query(
                    "SELECT entry_id, form_id FROM DictionarySearchElement WHERE ref = 9501 "
                            + "ORDER BY entryOrder, rank, entry_id LIMIT 1");
            try {
                if (cur.moveToFirst()) {
                    long entryId = cur.getLong(0);
                    long formId = cur.isNull(1) ? -1 : cur.getLong(1);
                    return new long[]{entryId, formId};
                }
                return null;
            } finally {
                cur.close();
            }
        } finally {
            tool.delete();
            tool.close();
        }
    }

    @Test
    public void headwordAndGlossText_areSelectable() throws Exception {
        activityRule.launchActivity(null);
        MainActivity activity = activityRule.getActivity();

        long[] hit = findKakeruEntryAndForm();
        Assume.assumeNotNull("skipped: no hit for 掛ける on this device's dictionary", hit);
        long entryId = hit[0];
        long formId = hit[1];

        Thread.sleep(3000);

        // BottomSheetDialogFragment's views live in the dialog's own Window, separate from the
        // host Activity's - Activity.findViewById() can't reach them (EntryDetailScreenshotTest
        // never notices, since it has no assertion and just proceeds to screenshot after its wait
        // times out either way). Look up views through the fragment's own root instead.
        EntryDetailBottomSheet[] sheetHolder = new EntryDetailBottomSheet[1];
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            EntryDetailBottomSheet sheet =
                    EntryDetailBottomSheet.Companion.newInstance(entryId, formId, false, null, null);
            sheet.show(activity.getSupportFragmentManager(), "selection_test");
            sheetHolder[0] = sheet;
        });
        EntryDetailBottomSheet sheet = sheetHolder[0];

        // render()'s DB fetch runs on Schedulers.io then populates on mainThread - poll for the
        // headword, the first thing it sets unconditionally, instead of a fixed sleep (see
        // EntryDetailScreenshotTest's identical wait for why a fixed sleep here was flaky).
        waitForNonEmptyText(sheet, R.id.entry_detail_headword, 20_000);

        boolean[] headwordSelectable = {false};
        boolean[] foundSelectableGloss = {false};
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
            View root = sheet.getView();
            TextView headword = root != null ? root.findViewById(R.id.entry_detail_headword) : null;
            headwordSelectable[0] = headword != null && headword.isTextSelectable();

            ViewGroup senses = root != null ? root.findViewById(R.id.entry_detail_senses) : null;
            foundSelectableGloss[0] = senses != null && anyNonEmptySelectableTextView(senses);
        });

        assertTrue("entry detail headword must be selectable so it can be copied", headwordSelectable[0]);
        assertTrue("at least one sense/gloss TextView under entry_detail_senses must be selectable",
                foundSelectableGloss[0]);
    }

    private boolean anyNonEmptySelectableTextView(View view) {
        if (view instanceof TextView) {
            TextView tv = (TextView) view;
            if (tv.getText().length() > 0 && tv.isTextSelectable()) {
                return true;
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                if (anyNonEmptySelectableTextView(group.getChildAt(i))) {
                    return true;
                }
            }
        }
        return false;
    }

    // Busy-polls the main thread for viewId (looked up through the fragment's own root - see the
    // Window note above) to hold non-empty text, up to timeoutMillis.
    private void waitForNonEmptyText(EntryDetailBottomSheet sheet, int viewId, long timeoutMillis)
            throws InterruptedException {
        MainActivity activity = activityRule.getActivity();
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            boolean[] hasText = {false};
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> {
                View root = sheet.getView();
                TextView view = root != null ? root.findViewById(viewId) : null;
                hasText[0] = view != null && view.getText().length() > 0;
            });
            if (hasText[0]) {
                return;
            }
            Thread.sleep(200);
        }
    }
}
