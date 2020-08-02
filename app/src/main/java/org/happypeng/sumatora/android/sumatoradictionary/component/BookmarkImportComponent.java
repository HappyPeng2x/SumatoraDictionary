/* Sumatora Dictionary
        Copyright (C) 2020 Nicolas Centa

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

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;

import androidx.annotation.WorkerThread;
import androidx.sqlite.db.SupportSQLiteStatement;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryBookmarkImport;
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentDatabase;
import org.happypeng.sumatora.android.sumatoradictionary.xml.DictionaryBookmarkXML;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import dagger.hilt.android.qualifiers.ApplicationContext;

@Singleton
public class BookmarkImportComponent {
    private static String SQL_BOOKMARK_IMPORT_COMMIT = "INSERT OR IGNORE INTO DictionaryBookmark SELECT seq, bookmark, memo FROM DictionaryBookmarkImport WHERE ref = ?";

    private final Context context;
    private final PersistentDatabaseComponent persistentDatabaseComponent;

    private SupportSQLiteStatement commitQuery;

    @Inject
    BookmarkImportComponent(@ApplicationContext final Context context, final PersistentDatabaseComponent persistentDatabaseComponent) {
        this.context = context;
        this.persistentDatabaseComponent = persistentDatabaseComponent;
    }

    private synchronized void initQuery() {
        if (commitQuery == null) {
            final PersistentDatabase persistentDatabase = persistentDatabaseComponent.getDatabase();

            commitQuery = persistentDatabase.compileStatement(SQL_BOOKMARK_IMPORT_COMMIT);
        }
    }

    @WorkerThread
    public void commitBookmarks(final int ref) {
        final PersistentDatabase database = persistentDatabaseComponent.getDatabase();

        initQuery();

        database.runInTransaction(() -> {
            commitQuery.bindLong(1, ref);
            commitQuery.executeInsert();

            database.dictionaryBookmarkImportDao().delete(ref);
        });
    }

    @WorkerThread
    public void cancelImport(final int ref) {
        final PersistentDatabase database = persistentDatabaseComponent.getDatabase();

        database.dictionaryBookmarkImportDao().delete(ref);
    }

    @WorkerThread
    public void processURI(final Uri uri, final int key) {
        final PersistentDatabase database = persistentDatabaseComponent.getDatabase();

        try {
            final ContentResolver contentResolver = context.getContentResolver();
            final InputStream is = contentResolver.openInputStream(uri);
            final String type = contentResolver.getType(uri);

            if ("text/xml".equals(type)) {
                final List<Long> seqs = DictionaryBookmarkXML.readXML(is);
                is.close();

                if (seqs == null) {
                    // TBD: error management

                    return;
                }

                database.runInTransaction(() -> {
                    database.dictionaryBookmarkImportDao().delete(key);

                    for (Long seq : seqs) {
                        database.dictionaryBookmarkImportDao().insert(
                                new DictionaryBookmarkImport(key, seq, 1, null));
                    }
                });
            } else if ("application/json".equals(type)) {
                final ObjectMapper mapper = new ObjectMapper();
                final List<DictionaryBookmarkImport> bookmarks = mapper.readValue(is, new TypeReference<List<DictionaryBookmarkImport>>() {});
                is.close();

                for (DictionaryBookmarkImport dictionaryBookmarkImport : bookmarks) {
                    dictionaryBookmarkImport.ref = key;
                }

                if (bookmarks == null) {
                    // TBD: error management

                    return;
                }

                database.runInTransaction(() -> {
                    database.dictionaryBookmarkImportDao().delete(key);
                    database.dictionaryBookmarkImportDao().insertMany(bookmarks);
                });
            }

            // TBD: status management
            // mStatus.postValue(STATUS_PROCESSED);
        } catch (IOException e) {
            System.err.println(e.toString());

            // TBD: error management
        }
    }
}
