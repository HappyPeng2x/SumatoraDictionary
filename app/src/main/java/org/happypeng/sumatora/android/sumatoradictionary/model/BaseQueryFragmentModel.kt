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
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.ObservableEmitter
import io.reactivex.rxjava3.core.ObservableOnSubscribe
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.happypeng.sumatora.android.sumatoradictionary.component.BookmarkComponent
import org.happypeng.sumatora.android.sumatoradictionary.component.BookmarkShareComponent
import org.happypeng.sumatora.android.sumatoradictionary.component.LanguageSettingsComponent
import org.happypeng.sumatora.android.sumatoradictionary.component.PersistentDatabaseComponent
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryBookmark
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchElement
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentLanguageSettings
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.DictionarySearchQueryTool
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.ValueHolder
import org.happypeng.sumatora.android.sumatoradictionary.model.intent.*
import org.happypeng.sumatora.android.sumatoradictionary.model.status.QueryStatus
import java.io.File
import java.util.*

abstract class BaseQueryFragmentModel protected constructor(private val bookmarkComponent: BookmarkComponent,
                                                            persistentDatabaseComponent: PersistentDatabaseComponent,
                                                            languageSettingsComponent: LanguageSettingsComponent,
                                                            private val bookmarkShareComponent: BookmarkShareComponent,
                                                            pagedListFactory: (PersistentDatabaseComponent, PagedList.BoundaryCallback<DictionarySearchElement?>?) ->
                                                            LiveData<PagedList<DictionarySearchElement?>>
) : BaseFragmentModel<QueryStatus>(persistentDatabaseComponent, languageSettingsComponent, pagedListFactory) {
    private val compositeDisposable: CompositeDisposable = CompositeDisposable()

    fun setTerm(t: String?) {
        sendIntent(SearchIntent(t!!))
    }

    override fun setLanguage(language: String) {
        val newLanguageSettings = PersistentLanguageSettings()
        newLanguageSettings.lang = language
        newLanguageSettings.backupLang = if (language == "eng") null else "eng"
        languageSettingsComponent.updatePersistentLanguageSettings(newLanguageSettings)
    }

    fun shareBookmarks() {
        compositeDisposable.add(Observable.defer { Observable.just(bookmarkShareComponent.writeBookmarks()) }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { outputFile: File? -> bookmarkShareComponent.shareBookmarks(outputFile) })
    }

    fun viewDestroyed() {
        sendIntent(ViewDestroyedIntent)
    }

    override fun getIntentObservablesToMerge(): MutableList<Observable<MVIIntent>> {
        val observables: MutableList<Observable<MVIIntent>> = LinkedList()
        observables.add(bookmarkComponent.bookmarkChanges.map { BookmarkIntent })
        observables.add(languageSettingsComponent.persistentLanguageSettings.cast(MVIIntent::class.java))
        return observables
    }

    override fun transformStatus(previousStatus: QueryStatus, intent: MVIIntent): Observable<QueryStatus> {
        return Observable.create(ObservableOnSubscribe { emitter: ObservableEmitter<QueryStatus> ->
            val persistentDatabase = persistentDatabaseComponent.database
            val key = previousStatus.key
            val term = if (intent is SearchIntent) intent.term else previousStatus.term
            val lastQuery = if (intent is SearchIntent) 0 else previousStatus.lastQuery
            val persistentLanguageSettings = if (intent is LanguageSettingIntent) intent.languageSettings else previousStatus.persistentLanguageSettings
            val found = intent !is SearchIntent && previousStatus.found
            val filterBookmarks = intent is FilterBookmarksIntent && intent.filter || previousStatus.filterBookmarks
            val filterMemos = intent is FilterMemosIntent && intent.filter || previousStatus.filterMemos
            val title = previousStatus.title
            val searchIconifiedByDefault = previousStatus.searchIconifiedByDefault
            val shareButtonVisible = previousStatus.shareButtonVisible
            val close = intent is CloseIntent
            val viewDestroyed = intent is ViewDestroyedIntent
            val searching = intent is SearchIntent
            val preparing = intent is LanguageSettingDetachedIntent

            if (intent is LanguageSettingDetachedIntent || intent is CloseIntent) {
                previousStatus.queryTool?.close()
            }

            val queryTool = if (intent is LanguageSettingAttachedIntent) DictionarySearchQueryTool(persistentDatabaseComponent,
                    previousStatus.key, persistentLanguageSettings) else if (intent is LanguageSettingDetachedIntent || intent is CloseIntent) null else previousStatus.queryTool

            if (intent is CloseIntent ||
                    intent is LanguageSettingDetachedIntent ||
                    intent is ViewDestroyedIntent || queryTool == null) {
                emitter.onNext(QueryStatus(key, term, lastQuery, queryTool, found, filterMemos, filterBookmarks,
                        title, searchIconifiedByDefault, shareButtonVisible, persistentLanguageSettings, close, searching, preparing, viewDestroyed))
                emitter.onComplete()
            } else {
                val currentQuery = if (intent is ScrollIntent) previousStatus.lastQuery else 0
                val clearResults = intent is BookmarkIntent || intent is LanguageSettingAttachedIntent
                val executeUntilFound = lastQuery == 0 || intent is ScrollIntent
                val resultLastQuery = ValueHolder(0)
                val resultFound = ValueHolder(false)
                val resultSearching = false

                if (intent is SearchIntent) {
                    persistentDatabase.runInTransaction { queryTool.delete() }
                    emitter.onNext(QueryStatus(key, term, lastQuery, queryTool, found, filterMemos, filterBookmarks,
                            title, searchIconifiedByDefault, shareButtonVisible, persistentLanguageSettings, close, searching, preparing, viewDestroyed))
                }

                persistentDatabase.runInTransaction {
                    var current = currentQuery
                    var currentFound = false
                    val queryCount = queryTool.getCount(term)
                    if (clearResults) {
                        queryTool.delete()
                    }

                    while (executeUntilFound && current < queryCount && !currentFound ||
                            current < lastQuery) {
                        currentFound = queryTool.execute(term, current, filterBookmarks, filterMemos)
                        current++
                    }

                    resultFound.value = if (intent is SearchIntent) currentFound else found
                    resultLastQuery.value = current
                    emitter.onNext(QueryStatus(key, term, resultLastQuery.value, queryTool, resultFound.value, filterMemos, filterBookmarks,
                            title, searchIconifiedByDefault, shareButtonVisible, persistentLanguageSettings, close, resultSearching, preparing, viewDestroyed))
                    emitter.onComplete()
                }
            }
        } as ObservableOnSubscribe<QueryStatus>).subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())
    }

    override fun onCleared() {
        setTerm("")
        compositeDisposable.dispose()
        super.onCleared()
    }

    override fun commitBookmarks(seq: Long, bookmark: Long, memo: String?) {
        val dictionaryBookmark = DictionaryBookmark()
        dictionaryBookmark.memo = memo
        dictionaryBookmark.bookmark = bookmark
        dictionaryBookmark.seq = seq
        bookmarkComponent.updateBookmark(dictionaryBookmark)
    }

    init {
        connectIntents()
    }
}