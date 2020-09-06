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
package org.happypeng.sumatora.android.sumatoradictionary.model

import androidx.lifecycle.LiveData
import androidx.paging.PagedList
import androidx.paging.PagedList.BoundaryCallback
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.happypeng.sumatora.android.sumatoradictionary.adapter.DictionaryPagedListAdapter
import org.happypeng.sumatora.android.sumatoradictionary.component.LanguageSettingsComponent
import org.happypeng.sumatora.android.sumatoradictionary.component.PersistentDatabaseComponent
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchElement
import org.happypeng.sumatora.android.sumatoradictionary.db.InstalledDictionary
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentLanguageSettings
import org.happypeng.sumatora.android.sumatoradictionary.model.intent.ScrollIntent
import org.happypeng.sumatora.android.sumatoradictionary.model.status.MVIStatus
import org.happypeng.sumatora.android.sumatoradictionary.operator.LiveDataWrapper
import java.util.*

abstract class BaseFragmentModel<S : MVIStatus> protected constructor(protected val persistentDatabaseComponent: PersistentDatabaseComponent,
                                                                       protected val languageSettingsComponent: LanguageSettingsComponent,
                                                                       pagedListFactory: (PersistentDatabaseComponent, BoundaryCallback<DictionarySearchElement?>?) ->
                                                                       LiveData<PagedList<DictionarySearchElement?>>) : MVIViewModel<S>() {
    val pagedListAdapter: Observable<DictionaryPagedListAdapter>

    open fun setLanguage(language: String) {
        val newLanguageSettings = PersistentLanguageSettings()
        newLanguageSettings.lang = language
        newLanguageSettings.backupLang = if (language == "eng") null else "eng"
        languageSettingsComponent.updatePersistentLanguageSettings(newLanguageSettings)
    }

    val installedDictionaries: Observable<List<InstalledDictionary?>?>
        get() = Observable.defer { Observable.just(persistentDatabaseComponent.database.installedDictionaryDao().all) }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())

    protected abstract fun commitBookmarks(seq: Long, bookmark: Long, memo: String?)

    protected open fun disableBookmarkButton(): Boolean {
        return false
    }

    protected open fun disableMemoEdit(): Boolean {
        return false
    }

    init {
        pagedListAdapter = Observable.fromCallable { persistentDatabaseComponent.entities }
                .subscribeOn(Schedulers.io())
                .map { entities: HashMap<String, String>? ->
                    DictionaryPagedListAdapter(entities!!,
                            disableBookmarkButton(), disableMemoEdit()) { seq: Long, bookmark: Long, memo: String? -> commitBookmarks(seq, bookmark, memo) }
                }
                .observeOn(AndroidSchedulers.mainThread())
                .share()
                .takeUntil(statusObservable.filter { it != null && it.closed })
                .replay(1).autoConnect()

        val pagedList = pagedListFactory.invoke(persistentDatabaseComponent, object : BoundaryCallback<DictionarySearchElement?>() {
            override fun onItemAtEndLoaded(itemAtEnd: DictionarySearchElement) {
                super.onItemAtEndLoaded(itemAtEnd)
                sendIntent(ScrollIntent)
            }
        })

        val pagedListObservable: Observable<PagedList<DictionarySearchElement?>> = LiveDataWrapper.wrap(pagedList, statusObservable.filter { it != null && it.closed })
        pagedListObservable.withLatestFrom(pagedListAdapter,
                { list: PagedList<DictionarySearchElement?>, adapter: DictionaryPagedListAdapter ->
                    adapter.submitList(list)
                    true
                }).subscribe()
    }
}