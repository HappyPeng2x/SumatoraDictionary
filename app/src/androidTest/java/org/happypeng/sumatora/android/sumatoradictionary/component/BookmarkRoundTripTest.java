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
import static org.junit.Assert.assertTrue;

import android.net.Uri;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryBookmark;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryBookmarkTag;
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentDatabase;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.testing.HiltAndroidRule;
import dagger.hilt.android.testing.HiltAndroidTest;

/**
 * Round-trip test: insert bookmarks/memos/tags directly, export to JSON,
 * wipe the database, import the JSON back, and verify that all data is
 * restored identically.  This guards against regressions in the
 * export/import path that would silently destroy user data.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4.class)
public class BookmarkRoundTripTest {

    @Rule
    public HiltAndroidRule hiltRule = new HiltAndroidRule(this);

    @Inject BookmarkImportComponent importComponent;
    @Inject BookmarkShareComponent shareComponent;
    @Inject PersistentDatabaseComponent dbComponent;

    // Four representative seq values that cover all data combinations.
    private static final long SEQ_BOOKMARK_ONLY = 1001200;
    private static final long SEQ_MEMO_ONLY     = 1001210;
    private static final long SEQ_TAGS_ONLY     = 1001220;
    private static final long SEQ_FULL          = 1001230;

    @Before
    public void setup() {
        hiltRule.inject();
        clearAllUserData();
    }

    /**
     * Deletes every row from DictionaryBookmark and DictionaryBookmarkTag.
     * Room does not cascade FK deletes between these two tables, so both
     * must be cleared explicitly.
     */
    private void clearAllUserData() {
        PersistentDatabase db = dbComponent.getDatabase();
        db.runInTransaction(() -> {
            List<DictionaryBookmark> all = db.dictionaryBookmarkDao().getAll();
            for (DictionaryBookmark b : all) {
                db.dictionaryBookmarkTagDao().deleteTagsForSeq(b.seq);
                db.dictionaryBookmarkDao().delete(b);
            }
        });
    }

    @Test
    public void testExportImportRoundTripPreservesBookmarksMemosTags() throws Exception {
        PersistentDatabase db = dbComponent.getDatabase();

        // ── 1. Insert test data directly into the database ────────────────────

        db.runInTransaction(() -> {
            // Bookmark only — no memo, no tags
            db.dictionaryBookmarkDao().insert(
                    new DictionaryBookmark(SEQ_BOOKMARK_ONLY, 1, null));

            // Bookmark + memo, no tags
            db.dictionaryBookmarkDao().insert(
                    new DictionaryBookmark(SEQ_MEMO_ONLY, 1, "Japanese memo どく"));

            // Bookmark + tags, no memo (tags string + individual tag rows)
            db.dictionaryBookmarkDao().insert(
                    new DictionaryBookmark(SEQ_TAGS_ONLY, 1, null, "noun,common"));
            db.dictionaryBookmarkTagDao().insertMany(Arrays.asList(
                    new DictionaryBookmarkTag(SEQ_TAGS_ONLY, "noun"),
                    new DictionaryBookmarkTag(SEQ_TAGS_ONLY, "common")));

            // Full: bookmark + memo + tags
            db.dictionaryBookmarkDao().insert(
                    new DictionaryBookmark(SEQ_FULL, 1, "Full entry memo", "verb,transitive,jlpt4"));
            db.dictionaryBookmarkTagDao().insertMany(Arrays.asList(
                    new DictionaryBookmarkTag(SEQ_FULL, "verb"),
                    new DictionaryBookmarkTag(SEQ_FULL, "transitive"),
                    new DictionaryBookmarkTag(SEQ_FULL, "jlpt4")));
        });

        // ── 2. Export to JSON ─────────────────────────────────────────────────

        File exportFile = shareComponent.writeBookmarks();

        // ── 3. Wipe everything ────────────────────────────────────────────────

        clearAllUserData();

        assertEquals("DictionaryBookmark should be empty after wipe",
                0, db.dictionaryBookmarkDao().getAll().size());
        assertEquals("DictionaryBookmarkTag should be empty after wipe",
                0, db.dictionaryBookmarkTagDao().getAllTags().size());

        // ── 4. Import the exported file back ─────────────────────────────────
        // Uri.fromFile triggers the .json fallback type detection in processURI.

        importComponent.processURI(Uri.fromFile(exportFile), 1);
        importComponent.commitBookmarks(1);

        // ── 5. Verify every bookmark, memo, and tag is restored ───────────────

        List<DictionaryBookmark> restored =
                new ArrayList<>(db.dictionaryBookmarkDao().getAll());
        assertEquals("All 4 entries must be restored", 4, restored.size());

        restored.sort((a, b) -> Long.compare(a.seq, b.seq));

        // Entry 1: bookmark only
        DictionaryBookmark r1 = restored.get(0);
        assertEquals(SEQ_BOOKMARK_ONLY, r1.seq);
        assertEquals(1L, r1.bookmark);
        assertNull("memo must be null", r1.memo);
        assertNull("tags string must be null", r1.tags);
        assertEquals("no tag rows expected",
                0, db.dictionaryBookmarkTagDao().getTagsForSeq(SEQ_BOOKMARK_ONLY).size());

        // Entry 2: bookmark + memo
        DictionaryBookmark r2 = restored.get(1);
        assertEquals(SEQ_MEMO_ONLY, r2.seq);
        assertEquals(1L, r2.bookmark);
        assertEquals("Japanese memo どく", r2.memo);
        assertNull("tags string must be null", r2.tags);
        assertEquals("no tag rows expected",
                0, db.dictionaryBookmarkTagDao().getTagsForSeq(SEQ_MEMO_ONLY).size());

        // Entry 3: bookmark + tags
        DictionaryBookmark r3 = restored.get(2);
        assertEquals(SEQ_TAGS_ONLY, r3.seq);
        assertEquals(1L, r3.bookmark);
        assertNull("memo must be null", r3.memo);
        assertEquals("noun,common", r3.tags);
        List<String> tags3 = db.dictionaryBookmarkTagDao().getTagsForSeq(SEQ_TAGS_ONLY);
        assertEquals("2 tag rows expected", 2, tags3.size());
        assertTrue("'noun' tag must be present", tags3.contains("noun"));
        assertTrue("'common' tag must be present", tags3.contains("common"));

        // Entry 4: bookmark + memo + tags
        DictionaryBookmark r4 = restored.get(3);
        assertEquals(SEQ_FULL, r4.seq);
        assertEquals(1L, r4.bookmark);
        assertEquals("Full entry memo", r4.memo);
        assertEquals("verb,transitive,jlpt4", r4.tags);
        List<String> tags4 = db.dictionaryBookmarkTagDao().getTagsForSeq(SEQ_FULL);
        assertEquals("3 tag rows expected", 3, tags4.size());
        assertTrue("'verb' tag must be present", tags4.contains("verb"));
        assertTrue("'transitive' tag must be present", tags4.contains("transitive"));
        assertTrue("'jlpt4' tag must be present", tags4.contains("jlpt4"));
    }
}
