/* Sumatora Dictionary
        Copyright (C) 2019 Nicolas Centa

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

package org.happypeng.sumatora.android.sumatoradictionary.db.tools;

import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.sqlite.db.SupportSQLiteStatement;

import org.happypeng.sumatora.android.sumatoradictionary.component.PersistentDatabaseComponent;
import org.happypeng.sumatora.android.sumatoradictionary.db.InstalledDictionary;
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentDatabase;
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentLanguageSettings;

import java.io.IOException;
import java.util.List;

// Populates DictionarySearchElement from DictionaryBookmarkImport (an in-review bookmark import
// batch, keyed by JMdict seq like DictionaryBookmark) so the import-review UI can list the
// entries about to be imported using the normal paged search-element display path. No form_id
// dependency here (it's a listing by seq, not a text search), but the render payload still needs
// the current language settings, same as the bookmark-listing tier this mirrors.
public class BookmarkImportQueryTool {
    static final String SQL_QUERY_INSERT_DISPLAY_ELEMENT =
            "INSERT OR IGNORE INTO DictionarySearchElement "
                    + "(ref, entryOrder, entry_id, seq, form_id, match_kind, original_query, matched_text, "
                    + "dictionary_form, deinflection_label, rank, bookmark, memo, tags, render_json) "
                    + "SELECT ? AS ref, 0 AS entryOrder, Entry.entry_id, DictionaryBookmarkImport.seq, NULL AS form_id, "
                    + "'bookmark_import' AS match_kind, NULL AS original_query, NULL AS matched_text, "
                    + "NULL, NULL, (0 - Entry.score) AS rank, "
                    + "DictionaryBookmarkImport.bookmark, DictionaryBookmarkImport.memo, DictionaryBookmarkImport.tags, "
                    + "%s "
                    + "FROM DictionaryBookmarkImport "
                    + "JOIN core.Entry ON CAST(Entry.source_key AS INTEGER) = DictionaryBookmarkImport.seq";

    private final PersistentDatabaseComponent persistentDatabaseComponent;
    private final int key;
    private final PersistentLanguageSettings persistentLanguageSettings;

    private SupportSQLiteStatement deleteStatement;
    private SupportSQLiteStatement queryStatement;

    public BookmarkImportQueryTool(final PersistentDatabaseComponent persistentDatabaseComponent, final int key,
                                   final PersistentLanguageSettings persistentLanguageSettings) {
        this.persistentDatabaseComponent = persistentDatabaseComponent;
        this.key = key;
        this.persistentLanguageSettings = persistentLanguageSettings;

        initialize();
    }

    public synchronized void initialize() {
        final PersistentDatabase db = persistentDatabaseComponent.getDatabase();

        final List<InstalledDictionary> installedDictionaries = db.installedDictionaryDao().getAll();
        final boolean glossInstalled = DictionarySearchQueryTool.isInstalled(installedDictionaries, "gloss", persistentLanguageSettings.lang);
        final boolean glossBackupInstalled = persistentLanguageSettings.backupLang != null
                && DictionarySearchQueryTool.isInstalled(installedDictionaries, "gloss", persistentLanguageSettings.backupLang);
        @Nullable final String glossAliasOrNull = glossInstalled ? DictionarySearchQueryTool.glossAlias(persistentLanguageSettings.lang) : null;
        @Nullable final String backupGlossAliasOrNull = glossBackupInstalled ? DictionarySearchQueryTool.glossAlias(persistentLanguageSettings.backupLang) : null;
        final String renderJsonExpr = DictionarySearchQueryTool.buildRenderJsonExpr("Entry.entry_id", glossAliasOrNull, backupGlossAliasOrNull);

        deleteStatement = db.compileStatement(DictionarySearchQueryTool.SQL_QUERY_DELETE);
        queryStatement = db.compileStatement(String.format(SQL_QUERY_INSERT_DISPLAY_ELEMENT, renderJsonExpr));
    }

    public void delete() {
        deleteStatement.bindLong(1, key);
        deleteStatement.execute();
    }

    public boolean execute() {
        queryStatement.bindLong(1, key);

        long insert = queryStatement.executeInsert();

        return insert >= 0;
    }

    public void close() {
        if (queryStatement != null) {
            try {
                queryStatement.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            queryStatement = null;
        }

        if (deleteStatement != null) {
            try {
                deleteStatement.close();
            } catch (IOException e) {
                e.printStackTrace();
            }

            deleteStatement = null;
        }
    }

    public int getKey() {
        return key;
    }
}
