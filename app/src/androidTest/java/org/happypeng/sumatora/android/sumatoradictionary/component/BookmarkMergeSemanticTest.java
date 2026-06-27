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
 * Tests the UPSERT merge SQL in BookmarkImportComponent.commitBookmarks in isolation
 * by staging rows directly into DictionaryBookmarkImport rather than going through
 * processURI, so that file-parsing is not a confounding factor.
 *
 * Merge rules under test:
 *   bookmark = MAX(existing, incoming)
 *   memo     = incoming  if incoming is non-null AND non-empty, else existing
 *   tags     = incoming  if incoming is non-null AND non-empty, else existing
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4.class)
public class BookmarkMergeSemanticTest {

    @Rule
    public HiltAndroidRule hiltRule = new HiltAndroidRule(this);

    @Inject BookmarkImportComponent importComponent;
    @Inject PersistentDatabaseComponent dbComponent;

    private static final long SEQ = 1001500;
    private static final int  REF = 1;

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

    private void stageAndCommit(long bookmark, String memo, String tags) {
        db.dictionaryBookmarkImportDao().delete(REF);
        db.dictionaryBookmarkImportDao().insert(
                new DictionaryBookmarkImport(REF, SEQ, bookmark, memo, tags));
        importComponent.commitBookmarks(REF);
    }

    // ── bookmark field ────────────────────────────────────────────────────────

    @Test
    public void testBookmarkMaxIncomingWins() {
        db.dictionaryBookmarkDao().insert(new DictionaryBookmark(SEQ, 0, null, null));
        stageAndCommit(1, null, null);
        assertEquals("MAX(0,1) must be 1", 1L, db.dictionaryBookmarkDao().getBySeq(SEQ).bookmark);
    }

    @Test
    public void testBookmarkMaxExistingWins() {
        db.dictionaryBookmarkDao().insert(new DictionaryBookmark(SEQ, 1, null, null));
        stageAndCommit(0, null, null);
        assertEquals("MAX(1,0) must be 1", 1L, db.dictionaryBookmarkDao().getBySeq(SEQ).bookmark);
    }

    // ── memo field ────────────────────────────────────────────────────────────

    @Test
    public void testMemoPreservedWhenImportHasNull() {
        db.dictionaryBookmarkDao().insert(new DictionaryBookmark(SEQ, 1, "keep me", null));
        stageAndCommit(1, null, null);
        assertEquals("memo must be preserved when import memo is null",
                "keep me", db.dictionaryBookmarkDao().getBySeq(SEQ).memo);
    }

    @Test
    public void testMemoPreservedWhenImportHasEmptyString() {
        db.dictionaryBookmarkDao().insert(new DictionaryBookmark(SEQ, 1, "keep me", null));
        stageAndCommit(1, "", null);
        assertEquals("memo must be preserved when import memo is empty string",
                "keep me", db.dictionaryBookmarkDao().getBySeq(SEQ).memo);
    }

    @Test
    public void testMemoOverwrittenByNonEmptyImport() {
        db.dictionaryBookmarkDao().insert(new DictionaryBookmark(SEQ, 1, "old memo", null));
        stageAndCommit(1, "new memo", null);
        assertEquals("memo must be overwritten by non-empty import",
                "new memo", db.dictionaryBookmarkDao().getBySeq(SEQ).memo);
    }

    // ── tags field ────────────────────────────────────────────────────────────

    @Test
    public void testTagsPreservedWhenImportHasNull() {
        db.dictionaryBookmarkDao().insert(new DictionaryBookmark(SEQ, 1, null, "noun,verb"));
        stageAndCommit(1, null, null);
        assertEquals("tags must be preserved when import tags is null",
                "noun,verb", db.dictionaryBookmarkDao().getBySeq(SEQ).tags);
    }

    @Test
    public void testTagsPreservedWhenImportHasEmptyString() {
        db.dictionaryBookmarkDao().insert(new DictionaryBookmark(SEQ, 1, null, "noun,verb"));
        stageAndCommit(1, null, "");
        assertEquals("tags must be preserved when import tags is empty string",
                "noun,verb", db.dictionaryBookmarkDao().getBySeq(SEQ).tags);
    }

    @Test
    public void testTagsOverwrittenByNonEmptyImport() {
        db.dictionaryBookmarkDao().insert(new DictionaryBookmark(SEQ, 1, null, "old-tag"));
        stageAndCommit(1, null, "new-tag,another");
        assertEquals("tags must be overwritten by non-empty import",
                "new-tag,another", db.dictionaryBookmarkDao().getBySeq(SEQ).tags);
    }

    // ── new entry (no existing row) ───────────────────────────────────────────

    @Test
    public void testNewEntryInsertedWhenNoExistingRow() {
        stageAndCommit(1, "memo", "tag1");
        DictionaryBookmark b = db.dictionaryBookmarkDao().getBySeq(SEQ);
        assertNotNull("new entry must be inserted when no existing row", b);
        assertEquals(1L, b.bookmark);
        assertEquals("memo", b.memo);
        assertEquals("tag1", b.tags);
    }
}
