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

import androidx.annotation.MainThread;

import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryBookmark;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryBookmarkDao;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;

@Singleton
public class BookmarkComponent {
    private final PersistentDatabaseComponent persistentDatabaseComponent;
    private final Observable<List<DictionaryBookmark>> bookmarkChangesObservable;

    @Inject
    BookmarkComponent(final PersistentDatabaseComponent persistentDatabaseComponent) {
        this.persistentDatabaseComponent = persistentDatabaseComponent;

        // Use Room's native RxJava support to create an observable directly from the DAO.
        // This ensures Room is the single source of truth and greatly simplifies implementation.
        this.bookmarkChangesObservable = Observable.defer(() ->
                persistentDatabaseComponent.getDatabase().dictionaryBookmarkDao().getAllObservable())
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .replay(1)
                .autoConnect();
    }

    public Observable<List<DictionaryBookmark>> getBookmarkChanges() {
        return bookmarkChangesObservable;
    }

    @MainThread
    public void updateBookmark(final DictionaryBookmark bookmark) {
        // Only write to the database. Room will automatically emit updates to bookmarkChangesObservable.
        Completable.fromAction(() -> {
            final DictionaryBookmarkDao dictionaryBookmarkDao = persistentDatabaseComponent.getDatabase().dictionaryBookmarkDao();

            if (bookmark.bookmark > 0 || (bookmark.memo != null && !bookmark.memo.isEmpty())) {
                dictionaryBookmarkDao.insert(bookmark);
            } else {
                dictionaryBookmarkDao.delete(bookmark);
            }
        }).subscribeOn(Schedulers.io()).subscribe();
    }
}
