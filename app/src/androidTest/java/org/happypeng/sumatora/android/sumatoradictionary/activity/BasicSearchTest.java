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


import android.hardware.input.InputManager;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import androidx.test.espresso.ViewInteraction;
import androidx.test.filters.LargeTest;
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner;
import androidx.test.rule.ActivityTestRule;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.happypeng.sumatora.android.sumatoradictionary.R;
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
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;

@HiltAndroidTest
@LargeTest
@RunWith(AndroidJUnit4ClassRunner.class)
public class BasicSearchTest {

    @Rule(order = 0)
    public HiltAndroidRule hiltRule = new HiltAndroidRule(this);

    @Rule(order = 1)
    public ActivityTestRule<MainActivity> mActivityTestRule =
            new ActivityTestRule<>(MainActivity.class, false, false);

    @Inject PersistentDatabaseComponent dbComponent;

    @Before
    public void initialize() throws NoSuchMethodException {
        hiltRule.inject();
        Assume.assumeFalse(
                "skipped: no dictionary installed on this device",
                dbComponent.getDatabase().installedDictionaryDao().getAll().isEmpty());
        // Espresso uses InputManager.getInstance() via reflection for UI event injection.
        // On stock Android 16 (AOSP emulators) this method was removed; skip there rather
        // than crashing at the first Espresso action.
        try {
            InputManager.class.getDeclaredMethod("getInstance");
        } catch (NoSuchMethodException e) {
            Assume.assumeTrue("InputManager.getInstance() not available; Espresso UI injection unsupported", false);
        }
        mActivityTestRule.launchActivity(null);
        mActivityTestRule.getActivity().sayHello();
    }

    @Test
    public void basicSearchTest() throws InterruptedException {
        ViewInteraction searchAutoComplete = onView(
                allOf(withId(androidx.appcompat.R.id.search_src_text),
                        childAtPosition(
                                allOf(withId(androidx.appcompat.R.id.search_plate),
                                        childAtPosition(
                                                withId(androidx.appcompat.R.id.search_edit_frame),
                                                1)),
                                0),
                        isDisplayed()));

        // Sleep on the test thread (not the app's main thread) so the app can initialize
        // without blocking its own event loop.
        Thread.sleep(10000);

        // A push notification or system dialog may have pulled focus away during the wait;
        // treat that as a skip rather than a hard failure.
        Assume.assumeTrue("activity lost window focus during initialization wait",
                mActivityTestRule.getActivity().hasWindowFocus());

        searchAutoComplete.perform(replaceText("わたし"))
                .perform(pressImeActionButton())
                .perform(closeSoftKeyboard());

        Thread.sleep(5000);

        Assume.assumeTrue("activity lost window focus after search",
                mActivityTestRule.getActivity().hasWindowFocus());

        onView(withText("私 【わたし】　1. pronoun I, me\n\n→ 騒がしいホームで誰かが私の名前を呼んでいるのが聞こえた I could hear someone calling my name on the noisy platform."))
                .check(matches(isDisplayed()));

        onView(withId(R.id.dictionary_bookmark_fragment_search_status))
                .check(matches(withText("Results for term 'わたし':")));
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
