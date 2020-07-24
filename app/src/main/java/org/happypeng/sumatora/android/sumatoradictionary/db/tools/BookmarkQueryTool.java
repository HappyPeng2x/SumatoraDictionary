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

import androidx.sqlite.db.SupportSQLiteStatement;

import org.happypeng.sumatora.android.sumatoradictionary.component.PersistentDatabaseComponent;
import org.happypeng.sumatora.android.sumatoradictionary.db.InstalledDictionary;
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentDatabase;
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentLanguageSettings;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

public class BookmarkQueryTool extends DictionarySearchQueryTool {
    private final static String WHERE_CLAUSE = "((? AND IFNULL(DictionaryBookmark.bookmark, 0) > 0) OR (? AND DictionaryBookmark.memo IS NOT NULL))";

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
                    + "DictionaryBookmark.bookmark, "
                    + "DictionaryBookmark.memo "
                    + "FROM jmdict.DictionaryEntry %s, "
                    + "%s.DictionaryTranslation, "
                    + "DictionaryBookmark "
                    + "WHERE DictionaryEntry.seq = DictionaryTranslation.seq AND "
                    + "DictionaryEntry.seq = DictionaryBookmark.seq "
                    + "AND " + WHERE_CLAUSE + " "
                    + "GROUP BY DictionaryEntry.seq";

    private SupportSQLiteStatement queryStatement;
    private SupportSQLiteStatement queryStatementBackup;

    public BookmarkQueryTool(final PersistentDatabaseComponent persistentDatabaseComponent, final int key,
                             final PersistentLanguageSettings languageSettings) {
        super(persistentDatabaseComponent, key, WHERE_CLAUSE, languageSettings);

        initialize();
    }

    private void initialize() {
        final PersistentDatabase db = persistentDatabase.getDatabase();

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

    @Override
    public boolean execute(final String term, final int number) {
        return execute(term, number, true, false);
    }

    public boolean execute(final String term, final int number, final boolean isBookmarked, final boolean hasMemo) {
        final List<Object> parameters = new LinkedList<>();

        parameters.add(isBookmarked);
        parameters.add(hasMemo);

        if ("".equals(term)) {
            final ValueHolder<Boolean> result = new ValueHolder<>(false);

            persistentDatabase.getDatabase().runInTransaction(() -> {
                queryStatement.bindLong(1, getKey());
                queryStatement.bindString(2, persistentLanguageSettings.lang);
                queryStatement.bindString(3, persistentLanguageSettings.lang);

                QueryStatement.bind(queryStatement, parameters, 4);

                result.setValue(queryStatement.executeInsert() > -1);

                if (queryStatementBackup != null) {
                    queryStatementBackup.bindLong(1, getKey());
                    queryStatementBackup.bindString(2, persistentLanguageSettings.backupLang);
                    queryStatementBackup.bindString(3, persistentLanguageSettings.lang);

                    QueryStatement.bind(queryStatementBackup, parameters, 4);

                    result.setValue(queryStatementBackup.executeInsert() > -1 || result.getValue());
                }
            });

            return false;
        } else {
            return super.execute(term, number, parameters);
        }
    }

    @Override
    public int getCount(String term) {
        if ("".equals(term)) {
            return 1;
        } else {
            return super.getCount(term);
        }
    }

    @Override
    public void close() {
        super.close();

        if (queryStatement != null) {
            try {
                queryStatement.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (queryStatementBackup != null) {
            try {
                queryStatementBackup.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
