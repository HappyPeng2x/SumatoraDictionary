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
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.PublishSubject
import org.happypeng.sumatora.android.sumatoradictionary.component.BookmarkComponent
import org.happypeng.sumatora.android.sumatoradictionary.component.BookmarkShareComponent
import org.happypeng.sumatora.android.sumatoradictionary.component.LanguageSettingsComponent
import org.happypeng.sumatora.android.sumatoradictionary.component.PersistentDatabaseComponent
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryBookmark
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchElement
import org.happypeng.sumatora.android.sumatoradictionary.model.intent.*
import org.happypeng.sumatora.android.sumatoradictionary.model.processor.QueryActionProcessorHolder
import org.happypeng.sumatora.android.sumatoradictionary.model.result.QueryResult
import org.happypeng.sumatora.android.sumatoradictionary.model.state.QueryState
import org.happypeng.sumatora.android.sumatoradictionary.mvibase.MviViewModel
import org.happypeng.sumatora.android.sumatoradictionary.model.transformer.QueryIntentTransformer
import java.io.File

abstract class BaseQueryFragmentModel protected constructor(private val bookmarkComponent: BookmarkComponent,
                                                            persistentDatabaseComponent: PersistentDatabaseComponent,
                                                            languageSettingsComponent: LanguageSettingsComponent,
                                                            private val bookmarkShareComponent: BookmarkShareComponent,
                                                            pagedListFactory: (PersistentDatabaseComponent, PagedList.BoundaryCallback<DictionarySearchElement?>?) ->
                                                            LiveData<PagedList<DictionarySearchElement?>>,
                                                            val key: Int,
                                                            val searchIconifiedByDefault: Boolean,
                                                            val shareButtonVisible: Boolean,
                                                            val title: String,
                                                            private val filterBookmarks: Boolean,
                                                            disableBookmarkButton: Boolean,
                                                            disableMemoEdit: Boolean
) : BaseFragmentModel(persistentDatabaseComponent, languageSettingsComponent, pagedListFactory, disableBookmarkButton, disableMemoEdit), MviViewModel<QueryIntent, QueryState> {
    private val intentsSubject: PublishSubject<QueryIntent> = PublishSubject.create()
    private val statesObservable = compose()
    private val closedObservable = statesObservable.filter { it.closed }.map { Unit }

    final override fun processIntents(intents: Observable<QueryIntent>) {
        intents.takeUntil(closedObservable).subscribe(intentsSubject::onNext)
    }

    override fun states(): Observable<QueryState> = statesObservable

    private fun compose(): Observable<QueryState> {
        val actionProcessorHolder = QueryActionProcessorHolder(persistentDatabaseComponent, key, filterBookmarks)

        return intentsSubject
                .compose(QueryIntentTransformer())
                .compose(actionProcessorHolder.actionProcessor)
                .scan(QueryState("", false, null, false,
                        searching = false, false), this::transformStatus)
                .distinctUntilChanged()
                .replay(1)
                .autoConnect(0)
    }

    fun setTerm(t: String) {
        processIntents(Observable.just(SearchIntent(t.replace("\"", ""))))
    }

    fun shareBookmarks() {
        Observable.defer { Observable.just(bookmarkShareComponent.writeBookmarks()) }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { outputFile: File? -> bookmarkShareComponent.shareBookmarks(outputFile) }
    }

    init {
        processIntents(bookmarkComponent.bookmarkChanges.map { BookmarkIntent })
        processIntents(languageSettingsComponent.persistentLanguageSettings
                .map { intent: LanguageSettingIntent ->
                    when (intent) {
                        is LanguageSettingDetachedIntent -> QueryLanguageSettingDetachedIntent(intent.languageSettings)
                        is LanguageSettingAttachedIntent -> QueryLanguageSettingAttachedIntent(intent.languageSettings)
                    }
                })
        processIntents(scrollObservable.map { ScrollIntent(it.entryOrder) })
        processIntents(clearedObservable.map { QueryCloseIntent })
    }

    private fun transformStatus(previousState: QueryState, result: QueryResult): QueryState {
        return QueryState(term = result.term, found = result.found, searching = result.searching,
                ready = result.ready, closed = result.closed, languageSettings = result.languageSettings)
    }

    override fun commitBookmarks(seq: Long, bookmark: Long, memo: String?) {
        val dictionaryBookmark = DictionaryBookmark()
        dictionaryBookmark.memo = memo
        dictionaryBookmark.bookmark = bookmark
        dictionaryBookmark.seq = seq
        bookmarkComponent.updateBookmark(dictionaryBookmark)
    }
}