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
import androidx.lifecycle.LiveData;
import androidx.paging.PagedList;

import org.happypeng.sumatora.android.sumatoradictionary.adapter.DictionaryPagedListAdapter;
import org.happypeng.sumatora.android.sumatoradictionary.component.LanguageSettingsComponent;
import org.happypeng.sumatora.android.sumatoradictionary.component.PersistentDatabaseComponent;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchElement;
import org.happypeng.sumatora.android.sumatoradictionary.db.InstalledDictionary;
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentLanguageSettings;
import org.happypeng.sumatora.android.sumatoradictionary.model.intent.ScrollIntent;
import org.happypeng.sumatora.android.sumatoradictionary.model.status.MVIStatus;
import org.happypeng.sumatora.android.sumatoradictionary.model.status.QueryStatus;
import org.happypeng.sumatora.android.sumatoradictionary.operator.LiveDataWrapper;
import org.happypeng.sumatora.android.sumatoradictionary.viewholder.DictionarySearchElementViewHolder;

import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import io.reactivex.rxjava3.subjects.Subject;

public abstract class BaseFragmentModel<S extends MVIStatus> extends MVIViewModel<S> {
    final protected PersistentDatabaseComponent persistentDatabaseComponent;
    final protected LanguageSettingsComponent languageSettingsComponent;

    final private Observable<DictionaryPagedListAdapter> pagedListAdapterObservable;

    public void setLanguage(final @NonNull String language) {
        final PersistentLanguageSettings newLanguageSettings = new PersistentLanguageSettings();
        newLanguageSettings.lang = language;
        newLanguageSettings.backupLang = language.equals("eng") ? null : "eng";

        languageSettingsComponent.updatePersistentLanguageSettings(newLanguageSettings);
    }

    public Observable<DictionaryPagedListAdapter> getPagedListAdapter() {
        return pagedListAdapterObservable;
    }

    public Observable<List<InstalledDictionary>> getInstalledDictionaries() {
        return Observable.defer(() ->
                Observable.just(persistentDatabaseComponent.getDatabase().installedDictionaryDao().getAll()))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }

    protected abstract LiveData<PagedList<DictionarySearchElement>> getPagedList(PagedList.BoundaryCallback<DictionarySearchElement> boundaryCallback);

    protected abstract void commitBookmarks(long seq, long bookmark, String memo);

    protected boolean disableBookmarkButton() { return false; }
    protected boolean disableMemoEdit() { return false; }

    protected BaseFragmentModel(final PersistentDatabaseComponent persistentDatabaseComponent,
                                final LanguageSettingsComponent languageSettingsComponent) {
        this.persistentDatabaseComponent = persistentDatabaseComponent;
        this.languageSettingsComponent = languageSettingsComponent;

        pagedListAdapterObservable =
                Observable.fromCallable(persistentDatabaseComponent::getEntities)
                        .subscribeOn(Schedulers.io())
                        .map(entities -> new DictionaryPagedListAdapter(entities,
                                disableBookmarkButton(), disableMemoEdit(),
                                this::commitBookmarks))
                        .observeOn(AndroidSchedulers.mainThread())
                        .share()
                        .takeUntil(getStatusObservable().filter(MVIStatus::getClosed))
                        .replay(1).autoConnect();

        final LiveData<PagedList<DictionarySearchElement>> pagedList =
                getPagedList(new PagedList.BoundaryCallback<DictionarySearchElement>() {
                    @Override
                    public void onItemAtEndLoaded(@NonNull DictionarySearchElement itemAtEnd) {
                        super.onItemAtEndLoaded(itemAtEnd);

                        sendIntent(new ScrollIntent());
                    }
                });

        final Observable<PagedList<DictionarySearchElement>> pagedListObservable =
                LiveDataWrapper.wrap(pagedList, getStatusObservable().filter(MVIStatus::getClosed));

        pagedListObservable.withLatestFrom(pagedListAdapterObservable,
                (list, adapter) -> {
                    adapter.submitList(list);

                    return true;
                }).subscribe();
    }

    @Override
    protected void onCleared() {
        super.onCleared();
    }
}
