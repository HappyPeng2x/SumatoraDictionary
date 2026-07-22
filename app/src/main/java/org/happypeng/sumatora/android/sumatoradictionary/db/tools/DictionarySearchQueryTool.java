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
    //
    // render_json starts NULL here, like every other tier - see backfillRenderJson() below. It used
    // to be computed inline in this same INSERT (one json_group_array()-packed correlated-subquery
    // pass per matched row, via buildRenderJsonExpr), which fixed a real bug (fast-scrolling a huge
    // bookmark list piled up dozens of concurrent per-row live fetches on
    // PersistentDatabaseComponent's single non-WAL connection) but at a cost nobody noticed at the
    // time: a broad tier (a short/common query, or a large bookmark list) could match thousands of
    // rows, and every one of them paid that ~15-subquery cost immediately, unconditionally, the
    // moment the tier ran - not just the handful about to be shown. Measured against the real
    // dictionary: ~90us/row with render_json inline vs ~5us/row without, so a single scroll event
    // reaching a broad tier (easily 5,000-12,000 rows for a one-character kana query) cost
    // 500-1000ms+ of synchronous work. The real root cause of the *original* per-row-live-fetch jank
    // turned out to be a missing index (Sense.sense_group_id - see SumatoraIndex commit
    // "Add three missing indices..."), fixed there and shipped in dictionaries-v12; with that in
    // place, bounding render_json to the page actually being displayed (backfillRenderJson) keeps
    // every tier's own match-finding cheap regardless of breadth, without reintroducing the
    // many-small-concurrent-queries pattern that caused the original bug (backfill is one batched
    // statement per call, not one query per row).
    private static final String SQL_QUERY_BOOKMARK_LISTING =
            "INSERT OR IGNORE INTO DictionarySearchElement "
                    + "(ref, entryOrder, entry_id, seq, form_id, match_kind, original_query, matched_text, "
                    + "dictionary_form, deinflection_label, rank, bookmark, memo, tags, render_json) "
                    + "SELECT ? AS ref, ? AS entryOrder, Entry.entry_id, DictionaryBookmark.seq, NULL AS form_id, "
                    + "'bookmark' AS match_kind, ? AS original_query, ? AS matched_text, "
                    + "NULL, NULL, (0 - Entry.score) AS rank, "
                    + "DictionaryBookmark.bookmark, DictionaryBookmark.memo, DictionaryBookmark.tags, "
                    + "NULL AS render_json "
                    + "FROM DictionaryBookmark "
                    + "JOIN core.Entry ON Entry.source_key = CAST(DictionaryBookmark.seq AS TEXT) "
                    + "WHERE %s";

    // One correlated scalar subquery per field instead of a single joined row, since plain SQLite
    // (no CTEs/LATERAL relied on here for compatibility) can't cheaply name and reuse a correlated
    // subquery's result across sibling expressions - each is a cheap indexed point lookup
    // (measured ~20 microseconds/row end to end against the bookmark-listing tier). Resolves
    // exactly like fetchDisplayForm: prefer the specific matched form (promoted to its paired
    // writing if the match itself is a bare reading), falling back to the entry's primary form
    // when there's no matched form_id at all (bookmark listing, gloss hits) or the matched one
    // turns out to be search-only. pack is "core" or "names"; entryIdExpr/matchedFormIdExpr are
    // SQL expressions correlated to the enclosing row (matchedFormIdExpr null means always use the
    // primary form). See PersistentDatabaseComponent.parsePrecomputedSummary, the client-side
    // counterpart this must stay in sync with.
    private static String buildChosenFormIdExpr(String pack, String entryIdExpr, @Nullable String matchedFormIdExpr) {
        final String primaryFormId = "(SELECT form_id FROM " + pack + ".EntryForm WHERE entry_id = " + entryIdExpr
                + " AND is_primary = 1 LIMIT 1)";
        if (matchedFormIdExpr == null) {
            return primaryFormId;
        }
        final String matchedValidFormId = "(SELECT form_id FROM " + pack + ".EntryForm WHERE form_id = " + matchedFormIdExpr
                + " AND is_search_only = 0 LIMIT 1)";
        final String matchedFormType = "(SELECT form_type FROM " + pack + ".EntryForm WHERE form_id = " + matchedFormIdExpr
                + " AND is_search_only = 0 LIMIT 1)";
        final String matchedFormText = "(SELECT text FROM " + pack + ".EntryForm WHERE form_id = " + matchedFormIdExpr
                + " AND is_search_only = 0 LIMIT 1)";
        final String pairedWritingFormId = "(SELECT form_id FROM " + pack + ".EntryForm WHERE entry_id = " + entryIdExpr
                + " AND form_type = 'writing' AND reading = " + matchedFormText + " AND is_search_only = 0 "
                + "ORDER BY is_primary DESC, score DESC, ord LIMIT 1)";
        return "COALESCE("
                + "(CASE WHEN " + matchedFormType + " = 'reading' THEN " + pairedWritingFormId + " END), "
                + matchedValidFormId + ", "
                + primaryFormId + ")";
    }

    // Word-entry (core pack) render payload: headword/furigana/alt-forms + sense groups. glossAlias
    // null means no gloss pack at all is installed (degenerate: empty senses); backupGlossAlias
    // null means no backup language is configured. This has to stay a single static, precompiled
    // statement reused for every row across every tier, so - unlike a live per-entry fetch that
    // could pick one *effective* language in Java before querying - it unconditionally aggregates
    // gloss text from both packs (glossBySense/glossBySenseBackup, both keyed per sense_id) and
    // leaves the per-sense effective-language decision to the client-side parse (see
    // PersistentDatabaseComponent.mergeSenseGroups): each sense picks its own gloss independently,
    // main language first, falling back to backup only for that sense - a partially-translated
    // entry ends up with some senses in the main language and others in backup, not an all-or-
    // nothing choice for the whole entry. 'usedBackupLang' here stays entry-wide ("does the main
    // language have zero senses at all for this entry") and is only a diagnostic signal now, not
    // something the parse depends on. matchedFormIdExpr null means this tier never carries a
    // specific matched form (bookmark listing, gloss hits) - the sense-applies-to-form filter is
    // skipped in that case (see senseFormFilter below).
    static String buildRenderJsonExpr(String entryIdExpr, @Nullable String matchedFormIdExpr,
                                               @Nullable String glossAlias, @Nullable String backupGlossAlias) {
        final String chosenFormId = buildChosenFormIdExpr("core", entryIdExpr, matchedFormIdExpr);
        final String chosenFormText = "(SELECT text FROM core.EntryForm WHERE form_id = " + chosenFormId + ")";
        final String chosenFormRawReading = "(SELECT reading FROM core.EntryForm WHERE form_id = " + chosenFormId + ")";
        final String chosenFormGatedReading = "(SELECT CASE WHEN form_type = 'writing' THEN reading ELSE NULL END "
                + "FROM core.EntryForm WHERE form_id = " + chosenFormId + ")";
        final String senseFormFilter = matchedFormIdExpr == null ? "1 = 1"
                : "(NOT EXISTS (SELECT 1 FROM core.SenseAppliesToForm a WHERE a.sense_id = s.sense_id) "
                        + "OR " + matchedFormIdExpr + " IS NULL "
                        + "OR EXISTS (SELECT 1 FROM core.SenseAppliesToForm a WHERE a.sense_id = s.sense_id AND a.form_id = " + matchedFormIdExpr + "))";

        final String usedBackupLangExpr = glossAlias == null ? "1"
                : "CASE WHEN EXISTS(SELECT 1 FROM " + glossAlias + ".Sense WHERE entry_id = " + entryIdExpr + ") THEN 0 ELSE 1 END";
        final String senseRowsExpr = glossAlias == null ? "NULL"
                : "(SELECT json_group_array(json_array(sg.sense_group_id, s.sense_id)) FROM core.Sense s "
                        + "JOIN core.SenseGroup sg ON sg.sense_group_id = s.sense_group_id "
                        + "WHERE sg.entry_id = " + entryIdExpr + " AND " + senseFormFilter + " "
                        + "ORDER BY sg.ord, s.ord)";
        final String tagsByGroupExpr = glossAlias == null ? "NULL"
                : "(SELECT json_group_array(json_array(sgt.sense_group_id, t.code)) FROM core.SenseGroupTag sgt "
                        + "JOIN core.Tag t ON t.tag_id = sgt.tag_id "
                        + "WHERE sgt.sense_group_id IN (SELECT sense_group_id FROM core.SenseGroup WHERE entry_id = " + entryIdExpr + ") "
                        + "ORDER BY sgt.sense_group_id, t.sort_order)";
        final String glossBySenseExpr = glossAlias == null ? "NULL"
                : "(SELECT json_group_array(json_array(g.sense_id, g.text)) FROM " + glossAlias + ".SenseGloss g "
                        + "JOIN core.Sense s ON s.sense_id = g.sense_id "
                        + "JOIN core.SenseGroup sg ON sg.sense_group_id = s.sense_group_id "
                        + "WHERE sg.entry_id = " + entryIdExpr + " ORDER BY g.sense_id, g.ord)";
        final String glossBySenseBackupExpr = backupGlossAlias == null ? "NULL"
                : "(SELECT json_group_array(json_array(g.sense_id, g.text)) FROM " + backupGlossAlias + ".SenseGloss g "
                        + "JOIN core.Sense s ON s.sense_id = g.sense_id "
                        + "JOIN core.SenseGroup sg ON sg.sense_group_id = s.sense_group_id "
                        + "WHERE sg.entry_id = " + entryIdExpr + " ORDER BY g.sense_id, g.ord)";
        final String restrictedFormsBySenseExpr = glossAlias == null ? "NULL"
                : "(SELECT json_group_array(json_array(a.sense_id, a.form_id)) FROM core.SenseAppliesToForm a "
                        + "JOIN core.Sense s ON s.sense_id = a.sense_id "
                        + "JOIN core.SenseGroup sg ON sg.sense_group_id = s.sense_group_id "
                        + "WHERE sg.entry_id = " + entryIdExpr + ")";

        return "(SELECT json_object("
                + "'isName', 0, "
                + "'primaryText', " + chosenFormText + ", "
                + "'primaryReading', " + chosenFormGatedReading + ", "
                + "'furigana', (SELECT json_group_array(json_array(base, ruby)) FROM core.FormFuriganaSegment "
                + "WHERE form_id = " + chosenFormId + " ORDER BY ord), "
                + "'altWritings', (SELECT json_group_array(json_object('text', alt.text, 'furigana', json(alt.furigana_json))) FROM ("
                + "SELECT ef.text AS text, "
                + "(SELECT json_group_array(json_array(ffs.base, ffs.ruby)) FROM core.FormFuriganaSegment ffs "
                + "WHERE ffs.form_id = ef.form_id ORDER BY ffs.ord) AS furigana_json "
                + "FROM core.EntryForm ef "
                + "WHERE ef.entry_id = " + entryIdExpr + " AND ef.form_type = 'writing' AND ef.is_search_only = 0 "
                + "AND ef.form_id != " + chosenFormId + " AND ef.reading = " + chosenFormRawReading + " "
                + "ORDER BY ef.is_primary DESC, ef.score DESC, ef.ord) AS alt), "
                + "'altReadings', (SELECT json_group_array(reading) FROM core.EntryForm WHERE entry_id = " + entryIdExpr + " "
                + "AND form_type = 'writing' AND text = " + chosenFormText + " AND is_search_only = 0 "
                + "AND reading != " + chosenFormGatedReading + " ORDER BY is_primary DESC, score DESC, ord), "
                + "'usedBackupLang', " + usedBackupLangExpr + ", "
                + "'senseRows', " + senseRowsExpr + ", "
                + "'tagsByGroup', " + tagsByGroupExpr + ", "
                + "'glossBySense', " + glossBySenseExpr + ", "
                + "'glossBySenseBackup', " + glossBySenseBackupExpr + ", "
                + "'restrictedFormsBySense', " + restrictedFormsBySenseExpr + ", "
                + "'nameTypeCodes', NULL, 'translations', NULL"
                + ")) AS render_json";
    }

    // Name-entry (names pack) render payload: no senses at all, just headword/furigana + name-type
    // tags + a flat translation list - see PersistentDatabaseComponent.parsePrecomputedSummary's
    // isName branch, the client-side counterpart this must stay in sync with.
    private static String buildNameRenderJsonExpr(String entryIdExpr, @Nullable String matchedFormIdExpr) {
        final String chosenFormId = buildChosenFormIdExpr("names", entryIdExpr, matchedFormIdExpr);
        final String chosenFormGatedReading = "(SELECT CASE WHEN form_type = 'writing' THEN reading ELSE NULL END "
                + "FROM names.EntryForm WHERE form_id = " + chosenFormId + ")";

        return "(SELECT json_object("
                + "'isName', 1, "
                + "'primaryText', (SELECT text FROM names.EntryForm WHERE form_id = " + chosenFormId + "), "
                + "'primaryReading', " + chosenFormGatedReading + ", "
                + "'furigana', (SELECT json_group_array(json_array(base, ruby)) FROM names.FormFuriganaSegment "
                + "WHERE form_id = " + chosenFormId + " ORDER BY ord), "
                + "'altWritings', NULL, 'altReadings', NULL, 'usedBackupLang', 0, "
                + "'senseRows', NULL, 'tagsByGroup', NULL, 'glossBySense', NULL, 'glossBySenseBackup', NULL, "
                + "'restrictedFormsBySense', NULL, "
                + "'nameTypeCodes', (SELECT json_group_array(Tag.code) FROM names.EntryTag "
                + "JOIN names.Tag ON Tag.tag_id = names.EntryTag.tag_id "
                + "WHERE names.EntryTag.entry_id = " + entryIdExpr + " AND Tag.category = 'name_type' ORDER BY Tag.sort_order), "
                + "'translations', (SELECT json_group_array(text) FROM names.NameTranslation WHERE entry_id = " + entryIdExpr + " ORDER BY ord)"
                + ")) AS render_json";
    }

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
                    + "dictionary_form, deinflection_label, rank, bookmark, memo, tags, render_json) "
                    + "SELECT ? AS ref, ? AS entryOrder, SearchTerm.entry_id, "
                    + "CAST(Entry.source_key AS INTEGER), SearchTerm.form_id, "
                    + "'%s' AS match_kind, ? AS original_query, SearchTerm.term AS matched_text, "
                    + "NULL, NULL, (0 - Entry.score) AS rank, "
                    + "IFNULL(DictionaryBookmark.bookmark, 0), DictionaryBookmark.memo, DictionaryBookmark.tags, "
                    + "NULL AS render_json "
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
                    + "dictionary_form, deinflection_label, rank, bookmark, memo, tags, render_json) "
                    + "SELECT ? AS ref, ? AS entryOrder, Sense.entry_id, "
                    + "CAST(Entry.source_key AS INTEGER), NULL AS form_id, "
                    + "'gloss' AS match_kind, ? AS original_query, SenseGloss.text AS matched_text, "
                    + "NULL, NULL, MIN(Sense.ord) AS rank, "
                    + "IFNULL(DictionaryBookmark.bookmark, 0), DictionaryBookmark.memo, DictionaryBookmark.tags, "
                    + "NULL AS render_json "
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
                    + "dictionary_form, deinflection_label, rank, bookmark, memo, tags, render_json) "
                    + "SELECT ? AS ref, ? AS entryOrder, SearchTerm.entry_id, "
                    + "CAST(Entry.source_key AS INTEGER), SearchTerm.form_id, "
                    + "'deinflection' AS match_kind, ? AS original_query, SearchTerm.term AS matched_text, "
                    + "? AS dictionary_form, ? AS deinflection_label, (0 - Entry.score) AS rank, "
                    + "IFNULL(DictionaryBookmark.bookmark, 0), DictionaryBookmark.memo, DictionaryBookmark.tags, "
                    + "NULL AS render_json "
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
                    + "dictionary_form, deinflection_label, rank, bookmark, memo, tags, render_json) "
                    + "SELECT ? AS ref, ? AS entryOrder, SearchTerm.entry_id, 0, SearchTerm.form_id, "
                    + "'name' AS match_kind, ? AS original_query, SearchTerm.term AS matched_text, "
                    + "NULL, NULL, 0 AS rank, 0, NULL, NULL, "
                    + "NULL AS render_json "
                    + "FROM names.SearchTerm "
                    + "WHERE SearchTerm.script = '%s' AND SearchTerm.normalized %s";

    static final String SQL_QUERY_DELETE =
            "DELETE FROM DictionarySearchElement WHERE ref = ?";

    // Fills in render_json for rows that already matched (any tier) but haven't been rendered yet,
    // bounded to `limit` rows in the same order the UI displays them - see the comment on
    // SQL_QUERY_BOOKMARK_LISTING for why this is a separate bounded pass instead of computing
    // render_json inline at match time. Two variants because core-pack rows (bookmark/basic/
    // deinflection/gloss tiers) and names-pack rows (proper noun tier) resolve their render payload
    // against different attached databases (core vs names); match_kind='name' distinguishes them.
    // "rowid IN (SELECT ... LIMIT ?)" is how a bounded UPDATE is expressed in stock SQLite, which
    // doesn't support UPDATE ... ORDER BY ... LIMIT directly.
    private static final String SQL_QUERY_BACKFILL_RENDER =
            "UPDATE DictionarySearchElement SET render_json = %s "
                    + "WHERE rowid IN (SELECT rowid FROM DictionarySearchElement "
                    + "WHERE ref = ? AND render_json IS NULL AND match_kind %s "
                    + "ORDER BY entryOrder, rank LIMIT ?)";

    // buildRenderJsonExpr/buildNameRenderJsonExpr are written as SELECT-list column expressions
    // ("(...) AS render_json") for their other caller (BookmarkImportQueryTool, which still computes
    // render_json eagerly at insert time - it's a small one-shot import preview, not the high-volume
    // scroll path this file's tiers are on, so the tradeoff that motivated bounding it here doesn't
    // apply there). SQL_QUERY_BACKFILL_RENDER's SET target can't carry a trailing alias, so strip it
    // here rather than changing the shared builders' contract.
    private static String asUpdateExpr(final String selectListExpr) {
        final String suffix = ") AS render_json";
        return selectListExpr.endsWith(suffix)
                ? selectListExpr.substring(0, selectListExpr.length() - suffix.length()) + ")"
                : selectListExpr;
    }

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

    private SupportSQLiteStatement backfillCoreStatement;
    private SupportSQLiteStatement backfillNamesStatement;

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

    static boolean isInstalled(final List<InstalledDictionary> installed, final String type, final String lang) {
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

    private SupportSQLiteStatement compileBasic(final SupportSQLiteDatabase db,
                                                 final String matchKind, final String fromClause, final String whereClause) {
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

        final String glossAliasOrNull = glossInstalled ? glossAlias(persistentLanguageSettings.lang) : null;
        final String backupGlossAliasOrNull = glossBackupInstalled ? glossAlias(persistentLanguageSettings.backupLang) : null;

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

        // Backfill: entry_id/form_id are now columns on the already-inserted row rather than
        // SearchTerm's, so this is one shared expression for every core-pack tier (bookmark listing
        // and gloss hits leave form_id NULL, which buildChosenFormIdExpr already resolves to "use
        // the entry's primary form" - the same fallback buildRenderJsonExpr already implements for a
        // literal null matchedFormIdExpr, just now driven by the column's runtime value instead of
        // a compile-time branch).
        final String backfillCoreRenderExpr = asUpdateExpr(buildRenderJsonExpr(
                "DictionarySearchElement.entry_id", "DictionarySearchElement.form_id", glossAliasOrNull, backupGlossAliasOrNull));
        backfillCoreStatement = db.compileStatement(String.format(SQL_QUERY_BACKFILL_RENDER, backfillCoreRenderExpr, "!= 'name'"));

        if (namesInstalled) {
            final String backfillNamesRenderExpr = asUpdateExpr(buildNameRenderJsonExpr(
                    "DictionarySearchElement.entry_id", "DictionarySearchElement.form_id"));
            backfillNamesStatement = db.compileStatement(String.format(SQL_QUERY_BACKFILL_RENDER, backfillNamesRenderExpr, "= 'name'"));
        }

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

    static String glossAlias(final String lang) {
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

    // Renders up to `limit` already-matched-but-unrendered rows, in display order - see
    // SQL_QUERY_BACKFILL_RENDER. Call after every match-finding step (LanguageAttached, a fresh
    // search, scrolling further, reopening the search box) so the rows about to be shown have a
    // render_json by the time the paged list actually reads them; safe and cheap to call
    // redundantly since render_json IS NULL naturally makes it a no-op once nothing needs it.
    @WorkerThread
    public void backfillRenderJson(int limit) {
        backfillCoreStatement.bindLong(1, key);
        backfillCoreStatement.bindLong(2, limit);
        backfillCoreStatement.execute();

        if (backfillNamesStatement != null) {
            backfillNamesStatement.bindLong(1, key);
            backfillNamesStatement.bindLong(2, limit);
            backfillNamesStatement.execute();
        }
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

        closeQuietly(backfillCoreStatement);
        closeQuietly(backfillNamesStatement);
        backfillCoreStatement = null;
        backfillNamesStatement = null;
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
