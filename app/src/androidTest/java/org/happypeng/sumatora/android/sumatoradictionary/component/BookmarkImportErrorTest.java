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

package org.happypeng.sumatora.android.sumatoradictionary.component;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import android.content.Context;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryBookmark;
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentDatabase;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import javax.inject.Inject;

import dagger.hilt.android.testing.HiltAndroidRule;
import dagger.hilt.android.testing.HiltAndroidTest;

@HiltAndroidTest
@RunWith(AndroidJUnit4.class)
public class BookmarkImportErrorTest {

    @Rule
    public HiltAndroidRule hiltRule = new HiltAndroidRule(this);

    @Inject BookmarkImportComponent importComponent;
    @Inject PersistentDatabaseComponent dbComponent;

    private static final long SEQ = 1001800;
    private static final int  REF = 1;

    private Context targetContext;
    private PersistentDatabase db;

    @Before
    public void setup() {
        targetContext = ApplicationProvider.getApplicationContext();
        hiltRule.inject();
        db = dbComponent.getDatabase();
        clearAllUserData();
    }

    private void clearAllUserData() {
        db.runInTransaction(() -> {
            for (DictionaryBookmark b : db.dictionaryBookmarkDao().getAll()) {
                db.dictionaryBookmarkTagDao().deleteTagsForSeq(b.seq);
                db.dictionaryBookmarkDao().delete(b);
            }
            db.dictionaryBookmarkImportDao().delete(REF);
        });
    }

    private Uri fileUri(String filename, String content) throws IOException {
        File f = new File(targetContext.getCacheDir(), filename);
        try (FileWriter w = new FileWriter(f)) {
            w.write(content);
        }
        return Uri.fromFile(f);
    }

    @Test
    public void testMalformedJsonDoesNotCrash() throws Exception {
        // Jackson throws JsonParseException (extends IOException); caught by processURI.
        importComponent.processURI(fileUri("bad.json", "{ not valid json [[["), REF);

        assertEquals("staging table must be empty after parse error",
                0, db.dictionaryBookmarkImportDao().getByRef(REF).size());
        assertNull("no bookmark must be committed",
                db.dictionaryBookmarkDao().getBySeq(SEQ));
    }

    @Test
    public void testMalformedJsonLeavesNoStagingRows() throws Exception {
        // Insert a staging row manually, then try to import malformed JSON for the same ref.
        // processURI deletes then re-inserts inside a transaction that never runs on error,
        // so the pre-existing row for a different import should be unaffected by a new ref.
        // This test checks no partial write occurs.
        importComponent.processURI(fileUri("bad2.json", "[{\"seq\":" + SEQ + ",\"bookmark\":1}, OOPS"), REF);

        assertEquals("no staging rows must remain after partial-parse error",
                0, db.dictionaryBookmarkImportDao().getByRef(REF).size());
    }

    @Test
    public void testEmptyJsonArrayCommitsNothing() throws Exception {
        importComponent.processURI(fileUri("empty.json", "[]"), REF);
        importComponent.commitBookmarks(REF);

        assertEquals("empty array import must result in no bookmarks",
                0, db.dictionaryBookmarkDao().getAll().size());
    }

    @Test
    public void testUnknownExtensionIsSkipped() throws Exception {
        // .txt has no matching MIME type and no matching extension fallback — processURI must skip it.
        String json = "[{\"seq\":" + SEQ + ",\"bookmark\":1}]";
        importComponent.processURI(fileUri("bookmarks.txt", json), REF);

        assertEquals("unknown extension must produce no staging rows",
                0, db.dictionaryBookmarkImportDao().getByRef(REF).size());
        assertNull("no bookmark must be committed for unknown type",
                db.dictionaryBookmarkDao().getBySeq(SEQ));
    }

    @Test
    public void testNonExistentFileUriDoesNotCrash() {
        // FileNotFoundException from openInputStream is caught by the IOException handler.
        File nonExistent = new File(targetContext.getCacheDir(), "does_not_exist.json");
        nonExistent.delete();

        importComponent.processURI(Uri.fromFile(nonExistent), REF);

        assertEquals("no staging rows must exist after file-not-found",
                0, db.dictionaryBookmarkImportDao().getByRef(REF).size());
    }
}
