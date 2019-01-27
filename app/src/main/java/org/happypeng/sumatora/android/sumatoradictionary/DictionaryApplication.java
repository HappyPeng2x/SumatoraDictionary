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

import android.database.sqlite.SQLiteDatabase;
import android.os.AsyncTask;

import com.fstyle.library.helper.AssetSQLiteOpenHelperFactory;

import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryBookmark;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryControl;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryControlDao;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryDatabase;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryTypeConverters;

import java.io.File;
import java.util.HashMap;
import java.util.List;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.room.Room;

public class DictionaryApplication extends Application {
    static final String DATABASE_NAME = "JMdict.db";

    protected MutableLiveData<DictionaryDatabase> m_dictionaryDatabase;

    public LiveData<DictionaryDatabase> getDictionaryDatabase() {
        return m_dictionaryDatabase;
    }

/*    private boolean hasExistingDatabase() {
        return getDatabasePath(DATABASE_NAME).exists();
    }

    private SQLiteDatabase openExistingDatabaseSQL() {
        return SQLiteDatabase.openDatabase(getDatabasePath(DATABASE_NAME).getAbsolutePath(),
                null, SQLiteDatabase.OPEN_READWRITE);
    }*/

    protected DictionaryDatabase getDatabase() {
        return Room.databaseBuilder(this,
                DictionaryDatabase.class,
                DATABASE_NAME)
                .openHelperFactory(new AssetSQLiteOpenHelperFactory())
                .fallbackToDestructiveMigration()
                .build();
    }

    private static class InitializeDBTask extends AsyncTask<DictionaryApplication, Void, Void> {
        protected Void doInBackground(DictionaryApplication... aParams) {
            if (aParams.length == 0) {
                return null;
            }

            // Remove older versions database
            File f = new File(aParams[0].getApplicationInfo().dataDir + "/JMdict.db");
            f.delete();

            System.out.println("Starting database check...");

            DictionaryDatabase db = aParams[0].getDatabase();

            DictionaryControlDao controlDao = db.dictionaryControlDao();

            Long imported = controlDao.get("imported");

            if (imported == null || imported != 1) { // We are dealing with freshly imported DB
                System.out.println("Database freshly imported...");

                controlDao.insert(new DictionaryControl("imported", 1));

                System.out.println("We just set this value: " + controlDao.get("imported"));
            } else {
                // The DB has been copied in the past, but is it the right version and up to date ?
                Long version = controlDao.get("version");
                Long date = controlDao.get("date");

                int currentDate = aParams[0].getResources().getInteger(R.integer.database_date);
                int currentVersion = aParams[0].getResources().getInteger(R.integer.database_version);
                int databaseReset = aParams[0].getResources().getInteger(R.integer.database_reset);

                if ((version == null || version < currentVersion) ||
                        (date == null || date < currentDate) || databaseReset != 0) {
                    System.out.println("Recreating database...");

                    // The current DB is older than advertised by assets
                    controlDao = null;

                    db.close();

                    // Import fresh database
                    // aParams[0].deleteDatabase(DictionaryApplication.DATABASE_NAME);
                    db = aParams[0].getDatabase();

                    controlDao = db.dictionaryControlDao();

                    controlDao.insert(new DictionaryControl("imported", 1));
                    controlDao.insert(new DictionaryControl("version", currentVersion));
                    controlDao.insert(new DictionaryControl("date", currentDate));
                } else {
                    System.out.println("Using database as is...");
                }
            }

            aParams[0].m_dictionaryDatabase.postValue(db);

            System.out.println("Database check ended...");

            return null;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        m_dictionaryDatabase = new MutableLiveData<DictionaryDatabase>();

        new InitializeDBTask().execute(this);
    }
}
