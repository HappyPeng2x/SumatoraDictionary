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

import static org.junit.Assert.assertEquals;
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

    @Test
    public void detachIncompatible_flagsOldGlossPack() {
        InstalledDictionary oldGloss = new InstalledDictionary(
                new File(installDir, "gloss_old.db").getAbsolutePath(),
                "test", "gloss", "_dt_old", 12, 20200101);
        db.installedDictionaryDao().insert(oldGloss);

        DictionaryControlInfo info = new DictionaryControlInfo();
        PersistentDatabaseInitialization.detachIncompatiblePacks(db, info);

        InstalledDictionary result = db.installedDictionaryDao().getForTypeLang("gloss", "_dt_old");
        assertEquals("old gloss pack should be reset to version 0", 0, result.version);
        assertTrue("old gloss pack should be in incompatiblePacks",
                info.incompatiblePacks.contains("gloss__dt_old"));

        db.installedDictionaryDao().delete(result);
    }

    @Test
    public void detachIncompatible_keepsNewGlossPack() {
        InstalledDictionary newGloss = new InstalledDictionary(
                new File(installDir, "gloss_new.db").getAbsolutePath(),
                "test", "gloss", "_dt_new", 18, 20260726);
        db.installedDictionaryDao().insert(newGloss);

        DictionaryControlInfo info = new DictionaryControlInfo();
        PersistentDatabaseInitialization.detachIncompatiblePacks(db, info);

        InstalledDictionary result = db.installedDictionaryDao().getForTypeLang("gloss", "_dt_new");
        assertEquals("new gloss pack should keep its version", 18, result.version);
        assertTrue("new gloss pack should NOT be in incompatiblePacks",
                info.incompatiblePacks.isEmpty());

        db.installedDictionaryDao().delete(result);
    }

    @Test
    public void detachIncompatible_alreadyFlaggedStillReported() {
        InstalledDictionary flagged = new InstalledDictionary(
                new File(installDir, "gloss_flagged.db").getAbsolutePath(),
                "test", "gloss", "_dt_flagged", 0, 0);
        db.installedDictionaryDao().insert(flagged);

        DictionaryControlInfo info = new DictionaryControlInfo();
        PersistentDatabaseInitialization.detachIncompatiblePacks(db, info);

        InstalledDictionary result = db.installedDictionaryDao().getForTypeLang("gloss", "_dt_flagged");
        assertEquals("already-flagged pack should stay at version 0", 0, result.version);
        assertTrue("already-flagged pack should still be in incompatiblePacks",
                info.incompatiblePacks.contains("gloss__dt_flagged"));

        db.installedDictionaryDao().delete(result);
    }

    @Test
    public void detachIncompatible_ignoresNonGlossTypes() {
        InstalledDictionary core = new InstalledDictionary(
                new File(installDir, "core_test.db").getAbsolutePath(),
                "test", "core", "_dt_core", 1, 20200101);
        db.installedDictionaryDao().insert(core);

        DictionaryControlInfo info = new DictionaryControlInfo();
        PersistentDatabaseInitialization.detachIncompatiblePacks(db, info);

        InstalledDictionary result = db.installedDictionaryDao().getForTypeLang("core", "_dt_core");
        assertEquals("non-gloss pack should keep its version", 1, result.version);
        assertTrue("non-gloss pack should NOT be in incompatiblePacks",
                info.incompatiblePacks.isEmpty());

        db.installedDictionaryDao().delete(result);
    }
}
