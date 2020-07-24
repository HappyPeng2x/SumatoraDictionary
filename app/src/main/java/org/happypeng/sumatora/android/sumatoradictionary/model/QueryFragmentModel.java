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

import java.util.LinkedList;
import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Completable;
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

    final private Observable<QueryEvent> queryEventObservable;

    private Observer<PagedList<DictionarySearchElement>> pagedListObserver;
    private LiveData<PagedList<DictionarySearchElement>> pagedList;

    public static class QueryEvent {
        final public int scrollId;
        final public int bookmarkId;
        final public String term;
        final public int lastQuery;
        final public DictionarySearchQueryTool queryTool;
        final public int termId;
        final public int queryToolId;
        final public boolean found;

        public QueryEvent(final int scrollId, final int bookmarkId,
                          final int termId,
                          final String term,
                          final int queryToolId,
                          final DictionarySearchQueryTool queryTool,
                          final int lastQuery,
                          final boolean found) {
            this.scrollId = scrollId;
            this.bookmarkId = bookmarkId;
            this.term = term;
            this.lastQuery = lastQuery;
            this.queryTool = queryTool;
            this.termId = termId;
            this.queryToolId = queryToolId;
            this.found = found;
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

        final Observable<Integer> scroll =
                itemAtEndSubject.scan(0, (acc, val) -> acc + 1);

        final Observable<Integer> bookmarks =
                bookmarkComponent.getBookmarkChanges().scan(0, (acc, val) -> acc + 1);

        final Observable<Pair<Integer, DictionarySearchQueryTool>> queryTool =
                languageSettingsComponent.getPersistentLanguageSettings()
                        .observeOn(Schedulers.io())
                        .scan(new Pair<>(0, null), (acc, value) ->
                                new Pair<>(acc.first + 1, value.second ? queryToolConstructor.create(value.first) : null));

        final Observable<Pair<Integer, String>> search =
                termSubject.distinctUntilChanged().scan(new Pair<>(0, ""), (acc, value) ->
                        new Pair<>(acc.first + 1, value));

        queryEventObservable = Observable.combineLatest(scroll, bookmarks, search, queryTool,
                (s, b, t, q) -> new QueryEvent(s, b, t.first, t.second, q.first, q.second, 0, false))
                .observeOn(Schedulers.io())
                .scan((acc, val) -> {
                    // In the process of changing language
                    if (val.queryTool == null) {
                        return acc;
                    }

                    final PersistentDatabase persistentDatabase = persistentDatabaseComponent.getDatabase();

                    // Changed search term or query not yet executed
                    if (val.termId != acc.termId || acc.lastQuery == 0) {
                        final ValueHolder<Pair<Integer, Boolean>> result = new ValueHolder<>(new Pair<>(0, false));

                        persistentDatabase.runInTransaction(val.queryTool::delete);

                        persistentDatabase.runInTransaction(() -> {
                            int current = 0;
                            boolean found = false;
                            final int queryCount = val.queryTool.getCount(val.term);

                            while (current < queryCount && !found) {
                                found = val.queryTool.execute(val.term, current);

                                current++;
                            }

                            result.setValue(new Pair<>(current, found));
                        });

                        return new QueryEvent(val.scrollId, val.bookmarkId, val.termId, val.term,
                                val.queryToolId, val.queryTool, result.getValue().first, result.getValue().second);
                    } else {
                        // Changed bookmarks or language
                        if (val.bookmarkId != acc.bookmarkId || val.queryToolId != acc.queryToolId) {
                            persistentDatabase.runInTransaction(() -> {
                                val.queryTool.delete();

                                for (int i = 0; i < acc.lastQuery; i++) {
                                    val.queryTool.execute(acc.term, i);
                                }
                            });

                            return new QueryEvent(val.scrollId, val.bookmarkId, val.termId,
                                    val.term, val.queryToolId, val.queryTool, acc.lastQuery, acc.found);
                        } else if (val.scrollId != acc.scrollId) {
                            // Scrolled
                            final ValueHolder<Integer> result = new ValueHolder<>(acc.lastQuery);

                            persistentDatabase.runInTransaction(() -> {
                                int current = acc.lastQuery;
                                boolean found = false;
                                final int queryCount = val.queryTool.getCount(val.term);

                                while (current < queryCount && !found) {
                                    found = val.queryTool.execute(val.term, current);

                                    current++;
                                }

                                result.setValue(current);
                            });

                            return new QueryEvent(val.scrollId, val.bookmarkId, val.termId,
                                    val.term, val.queryToolId, val.queryTool, result.getValue(), acc.found);
                        }
                    }

                    return val;
                }).share().replay(1).autoConnect();
    }

    public void toggleBookmark(final DictionarySearchElement aEntry) {
        DictionaryBookmark dictionaryBookmark = new DictionaryBookmark();
        dictionaryBookmark.bookmark = aEntry.getBookmark() == 0 ? 1 : 0;
        dictionaryBookmark.memo = aEntry.getMemo();
        dictionaryBookmark.seq = aEntry.getSeq();

        bookmarkComponent.updateBookmark(dictionaryBookmark);
    }

    public void editMemo(final DictionarySearchElement aEntry, final String aMemo) {
        DictionaryBookmark dictionaryBookmark = new DictionaryBookmark();
        dictionaryBookmark.bookmark = aEntry.getBookmark();
        dictionaryBookmark.memo = aMemo;
        dictionaryBookmark.seq = aEntry.getSeq();

        bookmarkComponent.updateBookmark(dictionaryBookmark);
    }

    public Observable<QueryEvent> getQueryEvent() {
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
