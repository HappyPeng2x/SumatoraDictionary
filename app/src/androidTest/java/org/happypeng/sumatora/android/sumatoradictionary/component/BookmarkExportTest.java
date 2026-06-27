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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryBookmark;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryBookmarkTag;
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentDatabase;
import org.happypeng.sumatora.core.bookmark.Bookmark;
import org.happypeng.sumatora.core.bookmark.BookmarkImportExportService;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
import java.util.Arrays;
import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.testing.HiltAndroidRule;
import dagger.hilt.android.testing.HiltAndroidTest;

@HiltAndroidTest
@RunWith(AndroidJUnit4.class)
public class BookmarkExportTest {

    @Rule
    public HiltAndroidRule hiltRule = new HiltAndroidRule(this);

    @Inject BookmarkShareComponent shareComponent;
    @Inject PersistentDatabaseComponent dbComponent;

    private static final long SEQ_NO_EXTRAS  = 1001900;
    private static final long SEQ_WITH_MEMO  = 1001910;
    private static final long SEQ_WITH_TAGS  = 1001920;
    private static final long SEQ_FULL       = 1001930;

    private PersistentDatabase db;
    private ObjectMapper mapper;

    @Before
    public void setup() {
        hiltRule.inject();
        db = dbComponent.getDatabase();
        mapper = new ObjectMapper();
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

    @Test
    public void testExportedJsonIsParseableByReadBookmarks() throws Exception {
        db.dictionaryBookmarkDao().insert(new DictionaryBookmark(SEQ_WITH_MEMO, 1, "memo text", null));

        File exported = shareComponent.writeBookmarks();

        try (FileInputStream fis = new FileInputStream(exported)) {
            List<Bookmark> parsed = BookmarkImportExportService.readBookmarks(fis);
            assertNotNull("readBookmarks must not return null", parsed);
            assertEquals(1, parsed.size());
            assertEquals(SEQ_WITH_MEMO, parsed.get(0).seq);
            assertEquals(1L, parsed.get(0).bookmark);
            assertEquals("memo text", parsed.get(0).memo);
        }
    }

    @Test
    public void testNullFieldsAbsentFromJsonOutput() throws Exception {
        // @JsonInclude(NON_NULL) on Bookmark means null memo/tags must not appear as JSON keys.
        db.dictionaryBookmarkDao().insert(new DictionaryBookmark(SEQ_NO_EXTRAS, 1, null, null));

        File exported = shareComponent.writeBookmarks();
        JsonNode root = mapper.readTree(exported);

        assertTrue("root must be an array", root.isArray());
        assertEquals(1, root.size());

        JsonNode entry = root.get(0);
        assertTrue("seq must be present", entry.has("seq"));
        assertTrue("bookmark must be present", entry.has("bookmark"));
        assertFalse("null memo must not appear as a key in JSON", entry.has("memo"));
        assertFalse("null tags must not appear as a key in JSON", entry.has("tags"));
    }

    @Test
    public void testEmptyBookmarkListExportsEmptyArray() throws Exception {
        // DB is empty after clearAllUserData
        File exported = shareComponent.writeBookmarks();
        JsonNode root = mapper.readTree(exported);

        assertTrue("root must be an array", root.isArray());
        assertEquals("empty DB must export as empty array", 0, root.size());
    }

    @Test
    public void testAllFieldsIncludedWhenPresent() throws Exception {
        db.runInTransaction(() -> {
            db.dictionaryBookmarkDao().insert(
                    new DictionaryBookmark(SEQ_FULL, 1, "full memo", "noun,verb"));
            db.dictionaryBookmarkTagDao().insertMany(Arrays.asList(
                    new DictionaryBookmarkTag(SEQ_FULL, "noun"),
                    new DictionaryBookmarkTag(SEQ_FULL, "verb")));
        });

        File exported = shareComponent.writeBookmarks();
        JsonNode root = mapper.readTree(exported);

        assertEquals(1, root.size());
        JsonNode entry = root.get(0);
        assertEquals(SEQ_FULL, entry.get("seq").asLong());
        assertEquals(1L, entry.get("bookmark").asLong());
        assertEquals("full memo", entry.get("memo").asText());
        assertEquals("noun,verb", entry.get("tags").asText());
    }

    @Test
    public void testExportContainsAllBookmarks() throws Exception {
        db.runInTransaction(() -> {
            db.dictionaryBookmarkDao().insert(new DictionaryBookmark(SEQ_NO_EXTRAS, 1, null, null));
            db.dictionaryBookmarkDao().insert(new DictionaryBookmark(SEQ_WITH_MEMO, 1, "m", null));
            db.dictionaryBookmarkDao().insert(new DictionaryBookmark(SEQ_WITH_TAGS, 1, null, "t"));
            db.dictionaryBookmarkDao().insert(new DictionaryBookmark(SEQ_FULL, 1, "m", "t"));
        });

        File exported = shareComponent.writeBookmarks();
        JsonNode root = mapper.readTree(exported);

        assertEquals("all 4 bookmarks must appear in export", 4, root.size());
    }
}
