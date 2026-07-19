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

import android.database.Cursor;

import androidx.room.testing.MigrationTestHelper;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

// Exercises the exact upgrade path a real 0.4.7.6 install goes through: schema v9 (the last
// released version, per git tag fdroid-0.4.7.6) -> v10 (this build), via the squashed
// MIGRATION_9_10 (see PersistentDatabaseParameters - originally several unreleased 9->14 steps,
// squashed into one since no released version ever saw the intermediate versions).
@RunWith(AndroidJUnit4.class)
public class PersistentDatabaseMigrationTest {
    private static final String TEST_DB = "migration-test";

    @Rule
    public MigrationTestHelper helper = new MigrationTestHelper(
            InstrumentationRegistry.getInstrumentation(),
            PersistentDatabase.class.getCanonicalName(),
            new FrameworkSQLiteOpenHelperFactory());

    @Test
    public void migrate9To10_preservesBookmarksAndValidatesSchema() throws IOException {
        SupportSQLiteDatabase v9 = helper.createDatabase(TEST_DB, 9);
        v9.execSQL("INSERT INTO DictionaryBookmark (seq, bookmark, memo, tags) "
                + "VALUES (1234, 1, 'test memo', '')");
        // A pre-migration installed pack under the old schema's type name - exactly what a real
        // 0.4.7.6 install has lying around before upgrading.
        v9.execSQL("INSERT INTO InstalledDictionary (description, type, lang, version, date, file) "
                + "VALUES ('Index', 'jmdict', '', 5, 20260701, '/data/dictionaries/jmdict.db')");
        v9.close();

        // Throws if the post-migration schema doesn't match what Room expects for version 10
        // (app/schemas/.../10.json) - that's the main safety net here.
        SupportSQLiteDatabase v10 = helper.runMigrationsAndValidate(
                TEST_DB, 10, true, PersistentDatabaseParameters.MIGRATION_9_10);

        Cursor bookmarkCursor = v10.query("SELECT seq, bookmark, memo FROM DictionaryBookmark");
        assertEquals(1, bookmarkCursor.getCount());
        bookmarkCursor.moveToFirst();
        assertEquals(1234, bookmarkCursor.getInt(0));
        assertEquals(1, bookmarkCursor.getInt(1));
        assertEquals("test memo", bookmarkCursor.getString(2));
        bookmarkCursor.close();

        // InstalledDictionary is intentionally recreated from scratch by this migration (see its
        // comment in PersistentDatabaseParameters) rather than incrementally altered, since the
        // old rows point at pre-SumatoraIndex-schema files the new query code can't read anyway.
        // PersistentDatabaseInitialization.cleanupOrphanedDictionaryFiles() is what reclaims the
        // now-unreferenced files those old rows used to point at (see
        // PersistentDatabaseInitializationTest).
        Cursor installedCursor = v10.query("SELECT COUNT(*) FROM InstalledDictionary");
        installedCursor.moveToFirst();
        assertEquals(0, installedCursor.getInt(0));
        installedCursor.close();
    }

    // MIGRATION_10_11 adds DictionarySearchElement.render_json (see PersistentDatabaseParameters)
    // so every search tier can precompute its row's list-card display payload at insert time
    // instead of a separate live per-row fetch at bind time - see
    // DictionarySearchQueryTool.buildRenderJsonExpr. It's a plain ADD COLUMN, so this checks a
    // pre-migration row survives untouched (with render_json defaulting to NULL) and that the new
    // column actually accepts data afterward.
    @Test
    public void migrate10To11_addsRenderJsonColumnAndPreservesExistingRows() throws IOException {
        SupportSQLiteDatabase v10 = helper.createDatabase(TEST_DB, 10);
        v10.execSQL("INSERT INTO DictionarySearchElement "
                + "(ref, entryOrder, entry_id, seq, form_id, match_kind, original_query, matched_text, "
                + "dictionary_form, deinflection_label, rank, bookmark, memo, tags) "
                + "VALUES (1, 0, 42, 1001, NULL, 'bookmark', '', '', NULL, NULL, 0, 1, NULL, NULL)");
        v10.close();

        // Throws if the post-migration schema doesn't match what Room expects for version 11
        // (app/schemas/.../11.json) - that's the main safety net here.
        SupportSQLiteDatabase v11 = helper.runMigrationsAndValidate(
                TEST_DB, 11, true, PersistentDatabaseParameters.MIGRATION_10_11);

        Cursor preMigrationRow = v11.query("SELECT entry_id, seq, render_json FROM DictionarySearchElement WHERE entry_id = 42");
        assertEquals(1, preMigrationRow.getCount());
        preMigrationRow.moveToFirst();
        assertEquals(42, preMigrationRow.getInt(0));
        assertEquals(1001, preMigrationRow.getInt(1));
        assertTrue(preMigrationRow.isNull(2));
        preMigrationRow.close();

        v11.execSQL("INSERT INTO DictionarySearchElement "
                + "(ref, entryOrder, entry_id, seq, form_id, match_kind, original_query, matched_text, "
                + "dictionary_form, deinflection_label, rank, bookmark, memo, tags, render_json) "
                + "VALUES (2, 0, 43, 1002, NULL, 'bookmark', '', '', NULL, NULL, 0, 1, NULL, NULL, '{\"primaryText\":\"test\"}')");

        Cursor newRow = v11.query("SELECT render_json FROM DictionarySearchElement WHERE entry_id = 43");
        newRow.moveToFirst();
        assertEquals("{\"primaryText\":\"test\"}", newRow.getString(0));
        newRow.close();
    }
}
