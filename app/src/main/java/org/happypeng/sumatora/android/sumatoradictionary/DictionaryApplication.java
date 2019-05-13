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

import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;
import android.os.StrictMode;
import android.util.Log;

import com.fstyle.library.helper.AssetSQLiteOpenHelperFactory;

import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryBookmark;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryDatabase;
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.QueryTool;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.room.Room;

public class DictionaryApplication extends Application {
    static public final String SQL_QUERY_DELETE_RESULTS =
            "DELETE FROM DictionarySearchResult";

    static final String DATABASE_NAME = "JMdict.db";

    protected MutableLiveData<DictionaryDatabase> m_dictionaryDatabase;

/*    protected MutableLiveData<String> m_queryTerm;
    protected MutableLiveData<String> m_queryLang;*/

    public LiveData<DictionaryDatabase> getDictionaryDatabase() {
        return m_dictionaryDatabase;
    }

    protected DictionaryDatabase getDatabase() {
        return Room.databaseBuilder(this,
                DictionaryDatabase.class,
                DATABASE_NAME)
                .openHelperFactory(new AssetSQLiteOpenHelperFactory())
                .fallbackToDestructiveMigration()
                .build();
    }

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

            Cursor cur = aDb.rawQuery("SELECT value FROM DictionaryControl WHERE control='version'", null);
            long version = 0;

            if (cur.getCount() == 0) {
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

            Cursor cur = aDb.rawQuery("SELECT value FROM DictionaryControl WHERE control='date'", null);
            long date = 0;

            if (cur.getCount() == 0) {
                return 0;
            }

            while (cur.moveToNext()) {
                date = cur.getLong(0);
            };

            cur.close();

            return date;
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

            DictionaryDatabase db = aParams[0].getDatabase();

            if (bookmarks != null) {
                db.dictionaryBookmarkDao().insertMany(bookmarks);
            }

            // Removing results from previous launch
            // In the future there will be persistence
            db.beginTransaction();

            db.getOpenHelper().getWritableDatabase().execSQL(SQL_QUERY_DELETE_RESULTS);

            db.setTransactionSuccessful();
            db.endTransaction();

            aParams[0].m_dictionaryDatabase.postValue(db);

            if (BuildConfig.DEBUG_MESSAGE) {
                Log.d("MIGRATE_DB", "Database check ended");
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

        m_dictionaryDatabase = new MutableLiveData<DictionaryDatabase>();

        new InitializeDBTask().execute(this);
    }
}
