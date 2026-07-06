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

import androidx.test.espresso.ViewInteraction;
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import org.happypeng.sumatora.android.sumatoradictionary.activity.MainActivity;
import org.happypeng.sumatora.android.sumatoradictionary.component.PersistentDatabaseComponent;
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

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.pressImeActionButton;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static org.hamcrest.Matchers.allOf;

// Not a correctness test - types a search term into the real search box and screenshots the
// resulting list screen so the migrated list rendering can be eyeballed. Pull with:
//   adb pull /sdcard/Pictures/search_list_preview.png
@HiltAndroidTest
@RunWith(AndroidJUnit4ClassRunner.class)
public class SearchListScreenshotTest {

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
    public void screenshotSearchList() throws Exception {
        activityRule.launchActivity(null);

        Thread.sleep(10000);

        Assume.assumeTrue("activity lost window focus during initialization wait",
                activityRule.getActivity().hasWindowFocus());

        ViewInteraction searchAutoComplete = onView(
                allOf(withId(androidx.appcompat.R.id.search_src_text), isDisplayed()));

        searchAutoComplete.perform(replaceText("食べる"))
                .perform(pressImeActionButton());

        // Let the PagedList settle fully before touching the keyboard/view hierarchy again -
        // closing the IME while a new page is still being latched races AsyncPagedListDiffer
        // against RecyclerView's layout pass (a pre-existing issue, reproduces identically on
        // unmodified BasicSearchTest - not something this diagnostic test should paper over by
        // retrying, just avoid poking it).
        Thread.sleep(8000);
        searchAutoComplete.perform(closeSoftKeyboard());
        Thread.sleep(3000);

        Bitmap bitmap = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        Assume.assumeNotNull("screenshot capture failed", bitmap);

        File dir = activityRule.getActivity().getExternalFilesDir(null);
        File out = new File(dir, "search_list_preview.png");
        try (FileOutputStream fos = new FileOutputStream(out)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
        }
    }
}
