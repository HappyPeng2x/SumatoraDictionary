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

import androidx.annotation.WorkerThread;
import androidx.sqlite.db.SupportSQLiteStatement;

import org.happypeng.sumatora.android.sumatoradictionary.component.PersistentDatabaseComponent;
import org.happypeng.sumatora.android.sumatoradictionary.db.InstalledDictionary;
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentDatabase;
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentLanguageSettings;

import java.io.IOException;
import java.util.List;

public class BookmarkImportQueryTool {
    static final String SQL_QUERY_INSERT_DISPLAY_ELEMENT =
            "INSERT OR IGNORE INTO DictionarySearchElement "
                    + "SELECT ? AS ref, "
                    + "0 AS entryOrder, "
                    + "DictionaryEntry.seq, "
                    + "DictionaryEntry.readingsPrio, "
                    + "DictionaryEntry.readings, "
                    + "DictionaryEntry.writingsPrio, "
                    + "DictionaryEntry.writings, "
                    + "DictionaryEntry.pos, "
                    + "DictionaryEntry.xref, "
                    + "DictionaryEntry.ant, "
                    + "DictionaryEntry.misc, "
                    + "DictionaryEntry.lsource, "
                    + "DictionaryEntry.dial, "
                    + "DictionaryEntry.s_inf, "
                    + "DictionaryEntry.field, "
                    + "? AS lang, "
                    + "? AS lang_setting, "
                    + "json_group_array(DictionaryTranslation.gloss) AS gloss, "
                    + "%s as example_sentences, "
                    + "%s as example_translations, "
                    + "0, "
                    + "DictionaryBookmarkImport.memo "
                    + "FROM jmdict.DictionaryEntry %s, "
                    + "%s.DictionaryTranslation, "
                    + "DictionaryBookmarkImport "
                    + "WHERE DictionaryEntry.seq = DictionaryTranslation.seq AND "
                    + "DictionaryEntry.seq = DictionaryBookmarkImport.seq "
                    + "GROUP BY DictionaryEntry.seq";

    private final PersistentDatabaseComponent persistentDatabaseComponent;
    private final int key;
    private final PersistentLanguageSettings persistentLanguageSettings;

    private SupportSQLiteStatement deleteStatement;
    private SupportSQLiteStatement queryStatement;
    private SupportSQLiteStatement queryStatementBackup;

    public BookmarkImportQueryTool(final PersistentDatabaseComponent persistentDatabaseComponent, final int key,
                                   final PersistentLanguageSettings persistentLanguageSettings) {
        this.persistentDatabaseComponent = persistentDatabaseComponent;
        this.key = key;
        this.persistentLanguageSettings = persistentLanguageSettings;

        initialize();
    }

    public synchronized void initialize() {
        final PersistentDatabase db = persistentDatabaseComponent.getDatabase();

        deleteStatement = db.compileStatement(DictionarySearchQueryTool.SQL_QUERY_DELETE);

        final List<InstalledDictionary> installedDictionaries = db.installedDictionaryDao().getAll();

        String examplesQuerySentences = "null";
        String examplesQueryTranslations = "null";
        String examplesLeftJoin = "";

        String backupExamplesQuery = "null";
        String backupExamplesQueryTranslations = "null";
        String backupExamplesLeftJoin = "";

        for (InstalledDictionary d : installedDictionaries) {
            if ("tatoeba".equals(d.getType()) && persistentLanguageSettings.lang.equals(d.getLang())) {
                examplesQuerySentences = DictionarySearchQueryTool.SQL_QUERY_EXAMPLE_SENTENCES;
                examplesQueryTranslations = DictionarySearchQueryTool.SQL_QUERY_EXAMPLE_TRANSLATIONS;
                examplesLeftJoin = String.format(DictionarySearchQueryTool.SQL_QUERY_JOIN_EXAMPLES, "examples_" + d.getLang());
            }
        }

        queryStatement =
                db.compileStatement(String.format(SQL_QUERY_INSERT_DISPLAY_ELEMENT,
                        examplesQuerySentences, examplesQueryTranslations, examplesLeftJoin, persistentLanguageSettings.lang));

        if (persistentLanguageSettings.backupLang != null) {
            queryStatementBackup =
                    db.compileStatement(String.format(SQL_QUERY_INSERT_DISPLAY_ELEMENT,
                            backupExamplesQuery, backupExamplesQueryTranslations, backupExamplesLeftJoin, persistentLanguageSettings.backupLang));
        }
    }

    public void delete() {
        deleteStatement.bindLong(1, key);
        deleteStatement.execute();
    }

    public boolean execute() {
        long backupInsert = -1;

        queryStatement.bindLong(1, key);
        queryStatement.bindString(2, persistentLanguageSettings.lang);
        queryStatement.bindString(3, persistentLanguageSettings.lang);

        long insert = queryStatement.executeInsert();

        if (queryStatementBackup != null) {
            queryStatementBackup.bindLong(1, key);
            queryStatementBackup.bindString(2, persistentLanguageSettings.backupLang);
            queryStatementBackup.bindString(3, persistentLanguageSettings.lang);

            backupInsert = queryStatementBackup.executeInsert();
        }

        return (insert >= 0) || (backupInsert >= 0);
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
