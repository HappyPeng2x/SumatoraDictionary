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
import androidx.paging.LivePagedListBuilder;
import androidx.paging.PagedList;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;

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
import org.happypeng.sumatora.core.dict.DictionaryQueryResult;
import org.happypeng.sumatora.jromkan.Romkan;

import java.util.ArrayList;
import java.util.List;

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
import static org.happypeng.sumatora.android.sumatoradictionary.db.PersistentDatabaseParameters.MIGRATION_12_13;
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
                        MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10,
                        MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13)
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


    private static final String FORM_QUERY =
            "SELECT text, reading, form_type, form_id FROM %s.EntryForm WHERE entry_id = ? AND is_primary = 1 LIMIT 1";

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

    // Fetches a first gloss + rank-0 sense's tags for entryId in the given gloss_{lang} pack.
    // Returns false (no mutation) if that pack isn't attached or has nothing for this entry.
    @WorkerThread
    private boolean fetchGlossPreview(SupportSQLiteDatabase readable, String lang, long entryId, EntryListSummary summary) {
        try {
            Cursor cur = readable.query(
                    "SELECT SenseGloss.text FROM gloss_" + lang + ".Sense "
                            + "JOIN gloss_" + lang + ".SenseGloss ON SenseGloss.sense_id = Sense.sense_id "
                            + "WHERE Sense.entry_id = ? ORDER BY Sense.ord, SenseGloss.ord LIMIT 1",
                    new Object[]{entryId});
            if (cur != null) {
                if (cur.moveToNext()) {
                    summary.glossPreview = cur.getString(0);
                }
                cur.close();
            }
        } catch (SQLException e) {
            return false;
        }
        return summary.glossPreview != null;
    }

    // Assembles the lean per-row summary a search-result card needs: primary headword + furigana,
    // first sense-group's tags, and a one-line gloss preview (falling back to the backup language
    // if the primary language has nothing for this entry). Proper names use their own pack/tables.
    @WorkerThread
    public EntryListSummary fetchListSummary(DictionaryQueryResult entry, PersistentLanguageSettings languageSettings) {
        final EntryListSummary summary = new EntryListSummary();
        final SupportSQLiteDatabase readable = getDatabase().getOpenHelper().getReadableDatabase();
        final boolean isName = "name".equals(entry.getMatchKind());
        summary.isName = isName;
        final String pack = isName ? "names" : "core";

        long primaryFormId = -1;
        try {
            Cursor cur = readable.query(String.format(FORM_QUERY, pack), new Object[]{entry.getEntryId()});
            if (cur != null) {
                if (cur.moveToNext()) {
                    summary.primaryText = cur.getString(0);
                    summary.primaryReading = "writing".equals(cur.getString(2)) ? cur.getString(1) : null;
                    primaryFormId = cur.getLong(3);
                }
                cur.close();
            }
        } catch (SQLException ignored) {
        }

        if (primaryFormId >= 0) {
            summary.furiganaSegments = fetchFurigana(readable, pack, primaryFormId);
        }

        if (isName) {
            try {
                Cursor cur = readable.query(
                        "SELECT Tag.code FROM names.EntryTag "
                                + "JOIN names.Tag ON Tag.tag_id = EntryTag.tag_id "
                                + "WHERE EntryTag.entry_id = ? AND Tag.category = 'name_type' ORDER BY Tag.sort_order",
                        new Object[]{entry.getEntryId()});
                if (cur != null) {
                    while (cur.moveToNext()) summary.nameTypeCodes.add(cur.getString(0));
                    cur.close();
                }
            } catch (SQLException ignored) {
            }
            try {
                Cursor cur = readable.query(
                        "SELECT text FROM names.NameTranslation WHERE entry_id = ? ORDER BY ord",
                        new Object[]{entry.getEntryId()});
                if (cur != null) {
                    while (cur.moveToNext()) summary.translations.add(cur.getString(0));
                    cur.close();
                }
            } catch (SQLException ignored) {
            }
            return summary;
        }

        try {
            Cursor cur = readable.query(
                    "SELECT Tag.code FROM core.SenseGroup "
                            + "JOIN core.SenseGroupTag ON SenseGroupTag.sense_group_id = SenseGroup.sense_group_id "
                            + "JOIN core.Tag ON Tag.tag_id = SenseGroupTag.tag_id "
                            + "WHERE SenseGroup.entry_id = ? "
                            + "AND SenseGroup.ord = (SELECT MIN(ord) FROM core.SenseGroup WHERE entry_id = ?) "
                            + "ORDER BY Tag.sort_order",
                    new Object[]{entry.getEntryId(), entry.getEntryId()});
            if (cur != null) {
                while (cur.moveToNext()) summary.tagCodes.add(cur.getString(0));
                cur.close();
            }
        } catch (SQLException ignored) {
        }

        if (!fetchGlossPreview(readable, languageSettings.lang, entry.getEntryId(), summary)
                && languageSettings.backupLang != null) {
            summary.usedBackupLang = fetchGlossPreview(readable, languageSettings.backupLang, entry.getEntryId(), summary);
        }

        return summary;
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
                    "SELECT EntryExample.example_id, EntryExample.matched_text, Example.translation "
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
                    example.segments = fetchFurigana(readable, "examples_" + lang, "ExampleSegment", "example_id", exampleId);
                    result.add(example);
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

        long primaryFormId = -1;
        try {
            Cursor cur = readable.query(
                    "SELECT text, reading, form_type, form_id, is_common FROM " + pack + ".EntryForm "
                            + "WHERE entry_id = ? AND is_primary = 1 LIMIT 1",
                    new Object[]{entryId});
            if (cur != null) {
                if (cur.moveToNext()) {
                    detail.primaryText = cur.getString(0);
                    detail.primaryReading = "writing".equals(cur.getString(2)) ? cur.getString(1) : null;
                    primaryFormId = cur.getLong(3);
                    detail.isPriority = cur.getInt(4) != 0;
                }
                cur.close();
            }
        } catch (SQLException ignored) {
        }

        if (primaryFormId >= 0) {
            detail.furiganaSegments = fetchFurigana(readable, pack, primaryFormId);
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

        long pitchFormId = formId != null ? formId : primaryFormId;
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
                            EntryDetail.Sense sense = new EntryDetail.Sense();
                            sense.displayIndex = ++displayIndex[0];
                            sense.glossText = glossLang != null ? fetchGlossText(readable, glossLang, senseId) : null;
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

                    EntryDetail.SenseGroup lastGroup = detail.senseGroups.isEmpty() ? null
                            : detail.senseGroups.get(detail.senseGroups.size() - 1);
                    if (lastGroup != null && lastGroup.posTagCodes.equals(pos) && lastGroup.miscTagCodes.equals(misc)
                            && lastGroup.fieldTagCodes.equals(field) && lastGroup.dialectTagCodes.equals(dialect)) {
                        lastGroup.senses.addAll(senses);
                    } else {
                        EntryDetail.SenseGroup newGroup = new EntryDetail.SenseGroup();
                        newGroup.posTagCodes = pos;
                        newGroup.miscTagCodes = misc;
                        newGroup.fieldTagCodes = field;
                        newGroup.dialectTagCodes = dialect;
                        newGroup.senses = senses;
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
        detail.examples = examples;

        return detail;
    }

    public LiveData<PagedList<DictionarySearchElement>> getSearchElements(int key, PagedList.BoundaryCallback<DictionarySearchElement> boundaryCallback) {
        final PagedList.Config pagedListConfig =
                (new PagedList.Config.Builder()).setEnablePlaceholders(false)
                        .setPrefetchDistance(PAGE_SIZE)
                        .setPageSize(PREFETCH_DISTANCE).build();

        return new LivePagedListBuilder<>(database.dictionarySearchElementDao().getAllDetailsLivePaged(key), pagedListConfig)
                .setBoundaryCallback(boundaryCallback).build();
    }
}
