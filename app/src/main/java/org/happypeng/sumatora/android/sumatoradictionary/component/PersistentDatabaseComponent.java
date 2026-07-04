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
import org.happypeng.sumatora.android.sumatoradictionary.db.InstalledDictionary;
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentDatabase;
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentDatabaseInitialization;
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentLanguageSettings;
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.SumatoraSQLiteOpenHelperFactory;
import org.happypeng.sumatora.jromkan.Romkan;

import java.util.HashMap;
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
import static org.happypeng.sumatora.android.sumatoradictionary.db.PersistentDatabaseParameters.PERSISTENT_DATABASE_NAME;

@Singleton
public class PersistentDatabaseComponent {
    private static final int PAGE_SIZE = 30;
    private static final int PREFETCH_DISTANCE = 50;

    private final PersistentDatabase database;
    private final Context context;
    private boolean databaseInitialized;
    private final HashMap<String, String> entities;
    private final DictionaryControlInfo dictionaryControlInfo;
    private final Romkan romkan;

    @Inject
    PersistentDatabaseComponent(@ApplicationContext final Context context) {
        this.context = context;
        this.databaseInitialized = false;
        this.entities = new HashMap<>();
        this.dictionaryControlInfo = new DictionaryControlInfo();

        database = Room.databaseBuilder(context,
                PersistentDatabase.class, PERSISTENT_DATABASE_NAME)
                .openHelperFactory(new SumatoraSQLiteOpenHelperFactory())
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4,
                        MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10,
                        MIGRATION_10_11)
                .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                .build();

        this.romkan = new Romkan();
    }

    @WorkerThread
    public HashMap<String, String> getEntities() {
        if (!databaseInitialized) {
            initialize();
        }

        return entities;
    }

    public Romkan getRomkan() {
        return romkan;
    }

    @WorkerThread
    public synchronized void initialize() {
        if (databaseInitialized) {
            return;
        }

        PersistentDatabaseInitialization.initializeDatabase(context, database, entities, dictionaryControlInfo);

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


    // Fetches a single DictionaryEntry by seq (e.g. to open a cross-referenced entry), joined
    // against the current UI language's glosses. Bookmark/example fields are left at their
    // defaults since this is a one-off lookup outside the normal search pipeline.
    @WorkerThread
    @Nullable
    public DictionarySearchElement fetchEntryBySeq(long seq) {
        final PersistentDatabase db = getDatabase();
        final PersistentLanguageSettings settings = db.persistentLanguageSettingsDao().getLanguageSettingsDirect(0);
        final String lang = settings != null ? settings.lang : PersistentLanguageSettings.LANG_DEFAULT;

        final SupportSQLiteDatabase readable = db.getOpenHelper().getReadableDatabase();
        final String sql = "SELECT DictionaryEntry.seq, DictionaryEntry.readingsPrio, DictionaryEntry.readings, "
                + "DictionaryEntry.writingsPrio, DictionaryEntry.writings, DictionaryEntry.pos, "
                + "DictionaryEntry.xref, DictionaryEntry.ant, DictionaryEntry.misc, DictionaryEntry.lsource, "
                + "DictionaryEntry.dial, DictionaryEntry.s_inf, DictionaryEntry.field, DictionaryEntry.furigana, "
                + "DictionaryEntry.score, DictionaryEntry.stagk, DictionaryEntry.stagr, "
                + "json_group_array(DictionaryTranslation.gloss) AS gloss "
                + "FROM jmdict.DictionaryEntry, " + lang + ".DictionaryTranslation "
                + "WHERE DictionaryEntry.seq = ? AND DictionaryEntry.seq = DictionaryTranslation.seq "
                + "GROUP BY DictionaryEntry.seq";

        try {
            Cursor cur = readable.query(sql, new Object[]{seq});

            if (cur == null) {
                return null;
            }

            DictionarySearchElement result = null;
            if (cur.moveToNext()) {
                result = new DictionarySearchElement();
                result.seq = cur.getLong(0);
                result.readingsPrio = cur.getString(1);
                result.readings = cur.getString(2);
                result.writingsPrio = cur.getString(3);
                result.writings = cur.getString(4);
                result.pos = cur.getString(5);
                result.xref = cur.getString(6);
                result.ant = cur.getString(7);
                result.misc = cur.getString(8);
                result.lsource = cur.getString(9);
                result.dial = cur.getString(10);
                result.s_inf = cur.getString(11);
                result.field = cur.getString(12);
                result.furigana = cur.getString(13);
                result.score = cur.getInt(14);
                result.stagk = cur.getString(15);
                result.stagr = cur.getString(16);
                result.gloss = cur.getString(17);
                result.lang = lang;
                result.lang_setting = lang;
            }

            cur.close();

            return result;
        } catch (SQLException e) {
            return null;
        }
    }

    @WorkerThread
    @Nullable
    public DictionaryKanjiInfo fetchKanjiInfo(String character) {
        final PersistentDatabase db = getDatabase();
        final SupportSQLiteDatabase readable = db.getOpenHelper().getReadableDatabase();

        try {
            Cursor cur = readable.query(
                    "SELECT \"on\", kun, meanings, strokes, grade, jlpt, freq, radical "
                            + "FROM kanjidic2.KanjiEntry WHERE char = ?",
                    new Object[]{character});

            if (cur == null) {
                return null;
            }

            DictionaryKanjiInfo result = null;
            if (cur.moveToNext()) {
                result = new DictionaryKanjiInfo();
                result.character = character;
                result.on = cur.getString(0);
                result.kun = cur.getString(1);
                result.meanings = cur.getString(2);
                result.strokes = cur.isNull(3) ? null : cur.getInt(3);
                result.grade = cur.isNull(4) ? null : cur.getInt(4);
                result.jlpt = cur.isNull(5) ? null : cur.getInt(5);
                result.freq = cur.isNull(6) ? null : cur.getInt(6);
                result.radical = cur.isNull(7) ? null : cur.getInt(7);
            }

            cur.close();

            return result;
        } catch (SQLException e) {
            return null;
        }
    }

    // Looks up pitch accent patterns for a displayed word: tries word+reading first, falling
    // back to reading-only (per Gap 10's documented lookup order).
    @WorkerThread
    @Nullable
    public String fetchPitchAccent(String word, String reading) {
        final PersistentDatabase db = getDatabase();
        final SupportSQLiteDatabase readable = db.getOpenHelper().getReadableDatabase();

        try {
            if (word != null) {
                Cursor cur = readable.query(
                        "SELECT pitches FROM pitch.PitchAccent WHERE word = ? AND reading = ?",
                        new Object[]{word, reading});
                if (cur != null) {
                    String pitches = cur.moveToNext() ? cur.getString(0) : null;
                    cur.close();
                    if (pitches != null) {
                        return pitches;
                    }
                }
            }

            Cursor cur = readable.query(
                    "SELECT pitches FROM pitch.PitchAccent WHERE reading = ?",
                    new Object[]{reading});
            if (cur == null) {
                return null;
            }
            String pitches = cur.moveToNext() ? cur.getString(0) : null;
            cur.close();

            return pitches;
        } catch (SQLException e) {
            return null;
        }
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
