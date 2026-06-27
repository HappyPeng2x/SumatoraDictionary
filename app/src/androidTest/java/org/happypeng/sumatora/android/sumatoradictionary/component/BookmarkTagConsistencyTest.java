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
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryBookmark;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryBookmarkImport;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryBookmarkTag;
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentDatabase;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.testing.HiltAndroidRule;
import dagger.hilt.android.testing.HiltAndroidTest;

/**
 * The app keeps tags in two places: a comma-separated string on DictionaryBookmark.tags
 * and normalised rows in DictionaryBookmarkTag.  These tests verify that commitBookmarks
 * keeps the two in sync under every import scenario.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4.class)
public class BookmarkTagConsistencyTest {

    @Rule
    public HiltAndroidRule hiltRule = new HiltAndroidRule(this);

    @Inject BookmarkImportComponent importComponent;
    @Inject PersistentDatabaseComponent dbComponent;

    private static final long SEQ   = 1001600;
    private static final int  REF   = 1;

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
            db.dictionaryBookmarkImportDao().delete(REF);
        });
    }

    private void stageAndCommit(String tags) {
        db.dictionaryBookmarkImportDao().delete(REF);
        db.dictionaryBookmarkImportDao().insert(
                new DictionaryBookmarkImport(REF, SEQ, 1, null, tags));
        importComponent.commitBookmarks(REF);
    }

    @Test
    public void testImportWithTagsCreatesTagRows() {
        stageAndCommit("noun,verb,common");

        DictionaryBookmark b = db.dictionaryBookmarkDao().getBySeq(SEQ);
        assertEquals("noun,verb,common", b.tags);

        List<String> tagRows = db.dictionaryBookmarkTagDao().getTagsForSeq(SEQ);
        assertEquals("tag row count must match comma-split count", 3, tagRows.size());
        assertTrue(tagRows.contains("noun"));
        assertTrue(tagRows.contains("verb"));
        assertTrue(tagRows.contains("common"));
    }

    @Test
    public void testReimportWithDifferentTagsReplacesRows() {
        // First import establishes "noun,verb"
        stageAndCommit("noun,verb");
        assertEquals(2, db.dictionaryBookmarkTagDao().getTagsForSeq(SEQ).size());

        // Second import replaces with "jlpt4,common" — old rows must be gone
        stageAndCommit("jlpt4,common");

        DictionaryBookmark b = db.dictionaryBookmarkDao().getBySeq(SEQ);
        assertEquals("jlpt4,common", b.tags);

        List<String> tagRows = db.dictionaryBookmarkTagDao().getTagsForSeq(SEQ);
        assertEquals("old tag rows must be replaced", 2, tagRows.size());
        assertTrue(tagRows.contains("jlpt4"));
        assertTrue(tagRows.contains("common"));
        assertTrue("old tag 'noun' must not survive", !tagRows.contains("noun"));
        assertTrue("old tag 'verb' must not survive", !tagRows.contains("verb"));
    }

    @Test
    public void testImportWithoutTagsPreservesExistingTagRows() {
        // Pre-insert entry with tag rows
        db.runInTransaction(() -> {
            db.dictionaryBookmarkDao().insert(new DictionaryBookmark(SEQ, 1, null, "noun,verb"));
            db.dictionaryBookmarkTagDao().insertMany(Arrays.asList(
                    new DictionaryBookmarkTag(SEQ, "noun"),
                    new DictionaryBookmarkTag(SEQ, "verb")));
        });

        // Import with no tags (null) for the same seq — existing tag rows must survive
        stageAndCommit(null);

        DictionaryBookmark b = db.dictionaryBookmarkDao().getBySeq(SEQ);
        assertEquals("tags string must be preserved", "noun,verb", b.tags);

        List<String> tagRows = db.dictionaryBookmarkTagDao().getTagsForSeq(SEQ);
        assertEquals("tag rows must be preserved", 2, tagRows.size());
        assertTrue(tagRows.contains("noun"));
        assertTrue(tagRows.contains("verb"));
    }

    @Test
    public void testTagRowsMatchTagStringSplit() {
        stageAndCommit("alpha,beta,gamma");

        String tagsString = db.dictionaryBookmarkDao().getBySeq(SEQ).tags;
        List<String> fromString = Arrays.asList(tagsString.split(","));
        List<String> tagRows = db.dictionaryBookmarkTagDao().getTagsForSeq(SEQ);

        assertEquals("tag row count must equal number of comma-split tokens",
                fromString.size(), tagRows.size());
        for (String t : fromString) {
            assertTrue("tag row must exist for each token in the tags string",
                    tagRows.contains(t));
        }
    }

    @Test
    public void testGetSeqsForTagReturnsCorrectSeq() {
        stageAndCommit("unique-tag");

        List<Long> seqs = db.dictionaryBookmarkTagDao().getSeqsForTag("unique-tag");
        assertEquals(1, seqs.size());
        assertEquals(SEQ, (long) seqs.get(0));
    }
}
