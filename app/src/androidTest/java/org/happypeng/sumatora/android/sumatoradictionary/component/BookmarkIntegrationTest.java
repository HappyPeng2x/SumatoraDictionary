package org.happypeng.sumatora.android.sumatoradictionary.component;

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryBookmark;
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentDatabase;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.testing.HiltAndroidRule;
import dagger.hilt.android.testing.HiltAndroidTest;

@HiltAndroidTest
@RunWith(AndroidJUnit4.class)
public class BookmarkIntegrationTest {

    @Rule
    public HiltAndroidRule hiltRule = new HiltAndroidRule(this);

    @Inject BookmarkImportComponent importComponent;
    @Inject BookmarkShareComponent shareComponent;
    @Inject PersistentDatabaseComponent dbComponent;

    private Context targetContext;
    private Context testContext;
    private ObjectMapper mapper;

    @Before
    public void init() {
        targetContext = ApplicationProvider.getApplicationContext();
        testContext = InstrumentationRegistry.getInstrumentation().getContext();
        hiltRule.inject();
        mapper = new ObjectMapper();

        // Clear the database before each test to ensure a blank state
        PersistentDatabase db = dbComponent.getDatabase();
        db.runInTransaction(() -> {
            List<DictionaryBookmark> all = db.dictionaryBookmarkDao().getAll();
            for (DictionaryBookmark b : all) {
                db.dictionaryBookmarkDao().delete(b);
            }
        });
    }

    @Test
    public void testImportSequenceProducesExpectedExport() throws Exception {
        // 1. Import first file (key 1) - contains initial bookmarks and memos
        Uri uri1 = getAssetUri("import1.json");
        importComponent.processURI(uri1, 1);
        importComponent.commitBookmarks(1);

        // 2. Import second file (key 2) - contains updates (some empty memos, some new bookmarks)
        Uri uri2 = getAssetUri("import2.json");
        importComponent.processURI(uri2, 2);
        importComponent.commitBookmarks(2);

        // 3. Export the resulting state to JSON
        File exportedFile = shareComponent.writeBookmarks();

        // 4. Load expected result from assets and compare
        // Jackson's readTree allows for order-independent comparison
        JsonNode expectedJson = mapper.readTree(testContext.getAssets().open("expected_result.json"));
        JsonNode actualJson = mapper.readTree(exportedFile);

        assertEquals("The exported JSON does not match the expected merged result", expectedJson, actualJson);
    }

    /**
     * Helper to copy an asset to a temporary file and return its Uri.
     * This works with the fallback logic in BookmarkImportComponent to identify JSON files.
     */
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
