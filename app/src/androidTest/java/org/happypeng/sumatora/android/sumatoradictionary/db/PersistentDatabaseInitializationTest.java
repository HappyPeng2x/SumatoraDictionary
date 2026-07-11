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
        along with this program.  If not, see <http://www.gnu.org/licenses/>. */

package org.happypeng.sumatora.android.sumatoradictionary.db;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.happypeng.sumatora.android.sumatoradictionary.component.PersistentDatabaseComponent;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import javax.inject.Inject;

import dagger.hilt.android.testing.HiltAndroidRule;
import dagger.hilt.android.testing.HiltAndroidTest;

// Covers the file-leak half of the schema v1 -> v2 upgrade: MIGRATION_9_10 recreates
// InstalledDictionary from scratch (see PersistentDatabaseMigrationTest), so a pre-migration
// row's backing file has nothing left pointing at it after upgrade. cleanupOrphanedDictionaryFiles
// is what's supposed to reclaim that.
@HiltAndroidTest
@RunWith(AndroidJUnit4.class)
public class PersistentDatabaseInitializationTest {

    @Rule
    public HiltAndroidRule hiltRule = new HiltAndroidRule(this);

    @Inject PersistentDatabaseComponent dbComponent;

    private Context context;
    private PersistentDatabase db;
    private File installDir;
    private File orphanFile;
    private File keptFile;

    @Before
    public void setup() throws IOException {
        hiltRule.inject();
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        db = dbComponent.getDatabase();

        File databaseRoot = context.getDatabasePath(PersistentDatabaseParameters.PERSISTENT_DATABASE_NAME)
                .getParentFile();
        installDir = new File(databaseRoot, "dictionaries");
        installDir.mkdirs();

        orphanFile = new File(installDir, "orphan_test.db");
        keptFile = new File(installDir, "kept_test.db");
        writeFile(orphanFile);
        writeFile(keptFile);

        InstalledDictionary kept = new InstalledDictionary(
                keptFile.getAbsolutePath(), "test", "_cleanup_test", "", 1, 1);
        db.installedDictionaryDao().insert(kept);
    }

    @After
    public void tearDown() {
        db.installedDictionaryDao().delete(
                db.installedDictionaryDao().getForTypeLang("_cleanup_test", ""));
        orphanFile.delete();
        keptFile.delete();
    }

    private static void writeFile(File f) throws IOException {
        try (FileOutputStream out = new FileOutputStream(f)) {
            out.write(new byte[]{1, 2, 3});
        }
    }

    @Test
    public void cleanup_deletesUnreferencedFile_keepsReferencedFile() {
        assertTrue(orphanFile.exists());
        assertTrue(keptFile.exists());

        PersistentDatabaseInitialization.cleanupOrphanedDictionaryFiles(context, db);

        assertFalse("file with no InstalledDictionary row should be deleted", orphanFile.exists());
        assertTrue("file a current InstalledDictionary row points at should survive", keptFile.exists());
    }
}
