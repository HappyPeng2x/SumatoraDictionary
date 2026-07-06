/* Sumatora Dictionary
        Copyright (C) 2026 Nicolas Centa

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

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.paging.PagedList
import dagger.hilt.android.lifecycle.HiltViewModel
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.BehaviorSubject
import io.reactivex.rxjava3.subjects.PublishSubject
import io.reactivex.rxjava3.subjects.Subject
import org.happypeng.sumatora.android.sumatoradictionary.component.BookmarkImportComponent
import org.happypeng.sumatora.android.sumatoradictionary.component.BookmarkShareComponent
import org.happypeng.sumatora.android.sumatoradictionary.component.LanguageSettingsComponent
import org.happypeng.sumatora.android.sumatoradictionary.component.PersistentDatabaseComponent
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.schedulers.Schedulers
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchElement
import org.happypeng.sumatora.android.sumatoradictionary.db.EntryListSummary
import org.happypeng.sumatora.android.sumatoradictionary.db.InstalledDictionary
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentLanguageSettings
import org.happypeng.sumatora.core.dict.DictionaryQueryResult
import org.happypeng.sumatora.android.sumatoradictionary.model.intent.ImportCancelIntent
import org.happypeng.sumatora.android.sumatoradictionary.model.intent.ImportCloseIntent
import org.happypeng.sumatora.android.sumatoradictionary.model.intent.ImportCommitIntent
import org.happypeng.sumatora.android.sumatoradictionary.model.intent.ImportFileIntent
import org.happypeng.sumatora.android.sumatoradictionary.model.intent.ImportIntent
import org.happypeng.sumatora.android.sumatoradictionary.model.intent.ImportLanguageSettingAttachedIntent
import org.happypeng.sumatora.android.sumatoradictionary.model.intent.ImportLanguageSettingDetachedIntent
import org.happypeng.sumatora.android.sumatoradictionary.model.intent.LanguageSettingAttachedIntent
import org.happypeng.sumatora.android.sumatoradictionary.model.intent.LanguageSettingDetachedIntent
import org.happypeng.sumatora.android.sumatoradictionary.model.processor.ImportActionProcessorHolder
import org.happypeng.sumatora.android.sumatoradictionary.model.result.ImportResult
import org.happypeng.sumatora.android.sumatoradictionary.model.state.ImportState
import org.happypeng.sumatora.android.sumatoradictionary.model.transformer.ImportIntentTransformer
import org.happypeng.sumatora.android.sumatoradictionary.mvibase.MviViewModel
import org.happypeng.sumatora.android.sumatoradictionary.operator.LiveDataWrapper
import javax.inject.Inject

@HiltViewModel
class BookmarkImportModel @Inject constructor(
    private val bookmarkImportComponent: BookmarkImportComponent,
    private val persistentDatabaseComponent: PersistentDatabaseComponent,
    private val languageSettingsComponent: LanguageSettingsComponent,
    bookmarkShareComponent: BookmarkShareComponent,
    savedStateHandle: SavedStateHandle?
) : ViewModel(), MviViewModel<ImportIntent, ImportState> {

    companion object {
        const val KEY = 3
    }

    val disableBookmarkButton: Boolean = false
    val disableMemoEdit: Boolean = true
    val disableTagEdit: Boolean = true

    val installedDictionaries: Observable<List<InstalledDictionary>>
        get() = Observable.defer {
            Observable.just(persistentDatabaseComponent.database.installedDictionaryDao().all)
        }.subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())

    val listSummaryFun: (DictionaryQueryResult) -> EntryListSummary = { entry ->
        val settings = persistentDatabaseComponent.database.persistentLanguageSettingsDao()
            .getLanguageSettingsDirect(0)
            ?: PersistentLanguageSettings().also { it.lang = PersistentLanguageSettings.LANG_DEFAULT }
        persistentDatabaseComponent.fetchListSummary(entry, settings)
    }

    fun setLanguage(language: String) {
        val settings = PersistentLanguageSettings()
        settings.lang = language
        settings.backupLang = if (language == "eng") null else "eng"
        languageSettingsComponent.updatePersistentLanguageSettings(settings)
    }

    private val clearedSubject: Subject<Unit> = PublishSubject.create()
    val clearedObservable: Observable<Unit> = clearedSubject

    private val pagedListSubject: Subject<PagedList<DictionarySearchElement>> = BehaviorSubject.create()
    val pagedListObservable: Observable<PagedList<DictionarySearchElement>> = pagedListSubject

    private val intentsSubject: PublishSubject<ImportIntent> = PublishSubject.create()
    private val statesObservable: Observable<ImportState> = compose()
    private val closedObservable = statesObservable.filter { it.closed }.map { Unit }

    private fun compose(): Observable<ImportState> {
        val actionProcessorHolder = ImportActionProcessorHolder(persistentDatabaseComponent, bookmarkImportComponent, KEY)
        return intentsSubject
            .compose(ImportIntentTransformer())
            .compose(actionProcessorHolder.actionProcessor)
            .scan(ImportState(false, null, closed = false, processing = false), this::transformStatus)
            .distinctUntilChanged()
            .replay(1)
            .autoConnect(0)
    }

    private fun transformStatus(previousState: ImportState, result: ImportResult): ImportState =
        ImportState(result.executed, result.persistentLanguageSettings, result.close, result.processing)

    override fun processIntents(intents: Observable<ImportIntent>) {
        intents.takeUntil(closedObservable).subscribe(intentsSubject::onNext)
    }

    override fun states(): Observable<ImportState> = statesObservable

    fun bookmarkImportFileOpen(uri: Uri) = processIntents(Observable.just(ImportFileIntent(uri)))
    fun bookmarkImportCommit() = processIntents(Observable.just(ImportCommitIntent))
    fun bookmarkImportCancel() = processIntents(Observable.just(ImportCancelIntent))

    override fun onCleared() {
        clearedSubject.onNext(Unit)
        clearedSubject.onComplete()
        super.onCleared()
    }

    init {
        processIntents(languageSettingsComponent.persistentLanguageSettings.map {
            when (it) {
                is LanguageSettingDetachedIntent -> ImportLanguageSettingDetachedIntent
                is LanguageSettingAttachedIntent -> ImportLanguageSettingAttachedIntent(it.languageSettings)
            }
        })
        processIntents(clearedObservable.map { ImportCloseIntent })

        val pagedList = persistentDatabaseComponent.getSearchElements(KEY, object : PagedList.BoundaryCallback<DictionarySearchElement>() {})
        LiveDataWrapper.wrap(pagedList, clearedSubject).subscribe(pagedListSubject)
    }
}
