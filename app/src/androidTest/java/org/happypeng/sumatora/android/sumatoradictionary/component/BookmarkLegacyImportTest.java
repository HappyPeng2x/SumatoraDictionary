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
import androidx.test.platform.app.InstrumentationRegistry;

import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryBookmark;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryBookmarkTag;
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentDatabase;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.testing.HiltAndroidRule;
import dagger.hilt.android.testing.HiltAndroidTest;

/**
 * Tests that JSON exported by previous versions of the app (without the "tags"
 * field, matching the format before tag support was added) can be imported into
 * the current version without crashing and with correct merge semantics.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4.class)
public class BookmarkLegacyImportTest {

    @Rule
    public HiltAndroidRule hiltRule = new HiltAndroidRule(this);

    @Inject BookmarkImportComponent importComponent;
    @Inject PersistentDatabaseComponent dbComponent;

    private static final long SEQ_BOOKMARK_ONLY  = 1001300;
    private static final long SEQ_WITH_MEMO      = 1001310;
    private static final long SEQ_WITH_TAGS      = 1001320;

    private Context targetContext;
    private Context testContext;

    @Before
    public void setup() {
        targetContext = ApplicationProvider.getApplicationContext();
        testContext = InstrumentationRegistry.getInstrumentation().getContext();
        hiltRule.inject();
        clearAllUserData();
    }

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

    /**
     * Import a JSON file that matches the format exported by app versions before
     * tag support was added: objects contain only "seq", "bookmark", and optionally
     * "memo" — no "tags" field at all.
     */
    @Test
    public void testLegacyJsonWithoutTagsImportsCorrectly() throws Exception {
        Uri uri = getAssetUri("legacy_no_tags.json");
        importComponent.processURI(uri, 1);
        importComponent.commitBookmarks(1);

        PersistentDatabase db = dbComponent.getDatabase();
        List<DictionaryBookmark> all = db.dictionaryBookmarkDao().getAll();
        assertEquals("All 3 legacy entries must be imported", 3, all.size());

        DictionaryBookmark bookmarkOnly = db.dictionaryBookmarkDao().getBySeq(SEQ_BOOKMARK_ONLY);
        assertEquals(1L, bookmarkOnly.bookmark);
        assertNull("memo must be null for bookmark-only entry", bookmarkOnly.memo);
        assertNull("tags must be null for legacy entry without tags", bookmarkOnly.tags);

        DictionaryBookmark withMemo = db.dictionaryBookmarkDao().getBySeq(SEQ_WITH_MEMO);
        assertEquals(1L, withMemo.bookmark);
        assertEquals("legacy memo どく", withMemo.memo);
        assertNull("tags must be null for legacy entry without tags", withMemo.tags);

        DictionaryBookmark third = db.dictionaryBookmarkDao().getBySeq(SEQ_WITH_TAGS);
        assertEquals(1L, third.bookmark);
        assertNull("memo must be null", third.memo);
        assertNull("tags must be null for legacy entry without tags", third.tags);

        assertEquals("No tag rows expected for legacy import",
                0, db.dictionaryBookmarkTagDao().getAllTags().size());
    }

    /**
     * When a legacy (no-tags) JSON is imported over existing entries that DO have
     * tags, the existing tags must be preserved (not wiped by the tagless import).
     */
    @Test
    public void testLegacyImportPreservesExistingTags() throws Exception {
        PersistentDatabase db = dbComponent.getDatabase();

        // Pre-insert an entry with tags
        db.runInTransaction(() -> {
            db.dictionaryBookmarkDao().insert(
                    new DictionaryBookmark(SEQ_BOOKMARK_ONLY, 1, "existing memo", "noun,verb"));
            db.dictionaryBookmarkTagDao().insertMany(Arrays.asList(
                    new DictionaryBookmarkTag(SEQ_BOOKMARK_ONLY, "noun"),
                    new DictionaryBookmarkTag(SEQ_BOOKMARK_ONLY, "verb")));
        });

        // Import legacy JSON for the same seq — no "tags" field
        Uri uri = getAssetUri("legacy_no_tags.json");
        importComponent.processURI(uri, 1);
        importComponent.commitBookmarks(1);

        // Tags and existing memo must be preserved since the import had no tags/memo for that entry
        DictionaryBookmark merged = db.dictionaryBookmarkDao().getBySeq(SEQ_BOOKMARK_ONLY);
        assertEquals(1L, merged.bookmark);
        assertEquals("existing memo should be preserved when import has no memo",
                "existing memo", merged.memo);
        assertEquals("tags string must be preserved when import has no tags",
                "noun,verb", merged.tags);

        List<String> tagRows = db.dictionaryBookmarkTagDao().getTagsForSeq(SEQ_BOOKMARK_ONLY);
        assertEquals("Tag rows must be preserved", 2, tagRows.size());
    }

    /**
     * When a legacy import has a memo and an existing entry also has a memo,
     * the import memo takes precedence (non-empty imported memo wins).
     */
    @Test
    public void testLegacyImportUpdatesExistingMemo() throws Exception {
        PersistentDatabase db = dbComponent.getDatabase();

        db.runInTransaction(() -> {
            db.dictionaryBookmarkDao().insert(
                    new DictionaryBookmark(SEQ_WITH_MEMO, 1, "old memo", null));
        });

        Uri uri = getAssetUri("legacy_no_tags.json");
        importComponent.processURI(uri, 1);
        importComponent.commitBookmarks(1);

        DictionaryBookmark merged = db.dictionaryBookmarkDao().getBySeq(SEQ_WITH_MEMO);
        assertEquals("imported memo must overwrite old memo",
                "legacy memo どく", merged.memo);
        assertNull("tags must remain null", merged.tags);
    }

    private Uri getAssetUri(String assetName) throws IOException {
        File tempFile = new File(targetContext.getCacheDir(), assetName);
        try (InputStream is = testContext.getAssets().open(assetName);
             FileOutputStream fos = new FileOutputStream(tempFile)) {
            byte[] buffer = new byte[1024];
            int read;
            while ((read = is.read(buffer)) != -1) {
                fos.write(buffer, 0, read);
            }
        }
        return Uri.fromFile(tempFile);
    }
}
