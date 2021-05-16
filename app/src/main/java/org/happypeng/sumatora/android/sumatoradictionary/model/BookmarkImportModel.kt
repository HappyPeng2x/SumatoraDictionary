package org.happypeng.sumatora.android.sumatoradictionary.model

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.paging.PagedList
import dagger.hilt.android.lifecycle.HiltViewModel
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.PublishSubject
import org.happypeng.sumatora.android.sumatoradictionary.component.BookmarkImportComponent
import org.happypeng.sumatora.android.sumatoradictionary.component.BookmarkShareComponent
import org.happypeng.sumatora.android.sumatoradictionary.component.LanguageSettingsComponent
import org.happypeng.sumatora.android.sumatoradictionary.component.PersistentDatabaseComponent
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchElement
import org.happypeng.sumatora.android.sumatoradictionary.model.intent.*
import org.happypeng.sumatora.android.sumatoradictionary.model.processor.ImportActionProcessorHolder
import org.happypeng.sumatora.android.sumatoradictionary.model.result.ImportResult
import org.happypeng.sumatora.android.sumatoradictionary.model.state.ImportState
import org.happypeng.sumatora.android.sumatoradictionary.model.transformer.ImportIntentTransformer
import org.happypeng.sumatora.android.sumatoradictionary.mvibase.MviViewModel
import javax.inject.Inject

@HiltViewModel
class BookmarkImportModel @Inject constructor(private val bookmarkImportComponent: BookmarkImportComponent,
                                              persistentDatabaseComponent: PersistentDatabaseComponent,
                                              languageSettingsComponent: LanguageSettingsComponent,
                                              bookmarkShareComponent: BookmarkShareComponent,
                                              savedStateHandle: SavedStateHandle?) :
    BaseFragmentModel(persistentDatabaseComponent, languageSettingsComponent,
        { component: PersistentDatabaseComponent, callback: PagedList.BoundaryCallback<DictionarySearchElement?>? ->
            component.getSearchElements(KEY, callback) }, false, true), MviViewModel<ImportIntent, ImportState> {
    companion object {
        const val KEY = 3
    }

    private val intentsSubject: PublishSubject<ImportIntent> = PublishSubject.create()
    private val statesObservable: Observable<ImportState> = compose()
    private val closedObservable = statesObservable.filter { it.closed }.map { Unit }

    private fun compose(): Observable<ImportState> {
        val actionProcessorHolder = ImportActionProcessorHolder(persistentDatabaseComponent, bookmarkImportComponent, KEY)

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

    override fun processIntents(intents: Observable<ImportIntent>) {
        intents.takeUntil(closedObservable).subscribe(intentsSubject::onNext)
    }

    override fun states(): Observable<ImportState> = statesObservable

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
        processIntents(clearedObservable.map { ImportCloseIntent })
    }
}

