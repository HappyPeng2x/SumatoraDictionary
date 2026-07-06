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

import androidx.test.espresso.Espresso;
import androidx.test.espresso.action.ViewActions;
import androidx.test.espresso.matcher.ViewMatchers;
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import org.happypeng.sumatora.android.sumatoradictionary.R;
import org.happypeng.sumatora.android.sumatoradictionary.activity.MainActivity;
import org.happypeng.sumatora.android.sumatoradictionary.component.PersistentDatabaseComponent;
import org.happypeng.sumatora.android.sumatoradictionary.fragment.EntryDetailBottomSheet;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;

import javax.inject.Inject;

import dagger.hilt.android.testing.HiltAndroidRule;
import dagger.hilt.android.testing.HiltAndroidTest;

// Not a correctness test - renders EntryDetailBottomSheet for a fixed entry and screenshots it so
// the migrated UI can be eyeballed. Pull with:
//   adb pull /sdcard/Pictures/entry_detail_preview.png
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

    @Test
    public void screenshotEntryDetail() throws Exception {
        activityRule.launchActivity(null);
        MainActivity activity = activityRule.getActivity();

        // entry_id/form_id for 掛ける's exact-match entry, taken from SchemaV2QueryDiagnosticTest's
        // last run (top hit for the term "掛ける").
        long entryId = 775312;
        long formId = 1458865;

        Thread.sleep(3000);

        activity.runOnUiThread(() -> {
            EntryDetailBottomSheet sheet = EntryDetailBottomSheet.Companion.newInstance(entryId, formId, false, null, null);
            sheet.show(activity.getSupportFragmentManager(), "preview");
        });

        // Give the async DB fetch + render (Schedulers.io -> mainThread) time to complete.
        Thread.sleep(4000);

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
