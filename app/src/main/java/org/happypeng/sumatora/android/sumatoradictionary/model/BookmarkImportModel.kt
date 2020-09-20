package org.happypeng.sumatora.android.sumatoradictionary.model

import android.net.Uri
import androidx.hilt.Assisted
import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.SavedStateHandle
import androidx.paging.PagedList
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.subjects.PublishSubject
import org.happypeng.sumatora.android.sumatoradictionary.component.*
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchElement
import org.happypeng.sumatora.android.sumatoradictionary.model.intent.*
import org.happypeng.sumatora.android.sumatoradictionary.model.processor.ImportActionProcessorHolder
import org.happypeng.sumatora.android.sumatoradictionary.model.processor.QueryActionProcessorHolder
import org.happypeng.sumatora.android.sumatoradictionary.model.result.ImportResult
import org.happypeng.sumatora.android.sumatoradictionary.model.result.QueryResult
import org.happypeng.sumatora.android.sumatoradictionary.model.state.ImportState
import org.happypeng.sumatora.android.sumatoradictionary.model.state.QueryState
import org.happypeng.sumatora.android.sumatoradictionary.mvibase.MviViewModel
import org.happypeng.sumatora.android.sumatoradictionary.transformer.ImportIntentTransformer
import org.happypeng.sumatora.android.sumatoradictionary.transformer.QueryIntentTransformer

class BookmarkImportModel @ViewModelInject constructor(bookmarkImportComponent: BookmarkImportComponent,
                                                       persistentDatabaseComponent: PersistentDatabaseComponent,
                                                       languageSettingsComponent: LanguageSettingsComponent,
                                                       bookmarkShareComponent: BookmarkShareComponent,
                                                       @Assisted savedStateHandle: SavedStateHandle?) :
        BaseFragmentModel(persistentDatabaseComponent, languageSettingsComponent,
                { component: PersistentDatabaseComponent, callback: PagedList.BoundaryCallback<DictionarySearchElement?>? ->
                    component.getSearchElements(KEY, callback) }, true, true), MviViewModel<ImportIntent, ImportState> {
    companion object {
        const val KEY = 3
    }

    private val intentsSubject: PublishSubject<ImportIntent> = PublishSubject.create()
    private val statesObservable: Observable<ImportState> = compose()
    private val disposables: CompositeDisposable = CompositeDisposable()

    private val actionProcessorHolder = ImportActionProcessorHolder(persistentDatabaseComponent, bookmarkImportComponent, KEY)

    private fun compose(): Observable<ImportState> {
        return intentsSubject
                .compose(ImportIntentTransformer())
                .compose(actionProcessorHolder.actionProcessor)
                .scan(ImportState(false, null, closed = false, processing = false),
                        this::transformStatus)
                .distinctUntilChanged()
                .replay(1)
                .autoConnect(0)
    }

    private fun transformStatus(previousState: ImportState, result: ImportResult): ImportState {
        return ImportState(result.executed, result.persistentLanguageSettings, result.close, result.processing)
    }

    override fun commitBookmarks(seq: Long, bookmark: Long, memo: String?) {
        TODO("Not yet implemented")
    }

    override fun processIntents(intents: Observable<ImportIntent>) {
        disposables.add(intents.subscribe(intentsSubject::onNext))
    }

    override fun states(): Observable<ImportState> = statesObservable

    override fun onCleared() {
        disposables.dispose()
        super.onCleared()
    }

    fun bookmarkImportFileOpen(uri: Uri) {
        processIntents(Observable.just(ImportFileIntent(uri)))
    }

    fun bookmarkImportCommit() {
        processIntents(Observable.just(ImportCommitIntent))
    }

    fun bookmarkImportCancel() {
        processIntents(Observable.just(ImportCancelIntent))
    }

    init {
        processIntents(languageSettingsComponent.persistentLanguageSettings.map {
            when (it) {
                is LanguageSettingDetachedIntent -> ImportLanguageSettingDetachedIntent
                is LanguageSettingAttachedIntent -> ImportLanguageSettingAttachedIntent(it.languageSettings)
            }
        })
    }
}

