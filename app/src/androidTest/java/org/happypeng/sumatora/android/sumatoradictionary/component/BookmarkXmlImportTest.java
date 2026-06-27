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
import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.testing.HiltAndroidRule;
import dagger.hilt.android.testing.HiltAndroidTest;

@HiltAndroidTest
@RunWith(AndroidJUnit4.class)
public class BookmarkXmlImportTest {

    @Rule
    public HiltAndroidRule hiltRule = new HiltAndroidRule(this);

    @Inject BookmarkImportComponent importComponent;
    @Inject PersistentDatabaseComponent dbComponent;

    private static final long SEQ_1 = 1001400;
    private static final long SEQ_2 = 1001410;
    private static final long SEQ_3 = 1001420;

    private Context targetContext;

    @Before
    public void setup() {
        targetContext = ApplicationProvider.getApplicationContext();
        hiltRule.inject();
        clearAllUserData();
    }

    private void clearAllUserData() {
        PersistentDatabase db = dbComponent.getDatabase();
        db.runInTransaction(() -> {
            for (DictionaryBookmark b : db.dictionaryBookmarkDao().getAll()) {
                db.dictionaryBookmarkTagDao().deleteTagsForSeq(b.seq);
                db.dictionaryBookmarkDao().delete(b);
            }
            db.dictionaryBookmarkImportDao().delete(1);
        });
    }

    private Uri xmlFileUri(String filename, String content) throws IOException {
        File f = new File(targetContext.getCacheDir(), filename);
        try (FileWriter w = new FileWriter(f)) {
            w.write(content);
        }
        return Uri.fromFile(f);
    }

    @Test
    public void testXmlImportHappyPath() throws Exception {
        String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                + "<bookmarks>"
                + "<bookmark seq=\"" + SEQ_1 + "\" bookmark=\"1\"/>"
                + "<bookmark seq=\"" + SEQ_2 + "\" bookmark=\"1\" memo=\"test memo\"/>"
                + "<bookmark seq=\"" + SEQ_3 + "\" bookmark=\"1\" memo=\"jp memo\" tags=\"noun,verb\"/>"
                + "</bookmarks>";

        importComponent.processURI(xmlFileUri("xml_happy.xml", xml), 1);
        importComponent.commitBookmarks(1);

        PersistentDatabase db = dbComponent.getDatabase();

        DictionaryBookmark b1 = db.dictionaryBookmarkDao().getBySeq(SEQ_1);
        assertNotNull(b1);
        assertEquals(1L, b1.bookmark);
        assertNull(b1.memo);
        assertNull(b1.tags);

        DictionaryBookmark b2 = db.dictionaryBookmarkDao().getBySeq(SEQ_2);
        assertNotNull(b2);
        assertEquals("test memo", b2.memo);
        assertNull(b2.tags);

        DictionaryBookmark b3 = db.dictionaryBookmarkDao().getBySeq(SEQ_3);
        assertNotNull(b3);
        assertEquals("jp memo", b3.memo);
        assertEquals("noun,verb", b3.tags);
        List<String> tags3 = db.dictionaryBookmarkTagDao().getTagsForSeq(SEQ_3);
        assertEquals(2, tags3.size());
        assertTrue(tags3.contains("noun"));
        assertTrue(tags3.contains("verb"));
    }

    @Test
    public void testXmlJapaneseMemoParsedCorrectly() throws Exception {
        String memo = "読む・どく・ひと";
        String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                + "<bookmarks>"
                + "<bookmark seq=\"" + SEQ_1 + "\" bookmark=\"1\" memo=\"" + memo + "\"/>"
                + "</bookmarks>";

        importComponent.processURI(xmlFileUri("xml_japanese.xml", xml), 1);
        importComponent.commitBookmarks(1);

        DictionaryBookmark b = dbComponent.getDatabase().dictionaryBookmarkDao().getBySeq(SEQ_1);
        assertNotNull(b);
        assertEquals(memo, b.memo);
    }

    @Test
    public void testXmlWithWrongRootDoesNotCrash() throws Exception {
        // readXML explicitly returns null for any non-"bookmarks" root element;
        // processURI must exit early — no staging rows, no committed bookmarks.
        String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?><not_bookmarks/>";

        importComponent.processURI(xmlFileUri("xml_wrong_root.xml", xml), 1);

        assertEquals("staging table must be empty after wrong root element",
                0, dbComponent.getDatabase().dictionaryBookmarkImportDao().getByRef(1).size());
        assertNull("no bookmark must be committed",
                dbComponent.getDatabase().dictionaryBookmarkDao().getBySeq(SEQ_1));
    }

    @Test
    public void testXmlWithInvalidSeqAttributeDoesNotCrash() throws Exception {
        // Long.parseLong throws NumberFormatException, caught by readXML → returns null.
        String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?><bookmarks>"
                + "<bookmark seq=\"not-a-number\" bookmark=\"1\"/>"
                + "</bookmarks>";

        importComponent.processURI(xmlFileUri("xml_bad_seq.xml", xml), 1);

        assertEquals("staging table must be empty after invalid seq attribute",
                0, dbComponent.getDatabase().dictionaryBookmarkImportDao().getByRef(1).size());
    }

    @Test
    public void testXmlExtensionFallbackRoutes() throws Exception {
        // contentResolver.getType() returns null for file:// URIs; the .xml extension fallback
        // must select the XML code path (not JSON), producing correct results.
        String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                + "<bookmarks>"
                + "<bookmark seq=\"" + SEQ_1 + "\" bookmark=\"1\" memo=\"routed-via-xml\"/>"
                + "</bookmarks>";

        importComponent.processURI(xmlFileUri("route_check.xml", xml), 1);
        importComponent.commitBookmarks(1);

        DictionaryBookmark b = dbComponent.getDatabase().dictionaryBookmarkDao().getBySeq(SEQ_1);
        assertNotNull("XML extension must route to XML parser", b);
        assertEquals("routed-via-xml", b.memo);
    }

    @Test
    public void testXmlImportMergesWithExistingEntry() throws Exception {
        PersistentDatabase db = dbComponent.getDatabase();

        db.dictionaryBookmarkDao().insert(new DictionaryBookmark(SEQ_1, 1, "keep this memo", null));

        // Import bookmark=0 for the same seq — MAX(1,0) must keep 1; absent memo must preserve existing.
        String xml = "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                + "<bookmarks>"
                + "<bookmark seq=\"" + SEQ_1 + "\" bookmark=\"0\"/>"
                + "</bookmarks>";

        importComponent.processURI(xmlFileUri("xml_merge.xml", xml), 1);
        importComponent.commitBookmarks(1);

        DictionaryBookmark merged = db.dictionaryBookmarkDao().getBySeq(SEQ_1);
        assertNotNull(merged);
        assertEquals("bookmark MAX must keep 1", 1L, merged.bookmark);
        assertEquals("memo must be preserved when import has none", "keep this memo", merged.memo);
    }
}
