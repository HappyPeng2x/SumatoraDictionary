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
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryBookmark;
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentDatabase;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import javax.inject.Inject;

import dagger.hilt.android.testing.HiltAndroidRule;
import dagger.hilt.android.testing.HiltAndroidTest;

/**
 * Tests BookmarkComponent.updateBookmark, which fires a Completable on Schedulers.io()
 * and returns immediately.  Each test calls the method from the main thread (as the
 * @MainThread annotation requires) via runOnMainSync, then polls the DAO until the
 * expected DB state appears or the 2-second timeout expires.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4.class)
public class BookmarkComponentTest {

    @Rule
    public HiltAndroidRule hiltRule = new HiltAndroidRule(this);

    @Inject BookmarkComponent bookmarkComponent;
    @Inject PersistentDatabaseComponent dbComponent;

    private static final long SEQ_1 = 1002100;
    private static final long SEQ_2 = 1002110;

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
        });
    }

    private void updateOnMain(DictionaryBookmark bookmark) {
        InstrumentationRegistry.getInstrumentation().runOnMainSync(
                () -> bookmarkComponent.updateBookmark(bookmark));
    }

    private DictionaryBookmark pollUntilPresent(long seq) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 2000;
        DictionaryBookmark b;
        do {
            b = db.dictionaryBookmarkDao().getBySeq(seq);
            if (b != null) return b;
            Thread.sleep(50);
        } while (System.currentTimeMillis() < deadline);
        return null;
    }

    private boolean pollUntilAbsent(long seq) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 2000;
        do {
            if (db.dictionaryBookmarkDao().getBySeq(seq) == null) return true;
            Thread.sleep(50);
        } while (System.currentTimeMillis() < deadline);
        return false;
    }

    @Test
    public void testInsertNewBookmark() throws InterruptedException {
        updateOnMain(new DictionaryBookmark(SEQ_1, 1, null, null));

        DictionaryBookmark inserted = pollUntilPresent(SEQ_1);
        assertNotNull("bookmark must be inserted", inserted);
        assertEquals(1L, inserted.bookmark);
    }

    @Test
    public void testUpdateExistingBookmark() throws InterruptedException {
        db.dictionaryBookmarkDao().insert(new DictionaryBookmark(SEQ_1, 1, "old memo", null));

        // bookmark=0 but memo non-empty → insert (replace), not delete
        updateOnMain(new DictionaryBookmark(SEQ_1, 0, "new memo", null));

        // Poll until the memo changes
        long deadline = System.currentTimeMillis() + 2000;
        DictionaryBookmark updated = null;
        do {
            DictionaryBookmark b = db.dictionaryBookmarkDao().getBySeq(SEQ_1);
            if (b != null && "new memo".equals(b.memo)) { updated = b; break; }
            Thread.sleep(50);
        } while (System.currentTimeMillis() < deadline);

        assertNotNull("entry must still exist after update", updated);
        assertEquals("new memo", updated.memo);
        assertEquals(0L, updated.bookmark);
    }

    @Test
    public void testDeleteWhenBookmarkZeroNoMemoNoTags() throws InterruptedException {
        db.dictionaryBookmarkDao().insert(new DictionaryBookmark(SEQ_1, 1, null, null));

        // All conditions false → delete
        updateOnMain(new DictionaryBookmark(SEQ_1, 0, null, null));

        assertTrue("entry must be deleted when bookmark=0 and no memo/tags",
                pollUntilAbsent(SEQ_1));
    }

    @Test
    public void testKeepWhenBookmarkZeroButHasMemo() throws InterruptedException {
        updateOnMain(new DictionaryBookmark(SEQ_1, 0, "has memo", null));

        DictionaryBookmark kept = pollUntilPresent(SEQ_1);
        assertNotNull("entry with memo must be kept even when bookmark=0", kept);
        assertEquals("has memo", kept.memo);
    }

    @Test
    public void testKeepWhenBookmarkZeroButHasTags() throws InterruptedException {
        updateOnMain(new DictionaryBookmark(SEQ_1, 0, null, "some-tag"));

        DictionaryBookmark kept = pollUntilPresent(SEQ_1);
        assertNotNull("entry with tags must be kept even when bookmark=0", kept);
        assertEquals("some-tag", kept.tags);
    }

    @Test
    public void testIndependentSeqsDoNotInterfere() throws InterruptedException {
        updateOnMain(new DictionaryBookmark(SEQ_1, 1, "first", null));
        updateOnMain(new DictionaryBookmark(SEQ_2, 1, "second", null));

        assertNotNull("SEQ_1 must be present", pollUntilPresent(SEQ_1));
        assertNotNull("SEQ_2 must be present", pollUntilPresent(SEQ_2));
    }
}
