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
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

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
                int seqIndex = cur.getColumnIndex("seq");
                int bookmarkIndex = cur.getColumnIndex("bookmark");
                int memoIndex = cur.getColumnIndex("memo");

                while (cur.moveToNext()) {
                    long seq = seqIndex != -1 ? cur.getLong(seqIndex) : cur.getLong(0);
                    long bookmark = bookmarkIndex != -1 ? cur.getLong(bookmarkIndex) : 1;
                    String memo = memoIndex != -1 ? cur.getString(memoIndex) : null;
                    list.add(new DictionaryBookmark(seq, bookmark, memo));
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
                        public AssetDictionaryObject create(@NonNull String aFile, String aDescription, @NonNull String aType, @NonNull String aLang, int aVersion, int aDate, @NonNull String aSha256) {
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

            InstalledDictionary core = aDB.installedDictionaryDao().getForTypeLang("core", "");

            if (core == null || core.version < version || core.date < date) {
                List<AssetDictionaryObject> assetDictionaries = aDB.assetDictionaryObjectDao().getAll();

                for (AssetDictionaryObject d : assetDictionaries) {
                    d.install(assetManager, databaseInstallDir.getAbsolutePath(), aDB.installedDictionaryDao());
                }
            }
        } catch (IOException ignored) {

        }
    }

    // MIGRATION_9_10 (see PersistentDatabaseParameters) recreates InstalledDictionary from scratch
    // (DROP TABLE, not an incremental ALTER) as part of the schema v1 -> v2 rework, since the old
    // rows (type="jmdict"/"jmdict_translation") pointed at files in a format the new query code
    // can't read anyway. That means a user upgrading from 0.4.7.6 loses the DB rows for their old
    // packs, but the backing .db files those rows pointed at are untouched by a SQL migration and
    // would otherwise leak on disk forever with nothing left to reference them. Reconcile by
    // directory scan instead of by DB row: delete anything in the install directory that no
    // currently-tracked InstalledDictionary (including a pending-update file) points at.
    // Package-private (not private) so PersistentDatabaseInitializationTest can exercise it
    // directly.
    @WorkerThread
    static void cleanupOrphanedDictionaryFiles(@NonNull final Context aApp,
                                               @NonNull final PersistentDatabase aDB) {
        File databaseRoot = aApp.getDatabasePath(DATABASE_NAME).getParentFile();
        File installDir = new File(databaseRoot, "dictionaries");
        File[] files = installDir.listFiles();

        if (files == null) {
            return;
        }

        Set<String> referenced = new HashSet<>();
        for (InstalledDictionary d : aDB.installedDictionaryDao().getAll()) {
            referenced.add(d.file);
            if (d.pendingFile != null) {
                referenced.add(d.pendingFile);
            }
        }

        for (File f : files) {
            if (!referenced.contains(f.getAbsolutePath())) {
                f.delete();
            }
        }
    }

    // Gloss/tatoeba packs built by SumatoraIndex before this version lack the entry_source_key /
    // source_ord columns in the Sense table that the current app's cross-pack join queries rely on
    // (see PersistentDatabaseComponent.fetchGlossText). Such packs attach without error but fail
    // every query at runtime, producing silently empty search results. Detach them and set version
    // to 0 so the next update check will download a compatible replacement.
    private static final int MINIMUM_COMPATIBLE_GLOSS_VERSION = 12;

    @WorkerThread
    private static void detachIncompatiblePacks(@NonNull final PersistentDatabase persistentDatabase,
                                                 @NonNull final DictionaryControlInfo controlInfo) {
        List<InstalledDictionary> dictionaries = persistentDatabase.installedDictionaryDao().getAll();

        for (InstalledDictionary d : dictionaries) {
            if (!d.type.equals("gloss") && !d.type.equals("tatoeba")) {
                continue;
            }

            // version == 0 means already flagged by a previous run or recovered as missing —
            // nothing to probe.
            if (d.version <= 0 || d.version >= MINIMUM_COMPATIBLE_GLOSS_VERSION) {
                continue;
            }

            if (d.isAttached(persistentDatabase)) {
                d.detach(persistentDatabase);
            }

            d.version = 0;
            d.date = 0;
            persistentDatabase.installedDictionaryDao().insert(d);

            controlInfo.incompatiblePacks.add(d.getAlias());
        }
    }

    @WorkerThread
    private static List<DictionaryBookmark> readBackupBookmarks(@NonNull Context aApp) {
        File bookmarksBackup = new File(aApp.getFilesDir(), "bookmarks_backup.xml");
        List<DictionaryBookmark> resBookmarks = null;

        if (bookmarksBackup.exists()) {
            try {
                FileInputStream fis = new FileInputStream(bookmarksBackup);

                resBookmarks = DictionaryBookmarkXML.readXML(fis);

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
    private static void promotePendingUpdate(@NonNull final PersistentDatabase persistentDatabase,
                                             @NonNull final InstalledDictionary d) {
        new File(d.file).delete();

        d.file = d.pendingFile;
        d.version = d.pendingVersion;
        d.date = d.pendingDate;
        d.pendingFile = null;
        d.pendingVersion = null;
        d.pendingDate = null;

        persistentDatabase.installedDictionaryDao().insert(d);
    }

    @WorkerThread
    public static void initializeDatabase(@NonNull final Context context,
                                          @NonNull final PersistentDatabase persistentDatabase,
                                          @NonNull final DictionaryControlInfo controlInfo) {
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
        cleanupOrphanedDictionaryFiles(context, persistentDatabase);

        PersistentLanguageSettings persistentLanguageSettings =
                persistentDatabase.persistentLanguageSettingsDao().getLanguageSettingsDirect(0);

        if (persistentLanguageSettings == null) {
            persistentLanguageSettings = new PersistentLanguageSettings(0, PersistentLanguageSettings.LANG_DEFAULT,
                    PersistentLanguageSettings.BACKUP_LANG_DEFAULT);
            persistentDatabase.persistentLanguageSettingsDao().update(persistentLanguageSettings);
        }

        List<InstalledDictionary> dictionaries = persistentDatabase.installedDictionaryDao().getAll();

        for (InstalledDictionary d : dictionaries) {
            // A background update (update-pipeline.md) may have downloaded and verified a newer
            // version of this pack already - nothing has ATTACHed anything yet this session, so
            // this is the one safe moment to swap the old file out for the new one.
            if (d.hasPendingUpdate()) {
                promotePendingUpdate(persistentDatabase, d);
            }

            if (d.type.equals("core") || d.type.equals("kanji") || d.type.equals("pitch")
                    || d.type.equals("suffix") || d.type.equals("names")) {
                d.attach(persistentDatabase);
            }

            if (d.type.equals("gloss") || d.type.equals("tatoeba")) {
                if (d.lang.equals(persistentLanguageSettings.lang) ||
                        (d.lang.equals(persistentLanguageSettings.backupLang))) {
                    d.attach(persistentDatabase);
                }
            }
        }

        detachIncompatiblePacks(persistentDatabase, controlInfo);

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
            Cursor cur = persistentDatabase.getOpenHelper().getReadableDatabase().query("SELECT key, value FROM core.BuildMetadata");

            if (cur != null) {
                while (cur.moveToNext()) {
                    String key = cur.getString(0);
                    String value = cur.getString(1);

                    if ("build_timestamp".equals(key)) {
                        controlInfo.buildTimestamp = Long.parseLong(value);
                    } else if ("schema_version".equals(key)) {
                        controlInfo.formatVersion = Integer.parseInt(value);
                    } else if ("jmdict_entry_count".equals(key)) {
                        controlInfo.entryCount = Integer.parseInt(value);
                    }
                }

                cur.close();
            }
        } catch (SQLException | NumberFormatException ignored) {

        }

        // Clean bookmark table
        persistentDatabase.getOpenHelper().getWritableDatabase()
                .execSQL("DELETE FROM DictionaryBookmark WHERE bookmark = 0 AND IFNULL(memo, '') = '' AND IFNULL(tags, '') = ''");

        persistentDatabase.getOpenHelper().getWritableDatabase()
                .execSQL("UPDATE DictionaryBookmark SET bookmark = 1 WHERE IFNULL(memo, '') != '' OR IFNULL(tags, '') != ''");

        // No persistence - clear display on initialization
        persistentDatabase.dictionarySearchElementDao().deleteAll();
    }
}
