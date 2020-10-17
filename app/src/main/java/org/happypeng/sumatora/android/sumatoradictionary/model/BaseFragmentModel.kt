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
import androidx.lifecycle.ViewModel
import androidx.paging.PagedList
import androidx.paging.PagedList.BoundaryCallback
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.BehaviorSubject
import io.reactivex.rxjava3.subjects.PublishSubject
import io.reactivex.rxjava3.subjects.Subject
import org.happypeng.sumatora.android.sumatoradictionary.adapter.DictionaryPagedListAdapter
import org.happypeng.sumatora.android.sumatoradictionary.component.LanguageSettingsComponent
import org.happypeng.sumatora.android.sumatoradictionary.component.PersistentDatabaseComponent
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchElement
import org.happypeng.sumatora.android.sumatoradictionary.db.InstalledDictionary
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentLanguageSettings
import org.happypeng.sumatora.android.sumatoradictionary.model.intent.ScrollIntent
import org.happypeng.sumatora.android.sumatoradictionary.operator.LiveDataWrapper

abstract class BaseFragmentModel protected constructor(protected val persistentDatabaseComponent: PersistentDatabaseComponent,
                                                       protected val languageSettingsComponent: LanguageSettingsComponent,
                                                       pagedListFactory: (PersistentDatabaseComponent, BoundaryCallback<DictionarySearchElement?>?) ->
                                                       LiveData<PagedList<DictionarySearchElement?>>,
                                                       val disableBookmarkButton: Boolean,
                                                       val disableMemoEdit: Boolean): ViewModel() {
    class ScrolledEvent(val entryOrder: Int)

    private val clearedSubject: Subject<Unit> = PublishSubject.create()
    val clearedObservable = clearedSubject as Observable<Unit>

    private val pagedListSubject: Subject<PagedList<DictionarySearchElement?>> = BehaviorSubject.create()
    val pagedListObservable = pagedListSubject as Observable<PagedList<DictionarySearchElement?>>

    private val scrollSubject: Subject<ScrolledEvent> = PublishSubject.create()
    val scrollObservable = scrollSubject as Observable<ScrolledEvent>

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

    override fun onCleared() {
        clearedSubject.onNext(Unit)
        clearedSubject.onComplete()

        super.onCleared()
    }

    init {
        val pagedList = pagedListFactory.invoke(persistentDatabaseComponent, object: BoundaryCallback<DictionarySearchElement?>() {
            override fun onItemAtEndLoaded(itemAtEnd: DictionarySearchElement) {
                super.onItemAtEndLoaded(itemAtEnd)
                scrollSubject.onNext(ScrolledEvent(itemAtEnd.entryOrder))
            }
        })

        LiveDataWrapper.wrap(pagedList, clearedSubject).subscribe(pagedListSubject)
    }
}