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
import org.happypeng.sumatora.android.sumatoradictionary.component.BookmarkComponent;
import org.happypeng.sumatora.android.sumatoradictionary.component.LanguageSettingsComponent;
import org.happypeng.sumatora.android.sumatoradictionary.component.PersistentDatabaseComponent;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryBookmark;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchElement;
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentDatabase;
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentLanguageSettings;
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.DictionarySearchQueryTool;
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.ValueHolder;
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

public class QueryFragmentModel extends ViewModel {
    public static class Factory extends ViewModelProvider.AndroidViewModelFactory{
        public interface QueryToolConstructor {
            DictionarySearchQueryTool create(PersistentLanguageSettings persistentLanguageSettings);
        }

        private final QueryToolConstructor constructor;
        private final PersistentDatabaseComponent persistentDatabaseComponent;
        private final BookmarkComponent bookmarkComponent;
        private final LanguageSettingsComponent languageSettingsComponent;
        private final int key;

        public Factory(@NonNull final Application application,
                       @NonNull final PersistentDatabaseComponent persistentDatabaseComponent,
                       @NonNull final BookmarkComponent bookmarkComponent,
                       @NonNull final LanguageSettingsComponent languageSettingsComponent,
                       @NonNull final QueryToolConstructor constructor,
                       final int key) {
            super(application);

            this.constructor = constructor;
            this.persistentDatabaseComponent = persistentDatabaseComponent;
            this.bookmarkComponent = bookmarkComponent;
            this.languageSettingsComponent = languageSettingsComponent;
            this.key = key;
        }

        @NonNull
        @Override
        public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
            T ret = modelClass.cast(new QueryFragmentModel(persistentDatabaseComponent,
                    bookmarkComponent, languageSettingsComponent, constructor, key));

            if (ret != null) {
                return ret;
            }

            throw new IllegalArgumentException("ViewModel cast failed");
        }
    }

    final protected PersistentDatabaseComponent persistentDatabaseComponent;
    final protected BookmarkComponent bookmarkComponent;

    final protected CompositeDisposable compositeDisposable;

    final private Subject<DictionaryPagedListAdapter> pagedListAdapterSubject;
    final private Subject<DictionarySearchElement> itemAtEndSubject;
    final private Subject<String> termSubject;

    final private Observable<Event> queryEventObservable;

    private Observer<PagedList<DictionarySearchElement>> pagedListObserver;
    private LiveData<PagedList<DictionarySearchElement>> pagedList;

    public enum EventType {
        TERM,
        SCROLL,
        BOOKMARK,
        LANGUAGE
    }

    public static class Event {
        final public EventType type;
        final public String term;
        final public int lastQuery;
        final public DictionarySearchQueryTool queryTool;
        final public boolean found;

        public Event(final EventType type,
                     final String term,
                     final DictionarySearchQueryTool queryTool,
                     final int lastQuery,
                     final boolean found) {
            this.type = type;
            this.term = term;
            this.lastQuery = lastQuery;
            this.queryTool = queryTool;
            this.found = found;
        }

        public Event(final EventType type) {
            this(type, null, null, 0, false);
        }
    }

    public void setTerm(final String t) {
        termSubject.onNext(t);
    }

    public Observable<DictionaryPagedListAdapter> getPagedListAdapter() {
        return pagedListAdapterSubject;
    }

    public QueryFragmentModel(@NonNull final PersistentDatabaseComponent persistentDatabaseComponent,
                              @NonNull final BookmarkComponent bookmarkComponent,
                              @NonNull final LanguageSettingsComponent languageSettingsComponent,
                              @NonNull final Factory.QueryToolConstructor queryToolConstructor,
                              final int key) {
        super();

        this.persistentDatabaseComponent = persistentDatabaseComponent;
        this.bookmarkComponent = bookmarkComponent;

        this.compositeDisposable = new CompositeDisposable();

        pagedListAdapterSubject = BehaviorSubject.create();

        itemAtEndSubject = PublishSubject.create();
        termSubject = BehaviorSubject.create();

        compositeDisposable.add(Single.fromCallable(persistentDatabaseComponent::getEntities).map(entities -> new DictionaryPagedListAdapter(new DictionarySearchElementViewHolder.Status(entities),
                false, false)).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
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

        final Observable<Event> scroll =
                itemAtEndSubject.map(x -> new Event(EventType.SCROLL));

        final Observable<Event> bookmarks =
                bookmarkComponent.getBookmarkChanges().map(x -> new Event(EventType.BOOKMARK));

        final Observable<Event> queryTool =
                languageSettingsComponent.getPersistentLanguageSettings()
                        .observeOn(Schedulers.io())
                        .map(p -> new Event(EventType.LANGUAGE, null, p.second ? queryToolConstructor.create(p.first) : null, 0, false));

        final Observable<Event> search =
                termSubject.distinctUntilChanged().map(s -> new Event(EventType.TERM, s, null, 0, false));

        queryEventObservable = Observable.merge(scroll, bookmarks, search, queryTool)
                .observeOn(Schedulers.io())
                .scan((status, event) -> {
                    final DictionarySearchQueryTool searchQueryTool = event.type == EventType.LANGUAGE ? event.queryTool : status.queryTool;
                    final String searchTerm = event.type == EventType.TERM ? event.term : (status.term == null ? "" : status.term);
                    final int lastQuery = event.type == EventType.TERM ? 0 : status.lastQuery;
                    final boolean foundInitially = event.type == EventType.SCROLL && status.found;

                    if (searchQueryTool == null) {
                        return new Event(event.type, searchTerm, null, lastQuery, foundInitially);
                    }

                    final ValueHolder<Pair<Integer, Boolean>> result = new ValueHolder<>(new Pair<>(0, foundInitially));
                    final PersistentDatabase persistentDatabase = persistentDatabaseComponent.getDatabase();

                    if (event.type == EventType.TERM) {
                        persistentDatabase.runInTransaction(searchQueryTool::delete);
                    }

                    persistentDatabase.runInTransaction(() -> {
                        int current = event.type == EventType.SCROLL ? status.lastQuery : 0;
                        boolean found = false;
                        final int queryCount = searchQueryTool.getCount(status.term);

                        if (event.type == EventType.BOOKMARK ||
                                event.type == EventType.LANGUAGE) {
                            searchQueryTool.delete();
                        }

                        while (((lastQuery == 0|| event.type == EventType.SCROLL) && (current < queryCount && !found)) ||
                                (current < lastQuery)) {
                            found = searchQueryTool.execute(searchTerm, current);

                            current++;
                        }

                        result.setValue(new Pair<>(current, found));
                    });

                    return new Event(event.type, searchTerm, searchQueryTool, result.getValue().first,
                            event.type == EventType.SCROLL ? status.found : result.getValue().second);
                }).share().replay(1).autoConnect();
    }

    public void editBookmark(final DictionarySearchElement entry,
                             final long bookmark,
                             final String memo) {
        DictionaryBookmark dictionaryBookmark = new DictionaryBookmark();
        dictionaryBookmark.bookmark = bookmark;
        dictionaryBookmark.memo = memo;
        dictionaryBookmark.seq = entry.getSeq();

        bookmarkComponent.updateBookmark(dictionaryBookmark);
    }

    public Observable<Event> getQueryEvent() {
        return queryEventObservable;
    }

    @Override
    protected void onCleared() {
        super.onCleared();

        if (pagedList != null && pagedListObserver != null) {
            pagedList.removeObserver(pagedListObserver);
        }

        compositeDisposable.dispose();
    }
}
