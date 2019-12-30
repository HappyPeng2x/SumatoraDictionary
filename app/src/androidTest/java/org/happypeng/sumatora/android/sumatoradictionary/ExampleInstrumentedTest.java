package org.happypeng.sumatora.android.sumatoradictionary;

import android.content.Intent;
import android.net.Uri;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.ActivityTestRule;

import org.happypeng.sumatora.android.sumatoradictionary.activity.MainActivity;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.intent.Intents.intended;

/**
 * Instrumented test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class ExampleInstrumentedTest {
/*    @Rule
    public IntentsTestRule<Dictionary> intentsTestRule =
            new IntentsTestRule<>(Dictionary.class); */

    @Rule
    public ActivityTestRule activityRule = new ActivityTestRule<>(
            MainActivity.class,
            true,
            false);

    @Test
    public void useAppContext() throws Exception {
        // Context of the app under test.
        // Context appContext = InstrumentationRegistry.getInstrumentation().getContext();

        Intent intent = new Intent();
        intent.setAction(Intent.ACTION_SEND);
        intent.setType("text/xml");
        intent.putExtra(Intent.EXTRA_STREAM, Uri.parse("/plopolop"));

        activityRule.launchActivity(intent);

        //assertEquals("org.happypeng.sumatora.android.sumatoradictionary", appContext.getPackageName());
    }
}
