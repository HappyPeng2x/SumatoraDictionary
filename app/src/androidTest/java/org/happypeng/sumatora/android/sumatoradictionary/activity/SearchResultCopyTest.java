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

package org.happypeng.sumatora.android.sumatoradictionary.activity;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import androidx.test.espresso.ViewInteraction;
import androidx.test.espresso.action.ViewActions;
import androidx.test.filters.LargeTest;
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.happypeng.sumatora.android.sumatoradictionary.component.PersistentDatabaseComponent;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.inject.Inject;

import dagger.hilt.android.testing.HiltAndroidRule;
import dagger.hilt.android.testing.HiltAndroidTest;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.pressImeActionButton;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

// Regression coverage for: search-result rows never enabled text selection and had no clipboard
// code at all, so long-press did nothing (see CHANGELOG's Unreleased "Copy/paste" entry). Rows use
// long-press-to-copy instead of native text selection - see DictionarySearchElementViewHolder's
// comment on why (a RecyclerView row can be rebound out from under an in-progress selection).
// Drives a real search through the UI (same flow/query as BasicSearchTest) rather than binding a
// ViewHolder directly, so this also exercises the actual RecyclerView row a user long-presses.
@HiltAndroidTest
@LargeTest
@RunWith(AndroidJUnit4ClassRunner.class)
public class SearchResultCopyTest {

    @Rule(order = 0)
    public HiltAndroidRule hiltRule = new HiltAndroidRule(this);

    @Rule(order = 1)
    public ActivityTestRule<MainActivity> mActivityTestRule =
            new ActivityTestRule<>(MainActivity.class, false, false);

    @Inject
    PersistentDatabaseComponent dbComponent;

    @Before
    public void initialize() {
        hiltRule.inject();
        Assume.assumeFalse(
                "skipped: no dictionary installed on this device",
                dbComponent.getDatabase().installedDictionaryDao().getAll().isEmpty());
        mActivityTestRule.launchActivity(null);
        mActivityTestRule.getActivity().sayHello();
    }

    @Test
    public void longPressOnResultRow_copiesHeadwordAndReadingToClipboard() throws InterruptedException {
        ViewInteraction searchAutoComplete = onView(
                allOf(withId(androidx.appcompat.R.id.search_src_text),
                        childAtPosition(
                                allOf(withId(androidx.appcompat.R.id.search_plate),
                                        childAtPosition(
                                                withId(androidx.appcompat.R.id.search_edit_frame),
                                                1)),
                                0),
                        androidx.test.espresso.matcher.ViewMatchers.isDisplayed()));

        // Sleep on the test thread (not the app's main thread) so the app can initialize
        // without blocking its own event loop - mirrors BasicSearchTest's proven wait pattern.
        Thread.sleep(10000);

        Assume.assumeTrue("activity lost window focus during initialization wait",
                mActivityTestRule.getActivity().hasWindowFocus());

        searchAutoComplete.perform(replaceText("わたし"))
                .perform(pressImeActionButton())
                .perform(closeSoftKeyboard());

        Thread.sleep(5000);

        Assume.assumeTrue("activity lost window focus after search",
                mActivityTestRule.getActivity().hasWindowFocus());

        // Clear the clipboard first so a stray leftover value from another test can't produce a
        // false pass.
        ClipboardManager clipboard = (ClipboardManager) InstrumentationRegistry.getInstrumentation()
                .getTargetContext().getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("", ""));

        // Headword row is "私   わたし" (bold reading, no brackets) - see
        // SearchElementRenderer.renderHeadword. Long-clicking the headword TextView itself is
        // enough: it has no touch handling of its own, so the gesture reaches word_card_content's
        // OnLongClickListener exactly like a real user's finger would.
        onView(withText(containsString("私   わたし")))
                .perform(ViewActions.longClick());

        ClipData clip = clipboard.getPrimaryClip();
        assertNotNull("long-pressing a result row should have copied something to the clipboard", clip);
        String copied = clip.getItemAt(0).getText().toString();
        // buildCopyText() joins primaryText, primaryReading, and glosses with " - ", in that
        // order - only assert the structural (headword+reading) part, not gloss wording, since
        // gloss text can shift across dictionary rebuilds (see EntryDetailScreenshotTest's
        // comment on why entry ids/content aren't hardcoded either).
        assertTrue("expected copied text to start with the headword and reading, was: " + copied,
                copied.startsWith("私 - わたし"));
    }

    private static Matcher<View> childAtPosition(
            final Matcher<View> parentMatcher, final int position) {

        return new TypeSafeMatcher<View>() {
            @Override
            public void describeTo(Description description) {
                description.appendText("Child at position " + position + " in parent ");
                parentMatcher.describeTo(description);
            }

            @Override
            public boolean matchesSafely(View view) {
                ViewParent parent = view.getParent();
                return parent instanceof ViewGroup && parentMatcher.matches(parent)
                        && view.equals(((ViewGroup) parent).getChildAt(position));
            }
        };
    }
}
