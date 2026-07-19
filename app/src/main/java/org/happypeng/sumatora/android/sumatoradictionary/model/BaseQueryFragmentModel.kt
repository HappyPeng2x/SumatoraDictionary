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
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.BehaviorSubject
import io.reactivex.rxjava3.subjects.PublishSubject
import io.reactivex.rxjava3.subjects.Subject
import org.happypeng.sumatora.android.sumatoradictionary.component.BookmarkComponent
import org.happypeng.sumatora.android.sumatoradictionary.component.BookmarkShareComponent
import org.happypeng.sumatora.android.sumatoradictionary.component.LanguageSettingsComponent
import org.happypeng.sumatora.android.sumatoradictionary.component.PersistentDatabaseComponent
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryBookmark
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryBookmarkTag
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchElement
import org.happypeng.sumatora.android.sumatoradictionary.db.InstalledDictionary
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentLanguageSettings
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.DictionarySearchQueryTool
import org.happypeng.sumatora.core.bookmark.BookmarkMergeService
import org.happypeng.sumatora.core.search.TagQueryParser
import org.happypeng.sumatora.android.sumatoradictionary.model.intent.LanguageSettingAttachedIntent
import org.happypeng.sumatora.android.sumatoradictionary.model.intent.LanguageSettingDetachedIntent
import org.happypeng.sumatora.android.sumatoradictionary.model.state.QueryState
import org.happypeng.sumatora.android.sumatoradictionary.operator.LiveDataWrapper
import java.io.File

abstract class BaseQueryFragmentModel protected constructor(
    protected val persistentDatabaseComponent: PersistentDatabaseComponent,
    private val languageSettingsComponent: LanguageSettingsComponent,
    private val bookmarkComponent: BookmarkComponent,
    private val bookmarkShareComponent: BookmarkShareComponent,
    pagedListFactory: (PersistentDatabaseComponent, BoundaryCallback<DictionarySearchElement>?) -> LiveData<PagedList<DictionarySearchElement>>,
    val key: Int,
    val searchIconifiedByDefault: Boolean,
    val shareButtonVisible: Boolean,
    val title: String,
    private val filterBookmarks: Boolean,
    private val filterMemos: Boolean,
    val disableBookmarkButton: Boolean,
    val disableMemoEdit: Boolean,
    val disableTagEdit: Boolean,
    savedState: QueryState?
) : ViewModel() {

    // Internal operation sealed class replaces Intent→Action→Result layers.
    private sealed class Op {
        class LanguageAttached(val settings: PersistentLanguageSettings) : Op()
        object LanguageDetached : Op()
        class SetTerm(val term: String) : Op()
        object ExecuteSearch : Op()
        class CloseSearchBox(val input: String) : Op()
        object SearchBoxClosed : Op()
        object OpenSearchBox : Op()
        object RefreshBookmarks : Op()
        object Scroll : Op()
        object Clear : Op()
        object Close : Op()
    }

    private data class InternalState(
        val queryTool: DictionarySearchQueryTool? = null,
        val currentQuery: Int = 0,
        val term: String = "",
        val plainTerm: String = "",
        val tags: List<String> = emptyList(),
        val found: Boolean = false,
        val ready: Boolean = false,
        val searching: Boolean = false,
        val languageSettings: PersistentLanguageSettings? = null,
        val searchBoxClosed: Boolean = false,
        val setIntent: Boolean = false,
        val clearSearchBox: Boolean = false,
        val closed: Boolean = false
    ) {
        fun toQueryState() = QueryState(
            term = term,
            found = found,
            language = languageSettings?.lang,
            backupLanguage = languageSettings?.backupLang,
            closed = closed,
            searching = searching,
            ready = ready,
            searchBoxClosed = searchBoxClosed,
            setIntent = setIntent,
            clearSearchBox = clearSearchBox
        )
    }

    private val opsSubject: Subject<Op> = PublishSubject.create()
    private val closedSubject: Subject<Unit> = PublishSubject.create()

    private val pagedListSubject: Subject<PagedList<DictionarySearchElement>> = BehaviorSubject.create()
    val pagedListObservable: Observable<PagedList<DictionarySearchElement>> = pagedListSubject

    val installedDictionaries: Observable<List<InstalledDictionary>>
        get() = Observable.defer {
            Observable.just(persistentDatabaseComponent.database.installedDictionaryDao().all)
        }.subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread())

    private val statesObservable: Observable<QueryState>

    fun states(): Observable<QueryState> = statesObservable

    fun setTerm(term: String) {
        opsSubject.onNext(Op.SetTerm(term))
        opsSubject.onNext(Op.ExecuteSearch)
    }

    fun closeSearchBox(input: String) {
        opsSubject.onNext(Op.CloseSearchBox(input))
        opsSubject.onNext(Op.SearchBoxClosed)
    }

    fun openSearchBox() = opsSubject.onNext(Op.OpenSearchBox)

    open fun setLanguage(language: String) {
        val settings = PersistentLanguageSettings()
        settings.lang = language
        settings.backupLang = if (language == "eng") null else "eng"
        languageSettingsComponent.updatePersistentLanguageSettings(settings)
    }

    fun shareBookmarks() {
        Observable.defer { Observable.just(bookmarkShareComponent.writeBookmarks()) }
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe { file: File? -> bookmarkShareComponent.shareBookmarks(file) }
    }

    override fun onCleared() {
        opsSubject.onNext(Op.Clear)
        opsSubject.onNext(Op.Close)
        closedSubject.onNext(Unit)
        closedSubject.onComplete()
        super.onCleared()
    }

    val commitBookmarksFun: (Long, Long, String?) -> Unit = { seq, bookmark, memo ->
        Completable.fromAction {
            val db = persistentDatabaseComponent.database
            val existing = db.dictionaryBookmarkDao().getBySeq(seq)
            val bm = DictionaryBookmark(seq, bookmark, memo, existing?.tags)
            if (bm.bookmark > 0 || !bm.memo.isNullOrEmpty() || !bm.tags.isNullOrEmpty()) {
                db.dictionaryBookmarkDao().insert(bm)
            } else {
                db.dictionaryBookmarkDao().delete(bm)
            }
        }.subscribeOn(Schedulers.io()).subscribe()
    }

    val commitTagsFun: (Long, String) -> Unit = { seq, tagsStr ->
        Completable.fromAction {
            val db = persistentDatabaseComponent.database
            db.runInTransaction {
                val existing = db.dictionaryBookmarkDao().getBySeq(seq)
                    ?: DictionaryBookmark(seq, 0L, null, null)
                existing.tags = tagsStr.ifEmpty { null }
                if (existing.bookmark > 0 || !existing.memo.isNullOrEmpty() || !existing.tags.isNullOrEmpty()) {
                    db.dictionaryBookmarkDao().insert(existing)
                } else {
                    db.dictionaryBookmarkDao().delete(existing)
                }
                val tagDao = db.dictionaryBookmarkTagDao()
                tagDao.deleteTagsForSeq(seq)
                if (tagsStr.isNotEmpty()) {
                    tagDao.insertMany(
                        BookmarkMergeService.splitTags(tagsStr).map { DictionaryBookmarkTag(seq, it) }
                    )
                }
                db.dictionarySearchElementDao().updateTags(seq, existing.tags)
            }
        }.subscribeOn(Schedulers.io()).subscribe()
    }

    val availableTagsFun: () -> List<String> = {
        persistentDatabaseComponent.database.dictionaryBookmarkTagDao().getAllTags()
    }

    private fun reduce(prev: InternalState, op: Op): InternalState {
        val db = persistentDatabaseComponent.database
        return when (op) {
            is Op.LanguageDetached -> {
                prev.queryTool?.close()
                prev.copy(queryTool = null, ready = false, languageSettings = null,
                    setIntent = false, clearSearchBox = false)
            }
            is Op.LanguageAttached -> {
                prev.queryTool?.close()
                val queryTool = DictionarySearchQueryTool(persistentDatabaseComponent, key, op.settings)
                var current = 0
                var found = prev.found
                db.runInTransaction {
                    queryTool.delete()
                    val runUntilFound = prev.currentQuery == 0
                    val max = if (runUntilFound) queryTool.getCount(prev.plainTerm) else prev.currentQuery
                    while (current < max && (!runUntilFound || !found)) {
                        found = queryTool.execute(prev.plainTerm, current, filterBookmarks, filterMemos, prev.tags)
                        current++
                    }
                    if (!filterBookmarks && !filterMemos) {
                        found = queryTool.executeProperNouns(prev.plainTerm) || found
                        found = queryTool.executeDeinflection(prev.plainTerm) || found
                    }
                }
                prev.copy(queryTool = queryTool, currentQuery = current, found = found,
                    searching = false, languageSettings = op.settings, ready = true,
                    setIntent = false, clearSearchBox = false)
            }
            is Op.SetTerm -> {
                val (plainTerm, tags) = TagQueryParser.parse(op.term)
                prev.queryTool?.let { db.runInTransaction { it.delete() } }
                prev.copy(term = op.term, plainTerm = plainTerm, tags = tags, searching = true,
                    found = false, currentQuery = 0, setIntent = false, clearSearchBox = false)
            }
            Op.ExecuteSearch -> {
                val tool = prev.queryTool ?: return prev
                val max = tool.getCount(prev.plainTerm)
                var current = 0; var found = false
                db.runInTransaction {
                    tool.delete()
                    while (current < max && !found) {
                        found = tool.execute(prev.plainTerm, current, filterBookmarks, filterMemos, prev.tags)
                        current++
                    }
                    if (!filterBookmarks && !filterMemos) {
                        found = tool.executeProperNouns(prev.plainTerm) || found
                        found = tool.executeDeinflection(prev.plainTerm) || found
                    }
                }
                prev.copy(currentQuery = current, found = found, searching = false,
                    setIntent = false, clearSearchBox = false)
            }
            is Op.CloseSearchBox -> {
                var current = 0
                prev.queryTool?.let { tool ->
                    db.runInTransaction {
                        tool.delete()
                        if (filterBookmarks || filterMemos) {
                            tool.execute("", 0, filterBookmarks, filterMemos, emptyList())
                            current = 1
                        }
                    }
                }
                prev.copy(term = "", plainTerm = "", tags = emptyList(), searching = false, found = false,
                    currentQuery = current,
                    searchBoxClosed = op.input == "" && searchIconifiedByDefault,
                    setIntent = prev.term != "",
                    clearSearchBox = op.input != "")
            }
            Op.SearchBoxClosed -> prev.copy(setIntent = false, clearSearchBox = false)
            Op.OpenSearchBox -> prev.copy(searchBoxClosed = false)
            Op.RefreshBookmarks -> {
                val tool = prev.queryTool ?: return prev
                db.runInTransaction {
                    if (prev.plainTerm.isEmpty() && prev.tags.isEmpty()) {
                        tool.delete()
                        tool.execute("", 0, filterBookmarks, filterMemos, emptyList())
                    } else {
                        val rawDb = db.openHelper.writableDatabase
                        rawDb.execSQL(
                            "UPDATE DictionarySearchElement SET " +
                            "bookmark = IFNULL((SELECT bookmark FROM DictionaryBookmark WHERE DictionaryBookmark.seq = DictionarySearchElement.seq), 0), " +
                            "memo = (SELECT memo FROM DictionaryBookmark WHERE DictionaryBookmark.seq = DictionarySearchElement.seq), " +
                            "tags = (SELECT tags FROM DictionaryBookmark WHERE DictionaryBookmark.seq = DictionarySearchElement.seq) " +
                            "WHERE ref = ?", arrayOf<Any>(key))
                        if (filterBookmarks || filterMemos) {
                            rawDb.execSQL(
                                "DELETE FROM DictionarySearchElement WHERE ref = ? AND NOT (" +
                                "(? = 0 AND ? = 0) OR " +
                                "((? AND bookmark > 0) OR (? AND memo IS NOT NULL AND memo != ''))" +
                                ")",
                                arrayOf<Any>(key,
                                    if (filterBookmarks) 1 else 0, if (filterMemos) 1 else 0,
                                    if (filterBookmarks) 1 else 0, if (filterMemos) 1 else 0))
                        }
                    }
                }
                prev
            }
            Op.Scroll -> {
                val tool = prev.queryTool ?: return prev
                val max = tool.getCount(prev.plainTerm)
                var current = prev.currentQuery; var found = false
                db.runInTransaction {
                    while (current < max && !found) {
                        found = tool.execute(prev.plainTerm, current, filterBookmarks, filterMemos, prev.tags)
                        current++
                    }
                }
                prev.copy(currentQuery = current, found = prev.found || found, searching = false,
                    setIntent = false, clearSearchBox = false)
            }
            Op.Clear -> {
                prev.queryTool?.let { db.runInTransaction { it.delete() } }
                prev
            }
            Op.Close -> {
                prev.queryTool?.close()
                prev.copy(closed = true, setIntent = false, clearSearchBox = false)
            }
        }
    }

    init {
        val initialState = if (savedState != null) {
            val (plainTerm, tags) = TagQueryParser.parse(savedState.term)
            InternalState(
                term = savedState.term,
                plainTerm = plainTerm,
                tags = tags,
                found = savedState.found,
                searchBoxClosed = savedState.searchBoxClosed
            )
        } else {
            InternalState(searchBoxClosed = searchIconifiedByDefault)
        }

        statesObservable = opsSubject
            .observeOn(Schedulers.io())
            .scan(initialState, ::reduce)
            .skip(1)
            .map { it.toQueryState() }
            .distinctUntilChanged()
            .observeOn(AndroidSchedulers.mainThread())
            .replay(1)
            .autoConnect(0)

        languageSettingsComponent.persistentLanguageSettings
            .takeUntil(closedSubject)
            .subscribe { intent ->
                when (intent) {
                    is LanguageSettingAttachedIntent -> opsSubject.onNext(Op.LanguageAttached(intent.languageSettings))
                    is LanguageSettingDetachedIntent -> opsSubject.onNext(Op.LanguageDetached)
                }
            }

        bookmarkComponent.bookmarkChanges
            .takeUntil(closedSubject)
            .subscribe { opsSubject.onNext(Op.RefreshBookmarks) }

        val pagedList = pagedListFactory.invoke(persistentDatabaseComponent,
            object : BoundaryCallback<DictionarySearchElement>() {
                override fun onItemAtEndLoaded(itemAtEnd: DictionarySearchElement) {
                    opsSubject.onNext(Op.Scroll)
                }
            })
        LiveDataWrapper.wrap(pagedList, closedSubject).subscribe(pagedListSubject)
    }
}
