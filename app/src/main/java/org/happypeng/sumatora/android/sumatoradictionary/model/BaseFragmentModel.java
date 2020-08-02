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
import androidx.lifecycle.Observer;
import androidx.paging.PagedList;

import org.happypeng.sumatora.android.sumatoradictionary.adapter.DictionaryPagedListAdapter;
import org.happypeng.sumatora.android.sumatoradictionary.component.LanguageSettingsComponent;
import org.happypeng.sumatora.android.sumatoradictionary.component.PersistentDatabaseComponent;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchElement;
import org.happypeng.sumatora.android.sumatoradictionary.db.InstalledDictionary;
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentLanguageSettings;
import org.happypeng.sumatora.android.sumatoradictionary.model.intent.MVIIntent;
import org.happypeng.sumatora.android.sumatoradictionary.model.intent.ScrollIntent;
import org.happypeng.sumatora.android.sumatoradictionary.viewholder.DictionarySearchElementViewHolder;

import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.observers.DisposableSingleObserver;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subjects.Subject;

public abstract class BaseFragmentModel<I, S, V> extends MVIViewModel<I, S, V> {
    final protected PersistentDatabaseComponent persistentDatabaseComponent;
    final protected LanguageSettingsComponent languageSettingsComponent;

    final protected Subject<MVIIntent> intentSubject = PublishSubject.create();

    final private Subject<DictionaryPagedListAdapter> pagedListAdapterSubject = BehaviorSubject.create();

    private Observer<PagedList<DictionarySearchElement>> pagedListObserver;
    private LiveData<PagedList<DictionarySearchElement>> pagedList;

    public void setLanguage(final @NonNull String language) {
        final PersistentLanguageSettings newLanguageSettings = new PersistentLanguageSettings();
        newLanguageSettings.lang = language;
        newLanguageSettings.backupLang = language.equals("eng") ? null : "eng";

        languageSettingsComponent.updatePersistentLanguageSettings(newLanguageSettings);
    }

    public Observable<DictionaryPagedListAdapter> getPagedListAdapter() {
        return pagedListAdapterSubject;
    }

    public Observable<List<InstalledDictionary>> getInstalledDictionaries() {
        return Observable.defer(() ->
                Observable.just(persistentDatabaseComponent.getDatabase().installedDictionaryDao().getAll()))
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }

    protected abstract LiveData<PagedList<DictionarySearchElement>> getPagedList(PagedList.BoundaryCallback<DictionarySearchElement> boundaryCallback);

    protected boolean disableBookmarkButton() { return false; }
    protected boolean disableMemoEdit() { return false; }

    protected BaseFragmentModel(final PersistentDatabaseComponent persistentDatabaseComponent,
                                final LanguageSettingsComponent languageSettingsComponent) {
        this.persistentDatabaseComponent = persistentDatabaseComponent;
        this.languageSettingsComponent = languageSettingsComponent;


        compositeDisposable.add(Single.fromCallable(persistentDatabaseComponent::getEntities).map(entities -> new DictionaryPagedListAdapter(new DictionarySearchElementViewHolder.Status(entities),
                disableBookmarkButton(), disableMemoEdit())).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
                .subscribeWith(new DisposableSingleObserver<DictionaryPagedListAdapter>() {
                    @Override
                    public void onSuccess(@io.reactivex.rxjava3.annotations.NonNull DictionaryPagedListAdapter dictionaryPagedListAdapter) {
                        pagedList = getPagedList(new PagedList.BoundaryCallback<DictionarySearchElement>() {
                            @Override
                            public void onItemAtEndLoaded(@NonNull DictionarySearchElement itemAtEnd) {
                                super.onItemAtEndLoaded(itemAtEnd);

                                intentSubject.onNext(new ScrollIntent());
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
    }

    @Override
    protected void onCleared() {
        if (pagedList != null && pagedListObserver != null) {
            pagedList.removeObserver(pagedListObserver);
        }

        compositeDisposable.dispose();

        super.onCleared();
    }
}
