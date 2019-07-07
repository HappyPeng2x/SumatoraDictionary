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
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.BookmarkTool;
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.Languages;
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.Settings;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
import androidx.sqlite.db.SupportSQLiteDatabase;

public class DictionaryApplication extends Application {
    static public final String SQL_QUERY_DELETE_RESULTS =
            "DELETE FROM DictionarySearchResult";

    static final String DATABASE_NAME = "JMdict.db";
    static final String PERSISTENT_DATABASE_NAME = "PersistentDatabase.db";

    protected MutableLiveData<PersistentDatabase> m_persistentDatabase;
    protected MutableLiveData<List<DictionaryLanguage>> m_dictionaryLanguage;
    protected MutableLiveData<BookmarkTool> m_bookmarkTool;
    protected MutableLiveData<BookmarkImportTool> m_bookmarkImportTool;

    private Settings m_settings;

    public LiveData<PersistentDatabase> getPersistentDatabase() { return m_persistentDatabase; }
    public LiveData<List<DictionaryLanguage>> getDictionaryLanguage() { return m_dictionaryLanguage; }
    public LiveData<BookmarkTool> getBookmarkTool() { return m_bookmarkTool; }
    public LiveData<BookmarkImportTool> getBookmarkImportTool() { return m_bookmarkImportTool; }

    public Settings getSettings() { return m_settings; }

    private static class InitializeDBTask extends AsyncTask<DictionaryApplication, Void, Void> {
        private boolean hasExistingDatabase(DictionaryApplication aApp) {
            return aApp.getDatabasePath(DATABASE_NAME).exists();
        }

        private SQLiteDatabase openExistingDatabaseSQL(DictionaryApplication aApp) {
            try {
                return SQLiteDatabase.openDatabase(aApp.getDatabasePath(DATABASE_NAME).getAbsolutePath(),
                        null, SQLiteDatabase.OPEN_READWRITE);
            } catch(SQLException e) {
                if (BuildConfig.DEBUG_MESSAGE) {
                    Log.d("MIGRATE_DB", "DB corrupted, recreating it");
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
                if (BuildConfig.DEBUG_MESSAGE) {
                    Log.d("MIGRATE_DB","No table DictionaryBookmark");
                }
            }

            if (cur == null) {
                try {
                    cur = aDb.rawQuery("SELECT seq FROM DictionaryEntry WHERE lang='eng' AND bookmark != ''", null);
                } catch(SQLException e) {
                    if (BuildConfig.DEBUG_MESSAGE) {
                        Log.d("MIGRATE_DB","No table DictionaryEntry");
                    }
                }
            }

            if (cur != null) {
                if (BuildConfig.DEBUG_MESSAGE) {
                    Log.d("MIGRATE_DB", "Importing bookmarks from old database");
                }

                if (cur.getCount() != 0) {
                    while (cur.moveToNext()) {
                        if (BuildConfig.DEBUG_MESSAGE) {
                            Log.d("MIGRATE_DB", "Bookmark: " + cur.getLong(0));
                        }

                        list.add(new DictionaryBookmark(cur.getLong(0), 1));
                    }
                }

                cur.close();
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
                // We got an exception
            }

            long version = 0;

            if (cur == null || cur.getCount() == 0) {
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

        protected Void doInBackground(DictionaryApplication... aParams) {
            if (aParams.length == 0) {
                return null;
            }

            int currentDate = aParams[0].getResources().getInteger(R.integer.database_date);
            int currentVersion = aParams[0].getResources().getInteger(R.integer.database_version);
            int databaseReset = aParams[0].getResources().getInteger(R.integer.database_reset);

            // Remove older versions database
            File f = new File(aParams[0].getApplicationInfo().dataDir + "/JMdict.db");
            f.delete();

            if (BuildConfig.DEBUG_MESSAGE) {
                Log.d("MIGRATE_DB", "Starting database check");
            }

            long version = 0;
            long date = 0;

            SQLiteDatabase sqlDB = null;

            if (hasExistingDatabase(aParams[0])) {
                sqlDB = openExistingDatabaseSQL(aParams[0]);

                if (sqlDB != null) {
                    version = checkDatabaseVersion(sqlDB);
                    date = checkDatabaseDate(sqlDB);
                }
            }

            List<DictionaryBookmark> bookmarks = null;

            if (version < currentVersion || date < currentDate || databaseReset != 0) {
                if (BuildConfig.DEBUG_MESSAGE) {
                    Log.d("MIGRATE_DB", "Recreating database");
                }

                if (sqlDB != null) {
                    bookmarks = extractBookmarks(sqlDB, version);

                    sqlDB.close();
                    sqlDB = null;
                }

                aParams[0].deleteDatabase(DictionaryApplication.DATABASE_NAME);
            } else {
                if (BuildConfig.DEBUG_MESSAGE) {
                    Log.d("MIGRATE_DB", "Using database as it is");
                }
            }

            if (sqlDB != null) {
                sqlDB.close();
            }

            final File jmdictDbFile = aParams[0].getDatabasePath(DATABASE_NAME);
            copyAsset(aParams[0], "databases/JMdict.db", jmdictDbFile);

            if (BuildConfig.DEBUG_MESSAGE) {
                Log.d("MIGRATE_DB", "Database check ended");
            }

            final PersistentDatabase pDb = Room.databaseBuilder(aParams[0],
                    PersistentDatabase.class, PERSISTENT_DATABASE_NAME)
                    .setJournalMode(RoomDatabase.JournalMode.TRUNCATE)
                    .addCallback(new RoomDatabase.Callback() {
                        @Override
                        public void onOpen(@NonNull SupportSQLiteDatabase db) {
                            db.execSQL("ATTACH '" + jmdictDbFile.toString() + "' AS jmdict");
                        }
                    })
                    .build();

            pDb.persistentSettingsDao().insertDefault(new PersistentSetting(Settings.LANG,
                    Settings.LANG_DEFAULT));
            pDb.persistentSettingsDao().insertDefault(new PersistentSetting(Settings.BACKUP_LANG,
                    Settings.BACKUP_LANG_DEFAULT));

            pDb.runInTransaction(new Runnable() {
                @Override
                public void run() {
                    pDb.getOpenHelper().getWritableDatabase().execSQL(SQL_QUERY_DELETE_RESULTS);
                }
            });

            if (bookmarks != null) {
                pDb.dictionaryBookmarkDao().insertMany(bookmarks);
            }

            aParams[0].m_persistentDatabase.postValue(pDb);
            aParams[0].getSettings().postDatabase(pDb);

            final BookmarkTool bookmarkTool = new BookmarkTool(pDb);
            bookmarkTool.createStatements();

            aParams[0].m_bookmarkTool.postValue(bookmarkTool);

            final BookmarkImportTool bookmarkImportTool = new BookmarkImportTool(pDb, aParams[0]);
            bookmarkImportTool.createStatements();

            aParams[0].m_bookmarkImportTool.postValue(bookmarkImportTool);

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
        m_bookmarkTool = new MutableLiveData<>();
        m_bookmarkImportTool = new MutableLiveData<>();

        m_settings = new Settings();

        m_dictionaryLanguage.setValue(Languages.getLanguages());

        new InitializeDBTask().execute(this);
    }
}
