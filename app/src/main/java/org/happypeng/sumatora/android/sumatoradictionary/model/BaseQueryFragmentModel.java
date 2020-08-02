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

import androidx.annotation.NonNull;
import androidx.core.util.Pair;
import androidx.lifecycle.SavedStateHandle;

import org.happypeng.sumatora.android.sumatoradictionary.component.BookmarkComponent;
import org.happypeng.sumatora.android.sumatoradictionary.component.BookmarkShareComponent;
import org.happypeng.sumatora.android.sumatoradictionary.component.LanguageSettingsComponent;
import org.happypeng.sumatora.android.sumatoradictionary.component.PersistentDatabaseComponent;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryBookmark;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchElement;
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentDatabase;
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentLanguageSettings;
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.DictionarySearchQueryTool;
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.ValueHolder;
import org.happypeng.sumatora.android.sumatoradictionary.model.intent.BookmarkIntent;
import org.happypeng.sumatora.android.sumatoradictionary.model.intent.FilterBookmarksIntent;
import org.happypeng.sumatora.android.sumatoradictionary.model.intent.FilterMemosIntent;
import org.happypeng.sumatora.android.sumatoradictionary.model.intent.LanguageSettingIntent;
import org.happypeng.sumatora.android.sumatoradictionary.model.intent.MVIIntent;
import org.happypeng.sumatora.android.sumatoradictionary.model.intent.ScrollIntent;
import org.happypeng.sumatora.android.sumatoradictionary.model.intent.SearchIntent;
import org.happypeng.sumatora.android.sumatoradictionary.model.status.QueryStatus;
import org.happypeng.sumatora.android.sumatoradictionary.model.status.QueryViewStatus;

import java.util.LinkedList;
import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public abstract class BaseQueryFragmentModel extends BaseFragmentModel<MVIIntent, QueryStatus, QueryViewStatus>  {
    final protected BookmarkComponent bookmarkComponent;
    final protected BookmarkShareComponent bookmarkShareComponent;

    final protected CompositeDisposable compositeDisposable;

    public void setTerm(final String t) {
        intentSubject.onNext(new SearchIntent(t));
    }

    public void setFilterMemos(final boolean filter) { intentSubject.onNext(new FilterMemosIntent(true)); }
    public void setFilterBookmarks(final boolean filter) { intentSubject.onNext(new FilterBookmarksIntent(true)); }

    public void setLanguage(final @NonNull String language) {
        final PersistentLanguageSettings newLanguageSettings = new PersistentLanguageSettings();
        newLanguageSettings.lang = language;
        newLanguageSettings.backupLang = language.equals("eng") ? null : "eng";

        languageSettingsComponent.updatePersistentLanguageSettings(newLanguageSettings);
    }


    protected BaseQueryFragmentModel(final BookmarkComponent bookmarkComponent,
                                     final PersistentDatabaseComponent persistentDatabaseComponent,
                                     final LanguageSettingsComponent languageSettingsComponent,
                                     final BookmarkShareComponent bookmarkShareComponent,
                                     SavedStateHandle savedStateHandle) {
        super(persistentDatabaseComponent, languageSettingsComponent);

        this.bookmarkComponent = bookmarkComponent;
        this.bookmarkShareComponent = bookmarkShareComponent;

        connectIntents();

        this.compositeDisposable = new CompositeDisposable();
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

    public void shareBookmarks() {
        compositeDisposable.add(Observable.defer(() -> Observable.just(bookmarkShareComponent.writeBookmarks()))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(bookmarkShareComponent::shareBookmarks));
    }

    @NonNull
    @Override
    protected List<Observable<MVIIntent>> getIntentObservablesToMerge() {
        final List<Observable<MVIIntent>> observables = new LinkedList<>();

        observables.add(intentSubject);
        observables.add(bookmarkComponent.getBookmarkChanges().map(x -> new BookmarkIntent()));

        observables.add(languageSettingsComponent.getPersistentLanguageSettings()
                .observeOn(AndroidSchedulers.mainThread())
                .map(p -> new LanguageSettingIntent(p.first, p.second)));

        return observables;
    }

    @NonNull
    @Override
    public QueryStatus transformStatus(final QueryStatus previousStatus, final MVIIntent intent) {
        final PersistentLanguageSettings persistentLanguageSettings = intent instanceof LanguageSettingIntent ? ((LanguageSettingIntent) intent).getLanguageSettings() : previousStatus.getPersistentLanguageSettings();
        final DictionarySearchQueryTool queryTool = intent instanceof LanguageSettingIntent ? (((LanguageSettingIntent) intent).getAttached() ? new DictionarySearchQueryTool(persistentDatabaseComponent,
                previousStatus.getKey(), persistentLanguageSettings) : null) : previousStatus.getQueryTool();

        if (intent instanceof LanguageSettingIntent && previousStatus.getQueryTool() != null) {
            previousStatus.getQueryTool().close();
        }

        final String searchTerm = intent instanceof SearchIntent ? ((SearchIntent) intent).getTerm() : previousStatus.getTerm();
        final int lastQuery = intent instanceof SearchIntent ? 0 : previousStatus.getLastQuery();
        final boolean foundInitially = intent instanceof ScrollIntent && previousStatus.getFound();
        final boolean filterBookmarks = (intent instanceof FilterBookmarksIntent && ((FilterBookmarksIntent) intent).getFilter()) || previousStatus.getFilterBookmarks();
        final boolean filterMemos = (intent instanceof FilterMemosIntent && ((FilterMemosIntent) intent).getFilter()) || previousStatus.getFilterMemos();

        if (queryTool == null) {
            updateView(new QueryViewStatus(searchTerm, false, true, true, filterMemos, filterBookmarks,
                    previousStatus.getTitle(), previousStatus.getSearchIconifiedByDefault(),
                    previousStatus.getShareButtonVisible(),
                    persistentLanguageSettings));

            return new QueryStatus(getInitialStatus().getKey(), searchTerm, lastQuery, queryTool, foundInitially, filterMemos, filterBookmarks,
                    previousStatus.getTitle(), previousStatus.getSearchIconifiedByDefault(),
                    previousStatus.getShareButtonVisible(),
                    persistentLanguageSettings);
        }

        final ValueHolder<Pair<Integer, Boolean>> result = new ValueHolder<>(new Pair<>(0, foundInitially));
        final PersistentDatabase persistentDatabase = persistentDatabaseComponent.getDatabase();

        if (intent instanceof SearchIntent) {
            persistentDatabase.runInTransaction(queryTool::delete);

            updateView(new QueryViewStatus(searchTerm, false, false, true, filterMemos, filterBookmarks,
                    previousStatus.getTitle(), previousStatus.getSearchIconifiedByDefault(),
                    previousStatus.getShareButtonVisible(),
                    persistentLanguageSettings));
        }

        persistentDatabase.runInTransaction(() -> {
            int current = intent instanceof ScrollIntent ? previousStatus.getLastQuery() : 0;
            boolean found = false;
            final int queryCount = queryTool.getCount(searchTerm);

            if (intent instanceof BookmarkIntent ||
                    intent instanceof LanguageSettingIntent) {
                queryTool.delete();
            }

            while (((lastQuery == 0|| intent instanceof ScrollIntent) && (current < queryCount && !found)) ||
                    (current < lastQuery)) {
                found = queryTool.execute(searchTerm, current, filterBookmarks, filterMemos);

                current++;
            }

            result.setValue(new Pair<>(current, found));
        });

        final boolean found = intent instanceof ScrollIntent ? previousStatus.getFound() : result.getValue().second;

        updateView(new QueryViewStatus(searchTerm, found, false, false, filterMemos, filterBookmarks, previousStatus.getTitle(), previousStatus.getSearchIconifiedByDefault(),
                previousStatus.getShareButtonVisible(),
                persistentLanguageSettings));

        return new QueryStatus(previousStatus.getKey(),
                searchTerm,
                result.getValue().first,
                queryTool,
                found,
                filterMemos,
                filterBookmarks,
                previousStatus.getTitle(),
                previousStatus.getSearchIconifiedByDefault(),
                previousStatus.getShareButtonVisible(),
                persistentLanguageSettings);
    }

    @Override
    protected void onCleared() {
        setTerm("");

        compositeDisposable.dispose();

        super.onCleared();
    }
}
