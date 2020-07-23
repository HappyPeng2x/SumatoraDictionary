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

package org.happypeng.sumatora.android.sumatoradictionary.db;

import android.content.Context;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import org.happypeng.sumatora.android.sumatoradictionary.R;
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.BaseDictionaryObject;
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.Settings;
import org.happypeng.sumatora.android.sumatoradictionary.xml.DictionaryBookmarkXML;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

import static org.happypeng.sumatora.android.sumatoradictionary.db.PersistentDatabaseParameters.DATABASE_NAME;

public abstract class PersistentDatabaseInitialization {
    private static boolean hasExistingDatabase(Context aApp) {
        return aApp.getDatabasePath(DATABASE_NAME).exists();
    }

    private static SQLiteDatabase openExistingDatabaseSQL(Context aApp) {
        try {
            return SQLiteDatabase.openDatabase(aApp.getDatabasePath(DATABASE_NAME).getAbsolutePath(),
                    null, SQLiteDatabase.OPEN_READWRITE);
        } catch (SQLException e) {
            return null;
        }
    }

    private static List<DictionaryBookmark> extractBookmarks(SQLiteDatabase aDb, long aVersion) {
        if (aDb == null) {
            return null;
        }

        LinkedList<DictionaryBookmark> list = new LinkedList<>();

        Cursor cur = null;

        try {
            cur = aDb.rawQuery("SELECT * FROM DictionaryBookmark", null);
        } catch (SQLException ignored) {

        }

        if (cur == null) {
            try {
                cur = aDb.rawQuery("SELECT seq FROM DictionaryEntry WHERE lang='eng' AND bookmark != ''", null);
            } catch (SQLException ignored) {

            }
        }

        if (cur != null) {
            if (cur.getCount() != 0) {
                while (cur.moveToNext()) {
                    list.add(new DictionaryBookmark(cur.getLong(0), 1, null));
                }
            }

            cur.close();
        }

        return list;
    }

    private static long checkLegacyDatabaseVersion(SQLiteDatabase aDb) {
        if (aDb == null) {
            return 0;
        }

        Cursor cur = null;

        try {
            cur = aDb.rawQuery("SELECT value FROM DictionaryControl WHERE control='version'", null);
        } catch (SQLException ignored) {

        }

        long version = 0;

        if (cur == null || cur.getCount() == 0) {
            return 0;
        }

        while (cur.moveToNext()) {
            version = cur.getLong(0);
        }
        ;

        cur.close();

        return version;
    }

    @WorkerThread
    private static void updateDictionaries(final Context aApp,
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
        } catch (IOException ignored) {

        }
    }

    @WorkerThread
    private static List<DictionaryBookmark> readBackupBookmarks(@NonNull Context aApp) {
        File bookmarksBackup = new File(aApp.getFilesDir(), "bookmarks_backup.xml");
        List<DictionaryBookmark> resBookmarks = null;

        if (bookmarksBackup.exists()) {
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
                }

                fis.close();
            } catch (IOException ignored) {

            }
        }

        return resBookmarks;
    }

    @WorkerThread
    private static void deleteBookmarksBackup(@NonNull final Context aApp) {
        File bookmarksBackup = new File(aApp.getFilesDir(), "bookmarks_backup.xml");

        bookmarksBackup.delete();
    }

    @WorkerThread
    private static void saveBookmarksBackup(@NonNull final Context aApp,
                                            @NonNull final List<DictionaryBookmark> aBookmarks) {
        File bookmarksBackup = new File(aApp.getFilesDir(), "bookmarks_backup.xml");

        try {
            DictionaryBookmarkXML.writeXML(bookmarksBackup, aBookmarks);
        } catch (IOException ignored) {

        }
    }

    @WorkerThread
    public static void initializeDatabase(@NonNull final Context context,
                                          @NonNull final PersistentDatabase persistentDatabase,
                                          @NonNull final HashMap<String, String> entities) {
        int databaseReset = context.getResources().getInteger(R.integer.database_reset);

        // Remove older versions database
        File f = new File(context.getApplicationInfo().dataDir + "/JMdict.db");
        f.delete();

        long version = 0;

        SQLiteDatabase sqlDB = null;
        List<DictionaryBookmark> bookmarks = null;

        if (hasExistingDatabase(context)) {
            sqlDB = openExistingDatabaseSQL(context);

            if (sqlDB != null) {

                version = checkLegacyDatabaseVersion(sqlDB);
                bookmarks = extractBookmarks(sqlDB, version);

                if (bookmarks != null) {
                    saveBookmarksBackup(context, bookmarks);
                }

                sqlDB.close();
                sqlDB = null;
            }
        }

        context.deleteDatabase(DATABASE_NAME);

        updateDictionaries(context, persistentDatabase);

        PersistentLanguageSettings persistentLanguageSettings =
                persistentDatabase.persistentLanguageSettingsDao().getLanguageSettingsDirect(0);

        if (persistentLanguageSettings == null) {
            persistentLanguageSettings = new PersistentLanguageSettings(0, PersistentLanguageSettings.LANG_DEFAULT,
                    PersistentLanguageSettings.BACKUP_LANG_DEFAULT);
            persistentDatabase.persistentLanguageSettingsDao().update(persistentLanguageSettings);
        }

        List<InstalledDictionary> dictionaries = persistentDatabase.installedDictionaryDao().getAll();

        for (InstalledDictionary d : dictionaries) {
            if (d.type.equals("jmdict")) {
                d.attach(persistentDatabase);
            }

            if (d.type.equals("jmdict_translation") || d.type.equals("tatoeba")) {
                if (d.lang.equals(persistentLanguageSettings.lang) ||
                        (d.lang.equals(persistentLanguageSettings.backupLang))) {
                    d.attach(persistentDatabase);
                }
            }
        }

        persistentDatabase.persistentSettingsDao().insertDefault(new PersistentSetting(Settings.REPOSITORY_URL,
                context.getString(R.string.dictionaries_url)));

        if (bookmarks == null) {
            bookmarks = readBackupBookmarks(context);
        }

        if (bookmarks != null) {
            persistentDatabase.dictionaryBookmarkDao().insertMany(bookmarks);
        }

        deleteBookmarksBackup(context);

        try {
            Cursor cur = persistentDatabase.getOpenHelper().getReadableDatabase().query("SELECT name, content FROM jmdict.DictionaryEntity");

            if (cur != null) {
                if (cur.getCount() > 0) {
                    while (cur.moveToNext()) {
                        String name = cur.getString(0);
                        String content = cur.getString(1);

                        entities.put(name, content);
                    }
                }

                cur.close();
            }
        } catch (SQLException ignored) {

        }

        // No persistence - clear display on initialization
        persistentDatabase.dictionarySearchElementDao().deleteAll();
        persistentDatabase.dictionaryElementDao().deleteAll();
    }
}
