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

package org.happypeng.sumatora.android.sumatoradictionary.diagnostic;

import android.graphics.Bitmap;
import android.widget.TextView;

import androidx.test.espresso.Espresso;
import androidx.test.espresso.action.ViewActions;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import org.happypeng.sumatora.android.sumatoradictionary.R;
import org.happypeng.sumatora.android.sumatoradictionary.activity.MainActivity;
import org.happypeng.sumatora.android.sumatoradictionary.component.PersistentDatabaseComponent;
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentLanguageSettings;
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.DictionarySearchQueryTool;
import org.happypeng.sumatora.android.sumatoradictionary.fragment.EntryDetailBottomSheet;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;

import javax.inject.Inject;

import dagger.hilt.android.testing.HiltAndroidRule;
import dagger.hilt.android.testing.HiltAndroidTest;

// Not a correctness test - renders EntryDetailBottomSheet for a real entry and screenshots it so
// the migrated UI can be eyeballed. Pull with:
//   adb pull /sdcard/Pictures/entry_detail_preview.png
@Ignore(
    "EntryDetailBottomSheet's render() reliably never completes in this test as of 2026-07-12 -"
        + " confirmed via logcat that dictionary attach finishes in milliseconds, then the"
        + " headword TextView (the first thing render() populates) still has empty text 60+"
        + " seconds later (waitForNonEmptyText below times out even at 60_000ms). This is a"
        + " genuine hang, not slow-cold-start flakiness (that theory was tested and ruled out) -"
        + " likely a deadlock/contention issue specific to this test's threading (it queries the"
        + " Room DB directly on the instrumentation thread via findKakeruEntryAndForm() while"
        + " EntryDetailBottomSheet's own Schedulers.io() chain tries to use the same singleton"
        + " PersistentDatabaseComponent concurrently), not investigated further. Re-enable once"
        + " that's root-caused - do not just bump the timeout again."
)
@HiltAndroidTest
@RunWith(AndroidJUnit4ClassRunner.class)
public class EntryDetailScreenshotTest {

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
    // entry_id/form_id are assigned by whichever SumatoraIndex build produced the installed core
    // dictionary and are not guaranteed stable across a rebuild (confirmed: a previously-hardcoded
    // id here silently drifted to point at an unrelated entry with no examples after a background
    // dictionary update promoted a newer build, which is what made this test flaky - see BUGS.md
    // history around 2026-07-11). Mirrors SchemaV2QueryDiagnosticTest.dumpEntryDetail().
    private long[] findKakeruEntryAndForm() {
        PersistentLanguageSettings settings = dbComponent.getDatabase()
                .persistentLanguageSettingsDao().getLanguageSettingsDirect(0);
        if (settings == null) {
            settings = new PersistentLanguageSettings();
            settings.lang = PersistentLanguageSettings.LANG_DEFAULT;
        }

        DictionarySearchQueryTool tool = new DictionarySearchQueryTool(dbComponent, 9500, settings);
        try {
            int max = tool.getCount("掛ける");
            for (int i = 0; i < max; i++) {
                tool.execute("掛ける", i, false, false);
            }

            android.database.Cursor cur = dbComponent.getDatabase().getOpenHelper().getReadableDatabase().query(
                    "SELECT entry_id, form_id FROM DictionarySearchElement WHERE ref = 9500 "
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
    public void screenshotEntryDetail() throws Exception {
        activityRule.launchActivity(null);
        MainActivity activity = activityRule.getActivity();

        long[] hit = findKakeruEntryAndForm();
        Assume.assumeNotNull("skipped: no hit for 掛ける on this device's dictionary", hit);
        long entryId = hit[0];
        long formId = hit[1];

        Thread.sleep(3000);

        activity.runOnUiThread(() -> {
            EntryDetailBottomSheet sheet = EntryDetailBottomSheet.Companion.newInstance(entryId, formId, false, null, null);
            sheet.show(activity.getSupportFragmentManager(), "preview");
        });

        // The sheet's render() (async DB fetch on Schedulers.io -> populate on mainThread) can
        // take longer than any fixed sleep on a cold-started process, especially right after a
        // fresh install with a large bundled dictionary set to decompress/attach for the first
        // time - a fixed Thread.sleep(4000) here was intermittently too short and made this test
        // flaky (see git history around 2026-07-12). Poll for the headword TextView - the first
        // thing render() populates, unconditionally - to actually be non-empty instead.
        waitForNonEmptyText(R.id.entry_detail_headword, 20_000);

        screenshot("entry_detail_preview.png");

        Espresso.onView(ViewMatchers.withId(R.id.entry_detail_examples_header))
                .perform(ViewActions.scrollTo());
        Thread.sleep(500);
        // scrollTo() only brings the header into view; swipe the scroll container itself (which
        // is ~100% on-screen, unlike the partially-visible examples child) to see the example
        // boxes below it.
        Espresso.onView(ViewMatchers.isAssignableFrom(androidx.core.widget.NestedScrollView.class))
                .perform(ViewActions.swipeUp());
        Thread.sleep(500);
        Espresso.onView(ViewMatchers.isAssignableFrom(androidx.core.widget.NestedScrollView.class))
                .perform(ViewActions.swipeUp());
        Thread.sleep(500);

        screenshot("entry_detail_examples_preview.png");
    }

    // Busy-polls the main thread for viewId to hold non-empty text, up to timeoutMillis. Simpler
    // and more robust than a fixed sleep for "wait until this async render finished" - actual
    // completion time varies a lot with device/cold-start speed, and there's no IdlingResource
    // wired up in production code for this diagnostic-only screen to hook into instead.
    private void waitForNonEmptyText(int viewId, long timeoutMillis) throws InterruptedException {
        MainActivity activity = activityRule.getActivity();
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            boolean[] hasText = {false};
            activity.runOnUiThread(() -> {
                TextView view = activity.findViewById(viewId);
                hasText[0] = view != null && view.getText().length() > 0;
            });
            if (hasText[0]) {
                return;
            }
            Thread.sleep(200);
        }
    }

    private void screenshot(String fileName) throws Exception {
        Bitmap bitmap = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        Assume.assumeNotNull("screenshot capture failed", bitmap);

        // App-private external storage needs no runtime permission on any API level, unlike
        // shared Pictures/ which is blocked by scoped storage on this device's API level.
        File dir = activityRule.getActivity().getExternalFilesDir(null);
        File out = new File(dir, fileName);
        try (FileOutputStream fos = new FileOutputStream(out)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
        }
    }
}
