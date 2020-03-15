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

package org.happypeng.sumatora.android.sumatoradictionary;

import android.app.Application;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.StrictMode;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.arch.core.util.Function;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.Transformations;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import org.happypeng.sumatora.android.sumatoradictionary.broadcastreceiver.DownloadEventReceiver;
import org.happypeng.sumatora.android.sumatoradictionary.db.AssetDictionaryObject;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryBookmark;
import org.happypeng.sumatora.android.sumatoradictionary.db.InstalledDictionary;
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistantLanguageSettings;
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentDatabase;
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentSetting;
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.BaseDictionaryObject;
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.Settings;
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.SumatoraSQLiteOpenHelperFactory;
import org.happypeng.sumatora.android.sumatoradictionary.xml.DictionaryBookmarkXML;
import org.happypeng.sumatora.jromkan.Romkan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

public class DictionaryApplication extends Application {
    public static final String DATABASE_NAME = "JMdict.db";
    static final String PERSISTENT_DATABASE_NAME = "PersistentDatabase.db";

    protected MutableLiveData<PersistentDatabase> m_persistentDatabase;
    private MutableLiveData<PersistantLanguageSettings> m_persistentLanguageSettings;

    private HashMap<String, String> m_entities;

    private Settings m_settings;

    private DownloadEventReceiver m_downloadEventReceiver;

    private Romkan m_romkan;

    public LiveData<PersistentDatabase> getPersistentDatabase() { return m_persistentDatabase; }
    public LiveData<PersistantLanguageSettings> getPersistentLanguageSettings() { return m_persistentLanguageSettings; }

    public Settings getSettings() { return m_settings; }

    public HashMap<String, String> getEntities() { return m_entities; }

    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            Logger log;

            if (BuildConfig.DEBUG_DB_MIGRATION) {
                log = LoggerFactory.getLogger(this.getClass());

                log.info("Starting database migration");
            }

            database.execSQL("CREATE TABLE IF NOT EXISTS DictionarySearchResult (`entryOrder` INTEGER NOT NULL, `seq` INTEGER NOT NULL, `readingsPrio` TEXT, `readings` TEXT, `writingsPrio` TEXT, `writings` TEXT, `pos` TEXT, `xref` TEXT, `ant` TEXT, `misc` TEXT, `lsource` TEXT, `dial` TEXT, `s_inf` TEXT, `field` TEXT, `lang` TEXT, `gloss` TEXT, PRIMARY KEY(`seq`))");
            database.execSQL("CREATE TABLE IF NOT EXISTS DictionaryBookmarkElement (`entryOrder` INTEGER NOT NULL, `seq` INTEGER NOT NULL, `readingsPrio` TEXT, `readings` TEXT, `writingsPrio` TEXT, `writings` TEXT, `pos` TEXT, `xref` TEXT, `ant` TEXT, `misc` TEXT, `lsource` TEXT, `dial` TEXT, `s_inf` TEXT, `field` TEXT, `lang` TEXT, `gloss` TEXT, `bookmark` INTEGER NOT NULL, PRIMARY KEY(`seq`))");
            database.execSQL("CREATE TABLE IF NOT EXISTS DictionaryBookmarkImport (`entryOrder` INTEGER NOT NULL, `seq` INTEGER NOT NULL, `readingsPrio` TEXT, `readings` TEXT, `writingsPrio` TEXT, `writings` TEXT, `pos` TEXT, `xref` TEXT, `ant` TEXT, `misc` TEXT, `lsource` TEXT, `dial` TEXT, `s_inf` TEXT, `field` TEXT, `lang` TEXT, `gloss` TEXT, `bookmark` INTEGER NOT NULL, PRIMARY KEY(`seq`))");
            database.execSQL("CREATE TABLE IF NOT EXISTS DictionaryBookmark (`seq` INTEGER NOT NULL, `bookmark` INTEGER NOT NULL, PRIMARY KEY(`seq`))");

            if (BuildConfig.DEBUG_DB_MIGRATION) {
                log.info("Database migration ended");
            }
        }
    };

    static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            Logger log;

            if (BuildConfig.DEBUG_DB_MIGRATION) {
                log = LoggerFactory.getLogger(this.getClass());

                log.info("Starting database migration");
            }

            database.execSQL("DROP TABLE IF EXISTS DictionaryBookmarkElement");

            database.execSQL("CREATE TABLE IF NOT EXISTS DictionaryDisplayElement (`ref` INTEGER NOT NULL, `entryOrder` INTEGER NOT NULL, `seq` INTEGER NOT NULL, `readingsPrio` TEXT, `readings` TEXT, `writingsPrio` TEXT, `writings` TEXT, `pos` TEXT, `xref` TEXT, `ant` TEXT, `misc` TEXT, `lsource` TEXT, `dial` TEXT, `s_inf` TEXT, `field` TEXT, `lang` TEXT, `gloss` TEXT, PRIMARY KEY(`ref`, `seq`))");
            database.execSQL("CREATE TABLE IF NOT EXISTS DictionaryElement (`ref` INTEGER NOT NULL, `entryOrder` INTEGER NOT NULL, `seq` INTEGER NOT NULL, PRIMARY KEY(`ref`, `seq`))");

            if (BuildConfig.DEBUG_DB_MIGRATION) {
                log.info("Database migration ended");
            }
        }
    };

    static final Migration MIGRATION_3_4 = new Migration(3, 4) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            Logger log;

            if (BuildConfig.DEBUG_DB_MIGRATION) {
                log = LoggerFactory.getLogger(this.getClass());

                log.info("Starting database migration");
            }

            database.execSQL("DROP TABLE IF EXISTS DictionarySearchResult");

            if (BuildConfig.DEBUG_DB_MIGRATION) {
                log.info("Database migration ended");
            }
        }
    };

    static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            Logger log;

            if (BuildConfig.DEBUG_DB_MIGRATION) {
                log = LoggerFactory.getLogger(this.getClass());

                log.info("Starting database migration");
            }

            database.execSQL("DROP TABLE IF EXISTS DictionaryBookmarkImport");
            database.execSQL("CREATE TABLE IF NOT EXISTS DictionaryBookmarkImport (`ref` INTEGER NOT NULL, `seq` INTEGER NOT NULL, PRIMARY KEY(`ref`, `seq`))");

            database.execSQL("CREATE TABLE IF NOT EXISTS InstalledDictionary (`description` TEXT, `type` TEXT NOT NULL, `lang` TEXT NOT NULL, `version` INTEGER NOT NULL, `date` INTEGER NOT NULL, `file` TEXT NOT NULL, PRIMARY KEY(`type`, `lang`))");
            database.execSQL("CREATE TABLE IF NOT EXISTS RemoteDictionaryObject (`description` TEXT, `type` TEXT NOT NULL, `lang` TEXT NOT NULL, `version` INTEGER NOT NULL, `date` INTEGER NOT NULL, `file` TEXT NOT NULL, `localFile` TEXT NOT NULL, `downloadId` INTEGER NOT NULL, PRIMARY KEY(`type`, `lang`))");
            database.execSQL("CREATE TABLE IF NOT EXISTS LocalDictionaryObject (`description` TEXT, `type` TEXT NOT NULL, `lang` TEXT NOT NULL, `version` INTEGER NOT NULL, `date` INTEGER NOT NULL, `file` TEXT NOT NULL, PRIMARY KEY(`type`, `lang`))");
            database.execSQL("CREATE TABLE IF NOT EXISTS AssetDictionaryObject (`description` TEXT, `type` TEXT NOT NULL, `lang` TEXT NOT NULL, `version` INTEGER NOT NULL, `date` INTEGER NOT NULL, `file` TEXT NOT NULL, PRIMARY KEY(`type`, `lang`))");


            if (BuildConfig.DEBUG_DB_MIGRATION) {
                log.info("Database migration ended");
            }
        }
    };

    static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            Logger log;

            if (BuildConfig.DEBUG_DB_MIGRATION) {
                log = LoggerFactory.getLogger(this.getClass());

                log.info("Starting database migration");
            }

            database.execSQL("DROP TABLE IF EXISTS DictionaryDisplayElement");

            database.execSQL("CREATE TABLE IF NOT EXISTS DictionaryDisplayElement (`ref` INTEGER NOT NULL, `entryOrder` INTEGER NOT NULL, `seq` INTEGER NOT NULL, `readingsPrio` TEXT, `readings` TEXT, `writingsPrio` TEXT, `writings` TEXT, `pos` TEXT, `xref` TEXT, `ant` TEXT, `misc` TEXT, `lsource` TEXT, `dial` TEXT, `s_inf` TEXT, `field` TEXT, `lang` TEXT, `lang_setting` TEXT, `gloss` TEXT, `example_sentences` TEXT, PRIMARY KEY(`ref`, `seq`))");
            database.execSQL("CREATE TABLE IF NOT EXISTS PersistentLanguageSettings (`ref` INTEGER NOT NULL, `lang` TEXT NOT NULL, `backupLang` TEXT, PRIMARY KEY(`ref`))");
        }
    };

    private static class InitializeDBTask extends AsyncTask<DictionaryApplication, Void, Void> {
        private Logger m_log;

        InitializeDBTask() {
            super();

            if (BuildConfig.DEBUG_DB_MIGRATION) {
                m_log = LoggerFactory.getLogger(this.getClass());
            }
        }

        private boolean hasExistingDatabase(DictionaryApplication aApp) {
            return aApp.getDatabasePath(DATABASE_NAME).exists();
        }

        private SQLiteDatabase openExistingDatabaseSQL(DictionaryApplication aApp) {
            try {
                return SQLiteDatabase.openDatabase(aApp.getDatabasePath(DATABASE_NAME).getAbsolutePath(),
                        null, SQLiteDatabase.OPEN_READWRITE);
            } catch(SQLException e) {
                if (BuildConfig.DEBUG_DB_MIGRATION) {
                    m_log.info("Error while opening current database");
                }

                return null;
            }
        }

        private List<DictionaryBookmark> extractBookmarks(SQLiteDatabase aDb, long aVersion) {
            if (aDb == null) {
                return null;
            }

            LinkedList<DictionaryBookmark> list = new LinkedList<>();

            Cursor cur = null;

            try {
                cur = aDb.rawQuery("SELECT * FROM DictionaryBookmark", null);
            } catch(SQLException e) {
                if (BuildConfig.DEBUG_DB_MIGRATION) {
                    m_log.info("Could not extract bookmarks from DictionaryBookmark in existing dictionary");
                }
            }

            if (cur == null) {
                try {
                    cur = aDb.rawQuery("SELECT seq FROM DictionaryEntry WHERE lang='eng' AND bookmark != ''", null);
                } catch(SQLException e) {
                    if (BuildConfig.DEBUG_DB_MIGRATION) {
                        m_log.info("Could not extract bookmarks from DictionaryEntry in existing dictionary");
                    }
                }
            }

            if (cur != null) {
                if (BuildConfig.DEBUG_DB_MIGRATION) {
                    m_log.info("Read " + cur.getCount() + " bookmarks from dictionary");
                }

                if (cur.getCount() != 0) {
                    while (cur.moveToNext()) {
                        list.add(new DictionaryBookmark(cur.getLong(0), 1));
                    }
                }

                cur.close();
            } else {
                if (BuildConfig.DEBUG_DB_MIGRATION) {
                    m_log.info("No bookmarks could be read from dictionary");
                }
            }

            return list;
        }

        private long checkLegacyDatabaseVersion(SQLiteDatabase aDb) {
            if (aDb == null) {
                return 0;
            }

            Cursor cur = null;

            try {
                cur = aDb.rawQuery("SELECT value FROM DictionaryControl WHERE control='version'", null);
            } catch (SQLException e) {
                if (BuildConfig.DEBUG_DB_MIGRATION) {
                    m_log.info("Could not select from DictionaryControl");
                }
            }

            long version = 0;

            if (cur == null || cur.getCount() == 0) {
                if (BuildConfig.DEBUG_DB_MIGRATION) {
                    m_log.info("No version information in current directory");
                }

                return 0;
            }

            while (cur.moveToNext()) {
                version = cur.getLong(0);
            };

            cur.close();

            return version;
        }

        @WorkerThread
        private static void updateDictionaries(final DictionaryApplication aApp,
                                               final PersistentDatabase aDB) {
            AssetManager assetManager = aApp.getAssets();

            aDB.remoteDictionaryObjectDao().deleteMany(aDB.remoteDictionaryObjectDao().getAll());

            try {
                InputStream in = assetManager.open("dictionaries.xml");

                List<AssetDictionaryObject> dl = InstalledDictionary.fromXML(in,
                        new BaseDictionaryObject.Constructor<AssetDictionaryObject>() {
                            @Override
                            public AssetDictionaryObject create(@NonNull String aFile, String aDescription, @NonNull String aType, @NonNull String aLang, int aVersion, int aDate) {
                                return new AssetDictionaryObject(aFile, aDescription, aType, aLang, aVersion, aDate);
                            }
                        });

                in.close();

                aDB.assetDictionaryObjectDao().deleteMany(aDB.assetDictionaryObjectDao().getAll());
                aDB.assetDictionaryObjectDao().insertMany(dl);

                int version = 0;
                int date = 0;

                if (dl.size() > 0) {
                    version = dl.get(0).version;
                    date = dl.get(0).date;
                }

                File databaseRoot = aApp.getDatabasePath(DATABASE_NAME).getParentFile();
                File databaseInstallDir = new File(databaseRoot, "dictionaries");


                if (!databaseInstallDir.exists()) {
                    databaseInstallDir.mkdirs();
                }

                InstalledDictionary jmdict = aDB.installedDictionaryDao().getForTypeLang("jmdict", "");

                if (jmdict == null || jmdict.version < version || jmdict.date < date) {
                    List<AssetDictionaryObject> asset_jmdict = aDB.assetDictionaryObjectDao().getAll();

                    for (AssetDictionaryObject d : asset_jmdict) {
                        d.install(assetManager, databaseInstallDir.getAbsolutePath(), aDB.installedDictionaryDao());
                    }
                }
            } catch(IOException e) {
                Log.e("tag", "IOException: ", e);
                Log.e("tag", "Could not update dictionaries.");
            }
        }

        @WorkerThread
        private List<DictionaryBookmark> readBackupBookmarks(@NonNull DictionaryApplication aApp) {
            File bookmarksBackup = new File(aApp.getFilesDir(), "bookmarks_backup.xml");
            List<DictionaryBookmark> resBookmarks = null;

            if (bookmarksBackup.exists()) {
                if (BuildConfig.DEBUG_DB_MIGRATION) {
                    m_log.info("Bookmarks backup file found, importing");
                }

                if (BuildConfig.DEBUG_DB_MIGRATION) {
                    try {
                        FileInputStream fis = new FileInputStream(bookmarksBackup);

                        List<Long> bSeqs = DictionaryBookmarkXML.readXML(fis);

                        if (bSeqs != null) {
                            resBookmarks = new LinkedList<>();

                            for (Long seq : bSeqs) {
                                DictionaryBookmark b = new DictionaryBookmark();
                                b.seq = seq;
                                resBookmarks.add(b);
                            }
                        } else {
                            if (BuildConfig.DEBUG_DB_MIGRATION) {
                                m_log.info("No bookmarks found in backup file");
                            }
                        }

                        fis.close();
                    } catch (IOException e) {
                        if (BuildConfig.DEBUG_DB_MIGRATION) {
                            m_log.info("Exception occured while trying to import bookmarks backup file");
                        }

                        e.printStackTrace();
                    }
                }
            }

            return resBookmarks;
        }

        @WorkerThread
        private void deleteBookmarksBackup(@NonNull final DictionaryApplication aApp) {
            File bookmarksBackup = new File(aApp.getFilesDir(), "bookmarks_backup.xml");

            bookmarksBackup.delete();
        }

        @WorkerThread
        private void saveBookmarksBackup(@NonNull final DictionaryApplication aApp,
                                         @NonNull final List<DictionaryBookmark> aBookmarks) {
            File bookmarksBackup = new File(aApp.getFilesDir(), "bookmarks_backup.xml");

            if (BuildConfig.DEBUG_DB_MIGRATION) {
                m_log.info("Saving bookmarks in file");
            }

            try {
                DictionaryBookmarkXML.writeXML(bookmarksBackup, aBookmarks);
            } catch (IOException e) {
                if (BuildConfig.DEBUG_DB_MIGRATION) {
                    m_log.info("Exception occured while saving bookmarks in file");
                }

                e.printStackTrace();
            }

        }

        protected Void doInBackground(DictionaryApplication... aParams) {
            if (aParams.length == 0) {
                return null;
            }

            if (BuildConfig.DEBUG_DB_MIGRATION) {
                m_log.info("Background task started");
            }

            int databaseReset = aParams[0].getResources().getInteger(R.integer.database_reset);

            // Remove older versions database
            File f = new File(aParams[0].getApplicationInfo().dataDir + "/JMdict.db");
            f.delete();

            long version = 0;

            SQLiteDatabase sqlDB = null;
            List<DictionaryBookmark> bookmarks = null;

            if (hasExistingDatabase(aParams[0])) {
                sqlDB = openExistingDatabaseSQL(aParams[0]);

                if (sqlDB != null) {

                    version = checkLegacyDatabaseVersion(sqlDB);
                    bookmarks = extractBookmarks(sqlDB, version);

                    if (bookmarks != null) {
                        saveBookmarksBackup(aParams[0], bookmarks);
                    }

                    sqlDB.close();
                    sqlDB = null;
                }
            }

            aParams[0].deleteDatabase(DictionaryApplication.DATABASE_NAME);

            if (BuildConfig.DEBUG_DB_MIGRATION) {
                m_log.info("Initializing database");
            }

            final PersistentDatabase pDb = Room.databaseBuilder(aParams[0],
                    PersistentDatabase.class, PERSISTENT_DATABASE_NAME)
                    .openHelperFactory(new SumatoraSQLiteOpenHelperFactory())
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4,
                            MIGRATION_4_5, MIGRATION_5_6)
                    .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                    .build();

            updateDictionaries(aParams[0], pDb);

            List<InstalledDictionary> dictionaries = pDb.installedDictionaryDao().getAll();

            for (InstalledDictionary d : dictionaries) {
                if (d.type.equals("jmdict")) {
                    d.attach(pDb);
                }
            }

            if (BuildConfig.DEBUG_DB_MIGRATION) {
                m_log.info("Setting default settings");
            }

            if (pDb.persistentLanguageSettingsDao().getLanguageSettingsDirect(0) == null) {
                pDb.persistentLanguageSettingsDao().update(new PersistantLanguageSettings(0,
                        PersistantLanguageSettings.LANG_DEFAULT, PersistantLanguageSettings.BACKUP_LANG_DEFAULT));
            }

            pDb.persistentSettingsDao().insertDefault(new PersistentSetting(Settings.REPOSITORY_URL,
                    aParams[0].getString(R.string.dictionaries_url)));

            if (bookmarks == null) {
                bookmarks = readBackupBookmarks(aParams[0]);
            }

            if (bookmarks != null) {
                if (BuildConfig.DEBUG_DB_MIGRATION) {
                    m_log.info("Inserting bookmarks from previous dictionary");
                }

                pDb.dictionaryBookmarkDao().insertMany(bookmarks);
            }

            deleteBookmarksBackup(aParams[0]);

            try {
                Cursor cur = pDb.getOpenHelper().getReadableDatabase().query("SELECT name, content FROM jmdict.DictionaryEntity");

                if (cur != null) {
                    if (cur.getCount() > 0) {
                        HashMap<String, String> entities = new HashMap<>();

                        while (cur.moveToNext()) {
                            String name = cur.getString(0);
                            String content = cur.getString(1);

                            entities.put(name, content);
                        }

                        aParams[0].m_entities = entities;
                    }

                    cur.close();
                } else {
                    if (BuildConfig.DEBUG_DB_MIGRATION) {
                        m_log.info("Cursor was null on select from DictionaryEntity");
                    }
                }
            } catch (SQLException e) {
                if (BuildConfig.DEBUG_DB_MIGRATION) {
                    m_log.info("Could not select from DictionaryEntity");
                }
            }

            // No persistence - clear display on initialization
            pDb.dictionaryElementDao().deleteAll();
            pDb.dictionaryDisplayElementDao().deleteAll();

            aParams[0].m_persistentDatabase.postValue(pDb);
            aParams[0].getSettings().postDatabase(pDb);

            if (BuildConfig.DEBUG_DB_MIGRATION) {
                m_log.info("Background task ends");
            }

            return null;
        }
    }

    public Romkan getRomkan() { return m_romkan; }

    @Override
    public void onCreate() {
        super.onCreate();

        m_romkan = new Romkan();

        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .build());
            StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder()
                    .detectAll()
                    .penaltyLog()
                    .penaltyDeath()
                    .build());
        }

        m_persistentDatabase = new MutableLiveData<>();
        m_persistentLanguageSettings = new MutableLiveData<>();

        // setupDownloadService();
        setupAttachListener();

        m_settings = new Settings();

        m_entities = null;

        new InitializeDBTask().execute(this);
    }

    public void setPersistentLanguageSettings(final @NonNull String aLang, final @NonNull String aBackupLang) {
        if (m_persistentDatabase.getValue() == null) {
            return;
        }

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                m_persistentDatabase.getValue().persistentLanguageSettingsDao().update(new PersistantLanguageSettings(0, aLang, aBackupLang));

                return null;
            }
        }.execute();
    }

    private class ApplicationLanguageSettings {
        PersistentDatabase db;
        PersistantLanguageSettings settings;
    }

    private void setupAttachListener() {
        final MediatorLiveData<ApplicationLanguageSettings> liveSettings = new MediatorLiveData<>();
        final ApplicationLanguageSettings settings = new ApplicationLanguageSettings();

        LiveData<PersistantLanguageSettings> persistantLanguageSettings =
                Transformations.switchMap(m_persistentDatabase,
                        new Function<PersistentDatabase, LiveData<PersistantLanguageSettings>>() {
                            @Override
                            public LiveData<PersistantLanguageSettings> apply(PersistentDatabase input) {
                                if (input == null) {
                                    return null;
                                }

                                return input.persistentLanguageSettingsDao().getLanguageSettings(0);
                            }
                        });

        liveSettings.addSource(m_persistentDatabase,
                new Observer<PersistentDatabase>() {
                    @Override
                    public void onChanged(PersistentDatabase persistentDatabase) {
                        settings.db = persistentDatabase;

                        liveSettings.setValue(settings);
                    }
                });

        liveSettings.addSource(persistantLanguageSettings,
                new Observer<PersistantLanguageSettings>() {
                    @Override
                    public void onChanged(PersistantLanguageSettings persistantLanguageSettings) {
                        settings.settings = persistantLanguageSettings;

                        liveSettings.setValue(settings);
                    }
                });

        liveSettings.observeForever(new Observer<ApplicationLanguageSettings>() {
            @Override
            public void onChanged(ApplicationLanguageSettings applicationLanguageSettings) {
                if (m_persistentLanguageSettings.getValue() != null) {
                    m_persistentLanguageSettings.setValue(null);
                }

                if (settings.db != null && settings.settings != null) {
                    new AsyncTask<Void, Void, Void>() {
                        @Override
                        protected Void doInBackground(Void... voids) {
                            List<InstalledDictionary> dictionaries = settings.db.installedDictionaryDao().getAll();

                            for (InstalledDictionary d : dictionaries) {
                                if (d.type.equals("jmdict_translation") || d.type.equals("tatoeba")) {
                                    if (d.lang.equals(settings.settings.lang) ||
                                            (settings.settings.backupLang != null && d.lang.equals(settings.settings.backupLang))) {
                                        d.attach(settings.db);
                                    } else {
                                        d.detach(settings.db);
                                    }
                                }
                            }

                            return null;
                        }

                        @Override
                        protected void onPostExecute(Void aVoid) {
                            super.onPostExecute(aVoid);

                            m_persistentLanguageSettings.setValue(settings.settings);
                        }
                    }.execute();
                }
            }
        });
    }

    // Registers a broadcast receiver for download completions
    // Starts a foreground service to keep the application alive during downloads
    /* private void setupDownloadService() {
        m_persistentDatabase.observeForever(new Observer<PersistentDatabase>() {
            @Override
            public void onChanged(PersistentDatabase persistentDatabase) {
                if (m_downloadEventReceiver != null) {
                    unregisterReceiver(m_downloadEventReceiver);

                    m_downloadEventReceiver = null;
                }

                m_downloadEventReceiver = new DownloadEventReceiver(DictionaryApplication.this,
                        persistentDatabase);

                registerReceiver(m_downloadEventReceiver,
                        new IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE));
            }
        });

        LiveData<List<LocalDictionaryObject>> localDictionaryObjectInstall =
                Transformations.switchMap(m_persistentDatabase,
                        new Function<PersistentDatabase, LiveData<List<LocalDictionaryObject>>>() {
                            @Override
                            public LiveData<List<LocalDictionaryObject>> apply(PersistentDatabase input) {
                                return input.localDictionaryObjectDao().getInstallObjects();
                            }
                        });
    } */

    /*
    @MainThread
    public void updateDownloadService() {
        new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... voids) {
                PersistentDatabase db = m_persistentDatabase.getValue();

                if (db == null) {
                    return false;
                }

                if (db.remoteDictionaryObjectDao().getDownloadCount() > 0) {
                    return true;
                }

                return false;
            }

            @Override
            protected void onPostExecute(Boolean aBoolean) {
                super.onPostExecute(aBoolean);

                if (aBoolean) {
                    Intent serviceIntent = new Intent(DictionaryApplication.this,
                            DictionaryDownloadService.class);
                    ContextCompat.startForegroundService(DictionaryApplication.this,
                            serviceIntent);
                } else {
                    Intent serviceIntent = new Intent(DictionaryApplication.this,
                            DictionaryDownloadService.class);
                    stopService(serviceIntent);
                }
            }
        }.execute();
    } */

    /*
    @WorkerThread
    public void postDetachDatabase() {
        if (m_persistentDatabase != null) {
            m_persistentDatabase.postValue(null);
        }
    }
    */
}
