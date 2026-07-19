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
import android.util.Log;

import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.work.Configuration;
import androidx.work.testing.WorkManagerTestInitHelper;

import org.happypeng.sumatora.android.sumatoradictionary.R;
import org.happypeng.sumatora.android.sumatoradictionary.activity.DictionariesManagementActivity;
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

// Diagnostic only - launches DictionariesManagementActivity directly (bypasses the
// not-exported restriction that blocks `adb shell am start`), taps the first "download" button,
// waits, and screenshots + logs DownloadManager's status so real network behavior on the
// emulator can be checked. Read with: adb logcat -s DictMgmtDiag
@HiltAndroidTest
@RunWith(AndroidJUnit4ClassRunner.class)
public class DictionariesManagementScreenshotTest {
    private static final String TAG = "DictMgmtDiag";

    @Rule(order = 0)
    public HiltAndroidRule hiltRule = new HiltAndroidRule(this);

    @Rule(order = 1)
    public ActivityTestRule<DictionariesManagementActivity> activityRule =
            new ActivityTestRule<>(DictionariesManagementActivity.class, false, false);

    @Inject
    PersistentDatabaseComponent dbComponent;

    @Before
    public void setUp() {
        hiltRule.inject();

        // HiltTestApplication (swapped in by CustomTestRunner) doesn't implement
        // Configuration.Provider the way the real DictionaryApplication does, so
        // WorkManager.getInstance() would otherwise throw here - work-testing's helper does the
        // on-demand init the real Application would normally trigger.
        WorkManagerTestInitHelper.initializeTestWorkManager(
                InstrumentationRegistry.getInstrumentation().getTargetContext(),
                new Configuration.Builder().build());

        Assume.assumeFalse(
                "skipped: no dictionary installed on this device",
                dbComponent.getDatabase().installedDictionaryDao().getAll().isEmpty());
    }

    @Test
    public void screenshotAndTryDownload() throws Exception {
        activityRule.launchActivity(null);
        Thread.sleep(2000);

        screenshot("dictionaries_management_before.png");

        Log.i(TAG, "remote rows before tap: " + dbComponent.getDatabase().remoteDictionaryObjectDao().getAll().size());

        // Phase 0c: tap "Check for updates" and confirm the installed-dictionaries summary
        // populated without crashing (network itself is exercised separately by the worker).
        activityRule.getActivity().runOnUiThread(() -> {
            android.widget.Button checkUpdates = activityRule.getActivity()
                    .findViewById(R.id.activity_dictionaries_management_check_updates);
            checkUpdates.performClick();
        });

        Thread.sleep(1000);

        activityRule.getActivity().runOnUiThread(() -> {
            android.widget.TextView status = activityRule.getActivity()
                    .findViewById(R.id.activity_dictionaries_management_status);
            Log.i(TAG, "status pill text: " + status.getText());
        });

        screenshot("dictionaries_management_check_updates.png");

        // Rows are built programmatically (see DictionaryManagementRenderer) into one flat
        // container instead of a RecyclerView, and the install button carries no fixed id - find
        // the first one by its content description instead.
        activityRule.getActivity().runOnUiThread(() -> {
            android.view.ViewGroup container = activityRule.getActivity()
                    .findViewById(R.id.activity_dictionaries_management_container);
            android.view.View installButton = findByContentDescription(
                    container, activityRule.getActivity().getString(R.string.install_icon_description));
            if (installButton != null) {
                installButton.performClick();
            } else {
                Log.e(TAG, "no available-dictionary row found to click");
            }
        });

        Thread.sleep(3000);

        screenshot("dictionaries_management_after.png");

        for (org.happypeng.sumatora.android.sumatoradictionary.db.RemoteDictionaryObject r :
                dbComponent.getDatabase().remoteDictionaryObjectDao().getAll()) {
            Log.i(TAG, "remote: type=" + r.type + " lang=" + r.lang + " downloadId=" + r.downloadId
                    + " localFile=" + r.localFile + " url=" + r.file);
        }

        // DictionaryDownloadCompleteReceiver always deletes this pack's RemoteDictionaryObject row
        // once it finishes processing the completed download (success, checksum failure, or
        // download failure) - poll for that instead of a fixed sleep. A ~100MB compressed pack can
        // take far longer to download and decompress than any fixed budget depending on the
        // runner's network/CPU, and this diagnostic test used to be a no-op in CI because the pack
        // it installs pointed at a dead release URL - see CHANGELOG/BUGS.md history around
        // 2026-07-19 - so a real download+install was never actually exercised here before.
        // Bounded generously (diagnostic test, not a tight unit test) so a genuine network problem
        // still lets the test finish instead of hanging CI.
        long downloadDeadline = System.currentTimeMillis() + 180_000;
        while (System.currentTimeMillis() < downloadDeadline
                && !dbComponent.getDatabase().remoteDictionaryObjectDao().getAll().isEmpty()) {
            Thread.sleep(1000);
        }

        android.app.DownloadManager downloadManager = (android.app.DownloadManager)
                activityRule.getActivity().getSystemService(android.content.Context.DOWNLOAD_SERVICE);
        for (org.happypeng.sumatora.android.sumatoradictionary.db.RemoteDictionaryObject r :
                dbComponent.getDatabase().remoteDictionaryObjectDao().getAll()) {
            if (r.downloadId <= 0) continue;
            android.database.Cursor cur = downloadManager.query(
                    new android.app.DownloadManager.Query().setFilterById(r.downloadId));
            if (cur != null) {
                if (cur.moveToFirst()) {
                    int statusIdx = cur.getColumnIndex(android.app.DownloadManager.COLUMN_STATUS);
                    int reasonIdx = cur.getColumnIndex(android.app.DownloadManager.COLUMN_REASON);
                    Log.i(TAG, "download status for " + r.type + ": status=" + cur.getInt(statusIdx)
                            + " reason=" + cur.getInt(reasonIdx));
                }
                cur.close();
            }
        }

        screenshot("dictionaries_management_final.png");

        uninstallOptionalPacks();
    }

    // This test installs a real optional pack (suffix/names) via a real network download - remove
    // it afterward regardless of outcome, so it can't leak into later tests in the same
    // instrumentation run. Those tests don't expect an optional pack to be present and will
    // exercise real substring/names-tier queries against it once InstalledDictionaryDao reports it
    // installed (see CHANGELOG/BUGS.md history around 2026-07-19).
    private void uninstallOptionalPacks() {
        org.happypeng.sumatora.android.sumatoradictionary.db.PersistentDatabase db = dbComponent.getDatabase();
        for (org.happypeng.sumatora.android.sumatoradictionary.db.InstalledDictionary d : db.installedDictionaryDao().getAll()) {
            if (!org.happypeng.sumatora.android.sumatoradictionary.db.OptionalDictionaryCatalog.INSTANCE.getOPTIONAL_TYPES().contains(d.type)) {
                continue;
            }
            d.detach(db);
            new java.io.File(d.file).delete();
            db.installedDictionaryDao().delete(d);
        }
    }

    private static android.view.View findByContentDescription(android.view.ViewGroup root, CharSequence desc) {
        for (int i = 0; i < root.getChildCount(); i++) {
            android.view.View child = root.getChildAt(i);
            if (desc.equals(child.getContentDescription())) {
                return child;
            }
            if (child instanceof android.view.ViewGroup) {
                android.view.View found = findByContentDescription((android.view.ViewGroup) child, desc);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private void screenshot(String fileName) throws Exception {
        Bitmap bitmap = InstrumentationRegistry.getInstrumentation().getUiAutomation().takeScreenshot();
        Assume.assumeNotNull("screenshot capture failed", bitmap);
        File dir = activityRule.getActivity().getExternalFilesDir(null);
        File out = new File(dir, fileName);
        try (FileOutputStream fos = new FileOutputStream(out)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
        }
    }
}
