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
import org.happypeng.sumatora.android.sumatoradictionary.model.intent.CloseIntent;
import org.happypeng.sumatora.android.sumatoradictionary.model.intent.FilterBookmarksIntent;
import org.happypeng.sumatora.android.sumatoradictionary.model.intent.FilterMemosIntent;
import org.happypeng.sumatora.android.sumatoradictionary.model.intent.LanguageSettingAttachedIntent;
import org.happypeng.sumatora.android.sumatoradictionary.model.intent.LanguageSettingDetachedIntent;
import org.happypeng.sumatora.android.sumatoradictionary.model.intent.LanguageSettingIntent;
import org.happypeng.sumatora.android.sumatoradictionary.model.intent.MVIIntent;
import org.happypeng.sumatora.android.sumatoradictionary.model.intent.ScrollIntent;
import org.happypeng.sumatora.android.sumatoradictionary.model.intent.SearchIntent;
import org.happypeng.sumatora.android.sumatoradictionary.model.intent.ViewDestroyedIntent;
import org.happypeng.sumatora.android.sumatoradictionary.model.status.QueryStatus;

import java.util.LinkedList;
import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.ObservableOnSubscribe;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;

public abstract class BaseQueryFragmentModel extends BaseFragmentModel<QueryStatus>  {
    final protected BookmarkComponent bookmarkComponent;
    final protected BookmarkShareComponent bookmarkShareComponent;

    final protected CompositeDisposable compositeDisposable;

    public void setTerm(final String t) {
        sendIntent(new SearchIntent(t));
    }

    public void setFilterMemos(final boolean filter) { sendIntent(new FilterMemosIntent(true)); }
    public void setFilterBookmarks(final boolean filter) { sendIntent(new FilterBookmarksIntent(true)); }

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

    public void viewDestroyed() {
        sendIntent(new ViewDestroyedIntent());
    }

    @NonNull
    @Override
    protected List<Observable<MVIIntent>> getIntentObservablesToMerge() {
        final List<Observable<MVIIntent>> observables = new LinkedList<>();

        observables.add(bookmarkComponent.getBookmarkChanges().map(x -> new BookmarkIntent()));
        observables.add(languageSettingsComponent.getPersistentLanguageSettings().cast(MVIIntent.class));

        return observables;
    }

    @NonNull
    @Override
    public Observable<QueryStatus> transformStatus(final QueryStatus previousStatus, final MVIIntent intent) {
        return Observable.create((ObservableOnSubscribe<QueryStatus>) emitter -> {
            final PersistentDatabase persistentDatabase = persistentDatabaseComponent.getDatabase();

            final int key = previousStatus.getKey();
            final String term = intent instanceof SearchIntent ? ((SearchIntent) intent).getTerm() : previousStatus.getTerm();
            final int lastQuery = intent instanceof SearchIntent ? 0 : previousStatus.getLastQuery();
            final PersistentLanguageSettings persistentLanguageSettings = intent instanceof LanguageSettingIntent ? ((LanguageSettingIntent) intent).getLanguageSettings() : previousStatus.getPersistentLanguageSettings();
            final boolean found = !(intent instanceof SearchIntent) && previousStatus.getFound();
            final boolean filterBookmarks = (intent instanceof FilterBookmarksIntent && ((FilterBookmarksIntent) intent).getFilter()) || previousStatus.getFilterBookmarks();
            final boolean filterMemos = (intent instanceof FilterMemosIntent && ((FilterMemosIntent) intent).getFilter()) || previousStatus.getFilterMemos();
            final String title = previousStatus.getTitle();
            final boolean searchIconifiedByDefault = previousStatus.getSearchIconifiedByDefault();
            final boolean shareButtonVisible = previousStatus.getShareButtonVisible();
            final boolean close = intent instanceof CloseIntent;
            final boolean viewDestroyed = intent instanceof ViewDestroyedIntent;
            final boolean searching = intent instanceof SearchIntent;
            final boolean preparing = intent instanceof LanguageSettingDetachedIntent;

            if ((intent instanceof LanguageSettingDetachedIntent || intent instanceof CloseIntent)
                    && previousStatus.getQueryTool() != null) {
                previousStatus.getQueryTool().close();
            }

            final DictionarySearchQueryTool queryTool = intent instanceof LanguageSettingAttachedIntent ? new DictionarySearchQueryTool(persistentDatabaseComponent,
                    previousStatus.getKey(), persistentLanguageSettings) : ((intent instanceof LanguageSettingDetachedIntent || intent instanceof CloseIntent) ? null : previousStatus.getQueryTool());

            if (intent instanceof CloseIntent ||
                    intent instanceof LanguageSettingDetachedIntent ||
                    intent instanceof ViewDestroyedIntent ||
                    queryTool == null) {
                emitter.onNext(new QueryStatus(key, term, lastQuery, queryTool, found, filterMemos, filterBookmarks,
                        title, searchIconifiedByDefault, shareButtonVisible, persistentLanguageSettings, close, searching, preparing, viewDestroyed));
                emitter.onComplete();

                return;
            }

            final int currentQuery =  intent instanceof ScrollIntent ? previousStatus.getLastQuery() : 0;
            final boolean clearResults = intent instanceof BookmarkIntent || intent instanceof LanguageSettingAttachedIntent;
            final boolean executeUntilFound = lastQuery == 0 || intent instanceof ScrollIntent;

            final ValueHolder<Integer> resultLastQuery = new ValueHolder<>(0);
            final ValueHolder<Boolean> resultFound = new ValueHolder<>(false);
            final boolean resultSearching = false;

            if (intent instanceof SearchIntent) {
                persistentDatabase.runInTransaction(queryTool::delete);

                emitter.onNext(new QueryStatus(key, term, lastQuery, queryTool, found, filterMemos, filterBookmarks,
                        title, searchIconifiedByDefault, shareButtonVisible, persistentLanguageSettings, close, searching, preparing, viewDestroyed));
            }

            persistentDatabase.runInTransaction(() -> {
                int current = currentQuery;
                boolean currentFound = false;
                final int queryCount = queryTool.getCount(term);

                if (clearResults) {
                    queryTool.delete();
                }

                while ((executeUntilFound && (current < queryCount && !currentFound)) ||
                        (current < lastQuery)) {
                    currentFound = queryTool.execute(term, current, filterBookmarks, filterMemos);

                    current++;
                }

                resultFound.setValue(intent instanceof SearchIntent ? currentFound : found);
                resultLastQuery.setValue(current);

                emitter.onNext(new QueryStatus(key, term, resultLastQuery.getValue(), queryTool, resultFound.getValue(), filterMemos, filterBookmarks,
                        title, searchIconifiedByDefault, shareButtonVisible, persistentLanguageSettings, close, resultSearching, preparing, viewDestroyed));
                emitter.onComplete();
            });
        }).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread());
    }

    @Override
    protected void onCleared() {
        setTerm("");

        compositeDisposable.dispose();

        super.onCleared();
    }

    @Override
    protected void commitBookmarks(long seq, long bookmark, String memo) {
        final DictionaryBookmark dictionaryBookmark = new DictionaryBookmark();

        dictionaryBookmark.memo = memo;
        dictionaryBookmark.bookmark = bookmark;
        dictionaryBookmark.seq = seq;

        bookmarkComponent.updateBookmark(dictionaryBookmark);
    }
}
