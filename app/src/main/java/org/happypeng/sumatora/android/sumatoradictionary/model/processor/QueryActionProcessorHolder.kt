/* Sumatora Dictionary
        Copyright (C) 2020 Nicolas Centa

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

package org.happypeng.sumatora.android.sumatoradictionary.model.processor

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.ObservableTransformer
import io.reactivex.rxjava3.schedulers.Schedulers
import org.happypeng.sumatora.android.sumatoradictionary.component.PersistentDatabaseComponent
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentLanguageSettings
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.DictionarySearchQueryTool
import org.happypeng.sumatora.android.sumatoradictionary.model.action.*
import org.happypeng.sumatora.android.sumatoradictionary.model.result.QueryResult

class QueryActionProcessorHolder(private val databaseComponent: PersistentDatabaseComponent,
                                 private val key: Int,
                                 private val filterBookmarks: Boolean,
                                 private val filterMemos: Boolean,
                                 private val searchBoxClosed: Boolean) {
    data class State(val dictionarySearchQueryTool: DictionarySearchQueryTool?,
                     val currentQuery: Int,
                     val term: String,
                     val found: Boolean,
                     val ready: Boolean,
                     val searching: Boolean,
                     val persistentLanguageSettings: PersistentLanguageSettings?,
                     val initial: Boolean,
                     val closed: Boolean,
                     val searchBoxClosed: Boolean,
                     val setIntent: Boolean,
                     val clearSearchBox: Boolean) {
        fun toResult(): QueryResult {
            return QueryResult (currentQuery, term, found, ready, searching,
                    persistentLanguageSettings, closed, searchBoxClosed, setIntent,
                    clearSearchBox)
        }
    }

    internal val actionProcessor =
            ObservableTransformer<QueryAction, QueryResult> {
                it.observeOn(Schedulers.io())
                        .scan(State(null, 0, "",
                                false, ready = false, searching = false,
                                persistentLanguageSettings = null, initial = true,
                                closed = false, searchBoxClosed = searchBoxClosed,
                                setIntent = false, clearSearchBox = false),
                                { previousState, action ->
                                    when (action) {
                                        is QueryLanguageSettingDetachedAction -> {
                                            previousState.dictionarySearchQueryTool?.close()
                                            previousState.copy(dictionarySearchQueryTool = null, ready = false,
                                                    persistentLanguageSettings = null, initial = false,
                                                    setIntent = false, clearSearchBox = false)
                                        }
                                        is QueryLanguageSettingAttachedAction -> {
                                            previousState.dictionarySearchQueryTool?.close()
                                            
                                            val queryTool =  DictionarySearchQueryTool(databaseComponent, key,
                                                    action.persistentLanguageSettings)

                                            var current = 0
                                            var found = previousState.found

                                            databaseComponent.database.runInTransaction {
                                                queryTool.delete()

                                                val runUntilFound = previousState.currentQuery == 0
                                                val maxTransaction = if (runUntilFound)
                                                { queryTool.getCount(previousState.term) } else { previousState.currentQuery }

                                                // If currentQuery > 0, we are restoring. We must use !runUntilFound to ignore 'found' status.
                                                while (current < maxTransaction && (!runUntilFound || !found)) {
                                                    found = queryTool.execute(previousState.term, current, filterBookmarks, filterMemos)
                                                    current++
                                                }
                                            }

                                            previousState.copy(dictionarySearchQueryTool = queryTool,
                                                    currentQuery = current, found = found, searching = false,
                                                    persistentLanguageSettings = action.persistentLanguageSettings,
                                                    ready = true, initial = false, setIntent = false,
                                                    clearSearchBox = false)
                                        }
                                        is SetTermAction -> {
                                            previousState.dictionarySearchQueryTool?.let { tool ->
                                                databaseComponent.database.runInTransaction {
                                                    tool.delete()
                                                }
                                            }

                                            previousState.copy(term = action.term, searching = true, found = false, currentQuery = 0,
                                                    initial = false, setIntent = false,
                                                    clearSearchBox = false)
                                        }
                                        is CloseSearchBoxAction -> {
                                            var current = 0
                                            previousState.dictionarySearchQueryTool?.let { tool ->
                                                databaseComponent.database.runInTransaction {
                                                    tool.delete()
                                                    if (filterBookmarks || filterMemos) {
                                                        tool.execute("", 0, filterBookmarks, filterMemos)
                                                        current = 1
                                                    }
                                                }
                                            }
                                            previousState.copy(term = "", searching = false, found = false, currentQuery = current,
                                                    initial = false, searchBoxClosed = action.input == "" && searchBoxClosed,
                                                    setIntent = previousState.term != "",
                                                    clearSearchBox = action.input != "")
                                        }
                                        ClosedSearchBoxAction ->
                                            previousState.copy(initial = false, setIntent =  false, clearSearchBox = false)
                                        OpenSearchBoxAction -> previousState.copy(searchBoxClosed = false)
                                        is SearchAction -> {
                                            val queryTool = previousState.dictionarySearchQueryTool
                                            if (queryTool != null) {
                                                val maxTransaction = queryTool.getCount(previousState.term)

                                                var current = 0
                                                var found = false

                                                databaseComponent.database.runInTransaction {
                                                    queryTool.delete()
                                                    while (current < maxTransaction && !found) {
                                                        found = queryTool.execute(previousState.term, current, filterBookmarks, filterMemos)
                                                        current++
                                                    }
                                                }

                                                previousState.copy(currentQuery = current, found = found, searching = false,
                                                        initial = false, setIntent = false,
                                                        clearSearchBox = false)
                                            } else { previousState }
                                        }
                                        is ScrollAction -> {
                                            val queryTool = previousState.dictionarySearchQueryTool
                                            if (queryTool != null) {
                                                val maxTransaction = queryTool.getCount(previousState.term)

                                                var current = previousState.currentQuery
                                                // Fix: Reset 'found' to false for the loop so that it actually triggers the next levels
                                                var found = false

                                                databaseComponent.database.runInTransaction {
                                                    while (current < maxTransaction && !found) {
                                                        found = queryTool.execute(previousState.term, current, filterBookmarks, filterMemos)
                                                        current++
                                                    }
                                                }

                                                previousState.copy(currentQuery = current, 
                                                        found = previousState.found || found, 
                                                        searching = false,
                                                        initial = false, setIntent = false,
                                                        clearSearchBox = false)
                                            } else {
                                                previousState
                                            }
                                        }
                                        is BookmarkAction -> {
                                            val queryTool = previousState.dictionarySearchQueryTool
                                            if (queryTool != null) {
                                                val term = previousState.term
                                                databaseComponent.database.runInTransaction {
                                                    if (term.isEmpty()) {
                                                        queryTool.delete()
                                                        queryTool.execute("", 0, filterBookmarks, filterMemos)
                                                    } else {
                                                        val db = databaseComponent.database.openHelper.writableDatabase
                                                        db.execSQL("UPDATE DictionarySearchElement SET " +
                                                                "bookmark = IFNULL((SELECT bookmark FROM DictionaryBookmark WHERE DictionaryBookmark.seq = DictionarySearchElement.seq), 0), " +
                                                                "memo = (SELECT memo FROM DictionaryBookmark WHERE DictionaryBookmark.seq = DictionarySearchElement.seq) " +
                                                                "WHERE ref = ?", arrayOf<Any>(key))

                                                        if (filterBookmarks || filterMemos) {
                                                            db.execSQL("DELETE FROM DictionarySearchElement WHERE ref = ? AND NOT (" +
                                                                    "(? = 0 AND ? = 0) OR " +
                                                                    "((? AND bookmark > 0) OR (? AND memo IS NOT NULL AND memo != ''))" +
                                                                    ")",
                                                                    arrayOf<Any>(key, if (filterBookmarks) 1 else 0, if (filterMemos) 1 else 0,
                                                                            if (filterBookmarks) 1 else 0, if (filterMemos) 1 else 0))
                                                        }
                                                    }
                                                }
                                            }

                                            previousState
                                        }
                                        is QueryClearAction -> {
                                            previousState.dictionarySearchQueryTool?.let { tool ->
                                                databaseComponent.database.runInTransaction {
                                                    tool.delete()
                                                }
                                            }

                                            previousState
                                        }
                                        is QueryCloseAction -> {
                                            previousState.dictionarySearchQueryTool?.close()

                                            previousState.copy(closed = true, setIntent = false,
                                                    clearSearchBox = false)
                                        }
                                    }
                                })
                        .filter { state -> !state.initial }
                        .map { state -> state.toResult() }
                        .observeOn(AndroidSchedulers.mainThread())
            }
}
