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

package org.happypeng.sumatora.android.sumatoradictionary.model;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.core.util.Pair;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;
import androidx.paging.PagedList;

import org.happypeng.sumatora.android.sumatoradictionary.adapter.DictionaryPagedListAdapter;
import org.happypeng.sumatora.android.sumatoradictionary.component.BookmarkImportComponent;
import org.happypeng.sumatora.android.sumatoradictionary.component.LanguageSettingsComponent;
import org.happypeng.sumatora.android.sumatoradictionary.component.PersistentDatabaseComponent;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchElement;
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentDatabase;
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.BookmarkImportQueryTool;
import org.happypeng.sumatora.android.sumatoradictionary.viewholder.DictionarySearchElementViewHolder;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.observers.DisposableSingleObserver;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subjects.Subject;

public class BookmarkImportModel extends ViewModel {
    public static class Factory extends ViewModelProvider.AndroidViewModelFactory{
        private final PersistentDatabaseComponent persistentDatabaseComponent;
        private final BookmarkImportComponent bookmarkComponent;
        private final LanguageSettingsComponent languageSettingsComponent;
        private final int key;

        public Factory(@NonNull final Application application,
                       @NonNull final PersistentDatabaseComponent persistentDatabaseComponent,
                       @NonNull final BookmarkImportComponent bookmarkComponent,
                       @NonNull final LanguageSettingsComponent languageSettingsComponent,
                       final int key) {
            super(application);

            this.persistentDatabaseComponent = persistentDatabaseComponent;
            this.bookmarkComponent = bookmarkComponent;
            this.languageSettingsComponent = languageSettingsComponent;
            this.key = key;
        }

        @NonNull
        @Override
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            T ret = modelClass.cast(new BookmarkImportModel(persistentDatabaseComponent,
                    bookmarkComponent, languageSettingsComponent, key));

            if (ret != null) {
                return ret;
            }

            throw new IllegalArgumentException("ViewModel cast failed");
        }
    }

    final protected CompositeDisposable compositeDisposable;

    final private Subject<DictionaryPagedListAdapter> pagedListAdapterSubject;
    final private Subject<DictionarySearchElement> itemAtEndSubject;

    private Observer<PagedList<DictionarySearchElement>> pagedListObserver;
    private LiveData<PagedList<DictionarySearchElement>> pagedList;

    final private Observable<Event> queryEventObservable;

    public Observable<Event> getQueryEvent() {
        return queryEventObservable;
    }

    public Observable<DictionaryPagedListAdapter> getPagedListAdapter() {
        return pagedListAdapterSubject;
    }

    public enum EventType {
        LANGUAGE,
        BOOKMARK
    }

    public static class Event {
        public final EventType type;
        public final BookmarkImportQueryTool queryTool;
        public final boolean executed;

        public Event(final EventType type, final BookmarkImportQueryTool queryTool, final boolean executed) {
            this.type = type;
            this.queryTool = queryTool;
            this.executed = executed;
        }
    }

    public BookmarkImportModel(@NonNull final PersistentDatabaseComponent persistentDatabaseComponent,
                               @NonNull final BookmarkImportComponent bookmarkImportComponent,
                               @NonNull final LanguageSettingsComponent languageSettingsComponent,
                               final int key) {
        super();

        compositeDisposable = new CompositeDisposable();

        pagedListAdapterSubject = BehaviorSubject.create();
        itemAtEndSubject = PublishSubject.create();

        compositeDisposable.add(Single.fromCallable(persistentDatabaseComponent::getEntities).map(entities -> new DictionaryPagedListAdapter(new DictionarySearchElementViewHolder.Status(entities),
                false, true)).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                .subscribeWith(new DisposableSingleObserver<DictionaryPagedListAdapter>() {
                    @Override
                    public void onSuccess(@io.reactivex.rxjava3.annotations.NonNull DictionaryPagedListAdapter dictionaryPagedListAdapter) {
                        pagedList = persistentDatabaseComponent.getSearchElements(key,
                                new PagedList.BoundaryCallback<DictionarySearchElement>() {
                                    @Override
                                    public void onItemAtEndLoaded(@NonNull DictionarySearchElement itemAtEnd) {
                                        super.onItemAtEndLoaded(itemAtEnd);

                                        itemAtEndSubject.onNext(itemAtEnd);
                                    }
                                });

                        pagedListObserver = dictionaryPagedListAdapter::submitList;
                        pagedList.observeForever(pagedListObserver);

                        pagedListAdapterSubject.onNext(dictionaryPagedListAdapter);
                    }

                    @Override
                    public void onError(@io.reactivex.rxjava3.annotations.NonNull Throwable e) {
                        e.printStackTrace();
                    }
                }));

        final Observable<Event> bookmarks =
                bookmarkImportComponent.getImportBookmarksObservable()
                        .filter(i -> i.second == key)
                        .map(x -> new Event(EventType.BOOKMARK, null, false));

        final Observable<Event> queryTool =
                languageSettingsComponent.getPersistentLanguageSettings()
                        .observeOn(Schedulers.io())
                        .map(p -> new Event(EventType.LANGUAGE,
                                p.second ? new BookmarkImportQueryTool(persistentDatabaseComponent,
                                        key, p.first) : null, false));

        queryEventObservable = Observable.merge(bookmarks, queryTool)
                .observeOn(Schedulers.io())
                .scan((status, event) -> {
                    if (event.type == EventType.LANGUAGE && event.queryTool == null) {
                        return new Event(event.type, null, status.executed);
                    }

                    final PersistentDatabase persistentDatabase = persistentDatabaseComponent.getDatabase();
                    final BookmarkImportQueryTool bookmarkImportQueryTool =
                            event.type == EventType.LANGUAGE ? event.queryTool : status.queryTool;

                    if (event.type == EventType.LANGUAGE) {
                        persistentDatabase.runInTransaction(() -> {
                            bookmarkImportQueryTool.delete();
                            bookmarkImportQueryTool.execute();
                        });
                    } else {
                        persistentDatabase.runInTransaction(bookmarkImportQueryTool::delete);
                        persistentDatabase.runInTransaction(bookmarkImportQueryTool::execute);
                    }

                    return new Event(event.type, bookmarkImportQueryTool, true);
                }).share().replay(1).autoConnect();
    }
}
