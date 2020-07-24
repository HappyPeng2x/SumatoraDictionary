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

import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteStatement;

import org.happypeng.sumatora.android.sumatoradictionary.component.PersistentDatabaseComponent;
import org.happypeng.sumatora.android.sumatoradictionary.db.InstalledDictionary;
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentDatabase;
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentLanguageSettings;
import org.happypeng.sumatora.jromkan.Romkan;

import java.io.IOException;
import java.util.List;

public class DictionarySearchQueryTool {
    static final String SQL_QUERY_INSERT_DISPLAY_ELEMENT =
            "INSERT OR IGNORE INTO DictionarySearchElement "
                    + "SELECT ? AS ref, "
                    + "? AS entryOrder, "
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
                    + "IFNULL(DictionaryBookmark.bookmark, 0), "
                    + "DictionaryBookmark.memo "
                    + "FROM (jmdict.DictionaryEntry %s) LEFT JOIN DictionaryBookmark ON DictionaryBookmark.seq = DictionaryEntry.seq, "
                    + "%s.DictionaryTranslation "
                    + "WHERE DictionaryEntry.seq = DictionaryTranslation.seq AND "
                    + "DictionaryEntry.seq IN (%s) %s "
                    + "GROUP BY DictionaryEntry.seq";

    static final String SQL_QUERY_JOIN_EXAMPLES =
            "LEFT JOIN %s.ExamplesSummary ON DictionaryEntry.seq = ExamplesSummary.seq ";

    static final String SQL_QUERY_EXAMPLE_SENTENCES =
            "ExamplesSummary.sentences";

    static final String SQL_QUERY_EXAMPLE_TRANSLATIONS =
            "ExamplesSummary.translations";

    static private final String SQL_REVERSE_QUERY_INSERT_ELEMENT =
            "INSERT OR IGNORE INTO DictionaryElement "
                    + "SELECT ? AS ref, "
                    + "(1-min(1,max(length(DictionaryEntry.readingsPrio), length(DictionaryEntry.writingsPrio))))*10000+"
                    + "100*DictionaryTranslation.gloss_id+gloss_offset AS entryOrder, "
                    + "DictionaryEntry.seq "
                    + "FROM jmdict.DictionaryEntry, "
                    + "%s.DictionaryTranslation, "
                    + "(%s) AS DictionaryTranslationSelect "
                    + "WHERE DictionaryEntry.seq = DictionaryTranslation.seq AND "
                    + "DictionaryTranslation.rowid = DictionaryTranslationSelect.gloss_docid";

    static private final String SQL_REVERSE_QUERY_DELETE_ELEMENTS =
            "DELETE FROM DictionaryElement WHERE ref = ?";

    static private final String SQL_REVERSE_QUERY_EXACT =
            "SELECT DictionaryTranslationIndex.docid AS gloss_docid, split_offsets(offsets(DictionaryTranslationIndex), ' ', 2, 500) AS gloss_offset FROM %s.DictionaryTranslationIndex WHERE DictionaryTranslationIndex.gloss MATCH ?";

    static private final String SQL_REVERSE_QUERY_BEGIN =
            "SELECT DictionaryTranslationIndex.docid AS gloss_docid, split_offsets(offsets(DictionaryTranslationIndex), ' ', 2, 500) AS gloss_offset FROM %s.DictionaryTranslationIndex WHERE DictionaryTranslationIndex.gloss MATCH ? || '*'";

    static private final String SQL_QUERY_DICTIONARY_ELEMENT_DISPLAY =
            "INSERT OR IGNORE INTO DictionarySearchElement SELECT "
                    + "DictionaryElement.ref, "
                    + "DictionaryElement.entryOrder, "
                    + "DictionaryElement.seq, "
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
                    + "IFNULL(DictionaryBookmark.bookmark, 0), "
                    + "DictionaryBookmark.memo "
                    + "FROM (DictionaryElement LEFT JOIN DictionaryBookmark ON DictionaryElement.seq = DictionaryBookmark.seq), "
                    + "jmdict.DictionaryEntry %s, "
                    + "%s.DictionaryTranslation "
                    + "WHERE DictionaryElement.seq = DictionaryEntry.seq AND "
                    + "DictionaryElement.seq = DictionaryTranslation.seq AND "
                    + "DictionaryElement.ref = ? %s "
                    + "GROUP BY DictionaryEntry.seq";

    static final String SQL_QUERY_DELETE =
            "DELETE FROM DictionarySearchElement WHERE ref = ?";

    static private final String SQL_QUERY_EXACT_WRITING_PRIO =
            "SELECT DictionaryIndex.`rowid` AS seq "
                    + "FROM jmdict.DictionaryIndex "
                    + "WHERE writingsPrio MATCH ?";

    static private final String SQL_QUERY_EXACT_READING_PRIO =
            "SELECT DictionaryIndex.`rowid` AS seq "
                    + "FROM jmdict.DictionaryIndex "
                    + "WHERE readingsPrioKana MATCH ?";

    static private final String SQL_QUERY_EXACT_WRITING_NONPRIO =
            "SELECT DictionaryIndex.`rowid` AS seq "
                    + "FROM jmdict.DictionaryIndex "
                    + "WHERE writings MATCH ?";

    static private final String SQL_QUERY_EXACT_READING_NONPRIO =
            "SELECT DictionaryIndex.`rowid` AS seq "
                    + "FROM jmdict.DictionaryIndex "
                    + "WHERE readingsKana MATCH ?";

    static private final String SQL_QUERY_BEGIN_WRITING_PRIO =
            "SELECT DictionaryIndex.`rowid` AS seq "
                    + "FROM jmdict.DictionaryIndex "
                    + "WHERE writingsPrio MATCH ? || '*'";

    static private final String SQL_QUERY_BEGIN_READING_PRIO =
            "SELECT DictionaryIndex.`rowid` AS seq "
                    + "FROM jmdict.DictionaryIndex "
                    + "WHERE readingsPrioKana MATCH ? || '*'";

    static private final String SQL_QUERY_BEGIN_WRITING_NONPRIO =
            "SELECT DictionaryIndex.`rowid` AS seq "
                    + "FROM jmdict.DictionaryIndex "
                    + "WHERE writings MATCH ? || '*'";

    static private final String SQL_QUERY_BEGIN_READING_NONPRIO =
            "SELECT DictionaryIndex.`rowid` AS seq "
                    + "FROM jmdict.DictionaryIndex "
                    + "WHERE readingsKana MATCH ? || '*'";

    static private final String SQL_QUERY_PARTS_WRITING_PRIO =
            "SELECT DictionaryIndex.`rowid` AS seq "
                    + "FROM jmdict.DictionaryIndex "
                    + "WHERE writingsPrioParts MATCH ? || '*'";

    static private final String SQL_QUERY_PARTS_READING_PRIO =
            "SELECT DictionaryIndex.`rowid` AS seq "
                    + "FROM jmdict.DictionaryIndex "
                    + "WHERE readingsPrioKanaParts MATCH ? || '*'";

    static private final String SQL_QUERY_PARTS_WRITING_NONPRIO =
            "SELECT DictionaryIndex.`rowid` AS seq "
                    + "FROM jmdict.DictionaryIndex "
                    + "WHERE writingsParts MATCH ? || '*'";

    static private final String SQL_QUERY_PARTS_READING_NONPRIO =
            "SELECT DictionaryIndex.`rowid` AS seq "
                    + "FROM jmdict.DictionaryIndex "
                    + "WHERE readingsKanaParts MATCH ? || '*'";

    protected final PersistentDatabaseComponent persistentDatabase;
    private final String whereClause;

    private QueryStatement[] statements;
    private SupportSQLiteStatement deleteStatement;

    private final int key;

    protected final PersistentLanguageSettings persistentLanguageSettings;

    public DictionarySearchQueryTool(final PersistentDatabaseComponent persistentDatabaseComponent,
                                     final int key,
                                     final String whereClause,
                                     final PersistentLanguageSettings persistentLanguageSettings) {

        this.persistentDatabase = persistentDatabaseComponent;
        this.whereClause = whereClause;
        this.key = key;
        this.persistentLanguageSettings = persistentLanguageSettings;

        initialize();
    }

    private void initialize() {
        final PersistentDatabase database = persistentDatabase.getDatabase();
        final SupportSQLiteDatabase db = database.getOpenHelper().getWritableDatabase();

        final Romkan romkan = persistentDatabase.getRomkan();

        // "AND DictionaryEntry.seq IN ("
        String whereClause = this.whereClause == null ? "" : "AND " + this.whereClause;

        List<InstalledDictionary> installedDictionaries = database.installedDictionaryDao().getAll();

        String examplesQuerySentences = "null";
        String examplesQueryTranslations = "null";
        String examplesLeftJoin = "";

        String backupExamplesQuery = "null";
        String backupExamplesQueryTranslations = "null";
        String backupExamplesLeftJoin = "";

        for (InstalledDictionary d : installedDictionaries) {
            if ("tatoeba".equals(d.getType()) && persistentLanguageSettings.lang.equals(d.getLang())) {
                examplesQuerySentences = SQL_QUERY_EXAMPLE_SENTENCES;
                examplesQueryTranslations = SQL_QUERY_EXAMPLE_TRANSLATIONS;
                examplesLeftJoin = String.format(SQL_QUERY_JOIN_EXAMPLES, "examples_" + d.getLang());
            }
        }

        deleteStatement = db.compileStatement(SQL_QUERY_DELETE);

        final SupportSQLiteStatement queryExactPrioWriting =
                db.compileStatement(String.format(SQL_QUERY_INSERT_DISPLAY_ELEMENT,
                        examplesQuerySentences, examplesQueryTranslations, examplesLeftJoin, persistentLanguageSettings.lang, SQL_QUERY_EXACT_WRITING_PRIO, whereClause));
        final SupportSQLiteStatement queryExactPrioReading =
                db.compileStatement(String.format(SQL_QUERY_INSERT_DISPLAY_ELEMENT,
                        examplesQuerySentences, examplesQueryTranslations, examplesLeftJoin, persistentLanguageSettings.lang, SQL_QUERY_EXACT_READING_PRIO, whereClause));
        final SupportSQLiteStatement queryExactNonPrioWriting =
                db.compileStatement(String.format(SQL_QUERY_INSERT_DISPLAY_ELEMENT,
                        examplesQuerySentences, examplesQueryTranslations, examplesLeftJoin, persistentLanguageSettings.lang, SQL_QUERY_EXACT_WRITING_NONPRIO, whereClause));
        final SupportSQLiteStatement queryExactNonPrioReading =
                db.compileStatement(String.format(SQL_QUERY_INSERT_DISPLAY_ELEMENT,
                        examplesQuerySentences, examplesQueryTranslations, examplesLeftJoin, persistentLanguageSettings.lang, SQL_QUERY_EXACT_READING_NONPRIO, whereClause));
        final SupportSQLiteStatement queryBeginPrioWriting =
                db.compileStatement(String.format(SQL_QUERY_INSERT_DISPLAY_ELEMENT,
                        examplesQuerySentences, examplesQueryTranslations, examplesLeftJoin, persistentLanguageSettings.lang, SQL_QUERY_BEGIN_WRITING_PRIO, whereClause));
        final SupportSQLiteStatement queryBeginPrioReading =
                db.compileStatement(String.format(SQL_QUERY_INSERT_DISPLAY_ELEMENT,
                        examplesQuerySentences, examplesQueryTranslations, examplesLeftJoin, persistentLanguageSettings.lang, SQL_QUERY_BEGIN_READING_PRIO, whereClause));
        final SupportSQLiteStatement queryBeginNonPrioWriting =
                db.compileStatement(String.format(SQL_QUERY_INSERT_DISPLAY_ELEMENT,
                        examplesQuerySentences, examplesQueryTranslations, examplesLeftJoin, persistentLanguageSettings.lang, SQL_QUERY_BEGIN_WRITING_NONPRIO, whereClause));
        final SupportSQLiteStatement queryBeginNonPrioReading =
                db.compileStatement(String.format(SQL_QUERY_INSERT_DISPLAY_ELEMENT,
                        examplesQuerySentences, examplesQueryTranslations, examplesLeftJoin, persistentLanguageSettings.lang, SQL_QUERY_BEGIN_READING_NONPRIO, whereClause));
        final SupportSQLiteStatement queryPartsPrioWriting =
                db.compileStatement(String.format(SQL_QUERY_INSERT_DISPLAY_ELEMENT,
                        examplesQuerySentences, examplesQueryTranslations, examplesLeftJoin, persistentLanguageSettings.lang, SQL_QUERY_PARTS_WRITING_PRIO, whereClause));
        final SupportSQLiteStatement queryPartsPrioReading =
                db.compileStatement(String.format(SQL_QUERY_INSERT_DISPLAY_ELEMENT,
                        examplesQuerySentences, examplesQueryTranslations, examplesLeftJoin, persistentLanguageSettings.lang, SQL_QUERY_PARTS_READING_PRIO, whereClause));
        final SupportSQLiteStatement queryPartsNonPrioWriting =
                db.compileStatement(String.format(SQL_QUERY_INSERT_DISPLAY_ELEMENT,
                        examplesQuerySentences, examplesQueryTranslations, examplesLeftJoin, persistentLanguageSettings.lang, SQL_QUERY_PARTS_WRITING_NONPRIO, whereClause));
        final SupportSQLiteStatement queryPartsNonPrioReading =
                db.compileStatement(String.format(SQL_QUERY_INSERT_DISPLAY_ELEMENT,
                        examplesQuerySentences, examplesQueryTranslations, examplesLeftJoin, persistentLanguageSettings.lang, SQL_QUERY_PARTS_READING_NONPRIO, whereClause));
        final SupportSQLiteStatement reverseQueryExact =
                db.compileStatement(String.format(SQL_REVERSE_QUERY_INSERT_ELEMENT,
                        persistentLanguageSettings.lang, String.format(SQL_REVERSE_QUERY_EXACT, persistentLanguageSettings.lang)));
        final SupportSQLiteStatement reverseQueryBegin =
                db.compileStatement(String.format(SQL_REVERSE_QUERY_INSERT_ELEMENT,
                        persistentLanguageSettings.lang, String.format(SQL_REVERSE_QUERY_BEGIN, persistentLanguageSettings.lang)));

        final SupportSQLiteStatement reverseQueryDisplayElement =
                db.compileStatement(String.format(SQL_QUERY_DICTIONARY_ELEMENT_DISPLAY,
                        examplesQuerySentences, examplesQueryTranslations, examplesLeftJoin, persistentLanguageSettings.lang, whereClause));
        final SupportSQLiteStatement reverseQueryDeleteElements =
                db.compileStatement(SQL_REVERSE_QUERY_DELETE_ELEMENTS);

        SupportSQLiteStatement queryExactPrioWritingBackup = null;
        SupportSQLiteStatement queryExactPrioReadingBackup = null;
        SupportSQLiteStatement queryExactNonPrioWritingBackup = null;
        SupportSQLiteStatement queryExactNonPrioReadingBackup = null;
        SupportSQLiteStatement queryBeginPrioWritingBackup = null;
        SupportSQLiteStatement queryBeginPrioReadingBackup = null;
        SupportSQLiteStatement queryBeginNonPrioWritingBackup = null;
        SupportSQLiteStatement queryBeginNonPrioReadingBackup = null;
        SupportSQLiteStatement queryPartsPrioWritingBackup = null;
        SupportSQLiteStatement queryPartsPrioReadingBackup = null;
        SupportSQLiteStatement queryPartsNonPrioWritingBackup = null;
        SupportSQLiteStatement queryPartsNonPrioReadingBackup = null;
        SupportSQLiteStatement reverseQueryExactBackup = null;
        SupportSQLiteStatement reverseQueryBeginBackup = null;
        SupportSQLiteStatement reverseQueryDisplayBackupElement = null;

        if (persistentLanguageSettings.backupLang != null) {
            queryExactPrioWritingBackup =
                    db.compileStatement(String.format(SQL_QUERY_INSERT_DISPLAY_ELEMENT,
                            backupExamplesQuery, backupExamplesQueryTranslations, backupExamplesLeftJoin, persistentLanguageSettings.backupLang, SQL_QUERY_EXACT_WRITING_PRIO, whereClause));
            queryExactPrioReadingBackup =
                    db.compileStatement(String.format(SQL_QUERY_INSERT_DISPLAY_ELEMENT,
                            backupExamplesQuery, backupExamplesQueryTranslations, backupExamplesLeftJoin, persistentLanguageSettings.backupLang, SQL_QUERY_EXACT_READING_PRIO, whereClause));
            queryExactNonPrioWritingBackup =
                    db.compileStatement(String.format(SQL_QUERY_INSERT_DISPLAY_ELEMENT,
                            backupExamplesQuery, backupExamplesQueryTranslations, backupExamplesLeftJoin, persistentLanguageSettings.backupLang, SQL_QUERY_EXACT_WRITING_NONPRIO, whereClause));
            queryExactNonPrioReadingBackup =
                    db.compileStatement(String.format(SQL_QUERY_INSERT_DISPLAY_ELEMENT,
                            backupExamplesQuery, backupExamplesQueryTranslations, backupExamplesLeftJoin, persistentLanguageSettings.backupLang, SQL_QUERY_EXACT_READING_NONPRIO, whereClause));
            queryBeginPrioWritingBackup =
                    db.compileStatement(String.format(SQL_QUERY_INSERT_DISPLAY_ELEMENT,
                            backupExamplesQuery, backupExamplesQueryTranslations, backupExamplesLeftJoin, persistentLanguageSettings.backupLang, SQL_QUERY_BEGIN_WRITING_PRIO, whereClause));
            queryBeginPrioReadingBackup =
                    db.compileStatement(String.format(SQL_QUERY_INSERT_DISPLAY_ELEMENT,
                            backupExamplesQuery, backupExamplesQueryTranslations, backupExamplesLeftJoin, persistentLanguageSettings.backupLang, SQL_QUERY_BEGIN_READING_PRIO, whereClause));
            queryBeginNonPrioWritingBackup =
                    db.compileStatement(String.format(SQL_QUERY_INSERT_DISPLAY_ELEMENT,
                            backupExamplesQuery, backupExamplesQueryTranslations, backupExamplesLeftJoin, persistentLanguageSettings.backupLang, SQL_QUERY_BEGIN_READING_PRIO, whereClause));
            queryBeginNonPrioReadingBackup =
                    db.compileStatement(String.format(SQL_QUERY_INSERT_DISPLAY_ELEMENT,
                            backupExamplesQuery, backupExamplesQueryTranslations, backupExamplesLeftJoin, persistentLanguageSettings.backupLang, SQL_QUERY_BEGIN_READING_NONPRIO, whereClause));
            queryPartsPrioWritingBackup =
                    db.compileStatement(String.format(SQL_QUERY_INSERT_DISPLAY_ELEMENT,
                            backupExamplesQuery, backupExamplesQueryTranslations, backupExamplesLeftJoin, persistentLanguageSettings.backupLang, SQL_QUERY_PARTS_WRITING_PRIO, whereClause));
            queryPartsPrioReadingBackup =
                    db.compileStatement(String.format(SQL_QUERY_INSERT_DISPLAY_ELEMENT,
                            backupExamplesQuery, backupExamplesQueryTranslations, backupExamplesLeftJoin, persistentLanguageSettings.backupLang, SQL_QUERY_PARTS_READING_PRIO, whereClause));
            queryPartsNonPrioWritingBackup =
                    db.compileStatement(String.format(SQL_QUERY_INSERT_DISPLAY_ELEMENT,
                            backupExamplesQuery, backupExamplesQueryTranslations, backupExamplesLeftJoin, persistentLanguageSettings.backupLang, SQL_QUERY_PARTS_WRITING_NONPRIO, whereClause));
            queryPartsNonPrioReadingBackup =
                    db.compileStatement(String.format(SQL_QUERY_INSERT_DISPLAY_ELEMENT,
                            backupExamplesQuery, backupExamplesQueryTranslations, backupExamplesLeftJoin, persistentLanguageSettings.backupLang, SQL_QUERY_PARTS_READING_NONPRIO, whereClause));
            reverseQueryExactBackup =
                    db.compileStatement(String.format(SQL_REVERSE_QUERY_INSERT_ELEMENT,
                            persistentLanguageSettings.backupLang, String.format(SQL_REVERSE_QUERY_EXACT, persistentLanguageSettings.backupLang)));
            reverseQueryBeginBackup =
                    db.compileStatement(String.format(SQL_REVERSE_QUERY_INSERT_ELEMENT,
                            persistentLanguageSettings.backupLang, String.format(SQL_REVERSE_QUERY_BEGIN, persistentLanguageSettings.backupLang)));
            reverseQueryDisplayBackupElement =
                    db.compileStatement(String.format(SQL_QUERY_DICTIONARY_ELEMENT_DISPLAY,
                            backupExamplesQuery, backupExamplesQueryTranslations, backupExamplesLeftJoin, persistentLanguageSettings.backupLang, whereClause));
        }

        statements = new QueryStatement[14];

        statements[0] = new BasicQueryStatement(database, key, 1, persistentLanguageSettings, queryExactPrioWriting, queryExactPrioWritingBackup, false, romkan);
        statements[1] = new BasicQueryStatement(database, key, 2, persistentLanguageSettings, queryExactPrioReading, queryExactPrioReadingBackup, true, romkan);
        statements[2] = new BasicQueryStatement(database, key, 3, persistentLanguageSettings, queryExactNonPrioWriting, queryExactNonPrioWritingBackup, false, romkan);
        statements[3] = new BasicQueryStatement(database, key, 4, persistentLanguageSettings, queryExactNonPrioReading, queryExactNonPrioReadingBackup, true, romkan);
        statements[4] = new BasicQueryStatement(database, key, 5, persistentLanguageSettings, queryBeginPrioWriting, queryBeginPrioWritingBackup, false, romkan);
        statements[5] = new BasicQueryStatement(database, key, 6, persistentLanguageSettings, queryBeginPrioReading, queryBeginPrioReadingBackup, true, romkan);
        statements[6] = new BasicQueryStatement(database, key, 7, persistentLanguageSettings, queryBeginNonPrioWriting, queryBeginNonPrioWritingBackup, false, romkan);
        statements[7] = new BasicQueryStatement(database, key, 8, persistentLanguageSettings, queryBeginNonPrioReading, queryBeginNonPrioReadingBackup, true, romkan);
        statements[8] = new BasicQueryStatement(database, key, 9, persistentLanguageSettings, queryPartsPrioWriting, queryPartsPrioWritingBackup, false, romkan);
        statements[9] = new BasicQueryStatement(database, key, 10, persistentLanguageSettings, queryPartsPrioReading, queryPartsPrioReadingBackup, true, romkan);
        statements[10] = new BasicQueryStatement(database, key, 11, persistentLanguageSettings, queryPartsNonPrioWriting, queryPartsNonPrioWritingBackup, false, romkan);
        statements[11] = new BasicQueryStatement(database, key, 12, persistentLanguageSettings, queryPartsNonPrioReading, queryPartsNonPrioReadingBackup, true, romkan);
        statements[12] = new ReverseQueryStatement(database, key, persistentLanguageSettings, reverseQueryExact, reverseQueryExactBackup, reverseQueryDisplayElement, reverseQueryDisplayBackupElement, reverseQueryDeleteElements);
        statements[13] = new ReverseQueryStatement(database, key, persistentLanguageSettings, reverseQueryBegin, reverseQueryBeginBackup, reverseQueryDisplayElement, reverseQueryDisplayBackupElement, reverseQueryDeleteElements);
    }

    public void delete() {
        deleteStatement.bindLong(1, key);
        deleteStatement.execute();
    }

    protected boolean execute(final String term, final int number, final List<Object> parameters) {
        return statements[number].execute(term, parameters) >= 0;
    }

    public boolean execute(final String term, final int number) {
        return execute(term, number, null);
    }

    public int getCount(String term) {
        return statements.length;
    }

    public void close() {
        if (statements != null) {
            for (QueryStatement s : statements) {
                if (s != null) {
                    try {
                        s.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        statements = null;

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
