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

import androidx.annotation.WorkerThread;

import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryBookmark;
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentDatabase;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subjects.Subject;

@Singleton
public class BookmarkComponent {
    private final PersistentDatabaseComponent persistentDatabaseComponent;
    private final Subject<List<DictionaryBookmark>> bookmarkChanges;

    @Inject
    BookmarkComponent(final PersistentDatabaseComponent persistentDatabaseComponent) {
        this.persistentDatabaseComponent = persistentDatabaseComponent;

        final PublishSubject<List<DictionaryBookmark>> publishSubject = PublishSubject.create();
        this.bookmarkChanges = publishSubject.toSerialized();
    }

    public Observable<List<DictionaryBookmark>> getBookmarkChanges() {
        return bookmarkChanges;
    }

    @WorkerThread
    public void updateBookmarks(final List<DictionaryBookmark> bookmarks) {
        final PersistentDatabase database = persistentDatabaseComponent.getDatabase();

        database.runInTransaction(() -> database.dictionaryBookmarkDao().insertMany(bookmarks));

        bookmarkChanges.onNext(bookmarks);
    }
}
