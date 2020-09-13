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
                                                            LiveData<PagedList<DictionarySearchElement?>>,
                                                            initialStatus: QueryStatus
) : BaseFragmentModel<QueryStatus>(persistentDatabaseComponent, languageSettingsComponent, pagedListFactory, initialStatus) {
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

    override fun getIntentObservablesToMerge(): MutableList<Observable<BaseQueryIntent>> {
        val observables: MutableList<Observable<BaseQueryIntent>> = LinkedList()
        observables.add(bookmarkComponent.bookmarkChanges.map { BookmarkIntent })
        observables.add(languageSettingsComponent.persistentLanguageSettings.cast(BaseQueryIntent::class.java))
        return observables
    }

    override fun transformStatus(previousStatus: QueryStatus, intent: BaseQueryIntent): Observable<QueryStatus> {
        return Observable.create(ObservableOnSubscribe { emitter: ObservableEmitter<QueryStatus> ->
            val newStatus = QueryStatus(
                    key = previousStatus.key,
                    term = when(intent) {
                        is SearchIntent -> intent.term
                        else -> previousStatus.term
                    },
                    lastQuery = when(intent) {
                        is SearchIntent -> 0
                        else -> previousStatus.lastQuery
                    },
                    persistentLanguageSettings = when(intent) {
                        is LanguageSettingIntent -> intent.languageSettings
                        else -> previousStatus.persistentLanguageSettings
                    },
                    found = when(intent) {
                        is SearchIntent -> false
                        else -> previousStatus.found
                    },
                    filterBookmarks = when(intent) {
                        is FilterBookmarksIntent -> !previousStatus.filterBookmarks
                        else -> previousStatus.filterBookmarks
                    },
                    filterMemos = when(intent) {
                        is FilterMemosIntent -> !previousStatus.filterMemos
                        else -> previousStatus.filterMemos
                    },
                    title = previousStatus.title,
                    searchIconifiedByDefault = previousStatus.searchIconifiedByDefault,
                    shareButtonVisible = previousStatus.shareButtonVisible,
                    closed = intent is CloseIntent,
                    viewDestroyed = intent is ViewDestroyedIntent,
                    searching = intent is SearchIntent,
                    preparing = intent is LanguageSettingDetachedIntent,
                    queryTool = when(intent) {
                        is LanguageSettingDetachedIntent -> {
                            previousStatus.queryTool?.close()
                            null
                        }
                        is LanguageSettingAttachedIntent ->
                            DictionarySearchQueryTool(persistentDatabaseComponent,
                                    previousStatus.key, intent.languageSettings)
                        else -> previousStatus.queryTool
                    }
            )

            // Not ready or closing
            if (intent is CloseIntent ||
                    intent is LanguageSettingDetachedIntent ||
                    intent is ViewDestroyedIntent || newStatus.queryTool == null) {
                emitter.onNext(newStatus)
                emitter.onComplete()
            } else {
                val currentQuery = if (intent is ScrollIntent) previousStatus.lastQuery else 0
                val clearResults = intent is BookmarkIntent || intent is LanguageSettingAttachedIntent
                val executeUntilFound = newStatus.lastQuery == 0 || intent is ScrollIntent

                if (intent is SearchIntent) {
                    persistentDatabaseComponent.database.runInTransaction { newStatus.queryTool.delete() }
                    emitter.onNext(newStatus)
                }

                persistentDatabaseComponent.database.runInTransaction {
                    var current = currentQuery
                    var currentFound = false
                    val queryCount = newStatus.queryTool.getCount(newStatus.term)

                    if (clearResults) {
                        newStatus.queryTool.delete()
                    }

                    while (executeUntilFound && current < queryCount && !currentFound ||
                            current < newStatus.lastQuery) {
                        currentFound = newStatus.queryTool.execute(newStatus.term,
                                current, newStatus.filterBookmarks, newStatus.filterMemos)
                        current++
                    }

                    val finalStatus = QueryStatus(
                            key = newStatus.key,
                            term = newStatus.term,
                            lastQuery = current,
                            queryTool = newStatus.queryTool,
                            found = if (intent is SearchIntent) currentFound else previousStatus.found,
                            filterMemos = newStatus.filterMemos,
                            filterBookmarks = newStatus.filterBookmarks,
                            title = newStatus.title,
                            searchIconifiedByDefault = newStatus.searchIconifiedByDefault,
                            shareButtonVisible = newStatus.shareButtonVisible,
                            persistentLanguageSettings = newStatus.persistentLanguageSettings,
                            closed = false,
                            searching = false,
                            preparing = false,
                            viewDestroyed = false
                    )

                    emitter.onNext(finalStatus)
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