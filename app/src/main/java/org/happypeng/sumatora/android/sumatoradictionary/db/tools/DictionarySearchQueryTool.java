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
import org.happypeng.sumatora.core.search.Deinflector;
import org.happypeng.sumatora.core.search.DeinflectionCandidate;
import org.happypeng.sumatora.core.search.QueryUtils;
import org.happypeng.sumatora.jromkan.Romkan;

import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

// Schema v2 query layer: every tier writes entry_id + form_id + match metadata into
// DictionarySearchElement (Database.md "Query Result Shape") instead of a fully assembled
// DictionaryEntry-shaped row. Glosses/furigana/senses/examples are no longer part of the
// search-hit row at all - the display layer (EntryDetailBottomSheet et al.) queries those
// separately by entry_id/form_id once a row is shown or tapped.
public class DictionarySearchQueryTool {
    // Tier order, coarse-grained; DictionarySearchElementDao orders by (entryOrder, rank, entry_id)
    // so within-tier fine-grained ordering (e.g. "best sense first" for gloss hits) lives in rank.
    private static final int ORDER_BOOKMARK_LISTING = 1;
    private static final int ORDER_EXACT_PRIO_WRITING = 2;
    private static final int ORDER_EXACT_PRIO_KANA = 3;
    private static final int ORDER_EXACT_NONPRIO_WRITING = 4;
    private static final int ORDER_EXACT_NONPRIO_KANA = 5;
    private static final int ORDER_PREFIX_PRIO_WRITING = 6;
    private static final int ORDER_PREFIX_PRIO_KANA = 7;
    private static final int ORDER_PREFIX_NONPRIO_WRITING = 8;
    private static final int ORDER_PREFIX_NONPRIO_KANA = 9;
    private static final int ORDER_SUBSTRING_PRIO_WRITING = 10;
    private static final int ORDER_SUBSTRING_PRIO_KANA = 11;
    private static final int ORDER_SUBSTRING_NONPRIO_WRITING = 12;
    private static final int ORDER_SUBSTRING_NONPRIO_KANA = 13;
    private static final int ORDER_GLOSS_EXACT = 14;
    private static final int ORDER_GLOSS_PREFIX = 15;
    private static final int ORDER_DEINFLECTION = 16;
    private static final int ORDER_PROPER_NOUN_EXACT = 20;
    private static final int ORDER_PROPER_NOUN_BEGIN = 21;

    private static final String SCRIPT_WRITING = "writing";
    private static final String SCRIPT_KANA = "kana";

    // Casting DictionaryBookmark.seq to TEXT (not Entry.source_key to INTEGER) keeps
    // Entry.source_key bare so EntrySourceKeyOnly can be used for the join - see the query-plan
    // audit this fixed. Doesn't matter for correctness which side the cast is on (source_key is
    // always a plain decimal string), only for whether the indexed column stays seekable.
    private static final String BOOKMARK_JOIN =
            "LEFT JOIN DictionaryBookmark ON Entry.source_key = CAST(DictionaryBookmark.seq AS TEXT) ";

    private static final String BOOKMARKS_WHERE_CLAUSE =
            "((? = 0 AND ? = 0) OR (((? AND IFNULL(DictionaryBookmark.bookmark, 0) > 0) OR (? AND DictionaryBookmark.memo IS NOT NULL AND DictionaryBookmark.memo != ''))))";

    // A syntactically valid but always-empty statement, compiled in place of a real tier when
    // its backing pack (suffix/gloss for an uninstalled language) isn't attached - keeps the
    // statements[] array uniform instead of needing null checks at every call site. Still needs
    // the same 8-placeholder shape as a real basic tier (BasicQueryStatement always binds the
    // isBookmarked/hasMemo quartet), even though WHERE 0 makes them irrelevant to the result.
    private static final String SQL_NOOP_INSERT =
            "INSERT OR IGNORE INTO DictionarySearchElement "
                    + "(ref, entryOrder, entry_id, seq, form_id, match_kind, original_query, matched_text, "
                    + "dictionary_form, deinflection_label, rank, bookmark, memo, tags) "
                    + "SELECT ? AS ref, ? AS entryOrder, 0, 0, NULL, 'none', ? AS original_query, "
                    + "? AS matched_text, NULL, NULL, 0, 0, NULL, NULL WHERE 0 AND " + BOOKMARKS_WHERE_CLAUSE;

    private static final String SQL_TAG_ONLY_WHERE_CLAUSE =
            "DictionaryBookmark.tags IS NOT NULL AND DictionaryBookmark.tags != ''";

    // Bookmark/tag listing: term is empty, list only bookmarked/annotated entries. No FTS/SearchTerm
    // involved at all - just Entry joined straight to DictionaryBookmark.
    private static final String SQL_QUERY_BOOKMARK_LISTING =
            "INSERT OR IGNORE INTO DictionarySearchElement "
                    + "(ref, entryOrder, entry_id, seq, form_id, match_kind, original_query, matched_text, "
                    + "dictionary_form, deinflection_label, rank, bookmark, memo, tags) "
                    + "SELECT ? AS ref, ? AS entryOrder, Entry.entry_id, DictionaryBookmark.seq, NULL AS form_id, "
                    + "'bookmark' AS match_kind, ? AS original_query, ? AS matched_text, "
                    + "NULL, NULL, (0 - Entry.score) AS rank, "
                    + "DictionaryBookmark.bookmark, DictionaryBookmark.memo, DictionaryBookmark.tags "
                    + "FROM DictionaryBookmark "
                    + "JOIN core.Entry ON Entry.source_key = CAST(DictionaryBookmark.seq AS TEXT) "
                    + "WHERE %s";

    // Basic tier: exact/prefix/substring x writing/kana x prio/nonprio against SearchTerm, joined
    // back to Entry for the bookmark star. %s: FROM-clause tables, WHERE-clause match condition.
    //
    // A single entry_id can be reachable through several form_ids that all carry the identical
    // matched text but different readings (e.g. 二 as a bare kanji pairs with に/ふた/ふ/ふう, all
    // spelled 二) - INSERT OR IGNORE's (ref, entry_id) primary key means only the first such row
    // survives, and without an ORDER BY "first" is whatever order SQLite's query plan happens to
    // produce, which is not guaranteed stable. The ORDER BY makes the entry's designated primary
    // form win deterministically instead of an arbitrary reading.
    private static final String SQL_QUERY_BASIC_TIER =
            "INSERT OR IGNORE INTO DictionarySearchElement "
                    + "(ref, entryOrder, entry_id, seq, form_id, match_kind, original_query, matched_text, "
                    + "dictionary_form, deinflection_label, rank, bookmark, memo, tags) "
                    + "SELECT ? AS ref, ? AS entryOrder, SearchTerm.entry_id, "
                    + "CAST(Entry.source_key AS INTEGER), SearchTerm.form_id, "
                    + "'%s' AS match_kind, ? AS original_query, SearchTerm.term AS matched_text, "
                    + "NULL, NULL, (0 - Entry.score) AS rank, "
                    + "IFNULL(DictionaryBookmark.bookmark, 0), DictionaryBookmark.memo, DictionaryBookmark.tags "
                    + "FROM %s "
                    + "JOIN core.Entry ON Entry.entry_id = SearchTerm.entry_id "
                    + "LEFT JOIN core.EntryForm ON EntryForm.form_id = SearchTerm.form_id "
                    + BOOKMARK_JOIN
                    + "WHERE (%s) AND " + BOOKMARKS_WHERE_CLAUSE + " "
                    + "ORDER BY IFNULL(EntryForm.is_primary, 0) DESC, IFNULL(EntryForm.score, 0) DESC, IFNULL(EntryForm.ord, 0)";

    private static final String FROM_CORE_SEARCH_TERM = "core.SearchTerm";
    private static final String FROM_SUFFIX_SEARCH_TERM =
            "suffix.SearchSuffix JOIN suffix.SearchTerm ON SearchTerm.search_id = SearchSuffix.search_id";

    // Gloss (reverse/translation) search: one pass per language, no separate staging table needed
    // now that DictionarySearchElement doesn't carry gloss text - GROUP BY entry_id plus a single
    // MIN() aggregate (SQLite's documented "bare column" extension) picks matched_text/rank from
    // whichever sense produced the smallest ord, i.e. the best/first matching sense.
    private static final String SQL_QUERY_GLOSS_TIER =
            "INSERT OR IGNORE INTO DictionarySearchElement "
                    + "(ref, entryOrder, entry_id, seq, form_id, match_kind, original_query, matched_text, "
                    + "dictionary_form, deinflection_label, rank, bookmark, memo, tags) "
                    + "SELECT ? AS ref, ? AS entryOrder, Sense.entry_id, "
                    + "CAST(Entry.source_key AS INTEGER), NULL AS form_id, "
                    + "'gloss' AS match_kind, ? AS original_query, SenseGloss.text AS matched_text, "
                    + "NULL, NULL, MIN(Sense.ord) AS rank, "
                    + "IFNULL(DictionaryBookmark.bookmark, 0), DictionaryBookmark.memo, DictionaryBookmark.tags "
                    + "FROM %s.GlossSearchFts "
                    + "JOIN %s.SenseGloss ON SenseGloss.rowid = GlossSearchFts.rowid "
                    + "JOIN %s.Sense ON Sense.sense_id = SenseGloss.sense_id "
                    + "JOIN core.Entry ON Entry.entry_id = Sense.entry_id "
                    + BOOKMARK_JOIN
                    + "WHERE GlossSearchFts.text MATCH %s AND " + BOOKMARKS_WHERE_CLAUSE + " "
                    + "GROUP BY Sense.entry_id";

    // Deinflection: same shape as the exact-tier basic query, but requires FormRule to confirm the
    // matched form actually supports the rule the Deinflector candidate was generated under, and
    // carries dictionary_form/deinflection_label through so the UI can render "食べた -> 食べる (past)".
    private static final String SQL_QUERY_DEINFLECTION =
            "INSERT OR IGNORE INTO DictionarySearchElement "
                    + "(ref, entryOrder, entry_id, seq, form_id, match_kind, original_query, matched_text, "
                    + "dictionary_form, deinflection_label, rank, bookmark, memo, tags) "
                    + "SELECT ? AS ref, ? AS entryOrder, SearchTerm.entry_id, "
                    + "CAST(Entry.source_key AS INTEGER), SearchTerm.form_id, "
                    + "'deinflection' AS match_kind, ? AS original_query, SearchTerm.term AS matched_text, "
                    + "? AS dictionary_form, ? AS deinflection_label, (0 - Entry.score) AS rank, "
                    + "IFNULL(DictionaryBookmark.bookmark, 0), DictionaryBookmark.memo, DictionaryBookmark.tags "
                    + "FROM core.SearchTerm "
                    + "JOIN core.Entry ON Entry.entry_id = SearchTerm.entry_id "
                    + BOOKMARK_JOIN
                    + "WHERE SearchTerm.script = '%s' AND SearchTerm.normalized = ? "
                    + "AND EXISTS (SELECT 1 FROM core.FormRule WHERE FormRule.form_id = SearchTerm.form_id AND FormRule.rule = ?)";

    // Proper names (JMnedict): same 4-slot shape as a basic tier, just against the names pack's own
    // Entry/SearchTerm instead of core's - reuses BasicQueryStatement directly. Names aren't
    // bookmarkable in this app, so bookmark/memo/tags are literal defaults, no join needed.
    private static final String SQL_QUERY_PROPER_NOUN =
            "INSERT OR IGNORE INTO DictionarySearchElement "
                    + "(ref, entryOrder, entry_id, seq, form_id, match_kind, original_query, matched_text, "
                    + "dictionary_form, deinflection_label, rank, bookmark, memo, tags) "
                    + "SELECT ? AS ref, ? AS entryOrder, SearchTerm.entry_id, 0, SearchTerm.form_id, "
                    + "'name' AS match_kind, ? AS original_query, SearchTerm.term AS matched_text, "
                    + "NULL, NULL, 0 AS rank, 0, NULL, NULL "
                    + "FROM names.SearchTerm "
                    + "WHERE SearchTerm.script = '%s' AND SearchTerm.normalized %s";

    static final String SQL_QUERY_DELETE =
            "DELETE FROM DictionarySearchElement WHERE ref = ?";

    protected final PersistentDatabaseComponent persistentDatabase;

    private QueryStatement[] statements;
    private SupportSQLiteStatement deleteStatement;
    private SupportSQLiteStatement tagOnlyStatement;
    private SupportSQLiteStatement deleteByTagStatement;
    private SupportSQLiteStatement countByRefStatement;

    private BasicQueryStatement properNounExactWriting;
    private BasicQueryStatement properNounExactReading;
    private BasicQueryStatement properNounBeginWriting;
    private BasicQueryStatement properNounBeginReading;

    private SupportSQLiteStatement deinflectWritingPrioStatement;
    private SupportSQLiteStatement deinflectReadingPrioStatement;
    private Romkan romkanRef;

    private final int key;

    protected final PersistentLanguageSettings persistentLanguageSettings;

    public DictionarySearchQueryTool(final PersistentDatabaseComponent persistentDatabaseComponent,
                                     final int key,
                                     final PersistentLanguageSettings persistentLanguageSettings) {

        this.persistentDatabase = persistentDatabaseComponent;
        this.key = key;
        this.persistentLanguageSettings = persistentLanguageSettings;

        initialize();
    }

    private static boolean isInstalled(final List<InstalledDictionary> installed, final String type, final String lang) {
        for (InstalledDictionary d : installed) {
            if (type.equals(d.getType()) && (lang == null || lang.equals(d.getLang()))) {
                return true;
            }
        }
        return false;
    }

    private static String globEscape(final String term) {
        return term.replace("[", "[[]").replace("]", "[]]").replace("*", "[*]").replace("?", "[?]");
    }

    private static String basicTierWhere(final String matchOp, final String script, final boolean prio,
                                         final boolean prefixSearchable, final boolean substringSearchable) {
        StringBuilder sb = new StringBuilder();
        sb.append("SearchTerm.script = '").append(script).append("' AND SearchTerm.priority ")
                .append(prio ? "> 0" : "= 0").append(" AND SearchTerm.normalized ").append(matchOp);
        if (prefixSearchable) {
            sb.append(" AND SearchTerm.is_prefix_searchable = 1");
        }
        if (substringSearchable) {
            sb.append(" AND SearchTerm.is_substring_searchable = 1");
        }
        return sb.toString();
    }

    private SupportSQLiteStatement compileBasic(final SupportSQLiteDatabase db, final String matchKind,
                                                 final String fromClause, final String whereClause) {
        return db.compileStatement(String.format(SQL_QUERY_BASIC_TIER, matchKind, fromClause, whereClause));
    }

    private void initialize() {
        final PersistentDatabase database = persistentDatabase.getDatabase();
        final SupportSQLiteDatabase db = database.getOpenHelper().getWritableDatabase();

        final Romkan romkan = persistentDatabase.getRomkan();
        this.romkanRef = romkan;

        final List<InstalledDictionary> installedDictionaries = database.installedDictionaryDao().getAll();
        final boolean suffixInstalled = isInstalled(installedDictionaries, "suffix", "");
        final boolean namesInstalled = isInstalled(installedDictionaries, "names", "");
        final boolean glossInstalled = isInstalled(installedDictionaries, "gloss", persistentLanguageSettings.lang);
        final boolean glossBackupInstalled = persistentLanguageSettings.backupLang != null
                && isInstalled(installedDictionaries, "gloss", persistentLanguageSettings.backupLang);

        deleteStatement = db.compileStatement(SQL_QUERY_DELETE);

        final SupportSQLiteStatement queryBookmarkListing =
                db.compileStatement(String.format(SQL_QUERY_BOOKMARK_LISTING, BOOKMARKS_WHERE_CLAUSE));

        final SupportSQLiteStatement queryExactPrioWriting = compileBasic(db, "exact", FROM_CORE_SEARCH_TERM,
                basicTierWhere("= ?", SCRIPT_WRITING, true, false, false));
        final SupportSQLiteStatement queryExactPrioReading = compileBasic(db, "exact", FROM_CORE_SEARCH_TERM,
                basicTierWhere("= ?", SCRIPT_KANA, true, false, false));
        final SupportSQLiteStatement queryExactNonPrioWriting = compileBasic(db, "exact", FROM_CORE_SEARCH_TERM,
                basicTierWhere("= ?", SCRIPT_WRITING, false, false, false));
        final SupportSQLiteStatement queryExactNonPrioReading = compileBasic(db, "exact", FROM_CORE_SEARCH_TERM,
                basicTierWhere("= ?", SCRIPT_KANA, false, false, false));

        final SupportSQLiteStatement queryPrefixPrioWriting = compileBasic(db, "prefix", FROM_CORE_SEARCH_TERM,
                basicTierWhere("GLOB ?", SCRIPT_WRITING, true, true, false));
        final SupportSQLiteStatement queryPrefixPrioReading = compileBasic(db, "prefix", FROM_CORE_SEARCH_TERM,
                basicTierWhere("GLOB ?", SCRIPT_KANA, true, true, false));
        final SupportSQLiteStatement queryPrefixNonPrioWriting = compileBasic(db, "prefix", FROM_CORE_SEARCH_TERM,
                basicTierWhere("GLOB ?", SCRIPT_WRITING, false, true, false));
        final SupportSQLiteStatement queryPrefixNonPrioReading = compileBasic(db, "prefix", FROM_CORE_SEARCH_TERM,
                basicTierWhere("GLOB ?", SCRIPT_KANA, false, true, false));

        final SupportSQLiteStatement querySubstringPrioWriting;
        final SupportSQLiteStatement querySubstringPrioReading;
        final SupportSQLiteStatement querySubstringNonPrioWriting;
        final SupportSQLiteStatement querySubstringNonPrioReading;
        if (suffixInstalled) {
            querySubstringPrioWriting = db.compileStatement(String.format(SQL_QUERY_BASIC_TIER, "substring",
                    FROM_SUFFIX_SEARCH_TERM, substringWhere("SearchSuffix.suffix GLOB ?", SCRIPT_WRITING, true)));
            querySubstringPrioReading = db.compileStatement(String.format(SQL_QUERY_BASIC_TIER, "substring",
                    FROM_SUFFIX_SEARCH_TERM, substringWhere("SearchSuffix.suffix GLOB ?", SCRIPT_KANA, true)));
            querySubstringNonPrioWriting = db.compileStatement(String.format(SQL_QUERY_BASIC_TIER, "substring",
                    FROM_SUFFIX_SEARCH_TERM, substringWhere("SearchSuffix.suffix GLOB ?", SCRIPT_WRITING, false)));
            querySubstringNonPrioReading = db.compileStatement(String.format(SQL_QUERY_BASIC_TIER, "substring",
                    FROM_SUFFIX_SEARCH_TERM, substringWhere("SearchSuffix.suffix GLOB ?", SCRIPT_KANA, false)));
        } else {
            querySubstringPrioWriting = db.compileStatement(SQL_NOOP_INSERT);
            querySubstringPrioReading = db.compileStatement(SQL_NOOP_INSERT);
            querySubstringNonPrioWriting = db.compileStatement(SQL_NOOP_INSERT);
            querySubstringNonPrioReading = db.compileStatement(SQL_NOOP_INSERT);
        }

        final SupportSQLiteStatement queryGlossExact = glossInstalled
                ? db.compileStatement(String.format(SQL_QUERY_GLOSS_TIER, glossAlias(persistentLanguageSettings.lang),
                        glossAlias(persistentLanguageSettings.lang), glossAlias(persistentLanguageSettings.lang), "?"))
                : db.compileStatement(SQL_NOOP_INSERT);
        final SupportSQLiteStatement queryGlossExactBackup = glossBackupInstalled
                ? db.compileStatement(String.format(SQL_QUERY_GLOSS_TIER, glossAlias(persistentLanguageSettings.backupLang),
                        glossAlias(persistentLanguageSettings.backupLang), glossAlias(persistentLanguageSettings.backupLang), "?"))
                : null;

        final SupportSQLiteStatement queryGlossPrefix = glossInstalled
                ? db.compileStatement(String.format(SQL_QUERY_GLOSS_TIER, glossAlias(persistentLanguageSettings.lang),
                        glossAlias(persistentLanguageSettings.lang), glossAlias(persistentLanguageSettings.lang), "? || '*'"))
                : db.compileStatement(SQL_NOOP_INSERT);
        final SupportSQLiteStatement queryGlossPrefixBackup = glossBackupInstalled
                ? db.compileStatement(String.format(SQL_QUERY_GLOSS_TIER, glossAlias(persistentLanguageSettings.backupLang),
                        glossAlias(persistentLanguageSettings.backupLang), glossAlias(persistentLanguageSettings.backupLang), "? || '*'"))
                : null;

        tagOnlyStatement = db.compileStatement(String.format(SQL_QUERY_BOOKMARK_LISTING, SQL_TAG_ONLY_WHERE_CLAUSE));

        deleteByTagStatement = db.compileStatement(
                "DELETE FROM DictionarySearchElement WHERE ref = ? AND entry_id NOT IN ("
                        + "SELECT Entry.entry_id FROM core.Entry "
                        + "JOIN DictionaryBookmarkTag ON Entry.source_key = CAST(DictionaryBookmarkTag.seq AS TEXT) "
                        + "WHERE DictionaryBookmarkTag.tag = ?)");

        countByRefStatement = db.compileStatement(
                "SELECT COUNT(*) FROM DictionarySearchElement WHERE ref = ?");

        if (namesInstalled) {
            properNounExactWriting = new BasicQueryStatement(database, key, ORDER_PROPER_NOUN_EXACT, persistentLanguageSettings,
                    db.compileStatement(String.format(SQL_QUERY_PROPER_NOUN, SCRIPT_WRITING, "= ?")), null, false, "", romkan);
            properNounExactReading = new BasicQueryStatement(database, key, ORDER_PROPER_NOUN_EXACT, persistentLanguageSettings,
                    db.compileStatement(String.format(SQL_QUERY_PROPER_NOUN, SCRIPT_KANA, "= ?")), null, true, "", romkan);
            properNounBeginWriting = new BasicQueryStatement(database, key, ORDER_PROPER_NOUN_BEGIN, persistentLanguageSettings,
                    db.compileStatement(String.format(SQL_QUERY_PROPER_NOUN, SCRIPT_WRITING, "GLOB ?")), null, false, "*", romkan);
            properNounBeginReading = new BasicQueryStatement(database, key, ORDER_PROPER_NOUN_BEGIN, persistentLanguageSettings,
                    db.compileStatement(String.format(SQL_QUERY_PROPER_NOUN, SCRIPT_KANA, "GLOB ?")), null, true, "*", romkan);
        }

        // Deinflection doesn't split prio/nonprio (a conjugated hit is a conjugated hit regardless
        // of headword priority) - just script (writing/kana).
        deinflectWritingPrioStatement = db.compileStatement(String.format(SQL_QUERY_DEINFLECTION, SCRIPT_WRITING));
        deinflectReadingPrioStatement = db.compileStatement(String.format(SQL_QUERY_DEINFLECTION, SCRIPT_KANA));

        statements = new QueryStatement[15];

        statements[0] = new BasicQueryStatement(database, key, ORDER_BOOKMARK_LISTING, persistentLanguageSettings, queryBookmarkListing, null, false, "", romkan);
        statements[1] = new BasicQueryStatement(database, key, ORDER_EXACT_PRIO_WRITING, persistentLanguageSettings, queryExactPrioWriting, null, false, "", romkan);
        statements[2] = new BasicQueryStatement(database, key, ORDER_EXACT_PRIO_KANA, persistentLanguageSettings, queryExactPrioReading, null, true, "", romkan);
        statements[3] = new BasicQueryStatement(database, key, ORDER_EXACT_NONPRIO_WRITING, persistentLanguageSettings, queryExactNonPrioWriting, null, false, "", romkan);
        statements[4] = new BasicQueryStatement(database, key, ORDER_EXACT_NONPRIO_KANA, persistentLanguageSettings, queryExactNonPrioReading, null, true, "", romkan);
        statements[5] = new BasicQueryStatement(database, key, ORDER_PREFIX_PRIO_WRITING, persistentLanguageSettings, queryPrefixPrioWriting, null, false, "*", romkan);
        statements[6] = new BasicQueryStatement(database, key, ORDER_PREFIX_PRIO_KANA, persistentLanguageSettings, queryPrefixPrioReading, null, true, "*", romkan);
        statements[7] = new BasicQueryStatement(database, key, ORDER_PREFIX_NONPRIO_WRITING, persistentLanguageSettings, queryPrefixNonPrioWriting, null, false, "*", romkan);
        statements[8] = new BasicQueryStatement(database, key, ORDER_PREFIX_NONPRIO_KANA, persistentLanguageSettings, queryPrefixNonPrioReading, null, true, "*", romkan);
        statements[9] = new BasicQueryStatement(database, key, ORDER_SUBSTRING_PRIO_WRITING, persistentLanguageSettings, querySubstringPrioWriting, null, false, "*", romkan);
        statements[10] = new BasicQueryStatement(database, key, ORDER_SUBSTRING_PRIO_KANA, persistentLanguageSettings, querySubstringPrioReading, null, true, "*", romkan);
        statements[11] = new BasicQueryStatement(database, key, ORDER_SUBSTRING_NONPRIO_WRITING, persistentLanguageSettings, querySubstringNonPrioWriting, null, false, "*", romkan);
        statements[12] = new BasicQueryStatement(database, key, ORDER_SUBSTRING_NONPRIO_KANA, persistentLanguageSettings, querySubstringNonPrioReading, null, true, "*", romkan);
        statements[13] = new BasicQueryStatement(database, key, ORDER_GLOSS_EXACT, persistentLanguageSettings, queryGlossExact, queryGlossExactBackup, false, "", romkan);
        statements[14] = new BasicQueryStatement(database, key, ORDER_GLOSS_PREFIX, persistentLanguageSettings, queryGlossPrefix, queryGlossPrefixBackup, false, "", romkan);
    }

    private static String substringWhere(final String matchOp, final String script, final boolean prio) {
        return "SearchTerm.script = '" + script + "' AND SearchTerm.priority " + (prio ? "> 0" : "= 0")
                + " AND SearchTerm.is_substring_searchable = 1 AND " + matchOp;
    }

    private static String glossAlias(final String lang) {
        return "gloss_" + lang;
    }

    public void delete() {
        deleteStatement.bindLong(1, key);
        deleteStatement.execute();
    }

    protected boolean execute(final String term, final int number, final List<Object> parameters) {
        return statements[number].execute(term, parameters) >= 0;
    }

    public boolean execute(String term, int number, boolean isBookmarked, boolean hasMemo) {
        final List<Object> parameters = new LinkedList<>();

        parameters.add(isBookmarked);
        parameters.add(hasMemo);
        parameters.add(isBookmarked);
        parameters.add(hasMemo);

        if ("".equals(term)) {
            if (!(isBookmarked || hasMemo)) {
                return false;
            } else {
                return execute(term, number, parameters);
            }
        } else {
            return execute(term, number + 1, parameters);
        }
    }

    private boolean executeTagOnly() {
        tagOnlyStatement.bindLong(1, key);
        tagOnlyStatement.bindLong(2, ORDER_BOOKMARK_LISTING);
        tagOnlyStatement.bindString(3, "");
        tagOnlyStatement.bindString(4, "");

        return tagOnlyStatement.executeInsert() >= 0;
    }

    public boolean execute(String term, int number, boolean isBookmarked, boolean hasMemo, List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return execute(term, number, isBookmarked, hasMemo);
        }

        boolean found;
        if (term.isEmpty()) {
            if (isBookmarked || hasMemo) {
                found = execute(term, number, isBookmarked, hasMemo);
            } else {
                found = executeTagOnly();
            }
        } else {
            found = execute(term, number, isBookmarked, hasMemo);
        }

        if (found) {
            for (String tag : tags) {
                deleteByTagStatement.bindLong(1, key);
                deleteByTagStatement.bindString(2, tag);
                deleteByTagStatement.execute();
            }
            countByRefStatement.bindLong(1, key);
            return countByRefStatement.simpleQueryForLong() > 0;
        }

        return false;
    }

    public int getCount(String term) {
        if ("".equals(term)) {
            return 1;
        } else {
            return statements.length - 1;
        }
    }

    // Proper name (JMnedict) search: an appended pass run once per term alongside (not instead of)
    // the regular tiered dictionary search, so proper-name hits show below direct dictionary
    // results in the same list. No-op (returns false) when the optional names pack isn't installed.
    public boolean executeProperNouns(String term) {
        if (term == null || term.isEmpty() || properNounExactWriting == null) {
            return false;
        }

        long exactWriting = properNounExactWriting.execute(term, null);
        long exactReading = properNounExactReading.execute(term, null);
        long beginWriting = properNounBeginWriting.execute(term, null);
        long beginReading = properNounBeginReading.execute(term, null);

        return exactWriting >= 0 || exactReading >= 0 || beginWriting >= 0 || beginReading >= 0;
    }

    // Deinflection: an appended pass run once per term, alongside (not instead of) the regular
    // tiered search, so conjugated-form hits ("食べた" -> 食べる, past) show alongside direct hits.
    // Each Deinflector candidate is verified against FormRule by the SQL itself; candidates that
    // don't match a real rule code for the matched form just insert zero rows.
    public boolean executeDeinflection(String term) {
        if (term == null || term.isEmpty()) {
            return false;
        }

        // The Deinflector's rule table matches hiragana conjugation suffixes (e.g. "た", "ない"),
        // so romaji (and any katakana) input needs to be normalized to hiragana first.
        final String hiraganaTerm = romkanRef.to_hiragana(QueryUtils.escapeTerm(term));

        boolean anyFound = false;
        for (DeinflectionCandidate candidate : Deinflector.INSTANCE.deinflect(hiraganaTerm)) {
            final String writingTerm = QueryUtils.escapeTerm(candidate.getDictionaryForm());
            final String readingTerm = romkanRef.to_katakana(romkanRef.to_hepburn(writingTerm));

            long r1 = bindAndInsertDeinflection(deinflectWritingPrioStatement, term, writingTerm, candidate);
            long r3 = bindAndInsertDeinflection(deinflectReadingPrioStatement, term, readingTerm, candidate);

            if (r1 >= 0 || r3 >= 0) {
                anyFound = true;
            }
        }

        return anyFound;
    }

    private long bindAndInsertDeinflection(SupportSQLiteStatement statement, String originalQuery, String term,
                                           DeinflectionCandidate candidate) {
        statement.bindLong(1, key);
        statement.bindLong(2, ORDER_DEINFLECTION);
        statement.bindString(3, originalQuery);
        statement.bindString(4, candidate.getDictionaryForm());
        statement.bindString(5, candidate.getLabel());
        statement.bindString(6, term);
        statement.bindString(7, candidate.getRuleCode());

        return statement.executeInsert();
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

        closeQuietly(deleteStatement);
        deleteStatement = null;
        closeQuietly(tagOnlyStatement);
        tagOnlyStatement = null;
        closeQuietly(deleteByTagStatement);
        deleteByTagStatement = null;
        closeQuietly(countByRefStatement);
        countByRefStatement = null;

        closeStatement(properNounExactWriting);
        closeStatement(properNounExactReading);
        closeStatement(properNounBeginWriting);
        closeStatement(properNounBeginReading);
        properNounExactWriting = null;
        properNounExactReading = null;
        properNounBeginWriting = null;
        properNounBeginReading = null;

        closeQuietly(deinflectWritingPrioStatement);
        closeQuietly(deinflectReadingPrioStatement);
        deinflectWritingPrioStatement = null;
        deinflectReadingPrioStatement = null;
    }

    private static void closeStatement(QueryStatement statement) {
        if (statement != null) {
            try {
                statement.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private static void closeQuietly(SupportSQLiteStatement statement) {
        if (statement != null) {
            try {
                statement.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public int getKey() {
        return key;
    }
}
