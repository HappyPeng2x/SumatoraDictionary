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
import android.os.Build;
import android.os.StrictMode;
import android.util.Log;

import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryBookmark;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryLanguage;
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentDatabase;
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentSetting;
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.BookmarkImportTool;
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.Languages;
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.Settings;

import org.happypeng.sumatora.android.sumatoradictionary.xml.DictionaryBookmarkXML;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.room.InvalidationTracker;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import io.requery.android.database.sqlite.RequerySQLiteOpenHelperFactory;

public class DictionaryApplication extends Application {
    static final String DATABASE_NAME = "JMdict.db";
    static final String PERSISTENT_DATABASE_NAME = "PersistentDatabase.db";

    protected MutableLiveData<PersistentDatabase> m_persistentDatabase;
    protected MutableLiveData<List<DictionaryLanguage>> m_dictionaryLanguage;
    protected MutableLiveData<BookmarkImportTool> m_bookmarkImportTool;

    private HashMap<String, String> m_entities;

    private Settings m_settings;

    public LiveData<PersistentDatabase> getPersistentDatabase() { return m_persistentDatabase; }
    public LiveData<List<DictionaryLanguage>> getDictionaryLanguage() { return m_dictionaryLanguage; }
    public LiveData<BookmarkImportTool> getBookmarkImportTool() { return m_bookmarkImportTool; }

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

        private long checkDatabaseVersion(SQLiteDatabase aDb) {
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

        private long checkDatabaseDate(SQLiteDatabase aDb) {
            if (aDb == null) {
                return 0;
            }

            Cursor cur = null;

            try {
                cur = aDb.rawQuery("SELECT value FROM DictionaryControl WHERE control='date'", null);
            } catch (SQLException e) {
                // We got an exception
            }

            long date = 0;

            if (cur == null || cur.getCount() == 0) {
                return 0;
            }

            while (cur.moveToNext()) {
                date = cur.getLong(0);
            };

            cur.close();

            return date;
        }

        private void copyAsset(final DictionaryApplication aApp, String aName,
                               File aOutput) {
            AssetManager assetManager = aApp.getAssets();

            try {
                File parentDir = aOutput.getParentFile();

                if (aOutput.isDirectory() && !aOutput.delete()) {
                    System.err.println("Is directory and cannot delete " + aOutput.getAbsolutePath());
                }

                if (parentDir == null || !parentDir.mkdirs()) {
                    System.err.println("Could not create directories for " + aOutput.getAbsolutePath());
                }

                InputStream in = assetManager.open(aName);
                OutputStream out = new FileOutputStream(aOutput);
                copyFile(in, out);
                in.close();
                in = null;
                out.flush();
                out.close();
                out = null;
            } catch(IOException e) {
                Log.e("tag", "Failed to copy asset file: " + aName, e);
            }
        }

        private void copyFile(InputStream in, OutputStream out) throws IOException {
            byte[] buffer = new byte[1024];
            int read;
            while((read = in.read(buffer)) != -1){
                out.write(buffer, 0, read);
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

            int currentDate = aParams[0].getResources().getInteger(R.integer.database_date);
            int currentVersion = aParams[0].getResources().getInteger(R.integer.database_version);
            int databaseReset = aParams[0].getResources().getInteger(R.integer.database_reset);

            if (BuildConfig.DEBUG_DB_MIGRATION) {
                m_log.info("Current dictionary version is " + currentVersion + ", current date is " + currentDate);
                m_log.info("Requested dictionary reset status is " + databaseReset);
            }

            // Remove older versions database
            File f = new File(aParams[0].getApplicationInfo().dataDir + "/JMdict.db");
            f.delete();

            long version = 0;
            long date = 0;

            SQLiteDatabase sqlDB = null;

            if (hasExistingDatabase(aParams[0])) {
                sqlDB = openExistingDatabaseSQL(aParams[0]);

                if (BuildConfig.DEBUG_DB_MIGRATION) {
                    m_log.info("Have an existing dictionary, open status is " + (sqlDB != null));
                }

                if (sqlDB != null) {
                    version = checkDatabaseVersion(sqlDB);
                    date = checkDatabaseDate(sqlDB);
                }

                if (BuildConfig.DEBUG_DB_MIGRATION) {
                    m_log.info("Dictionary version is " + version + ", date is " + date);
                }
            } else {
                if (BuildConfig.DEBUG_DB_MIGRATION) {
                    m_log.info("Do not have any existing dictionary");
                }
            }

            List<DictionaryBookmark> bookmarks = null;

            final File jmdictDbFile = aParams[0].getDatabasePath(DATABASE_NAME);

            if (version < currentVersion || date < currentDate || databaseReset != 0) {
                if (BuildConfig.DEBUG_DB_MIGRATION) {
                    m_log.info("Dictionary will be re-imported from assets");
                    m_log.info("Extracting bookmarks from existing dictionary");
                }

                if (sqlDB != null) {
                    bookmarks = extractBookmarks(sqlDB, version);

                    if (bookmarks != null) {
                        saveBookmarksBackup(aParams[0], bookmarks);
                    }

                    sqlDB.close();
                    sqlDB = null;
                }

                if (BuildConfig.DEBUG_DB_MIGRATION) {
                    m_log.info("Deleting previous dictionary");
                }

                aParams[0].deleteDatabase(DictionaryApplication.DATABASE_NAME);

                if (BuildConfig.DEBUG_DB_MIGRATION) {
                    m_log.info("Copying dictionary from assets");
                }

                copyAsset(aParams[0], "databases/JMdict.db", jmdictDbFile);
            } else {
                if (BuildConfig.DEBUG_DB_MIGRATION) {
                    m_log.info("Dictionary will be used as it is");
                }

                if (sqlDB != null) {
                    sqlDB.close();
                }
            }

            if (bookmarks == null) {
                bookmarks = readBackupBookmarks(aParams[0]);
            }

            if (BuildConfig.DEBUG_DB_MIGRATION) {
                m_log.info("Initializing database");
            }

            final PersistentDatabase pDb = Room.databaseBuilder(aParams[0],
                    PersistentDatabase.class, PERSISTENT_DATABASE_NAME)
                    .openHelperFactory(new RequerySQLiteOpenHelperFactory())
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                    .addCallback(new RoomDatabase.Callback() {
                        @Override
                        public void onOpen(@NonNull SupportSQLiteDatabase db) {
                            db.execSQL("ATTACH '" + jmdictDbFile.toString() + "' AS jmdict");
                        }
                    })
                    .build();

            if (BuildConfig.DEBUG_DB_MIGRATION) {
                m_log.info("Setting default settings");
            }

            pDb.persistentSettingsDao().insertDefault(new PersistentSetting(Settings.LANG,
                    Settings.LANG_DEFAULT));
            pDb.persistentSettingsDao().insertDefault(new PersistentSetting(Settings.BACKUP_LANG,
                    Settings.BACKUP_LANG_DEFAULT));

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

            aParams[0].m_persistentDatabase.postValue(pDb);
            aParams[0].getSettings().postDatabase(pDb);

            final BookmarkImportTool bookmarkImportTool = new BookmarkImportTool(pDb, aParams[0]);
            bookmarkImportTool.createStatements();

            aParams[0].m_bookmarkImportTool.postValue(bookmarkImportTool);

            if (BuildConfig.DEBUG_DB_MIGRATION) {
                m_log.info("Background task ends");
            }

            return null;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

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

        m_dictionaryLanguage = new MutableLiveData<>();
        m_persistentDatabase = new MutableLiveData<>();
        m_bookmarkImportTool = new MutableLiveData<>();

        m_settings = new Settings();

        m_dictionaryLanguage.setValue(Languages.getLanguages());

        m_entities = null;

        new InitializeDBTask().execute(this);
    }
}
