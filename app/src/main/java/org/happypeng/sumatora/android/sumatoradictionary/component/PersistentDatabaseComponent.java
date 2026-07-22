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

package org.happypeng.sumatora.android.sumatoradictionary.component;

import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;
import androidx.paging.DataSource;
import androidx.paging.LivePagedListBuilder;
import androidx.paging.PagedList;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryControlInfo;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryKanjiInfo;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchElement;
import org.happypeng.sumatora.android.sumatoradictionary.db.EntryDetail;
import org.happypeng.sumatora.android.sumatoradictionary.db.EntryListSummary;
import org.happypeng.sumatora.android.sumatoradictionary.db.InstalledDictionary;
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentDatabase;
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentDatabaseInitialization;
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentLanguageSettings;
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.SumatoraSQLiteOpenHelperFactory;
import org.happypeng.sumatora.jromkan.Romkan;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

import static org.happypeng.sumatora.android.sumatoradictionary.db.PersistentDatabaseParameters.MIGRATION_1_2;
import static org.happypeng.sumatora.android.sumatoradictionary.db.PersistentDatabaseParameters.MIGRATION_2_3;
import static org.happypeng.sumatora.android.sumatoradictionary.db.PersistentDatabaseParameters.MIGRATION_3_4;
import static org.happypeng.sumatora.android.sumatoradictionary.db.PersistentDatabaseParameters.MIGRATION_4_5;
import static org.happypeng.sumatora.android.sumatoradictionary.db.PersistentDatabaseParameters.MIGRATION_5_6;
import static org.happypeng.sumatora.android.sumatoradictionary.db.PersistentDatabaseParameters.MIGRATION_6_7;
import static org.happypeng.sumatora.android.sumatoradictionary.db.PersistentDatabaseParameters.MIGRATION_7_8;
import static org.happypeng.sumatora.android.sumatoradictionary.db.PersistentDatabaseParameters.MIGRATION_8_9;
import static org.happypeng.sumatora.android.sumatoradictionary.db.PersistentDatabaseParameters.MIGRATION_9_10;
import static org.happypeng.sumatora.android.sumatoradictionary.db.PersistentDatabaseParameters.MIGRATION_10_11;
import static org.happypeng.sumatora.android.sumatoradictionary.db.PersistentDatabaseParameters.MIGRATION_11_12;
import static org.happypeng.sumatora.android.sumatoradictionary.db.PersistentDatabaseParameters.PERSISTENT_DATABASE_NAME;

@Singleton
public class PersistentDatabaseComponent {
    private static final int PAGE_SIZE = 30;
    private static final int PREFETCH_DISTANCE = 50;

    private final PersistentDatabase database;
    private final Context context;
    private boolean databaseInitialized;
    private final DictionaryControlInfo dictionaryControlInfo;
    private final Romkan romkan;

    @Inject
    PersistentDatabaseComponent(@ApplicationContext final Context context) {
        this.context = context;
        this.databaseInitialized = false;
        this.dictionaryControlInfo = new DictionaryControlInfo();

        database = Room.databaseBuilder(context,
                PersistentDatabase.class, PERSISTENT_DATABASE_NAME)
                .openHelperFactory(new SumatoraSQLiteOpenHelperFactory())
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4,
                        MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9,
                        MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12)
                .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                .build();

        this.romkan = new Romkan();
    }

    public Romkan getRomkan() {
        return romkan;
    }

    @WorkerThread
    public synchronized void initialize() {
        if (databaseInitialized) {
            return;
        }

        PersistentDatabaseInitialization.initializeDatabase(context, database, dictionaryControlInfo);

        databaseInitialized = true;
    }

    @WorkerThread
    public DictionaryControlInfo getDictionaryControlInfo() {
        if (!databaseInitialized) {
            initialize();
        }

        return dictionaryControlInfo;
    }

    @WorkerThread
    public PersistentDatabase getDatabase() {
        if (!databaseInitialized) {
            initialize();
        }

        return database;
    }


    private static final String FORM_QUERY_PRIMARY =
            "SELECT text, reading, form_type, form_id, is_common FROM %s.EntryForm WHERE entry_id = ? AND is_primary = 1 LIMIT 1";

    // The specific form a search hit matched (DictionaryQueryResult.formId) - e.g. 発条 matched
    // via a rK kanji spelling should still show 発条 rather than collapsing to the entry's
    // kana-only primary reading. is_search_only forms (JMdict sK/sk) are excluded even when
    // matched, since those exist purely to be found, not displayed.
    private static final String FORM_QUERY_BY_ID =
            "SELECT text, reading, form_type, form_id, is_common FROM %s.EntryForm WHERE form_id = ? AND is_search_only = 0 LIMIT 1";

    // Which SearchTerm row matched should only decide what gets highlighted, never make the app
    // hide content that exists on the entry - so a match on the bare kana reading still pairs it
    // with a kanji spelling here, the same way a match on a kanji form already shows its reading.
    private static final String FORM_QUERY_PAIRED_WRITING =
            "SELECT text, reading, form_type, form_id, is_common FROM %s.EntryForm "
                    + "WHERE entry_id = ? AND form_type = 'writing' AND reading = ? AND is_search_only = 0 "
                    + "ORDER BY is_primary DESC, score DESC, ord LIMIT 1";

    // Other kanji spellings sharing the exact reading being displayed - the list row only shows
    // one furigana reading, so mixing in a spelling read differently would look like it shares
    // the same pronunciation. Excludes the displayed form itself and is_search_only forms.
    // Furigana is joined in directly (grouped in code below) instead of a separate query per
    // alternate writing - one round trip instead of one query per result set.
    private static final String FORM_QUERY_ALTERNATE_WRITINGS =
            "SELECT ef.text, ef.form_id, ffs.base, ffs.ruby FROM %s.EntryForm ef "
                    + "LEFT JOIN %s.FormFuriganaSegment ffs ON ffs.form_id = ef.form_id "
                    + "WHERE ef.entry_id = ? AND ef.form_type = 'writing' AND ef.is_search_only = 0 "
                    + "AND ef.form_id != ? AND ef.reading = (SELECT reading FROM %s.EntryForm WHERE form_id = ?) "
                    + "ORDER BY ef.is_primary DESC, ef.score DESC, ef.ord, ffs.ord";

    // Other readings the displayed kanji spelling itself can take (e.g. 二 also reads ふた/ふ/ふう) -
    // the search could have hit any of them, but furigana only ever shows the one that was
    // actually matched/promoted, so a different valid reading would otherwise be invisible short
    // of opening the detail sheet's forms table.
    private static final String FORM_QUERY_ALTERNATE_READINGS =
            "SELECT reading FROM %s.EntryForm WHERE entry_id = ? AND form_type = 'writing' AND text = ? "
                    + "AND is_search_only = 0 AND reading != ? "
                    + "ORDER BY is_primary DESC, score DESC, ord";

    private static final class DisplayForm {
        @Nullable String text;
        @Nullable String reading;
        long formId = -1;
        boolean isCommon;
    }

    @WorkerThread
    private List<EntryListSummary.AlternateWriting> fetchAlternateWritings(SupportSQLiteDatabase readable, String pack,
                                                                            long entryId, long formId) {
        List<EntryListSummary.AlternateWriting> result = new ArrayList<>();
        Map<Long, EntryListSummary.AlternateWriting> byFormId = new LinkedHashMap<>();
        try {
            Cursor cur = readable.query(String.format(FORM_QUERY_ALTERNATE_WRITINGS, pack, pack, pack),
                    new Object[]{entryId, formId, formId});
            if (cur != null) {
                while (cur.moveToNext()) {
                    long altFormId = cur.getLong(1);
                    EntryListSummary.AlternateWriting alt = byFormId.get(altFormId);
                    if (alt == null) {
                        alt = new EntryListSummary.AlternateWriting();
                        alt.text = cur.getString(0);
                        byFormId.put(altFormId, alt);
                        result.add(alt);
                    }
                    final String base = cur.getString(2);
                    if (base != null) {
                        alt.furiganaSegments.add(new EntryListSummary.FuriganaSegment(base, cur.getString(3)));
                    }
                }
                cur.close();
            }
        } catch (SQLException ignored) {
        }
        return result;
    }

    @WorkerThread
    private List<String> fetchAlternateReadings(SupportSQLiteDatabase readable, String pack,
                                                 long entryId, String text, String matchedReading) {
        List<String> result = new ArrayList<>();
        try {
            Cursor cur = readable.query(String.format(FORM_QUERY_ALTERNATE_READINGS, pack),
                    new Object[]{entryId, text, matchedReading});
            if (cur != null) {
                while (cur.moveToNext()) {
                    result.add(cur.getString(0));
                }
                cur.close();
            }
        } catch (SQLException ignored) {
        }
        return result;
    }

    // Picks the form to show as headword+reading: the form that actually matched the search hit
    // when there is one and it's displayable, otherwise the entry's globally-designated primary
    // form (used for match kinds with no specific form, e.g. gloss/bookmark-listing hits).
    @WorkerThread
    private DisplayForm fetchDisplayForm(SupportSQLiteDatabase readable, String pack, long entryId,
                                          @Nullable Long matchedFormId) {
        final DisplayForm result = new DisplayForm();
        try {
            Cursor cur = null;
            if (matchedFormId != null) {
                cur = readable.query(String.format(FORM_QUERY_BY_ID, pack), new Object[]{matchedFormId});
                if (cur != null && cur.moveToFirst()) {
                    if ("reading".equals(cur.getString(2))) {
                        Cursor pairedCur = readable.query(String.format(FORM_QUERY_PAIRED_WRITING, pack),
                                new Object[]{entryId, cur.getString(0)});
                        if (pairedCur != null) {
                            if (pairedCur.moveToFirst()) {
                                cur.close();
                                cur = pairedCur;
                            } else {
                                pairedCur.close();
                            }
                        }
                    }
                } else if (cur != null) {
                    cur.close();
                    cur = null;
                }
            }
            if (cur == null) {
                cur = readable.query(String.format(FORM_QUERY_PRIMARY, pack), new Object[]{entryId});
                if (cur != null && !cur.moveToFirst()) {
                    cur.close();
                    cur = null;
                }
            }
            if (cur != null) {
                result.text = cur.getString(0);
                result.reading = "writing".equals(cur.getString(2)) ? cur.getString(1) : null;
                result.formId = cur.getLong(3);
                result.isCommon = cur.getInt(4) != 0;
                cur.close();
            }
        } catch (SQLException ignored) {
        }
        return result;
    }

    @WorkerThread
    private List<EntryListSummary.FuriganaSegment> fetchFurigana(SupportSQLiteDatabase readable,
                                                                  String pack, long formId) {
        return fetchFurigana(readable, pack, "FormFuriganaSegment", "form_id", formId);
    }

    // FormFuriganaSegment(form_id, ord, base, ruby) and ExampleSegment(example_id, ord, base, ruby)
    // share the same (id, ord, base, ruby) shape - one helper covers headword and example furigana.
    private List<EntryListSummary.FuriganaSegment> fetchFurigana(SupportSQLiteDatabase readable,
                                                                  String pack, String table,
                                                                  String idColumn, long id) {
        List<EntryListSummary.FuriganaSegment> segments = new ArrayList<>();
        try {
            Cursor cur = readable.query("SELECT base, ruby FROM " + pack + "." + table + " "
                    + "WHERE " + idColumn + " = ? ORDER BY ord", new Object[]{id});
            if (cur != null) {
                while (cur.moveToNext()) {
                    segments.add(new EntryListSummary.FuriganaSegment(cur.getString(0), cur.getString(1)));
                }
                cur.close();
            }
        } catch (SQLException ignored) {
        }
        return segments;
    }

    // Called from parsePrecomputedSummary() below to turn a bookmark/search row's precomputed
    // render_json sense data into displayed sense groups: adjacent sense groups sharing identical
    // tags AND identical restricted-form-id sets get merged into one displayed group. Each sense
    // is resolved independently - main-language gloss first, backup only for that specific sense
    // if the main language has nothing for it - so a partially-translated entry shows exactly
    // which senses are genuine main-language translations and which fell back (see
    // EntryListSummary.SenseSummary.usedBackupLang), rather than judging the whole entry at once.
    private static void mergeSenseGroups(List<long[]> senseRows, Map<Long, List<String>> tagsByGroup,
                                         Map<Long, List<String>> glossBySense, Map<Long, List<String>> glossBySenseBackup,
                                         Map<Long, List<Long>> restrictedFormsBySense,
                                         EntryListSummary summary) {
        if (senseRows.isEmpty()) {
            return;
        }

        int[] displayIndex = {0};
        EntryListSummary.SenseGroupSummary lastGroup = null;
        List<Long> lastRestrictedFormIds = null;

        long currentGroupId = 0;
        List<String> currentTags = Collections.emptyList();
        List<EntryListSummary.SenseSummary> currentSenses = new ArrayList<>();
        List<Long> currentRestrictedFormIds = null;
        boolean groupOpen = false;

        for (long[] row : senseRows) {
            long groupId = row[0];
            long senseId = row[1];

            if (groupOpen && groupId != currentGroupId) {
                if (!currentSenses.isEmpty()) {
                    List<Long> restrictedFormIds = currentRestrictedFormIds != null
                            ? currentRestrictedFormIds : Collections.emptyList();
                    if (lastGroup != null && lastGroup.tagCodes.equals(currentTags)
                            && lastRestrictedFormIds.equals(restrictedFormIds)) {
                        lastGroup.senses.addAll(currentSenses);
                    } else {
                        EntryListSummary.SenseGroupSummary newGroup = new EntryListSummary.SenseGroupSummary();
                        newGroup.tagCodes = currentTags;
                        newGroup.senses = currentSenses;
                        summary.senseGroups.add(newGroup);
                        lastGroup = newGroup;
                        lastRestrictedFormIds = restrictedFormIds;
                    }
                }
                groupOpen = false;
            }
            if (!groupOpen) {
                currentGroupId = groupId;
                currentTags = tagsByGroup.getOrDefault(groupId, Collections.emptyList());
                currentSenses = new ArrayList<>();
                currentRestrictedFormIds = null;
                groupOpen = true;
            }

            List<String> glossTexts = glossBySense.get(senseId);
            boolean usedBackupLang = false;
            if (glossTexts == null || glossTexts.isEmpty()) {
                glossTexts = glossBySenseBackup.get(senseId);
                usedBackupLang = true;
            }
            if (glossTexts == null || glossTexts.isEmpty()) {
                continue;
            }
            if (currentRestrictedFormIds == null) {
                currentRestrictedFormIds = restrictedFormsBySense.getOrDefault(senseId, Collections.emptyList());
            }
            EntryListSummary.SenseSummary sense = new EntryListSummary.SenseSummary();
            sense.displayIndex = ++displayIndex[0];
            sense.glossText = String.join("; ", glossTexts);
            sense.usedBackupLang = usedBackupLang;
            currentSenses.add(sense);
        }
        if (groupOpen && !currentSenses.isEmpty()) {
            List<Long> restrictedFormIds = currentRestrictedFormIds != null
                    ? currentRestrictedFormIds : Collections.emptyList();
            if (lastGroup != null && lastGroup.tagCodes.equals(currentTags)
                    && lastRestrictedFormIds.equals(restrictedFormIds)) {
                lastGroup.senses.addAll(currentSenses);
            } else {
                EntryListSummary.SenseGroupSummary newGroup = new EntryListSummary.SenseGroupSummary();
                newGroup.tagCodes = currentTags;
                newGroup.senses = currentSenses;
                summary.senseGroups.add(newGroup);
            }
        }
    }

    // Parses a search-result row's precomputed render_json (assembled at insert time by every
    // search tier - see DictionarySearchQueryTool.buildRenderJsonExpr/buildNameRenderJsonExpr)
    // into the display-ready EntryListSummary shape. Pure JSON parsing, no DB access, so this is
    // safe to call synchronously from the main thread at bind time. The top-level 'usedBackupLang'
    // is kept only as an entry-wide diagnostic ("does the main language have zero senses at all
    // for this entry") - the actual per-sense text selection happens in mergeSenseGroups(), which
    // is handed both raw gloss maps and picks main-vs-backup independently for each sense.
    @Nullable
    public static EntryListSummary parsePrecomputedSummary(String renderJson) {
        try {
            final JSONObject obj = new JSONObject(renderJson);
            final EntryListSummary summary = new EntryListSummary();

            summary.isName = obj.optInt("isName", 0) != 0;
            summary.primaryText = obj.isNull("primaryText") ? null : obj.getString("primaryText");
            summary.primaryReading = obj.isNull("primaryReading") ? null : obj.getString("primaryReading");
            summary.furiganaSegments = obj.isNull("furigana") ? new ArrayList<>() : parseFuriganaJson(obj.getString("furigana"));

            if (summary.isName) {
                // Name entries: no senses/alt-forms at all, just name-type tags and a flat
                // translation list - see buildNameRenderJsonExpr.
                summary.nameTypeCodes = obj.isNull("nameTypeCodes") ? new ArrayList<>() : parseStringArrayJson(obj.getString("nameTypeCodes"));
                summary.translations = obj.isNull("translations") ? new ArrayList<>() : parseStringArrayJson(obj.getString("translations"));
                return summary;
            }

            summary.alternateWritings = obj.isNull("altWritings") ? new ArrayList<>() : parseAlternateWritingsJson(obj.getString("altWritings"));
            summary.alternateReadings = obj.isNull("altReadings") ? new ArrayList<>() : parseStringArrayJson(obj.getString("altReadings"));
            summary.usedBackupLang = obj.optInt("usedBackupLang", 0) != 0;

            final List<long[]> senseRows = obj.isNull("senseRows") ? Collections.emptyList() : parseLongPairArray(obj.getString("senseRows"));
            final Map<Long, List<String>> tagsByGroup = obj.isNull("tagsByGroup") ? Collections.emptyMap() : parseLongToStringListMap(obj.getString("tagsByGroup"));
            final Map<Long, List<String>> glossMain = obj.isNull("glossBySense") ? Collections.emptyMap() : parseLongToStringListMap(obj.getString("glossBySense"));
            final Map<Long, List<String>> glossBackup = obj.isNull("glossBySenseBackup") ? Collections.emptyMap() : parseLongToStringListMap(obj.getString("glossBySenseBackup"));
            final Map<Long, List<Long>> restrictedFormsBySense = obj.isNull("restrictedFormsBySense") ? Collections.emptyMap() : parseLongToLongListMap(obj.getString("restrictedFormsBySense"));

            mergeSenseGroups(senseRows, tagsByGroup, glossMain, glossBackup, restrictedFormsBySense, summary);

            return summary;
        } catch (JSONException e) {
            return null;
        }
    }

    private static List<long[]> parseLongPairArray(String json) {
        List<long[]> result = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONArray pair = arr.getJSONArray(i);
                result.add(new long[]{pair.getLong(0), pair.getLong(1)});
            }
        } catch (JSONException ignored) {
        }
        return result;
    }

    private static Map<Long, List<String>> parseLongToStringListMap(String json) {
        Map<Long, List<String>> result = new LinkedHashMap<>();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONArray pair = arr.getJSONArray(i);
                result.computeIfAbsent(pair.getLong(0), k -> new ArrayList<>()).add(pair.getString(1));
            }
        } catch (JSONException ignored) {
        }
        return result;
    }

    private static Map<Long, List<Long>> parseLongToLongListMap(String json) {
        Map<Long, List<Long>> result = new LinkedHashMap<>();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONArray pair = arr.getJSONArray(i);
                result.computeIfAbsent(pair.getLong(0), k -> new ArrayList<>()).add(pair.getLong(1));
            }
        } catch (JSONException ignored) {
        }
        return result;
    }

    private static List<EntryListSummary.FuriganaSegment> parseFuriganaJsonArray(JSONArray arr) throws JSONException {
        List<EntryListSummary.FuriganaSegment> result = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            JSONArray seg = arr.getJSONArray(i);
            result.add(new EntryListSummary.FuriganaSegment(seg.getString(0), seg.isNull(1) ? null : seg.getString(1)));
        }
        return result;
    }

    private static List<EntryListSummary.FuriganaSegment> parseFuriganaJson(String json) {
        try {
            return parseFuriganaJsonArray(new JSONArray(json));
        } catch (JSONException e) {
            return new ArrayList<>();
        }
    }

    private static List<EntryListSummary.AlternateWriting> parseAlternateWritingsJson(String json) {
        List<EntryListSummary.AlternateWriting> result = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                EntryListSummary.AlternateWriting alt = new EntryListSummary.AlternateWriting();
                alt.text = obj.getString("text");
                alt.furiganaSegments = parseFuriganaJsonArray(obj.getJSONArray("furigana"));
                result.add(alt);
            }
        } catch (JSONException ignored) {
        }
        return result;
    }

    private static List<String> parseStringArrayJson(String json) {
        List<String> result = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) {
                result.add(arr.getString(i));
            }
        } catch (JSONException ignored) {
        }
        return result;
    }

    @WorkerThread
    @Nullable
    public DictionaryKanjiInfo fetchKanjiInfo(String character) {
        final PersistentDatabase db = getDatabase();
        final SupportSQLiteDatabase readable = db.getOpenHelper().getReadableDatabase();

        try {
            Cursor cur = readable.query(
                    "SELECT strokes, grade, jlpt, frequency, radical "
                            + "FROM kanji.KanjiEntry WHERE character = ?",
                    new Object[]{character});

            if (cur == null) {
                return null;
            }

            DictionaryKanjiInfo result = null;
            if (cur.moveToNext()) {
                result = new DictionaryKanjiInfo();
                result.character = character;
                result.strokes = cur.isNull(0) ? null : cur.getInt(0);
                result.grade = cur.isNull(1) ? null : cur.getInt(1);
                result.jlpt = cur.isNull(2) ? null : cur.getInt(2);
                result.freq = cur.isNull(3) ? null : cur.getInt(3);
                result.radical = cur.isNull(4) ? null : cur.getInt(4);
            }

            cur.close();

            if (result == null) {
                return null;
            }

            Cursor readingCur = readable.query(
                    "SELECT reading_type, text FROM kanji.KanjiReading WHERE character = ? ORDER BY reading_type, ord",
                    new Object[]{character});
            if (readingCur != null) {
                while (readingCur.moveToNext()) {
                    if ("on".equals(readingCur.getString(0))) {
                        result.onReadings.add(readingCur.getString(1));
                    } else if ("kun".equals(readingCur.getString(0))) {
                        result.kunReadings.add(readingCur.getString(1));
                    }
                }
                readingCur.close();
            }

            Cursor meaningCur = readable.query(
                    "SELECT text FROM kanji.KanjiMeaning WHERE character = ? AND lang = 'eng' ORDER BY ord",
                    new Object[]{character});
            if (meaningCur != null) {
                while (meaningCur.moveToNext()) {
                    result.meanings.add(meaningCur.getString(0));
                }
                meaningCur.close();
            }

            return result;
        } catch (SQLException e) {
            return null;
        }
    }

    // Looks up pitch pattern positions for a specific form (FormPitch links form_id directly to
    // a pitch row - no more guessing from whichever word/reading the app happened to have on
    // hand). Prefers an exact-confidence link, falls back to reading_fallback.
    @WorkerThread
    @Nullable
    public List<Integer> fetchPitchPatterns(long formId) {
        final SupportSQLiteDatabase readable = getDatabase().getOpenHelper().getReadableDatabase();

        try {
            Long pitchId = queryPitchId(readable, formId, "exact");
            if (pitchId == null) {
                pitchId = queryPitchId(readable, formId, "reading_fallback");
            }
            if (pitchId == null) {
                return null;
            }

            List<Integer> positions = new ArrayList<>();
            Cursor cur = readable.query(
                    "SELECT position FROM pitch.PitchPattern WHERE pitch_id = ? ORDER BY ord",
                    new Object[]{pitchId});
            if (cur != null) {
                while (cur.moveToNext()) {
                    positions.add(cur.getInt(0));
                }
                cur.close();
            }
            return positions;
        } catch (SQLException e) {
            return null;
        }
    }

    @WorkerThread
    @Nullable
    private Long queryPitchId(SupportSQLiteDatabase readable, long formId, String confidence) {
        Cursor cur = readable.query(
                "SELECT pitch_id FROM pitch.FormPitch WHERE form_id = ? AND confidence = ? LIMIT 1",
                new Object[]{formId, confidence});
        Long result = null;
        if (cur != null) {
            if (cur.moveToNext()) {
                result = cur.getLong(0);
            }
            cur.close();
        }
        return result;
    }

    @WorkerThread
    private List<String> fetchStrings(SupportSQLiteDatabase readable, String sql, long id) {
        List<String> result = new ArrayList<>();
        try {
            Cursor cur = readable.query(sql, new Object[]{id});
            if (cur != null) {
                while (cur.moveToNext()) {
                    result.add(cur.getString(0));
                }
                cur.close();
            }
        } catch (SQLException ignored) {
        }
        return result;
    }

    @WorkerThread
    private List<EntryDetail.Xref> fetchXrefs(SupportSQLiteDatabase readable, long senseId, String referenceType) {
        List<EntryDetail.Xref> result = new ArrayList<>();
        try {
            Cursor cur = readable.query(
                    "SELECT display_text, target_entry_id, target_sense_number, preview_text "
                            + "FROM core.SenseReference WHERE sense_id = ? AND reference_type = ? ORDER BY ord",
                    new Object[]{senseId, referenceType});
            if (cur != null) {
                while (cur.moveToNext()) {
                    result.add(new EntryDetail.Xref(
                            cur.getString(0),
                            cur.isNull(1) ? null : cur.getLong(1),
                            cur.isNull(2) ? null : cur.getInt(2),
                            cur.isNull(3) ? null : cur.getString(3)));
                }
                cur.close();
            }
        } catch (SQLException ignored) {
        }
        return result;
    }

    @WorkerThread
    private List<EntryDetail.LanguageSource> fetchLanguageSources(SupportSQLiteDatabase readable, long senseId) {
        List<EntryDetail.LanguageSource> result = new ArrayList<>();
        try {
            Cursor cur = readable.query(
                    "SELECT lang, text, is_wasei FROM core.SenseLanguageSource WHERE sense_id = ? ORDER BY ord",
                    new Object[]{senseId});
            if (cur != null) {
                while (cur.moveToNext()) {
                    result.add(new EntryDetail.LanguageSource(cur.getString(0), cur.getString(1), cur.getInt(2) != 0));
                }
                cur.close();
            }
        } catch (SQLException ignored) {
        }
        return result;
    }

    @WorkerThread
    private boolean glossHasEntry(SupportSQLiteDatabase readable, String lang, long entryId) {
        try {
            Cursor cur = readable.query("SELECT 1 FROM gloss_" + lang + ".Sense WHERE entry_id = ? LIMIT 1",
                    new Object[]{entryId});
            boolean has = cur != null && cur.moveToNext();
            if (cur != null) cur.close();
            return has;
        } catch (SQLException e) {
            return false;
        }
    }

    @WorkerThread
    @Nullable
    private String fetchGlossText(SupportSQLiteDatabase readable, String lang, long senseId) {
        try {
            Cursor cur = readable.query(
                    "SELECT text FROM gloss_" + lang + ".SenseGloss WHERE sense_id = ? ORDER BY ord",
                    new Object[]{senseId});
            if (cur == null) {
                return null;
            }
            List<String> texts = new ArrayList<>();
            while (cur.moveToNext()) {
                texts.add(cur.getString(0));
            }
            cur.close();
            return texts.isEmpty() ? null : String.join("; ", texts);
        } catch (SQLException e) {
            return null;
        }
    }

    @WorkerThread
    private List<EntryDetail.Example> fetchExamples(SupportSQLiteDatabase readable, String lang, long entryId) {
        List<EntryDetail.Example> result = new ArrayList<>();
        try {
            Cursor cur = readable.query(
                    "SELECT EntryExample.example_id, EntryExample.matched_text, Example.translation, "
                            + "EntryExample.sense_id "
                            + "FROM examples_" + lang + ".EntryExample "
                            + "JOIN examples_" + lang + ".Example ON Example.example_id = EntryExample.example_id "
                            + "WHERE EntryExample.entry_id = ? ORDER BY EntryExample.ord",
                    new Object[]{entryId});
            if (cur != null) {
                while (cur.moveToNext()) {
                    long exampleId = cur.getLong(0);
                    EntryDetail.Example example = new EntryDetail.Example();
                    example.matchedText = cur.getString(1);
                    example.translation = cur.getString(2);
                    example.senseId = cur.isNull(3) ? null : cur.getLong(3);
                    example.segments = fetchFurigana(readable, "examples_" + lang, "ExampleSegment", "example_id", exampleId);
                    result.add(example);
                }
                cur.close();
            }
        } catch (SQLException ignored) {
        }
        return result;
    }

    // Sorted SenseAppliesToForm.form_id set for one sense - empty (not null) when unrestricted,
    // so it can be compared directly against a sibling group's set for merge/label purposes.
    @WorkerThread
    private List<Long> fetchRestrictedFormIds(SupportSQLiteDatabase readable, long senseId) {
        List<Long> result = new ArrayList<>();
        try {
            Cursor cur = readable.query(
                    "SELECT form_id FROM core.SenseAppliesToForm WHERE sense_id = ? ORDER BY form_id",
                    new Object[]{senseId});
            if (cur != null) {
                while (cur.moveToNext()) {
                    result.add(cur.getLong(0));
                }
                cur.close();
            }
        } catch (SQLException ignored) {
        }
        return result;
    }

    // Human-readable readings a restricted sense group is limited to (e.g. "ばね・バネ"), built
    // from the restricted forms' own reading (writing forms) or text (kana-only reading forms).
    // Can't distinguish an original stagk (kanji-based) restriction from stagr (reading-based) -
    // the generator unions both into SenseAppliesToForm - but stagr is by far the common case.
    @WorkerThread
    @Nullable
    private String resolveRestrictionLabel(SupportSQLiteDatabase readable, List<Long> formIds) {
        if (formIds.isEmpty()) {
            return null;
        }
        StringBuilder placeholders = new StringBuilder();
        Object[] args = new Object[formIds.size()];
        for (int i = 0; i < formIds.size(); i++) {
            if (i > 0) placeholders.append(',');
            placeholders.append('?');
            args[i] = formIds.get(i);
        }
        LinkedHashSet<String> readings = new LinkedHashSet<>();
        try {
            Cursor cur = readable.query(
                    "SELECT COALESCE(reading, text) FROM core.EntryForm WHERE form_id IN (" + placeholders + ") "
                            + "GROUP BY COALESCE(reading, text) ORDER BY MIN(ord)",
                    args);
            if (cur != null) {
                while (cur.moveToNext()) {
                    readings.add(cur.getString(0));
                }
                cur.close();
            }
        } catch (SQLException ignored) {
        }
        return readings.isEmpty() ? null : String.join("・", readings);
    }

    // Every kanji+reading combination for the entry, for the "forms" table - excludes
    // is_search_only forms (JMdict sK/sk), same as the display-form picker already does.
    // Kana-only ("∅" column) rows are also excluded unless some sense actually carries JMdict's
    // "uk" (usually kana) tag - every reading gets a bare form_type='reading' row regardless of
    // real-world usage (it exists so kana-only search works even for kanji-only words), so
    // without the uk signal the "∅" column would falsely suggest the word is commonly written
    // without its kanji.
    @WorkerThread
    private List<EntryDetail.FormRow> fetchEntryForms(SupportSQLiteDatabase readable, long entryId) {
        List<EntryDetail.FormRow> result = new ArrayList<>();
        try {
            Cursor cur = readable.query(
                    "SELECT text, reading, form_type, is_primary, is_common, score FROM core.EntryForm "
                            + "WHERE entry_id = ? AND is_search_only = 0 "
                            + "AND (form_type != 'reading' OR EXISTS ("
                            + "SELECT 1 FROM core.SenseGroupTag "
                            + "JOIN core.SenseGroup ON SenseGroup.sense_group_id = SenseGroupTag.sense_group_id "
                            + "JOIN core.Tag ON Tag.tag_id = SenseGroupTag.tag_id "
                            + "WHERE SenseGroup.entry_id = EntryForm.entry_id AND Tag.code = 'uk')) "
                            + "ORDER BY ord",
                    new Object[]{entryId});
            if (cur != null) {
                while (cur.moveToNext()) {
                    String text = cur.getString(0);
                    String reading = cur.getString(1);
                    boolean isKanjiless = "reading".equals(cur.getString(2));
                    boolean isPrimary = cur.getInt(3) != 0;
                    int score = cur.getInt(5);
                    String tier = isPrimary ? "primary" : (score < 0 ? "rare" : "common");
                    result.add(new EntryDetail.FormRow(text, reading, isKanjiless, tier));
                }
                cur.close();
            }
        } catch (SQLException ignored) {
        }
        return result;
    }

    @WorkerThread
    public EntryDetail fetchEntryDetail(long entryId, @Nullable Long formId, boolean isName,
                                        PersistentLanguageSettings languageSettings) {
        final EntryDetail detail = new EntryDetail();
        detail.isName = isName;
        final SupportSQLiteDatabase readable = getDatabase().getOpenHelper().getReadableDatabase();
        final String pack = isName ? "names" : "core";

        final DisplayForm displayForm = fetchDisplayForm(readable, pack, entryId, formId);
        detail.primaryText = displayForm.text;
        detail.primaryReading = displayForm.reading;
        detail.isPriority = displayForm.isCommon;

        if (displayForm.formId >= 0) {
            detail.furiganaSegments = fetchFurigana(readable, pack, displayForm.formId);
        }

        if (isName) {
            try {
                Cursor cur = readable.query(
                        "SELECT Tag.code FROM names.EntryTag JOIN names.Tag ON Tag.tag_id = EntryTag.tag_id "
                                + "WHERE EntryTag.entry_id = ? AND Tag.category = 'name_type' ORDER BY Tag.sort_order",
                        new Object[]{entryId});
                if (cur != null) {
                    while (cur.moveToNext()) detail.nameTypeCodes.add(cur.getString(0));
                    cur.close();
                }
            } catch (SQLException ignored) {
            }
            try {
                Cursor cur = readable.query(
                        "SELECT text FROM names.NameTranslation WHERE entry_id = ? ORDER BY ord",
                        new Object[]{entryId});
                if (cur != null) {
                    while (cur.moveToNext()) detail.translations.add(cur.getString(0));
                    cur.close();
                }
            } catch (SQLException ignored) {
            }
            return detail;
        }

        if (displayForm.text != null && displayForm.reading != null) {
            detail.alternateReadings = fetchAlternateReadings(readable, pack, entryId,
                    displayForm.text, displayForm.reading);
        }

        long pitchFormId = formId != null ? formId : displayForm.formId;
        if (pitchFormId >= 0) {
            List<Integer> pitches = fetchPitchPatterns(pitchFormId);
            if (pitches != null) {
                detail.pitchPatterns = pitches;
            }
        }

        String effectiveLang = null;
        if (glossHasEntry(readable, languageSettings.lang, entryId)) {
            effectiveLang = languageSettings.lang;
        } else if (languageSettings.backupLang != null && glossHasEntry(readable, languageSettings.backupLang, entryId)) {
            effectiveLang = languageSettings.backupLang;
        }
        final String glossLang = effectiveLang;

        int[] displayIndex = {0};
        try {
            Cursor groupCur = readable.query(
                    "SELECT sense_group_id FROM core.SenseGroup WHERE entry_id = ? ORDER BY ord",
                    new Object[]{entryId});
            if (groupCur != null) {
                while (groupCur.moveToNext()) {
                    long groupId = groupCur.getLong(0);

                    List<String> pos = new ArrayList<>();
                    List<String> misc = new ArrayList<>();
                    List<String> field = new ArrayList<>();
                    List<String> dialect = new ArrayList<>();
                    Cursor tagCur = readable.query(
                            "SELECT Tag.category, Tag.code FROM core.SenseGroupTag "
                                    + "JOIN core.Tag ON Tag.tag_id = SenseGroupTag.tag_id "
                                    + "WHERE SenseGroupTag.sense_group_id = ? ORDER BY Tag.sort_order",
                            new Object[]{groupId});
                    if (tagCur != null) {
                        while (tagCur.moveToNext()) {
                            String category = tagCur.getString(0);
                            String code = tagCur.getString(1);
                            if ("pos".equals(category)) pos.add(code);
                            else if ("misc".equals(category)) misc.add(code);
                            else if ("field".equals(category)) field.add(code);
                            else if ("dialect".equals(category)) dialect.add(code);
                        }
                        tagCur.close();
                    }

                    List<EntryDetail.Sense> senses = new ArrayList<>();
                    // SenseGroup is 1:1 with the source sense (schema-v2.md), so every sense
                    // surviving the filter below shares the same SenseAppliesToForm set - resolve
                    // it once from whichever sense_id is seen first.
                    List<Long> restrictedFormIds = null;
                    Cursor senseCur = readable.query(
                            "SELECT sense_id FROM core.Sense WHERE sense_group_id = ? AND "
                                    + "(NOT EXISTS (SELECT 1 FROM core.SenseAppliesToForm a WHERE a.sense_id = Sense.sense_id) "
                                    + "OR ? IS NULL "
                                    + "OR EXISTS (SELECT 1 FROM core.SenseAppliesToForm a WHERE a.sense_id = Sense.sense_id AND a.form_id = ?)) "
                                    + "ORDER BY ord",
                            new Object[]{groupId, formId, formId});
                    if (senseCur != null) {
                        while (senseCur.moveToNext()) {
                            long senseId = senseCur.getLong(0);
                            String glossText = glossLang != null ? fetchGlossText(readable, glossLang, senseId) : null;
                            if (glossText == null && languageSettings.backupLang != null
                                    && !languageSettings.backupLang.equals(glossLang)) {
                                glossText = fetchGlossText(readable, languageSettings.backupLang, senseId);
                            }
                            if (glossText == null) {
                                continue;
                            }
                            if (restrictedFormIds == null) {
                                restrictedFormIds = fetchRestrictedFormIds(readable, senseId);
                            }
                            EntryDetail.Sense sense = new EntryDetail.Sense();
                            sense.senseId = senseId;
                            sense.displayIndex = ++displayIndex[0];
                            sense.glossText = glossText;
                            sense.notes = fetchStrings(readable,
                                    "SELECT text FROM core.SenseNote WHERE sense_id = ? ORDER BY ord", senseId);
                            sense.xrefs = fetchXrefs(readable, senseId, "xref");
                            sense.antonyms = fetchXrefs(readable, senseId, "antonym");
                            sense.languageSources = fetchLanguageSources(readable, senseId);
                            senses.add(sense);
                        }
                        senseCur.close();
                    }

                    if (senses.isEmpty()) {
                        continue;
                    }
                    if (restrictedFormIds == null) {
                        restrictedFormIds = Collections.emptyList();
                    }

                    EntryDetail.SenseGroup lastGroup = detail.senseGroups.isEmpty() ? null
                            : detail.senseGroups.get(detail.senseGroups.size() - 1);
                    if (lastGroup != null && lastGroup.posTagCodes.equals(pos) && lastGroup.miscTagCodes.equals(misc)
                            && lastGroup.fieldTagCodes.equals(field) && lastGroup.dialectTagCodes.equals(dialect)
                            && lastGroup.restrictedFormIds.equals(restrictedFormIds)) {
                        lastGroup.senses.addAll(senses);
                    } else {
                        EntryDetail.SenseGroup newGroup = new EntryDetail.SenseGroup();
                        newGroup.posTagCodes = pos;
                        newGroup.miscTagCodes = misc;
                        newGroup.fieldTagCodes = field;
                        newGroup.dialectTagCodes = dialect;
                        newGroup.senses = senses;
                        newGroup.restrictedFormIds = restrictedFormIds;
                        newGroup.restrictionLabel = resolveRestrictionLabel(readable, restrictedFormIds);
                        detail.senseGroups.add(newGroup);
                    }
                }
                groupCur.close();
            }
        } catch (SQLException ignored) {
        }

        List<EntryDetail.Example> examples = fetchExamples(readable, languageSettings.lang, entryId);
        if (examples.isEmpty() && languageSettings.backupLang != null) {
            examples = fetchExamples(readable, languageSettings.backupLang, entryId);
        }
        // Route each example to the sense it's linked to; anything with no sense_id (most
        // examples today) or whose sense_id didn't survive the matched-form filter above falls
        // back to the entry-level list rendered at the bottom, rather than guessing a sense.
        Map<Long, EntryDetail.Sense> sensesById = new HashMap<>();
        for (EntryDetail.SenseGroup group : detail.senseGroups) {
            for (EntryDetail.Sense sense : group.senses) {
                sensesById.put(sense.senseId, sense);
            }
        }
        List<EntryDetail.Example> fallbackExamples = new ArrayList<>();
        for (EntryDetail.Example example : examples) {
            EntryDetail.Sense owner = example.senseId != null ? sensesById.get(example.senseId) : null;
            if (owner != null) {
                owner.examples.add(example);
            } else {
                fallbackExamples.add(example);
            }
        }
        detail.examples = fallbackExamples;

        // Show the table whenever there's more than one way to write OR more than one way to
        // read this entry - a single kanji spelling with several readings (e.g. 今日/きょう・
        // こんにち) has the exact same "which combo is common vs. rare" question as several
        // kanji spellings sharing one reading, just along the other axis.
        List<EntryDetail.FormRow> forms = fetchEntryForms(readable, entryId);
        LinkedHashSet<String> distinctWritingTexts = new LinkedHashSet<>();
        LinkedHashSet<String> distinctReadings = new LinkedHashSet<>();
        for (EntryDetail.FormRow form : forms) {
            if (form.isKanjiless) {
                distinctReadings.add(form.text);
            } else {
                distinctWritingTexts.add(form.text);
                distinctReadings.add(form.reading);
            }
        }
        if (distinctWritingTexts.size() > 1 || distinctReadings.size() > 1) {
            detail.forms = forms;
        }

        return detail;
    }

    public LiveData<PagedList<DictionarySearchElement>> getSearchElements(int key, PagedList.BoundaryCallback<DictionarySearchElement> boundaryCallback) {
        final PagedList.Config pagedListConfig =
                (new PagedList.Config.Builder()).setEnablePlaceholders(false)
                        .setPrefetchDistance(PAGE_SIZE)
                        .setPageSize(PREFETCH_DISTANCE).build();

        // No per-page summary warm-up needed here: every search tier precomputes each row's
        // render_json at insert time (see DictionarySearchQueryTool), so a loaded page already
        // carries everything bindTo() needs - no background executor, no cache, no race to win.
        final DataSource.Factory<Integer, DictionarySearchElement> sourceFactory =
                database.dictionarySearchElementDao().getAllDetailsLivePaged(key);

        return new LivePagedListBuilder<>(sourceFactory, pagedListConfig)
                .setBoundaryCallback(boundaryCallback).build();
    }
}
