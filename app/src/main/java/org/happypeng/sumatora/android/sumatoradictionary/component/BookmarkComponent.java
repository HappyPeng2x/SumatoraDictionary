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
import androidx.annotation.WorkerThread;

import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryBookmark;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryBookmarkDao;
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentDatabase;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subjects.Subject;

@Singleton
public class BookmarkComponent {
    private final PersistentDatabaseComponent persistentDatabaseComponent;
    private final Subject<List<DictionaryBookmark>> bookmarkChanges;
    private final Observable<List<DictionaryBookmark>> bookmarkChangesObservable;

    @Inject
    BookmarkComponent(final PersistentDatabaseComponent persistentDatabaseComponent) {
        this.persistentDatabaseComponent = persistentDatabaseComponent;

        bookmarkChanges = PublishSubject.create();
        bookmarkChangesObservable =
                bookmarkChanges.observeOn(Schedulers.io()).map(l -> {
                    final PersistentDatabase persistentDatabase = persistentDatabaseComponent.getDatabase();
                    final DictionaryBookmarkDao dictionaryBookmarkDao = persistentDatabase.dictionaryBookmarkDao();

                    persistentDatabase.runInTransaction(() ->
                    {
                        for (DictionaryBookmark b : l) {
                            if (b.bookmark > 0 || (b.memo != null && !"".equals(b.memo))) {
                                dictionaryBookmarkDao.insert(b);
                            } else {
                                dictionaryBookmarkDao.delete(b);
                            }
                        }
                    });

                    return l;
                }).observeOn(AndroidSchedulers.mainThread()).publish().autoConnect();
    }

    public Observable<List<DictionaryBookmark>> getBookmarkChanges() {
        return bookmarkChangesObservable;
    }

    @MainThread
    public void updateBookmarks(final List<DictionaryBookmark> bookmarks) {
        bookmarkChanges.onNext(bookmarks);
    }

    @MainThread
    public void updateBookmark(final DictionaryBookmark bookmark) {
        final ArrayList<DictionaryBookmark> list = new ArrayList<>(1);
        list.add(bookmark);

        bookmarkChanges.onNext(list);
    }
}
