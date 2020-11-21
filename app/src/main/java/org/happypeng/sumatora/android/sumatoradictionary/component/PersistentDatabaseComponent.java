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

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;
import androidx.paging.LivePagedListBuilder;
import androidx.paging.PagedList;
import androidx.room.Room;
import androidx.room.RoomDatabase;

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
import static org.happypeng.sumatora.android.sumatoradictionary.db.PersistentDatabaseParameters.PERSISTENT_DATABASE_NAME;

@Singleton
public class PersistentDatabaseComponent {
    private static final int PAGE_SIZE = 30;
    private static final int PREFETCH_DISTANCE = 50;

    private final PersistentDatabase database;
    private final Context context;
    private boolean databaseInitialized;
    private final HashMap<String, String> entities;
    private final Romkan romkan;

    @Inject
    PersistentDatabaseComponent(@ApplicationContext final Context context) {
        this.context = context;
        this.databaseInitialized = false;
        this.entities = new HashMap<>();

        database = Room.databaseBuilder(context,
                PersistentDatabase.class, PERSISTENT_DATABASE_NAME)
                .openHelperFactory(new SumatoraSQLiteOpenHelperFactory())
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4,
                        MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8,
                        MIGRATION_8_9)
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

        PersistentDatabaseInitialization.initializeDatabase(context, database, entities);

        databaseInitialized = true;
    }

    @WorkerThread
    public PersistentDatabase getDatabase() {
        if (!databaseInitialized) {
            initialize();
        }

        return database;
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
