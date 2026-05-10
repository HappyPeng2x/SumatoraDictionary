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

import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryBookmark;
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
    // Optimization: Use UPSERT (ON CONFLICT) for clever merging rules.
    // - bookmark: true if true in either table (using MAX).
    // - memo: updated only if the imported memo is not empty.
    private static String SQL_BOOKMARK_IMPORT_COMMIT =
            "INSERT INTO DictionaryBookmark (seq, bookmark, memo) " +
            "SELECT seq, bookmark, memo FROM DictionaryBookmarkImport WHERE ref = ? " +
            "ON CONFLICT(seq) DO UPDATE SET " +
            "bookmark = MAX(DictionaryBookmark.bookmark, excluded.bookmark), " +
            "memo = CASE WHEN excluded.memo IS NOT NULL AND excluded.memo != '' THEN excluded.memo ELSE DictionaryBookmark.memo END";

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
            String type = contentResolver.getType(uri);

            // Fallback for file URIs where getType() might return null
            if (type == null && uri.getPath() != null) {
                if (uri.getPath().endsWith(".json")) {
                    type = "application/json";
                } else if (uri.getPath().endsWith(".xml")) {
                    type = "text/xml";
                }
            }

            if ("text/xml".equals(type)) {
                // Fix: Use the new return type that includes bookmark status and memo
                final List<DictionaryBookmark> bookmarks = DictionaryBookmarkXML.readXML(is);
                is.close();

                if (bookmarks == null) {
                    // TBD: error management
                    return;
                }

                database.runInTransaction(() -> {
                    database.dictionaryBookmarkImportDao().delete(key);

                    for (DictionaryBookmark b : bookmarks) {
                        database.dictionaryBookmarkImportDao().insert(
                                new DictionaryBookmarkImport(key, b.seq, b.bookmark, b.memo));
                    }
                });
            } else if ("application/json".equals(type)) {
                final ObjectMapper mapper = new ObjectMapper();
                final List<DictionaryBookmarkImport> bookmarks = mapper.readValue(is, new TypeReference<List<DictionaryBookmarkImport>>() {});
                is.close();

                if (bookmarks == null) {
                    // TBD: error management
                    return;
                }

                for (DictionaryBookmarkImport dictionaryBookmarkImport : bookmarks) {
                    dictionaryBookmarkImport.ref = key;
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
