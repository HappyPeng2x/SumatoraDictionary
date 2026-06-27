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

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryBookmark;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryBookmarkImport;
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentDatabase;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.inject.Inject;

import dagger.hilt.android.testing.HiltAndroidRule;
import dagger.hilt.android.testing.HiltAndroidTest;

/**
 * Verifies that import batches identified by different ref integers are fully isolated:
 * committing or cancelling one ref must not affect staging rows or committed bookmarks
 * belonging to a different ref.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4.class)
public class BookmarkMultiRefTest {

    @Rule
    public HiltAndroidRule hiltRule = new HiltAndroidRule(this);

    @Inject BookmarkImportComponent importComponent;
    @Inject PersistentDatabaseComponent dbComponent;

    private static final long SEQ_REF1 = 1002000;
    private static final long SEQ_REF2 = 1002010;
    private static final int  REF_1    = 1;
    private static final int  REF_2    = 2;

    private PersistentDatabase db;

    @Before
    public void setup() {
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
            db.dictionaryBookmarkImportDao().delete(REF_1);
            db.dictionaryBookmarkImportDao().delete(REF_2);
        });
    }

    @Test
    public void testCommitRef1DoesNotAffectRef2StagingRows() {
        db.dictionaryBookmarkImportDao().insert(
                new DictionaryBookmarkImport(REF_1, SEQ_REF1, 1, null, null));
        db.dictionaryBookmarkImportDao().insert(
                new DictionaryBookmarkImport(REF_2, SEQ_REF2, 1, null, null));

        importComponent.commitBookmarks(REF_1);

        assertEquals("ref=2 staging row must survive commit of ref=1",
                1, db.dictionaryBookmarkImportDao().getByRef(REF_2).size());
        assertEquals(SEQ_REF2, db.dictionaryBookmarkImportDao().getByRef(REF_2).get(0).seq);
    }

    @Test
    public void testCancelRef1DoesNotAffectRef2StagingRows() {
        db.dictionaryBookmarkImportDao().insert(
                new DictionaryBookmarkImport(REF_1, SEQ_REF1, 1, null, null));
        db.dictionaryBookmarkImportDao().insert(
                new DictionaryBookmarkImport(REF_2, SEQ_REF2, 1, null, null));

        importComponent.cancelImport(REF_1);

        assertEquals("ref=2 staging row must survive cancel of ref=1",
                1, db.dictionaryBookmarkImportDao().getByRef(REF_2).size());
    }

    @Test
    public void testTwoRefsCommitIndependently() {
        db.dictionaryBookmarkImportDao().insert(
                new DictionaryBookmarkImport(REF_1, SEQ_REF1, 1, "memo1", null));
        db.dictionaryBookmarkImportDao().insert(
                new DictionaryBookmarkImport(REF_2, SEQ_REF2, 1, "memo2", null));

        importComponent.commitBookmarks(REF_1);
        importComponent.commitBookmarks(REF_2);

        DictionaryBookmark b1 = db.dictionaryBookmarkDao().getBySeq(SEQ_REF1);
        DictionaryBookmark b2 = db.dictionaryBookmarkDao().getBySeq(SEQ_REF2);

        assertNotNull("ref=1 bookmark must be committed", b1);
        assertNotNull("ref=2 bookmark must be committed", b2);
        assertEquals("memo1", b1.memo);
        assertEquals("memo2", b2.memo);

        assertEquals("ref=1 staging must be cleared after commit",
                0, db.dictionaryBookmarkImportDao().getByRef(REF_1).size());
        assertEquals("ref=2 staging must be cleared after commit",
                0, db.dictionaryBookmarkImportDao().getByRef(REF_2).size());
    }

    @Test
    public void testCommitOneRefCancelOther() {
        db.dictionaryBookmarkImportDao().insert(
                new DictionaryBookmarkImport(REF_1, SEQ_REF1, 1, null, null));
        db.dictionaryBookmarkImportDao().insert(
                new DictionaryBookmarkImport(REF_2, SEQ_REF2, 1, null, null));

        importComponent.commitBookmarks(REF_1);
        importComponent.cancelImport(REF_2);

        assertNotNull("ref=1 must be committed to main table",
                db.dictionaryBookmarkDao().getBySeq(SEQ_REF1));
        assertNull("ref=2 must not appear in main table after cancel",
                db.dictionaryBookmarkDao().getBySeq(SEQ_REF2));
    }
}
