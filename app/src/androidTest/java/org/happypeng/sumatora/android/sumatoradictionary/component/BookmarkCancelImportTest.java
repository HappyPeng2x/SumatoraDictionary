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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import android.content.Context;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryBookmark;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryBookmarkImport;
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
public class BookmarkCancelImportTest {

    @Rule
    public HiltAndroidRule hiltRule = new HiltAndroidRule(this);

    @Inject BookmarkImportComponent importComponent;
    @Inject PersistentDatabaseComponent dbComponent;

    private static final long SEQ_EXISTING = 1001700;
    private static final long SEQ_IMPORT   = 1001710;
    private static final int  REF          = 1;

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

    private Uri jsonFileUri(String filename, String json) throws IOException {
        File f = new File(targetContext.getCacheDir(), filename);
        try (FileWriter w = new FileWriter(f)) {
            w.write(json);
        }
        return Uri.fromFile(f);
    }

    @Test
    public void testCancelImportClearsStagingRows() {
        db.dictionaryBookmarkImportDao().insert(
                new DictionaryBookmarkImport(REF, SEQ_IMPORT, 1, null, null));
        assertEquals(1, db.dictionaryBookmarkImportDao().getByRef(REF).size());

        importComponent.cancelImport(REF);

        assertEquals("staging rows must be gone after cancel",
                0, db.dictionaryBookmarkImportDao().getByRef(REF).size());
    }

    @Test
    public void testCancelImportLeavesMainTableUntouched() {
        db.dictionaryBookmarkDao().insert(new DictionaryBookmark(SEQ_EXISTING, 1, "memo", null));
        db.dictionaryBookmarkImportDao().insert(
                new DictionaryBookmarkImport(REF, SEQ_IMPORT, 1, null, null));

        importComponent.cancelImport(REF);

        assertNotNull("pre-existing bookmark must survive cancel",
                db.dictionaryBookmarkDao().getBySeq(SEQ_EXISTING));
        assertNull("cancelled import must not appear in main table",
                db.dictionaryBookmarkDao().getBySeq(SEQ_IMPORT));
    }

    @Test
    public void testCancelAfterProcessUriLeavesDbClean() throws Exception {
        String json = "[{\"seq\":" + SEQ_IMPORT + ",\"bookmark\":1}]";
        importComponent.processURI(jsonFileUri("cancel_test.json", json), REF);

        assertEquals("staging row must exist after processURI",
                1, db.dictionaryBookmarkImportDao().getByRef(REF).size());

        importComponent.cancelImport(REF);

        assertEquals("staging must be empty after cancel",
                0, db.dictionaryBookmarkImportDao().getByRef(REF).size());
        assertNull("main table must not contain cancelled entry",
                db.dictionaryBookmarkDao().getBySeq(SEQ_IMPORT));
    }

    @Test
    public void testCancelImportOnNonExistentRefIsNoOp() {
        db.dictionaryBookmarkDao().insert(new DictionaryBookmark(SEQ_EXISTING, 1, null, null));

        // cancelImport on a ref that has no staging rows must not throw and must not touch main table
        importComponent.cancelImport(99);

        assertNotNull("main table must be unchanged",
                db.dictionaryBookmarkDao().getBySeq(SEQ_EXISTING));
    }
}
