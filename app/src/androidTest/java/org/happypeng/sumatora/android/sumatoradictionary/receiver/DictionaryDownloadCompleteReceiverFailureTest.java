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

package org.happypeng.sumatora.android.sumatoradictionary.receiver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.DownloadManager;
import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.happypeng.sumatora.android.sumatoradictionary.component.PersistentDatabaseComponent;
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentDatabase;
import org.happypeng.sumatora.android.sumatoradictionary.db.RemoteDictionaryObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;

import javax.inject.Inject;

import dagger.hilt.android.testing.HiltAndroidRule;
import dagger.hilt.android.testing.HiltAndroidTest;

// Drives DictionaryDownloadCompleteReceiver.handleDownloadComplete() directly (see its
// @VisibleForTesting note) instead of through a real DownloadManager failure or a manually
// dispatched system broadcast - a downloadId that DownloadManager has never heard of makes
// downloadSucceeded() report false, the same as a real failed download (bad network, checksum
// mismatch, storage full) would.
//
// Regression coverage for: a failed download used to be deleted from RemoteDictionaryObject the
// same way a successful one is, so it silently reverted to "not installed" with no explanation
// (see CHANGELOG's Unreleased entry). It must now persist with failed=true instead so
// DictionariesManagementActivity/DictionaryManagementRenderer can offer a retry.
@HiltAndroidTest
@RunWith(AndroidJUnit4.class)
public class DictionaryDownloadCompleteReceiverFailureTest {

    @Rule
    public HiltAndroidRule hiltRule = new HiltAndroidRule(this);

    @Inject
    PersistentDatabaseComponent dbComponent;

    private static final String TYPE = "gloss";
    private static final String LANG = "test-fail";
    // No real DownloadManager download is ever enqueued with this id in the test process, so
    // DownloadManager.query() for it returns no row and downloadSucceeded() reports false.
    private static final long NONEXISTENT_DOWNLOAD_ID = 987_654_321L;

    private PersistentDatabase db;
    private Context context;

    @Before
    public void setup() {
        hiltRule.inject();
        db = dbComponent.getDatabase();
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        cleanUpTestRow();
    }

    @After
    public void tearDown() {
        cleanUpTestRow();
    }

    private void cleanUpTestRow() {
        RemoteDictionaryObject existing = db.remoteDictionaryObjectDao().getForTypeLang(TYPE, LANG);
        if (existing != null) {
            db.remoteDictionaryObjectDao().delete(existing);
        }
    }

    @Test
    public void handleDownloadComplete_downloadNeverSucceeded_marksRowFailedInsteadOfDeleting() throws Exception {
        File localFile = File.createTempFile("gloss-test-fail", ".db.gz", context.getExternalFilesDir(null));
        assertTrue("test fixture setup: partial file should exist before handling", localFile.exists());

        RemoteDictionaryObject remote = new RemoteDictionaryObject(
                "https://example.invalid/x.gz", "Test pack", TYPE, LANG, 12, 20260719, "");
        remote.localFile = localFile.getAbsolutePath();
        remote.downloadId = NONEXISTENT_DOWNLOAD_ID;
        db.remoteDictionaryObjectDao().insert(remote);

        DictionaryDownloadCompleteReceiver receiver = new DictionaryDownloadCompleteReceiver();
        receiver.persistentDatabaseComponent = dbComponent;
        receiver.handleDownloadComplete(context, NONEXISTENT_DOWNLOAD_ID);

        RemoteDictionaryObject after = db.remoteDictionaryObjectDao().getForTypeLang(TYPE, LANG);
        assertNotNull("a failed row must be kept, not deleted, so the UI can offer a retry", after);
        assertEquals(-1L, after.downloadId);
        assertTrue("failed must be set so DictionaryManagementRenderer shows the retry state", after.failed);
        assertFalse("the partial download file must still be cleaned up on failure", localFile.exists());
    }

    @Test
    public void download_afterAPreviousFailure_resetsFailedFlagAndGetsANewDownloadId() {
        RemoteDictionaryObject failed = new RemoteDictionaryObject(
                "https://example.invalid/x.gz", "Test pack", TYPE, LANG, 12, 20260719, "");
        failed.downloadId = -1;
        failed.failed = true;
        db.remoteDictionaryObjectDao().insert(failed);

        RemoteDictionaryObject toRetry = db.remoteDictionaryObjectDao().getForTypeLang(TYPE, LANG);
        assertNotNull(toRetry);
        assertTrue(toRetry.failed);

        DownloadManager downloadManager = (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        File downloadDir = new File(context.getExternalFilesDir(null), "downloads");
        downloadDir.mkdirs();
        // Mirrors DictionariesManagementActivity.startDownload(): re-download() the same row the
        // "tap to retry" button was built from (DictionaryManagementRenderer wires onRetry to the
        // same handler as onInstall), then persist it - a fresh download's row must not still
        // read as failed while it's in flight.
        toRetry.download(downloadManager, downloadDir);
        db.remoteDictionaryObjectDao().insert(toRetry);

        try {
            RemoteDictionaryObject afterRetry = db.remoteDictionaryObjectDao().getForTypeLang(TYPE, LANG);
            assertNotNull(afterRetry);
            assertFalse("retrying must clear the failed flag", afterRetry.failed);
            assertTrue("retrying must obtain a fresh DownloadManager id", afterRetry.downloadId > -1);
        } finally {
            downloadManager.remove(toRetry.downloadId);
        }
    }
}
