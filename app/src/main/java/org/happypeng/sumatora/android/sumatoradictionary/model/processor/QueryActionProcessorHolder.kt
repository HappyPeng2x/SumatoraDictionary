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
                                 private val filterBookmarks: Boolean) {
    data class State(val dictionarySearchQueryTool: DictionarySearchQueryTool?,
                     val currentQuery: Int,
                     val term: String,
                     val found: Boolean,
                     val ready: Boolean,
                     val searching: Boolean,
                     val persistentLanguageSettings: PersistentLanguageSettings?,
                     val initial: Boolean) {
        fun toResult(): QueryResult {
            return QueryResult (currentQuery, term, found, ready, searching, persistentLanguageSettings)
        }
    }

    internal val actionProcessor =
            ObservableTransformer<QueryAction, QueryResult> {
                it.observeOn(Schedulers.io())
                        .scan(State(null, 0, "", false, ready = false, searching = false,
                                persistentLanguageSettings = null, initial = true),
                                { previousState, action ->
                                    when (action) {
                                        is QueryLanguageSettingDetachedAction -> run {
                                            previousState.dictionarySearchQueryTool?.close()
                                            previousState.copy(dictionarySearchQueryTool = null, ready = false,
                                                    persistentLanguageSettings = null, initial = false)
                                        }
                                        is QueryLanguageSettingAttachedAction -> run {
                                            val queryTool =  DictionarySearchQueryTool(databaseComponent, key,
                                                    action.persistentLanguageSettings)

                                            var current = 0
                                            var found = previousState.found

                                            databaseComponent.database.runInTransaction {
                                                queryTool.delete()

                                                val runUntilFound = previousState.currentQuery == 0
                                                val maxTransaction = if (runUntilFound)
                                                { queryTool.getCount(previousState.term) } else { previousState.currentQuery }

                                                while (current < maxTransaction && ((runUntilFound && !found) || !runUntilFound)) {
                                                    found = queryTool.execute(previousState.term, current, filterBookmarks, false)
                                                    current++
                                                }
                                            }

                                            previousState.copy(dictionarySearchQueryTool = queryTool,
                                                    currentQuery = current, found = found, searching = false,
                                                    persistentLanguageSettings = action.persistentLanguageSettings,
                                                    ready = true, initial = false)
                                        }
                                        is SetTermAction -> run {
                                            if (previousState.dictionarySearchQueryTool != null) {
                                                databaseComponent.database.runInTransaction {
                                                    previousState.dictionarySearchQueryTool.delete()
                                                }
                                            }

                                            previousState.copy(term = action.term, searching = true, found = false, currentQuery = 0,
                                                    initial = false)
                                        }
                                        is SearchAction -> run {
                                            if (previousState.dictionarySearchQueryTool != null) {
                                                val queryTool = previousState.dictionarySearchQueryTool
                                                val maxTransaction = queryTool.getCount(previousState.term)

                                                var current = 0
                                                var found = false

                                                databaseComponent.database.runInTransaction {
                                                    queryTool.delete()
                                                }

                                                databaseComponent.database.runInTransaction {
                                                    while (current < maxTransaction && !found) {
                                                        found = queryTool.execute(previousState.term, current, filterBookmarks, false)
                                                        current++
                                                    }
                                                }

                                                previousState.copy(dictionarySearchQueryTool = queryTool,
                                                        currentQuery = current, found = found, searching = false,
                                                        initial = false)
                                            } else { previousState }
                                        }
                                        is ScrollAction -> run {
                                            if (previousState.dictionarySearchQueryTool != null) {
                                                val queryTool = previousState.dictionarySearchQueryTool
                                                val maxTransaction = queryTool.getCount(previousState.term)

                                                var current = previousState.currentQuery
                                                var found = false

                                                databaseComponent.database.runInTransaction {
                                                    while (current < maxTransaction && !found) {
                                                        found = queryTool.execute(previousState.term, current, filterBookmarks, false)
                                                        current++
                                                    }
                                                }

                                                previousState.copy(dictionarySearchQueryTool = queryTool,
                                                        currentQuery = current, found = found, searching = false,
                                                        initial = false)
                                            } else {
                                                previousState
                                            }
                                        }
                                        is BookmarkAction -> run {
                                            if (previousState.dictionarySearchQueryTool != null) {
                                                val queryTool = previousState.dictionarySearchQueryTool
                                                val maxTransaction = previousState.currentQuery

                                                var current = 0

                                                databaseComponent.database.runInTransaction {
                                                    queryTool.delete()

                                                    while (current < maxTransaction) {
                                                        queryTool.execute(previousState.term, current, filterBookmarks, false)
                                                        current++
                                                    }
                                                }
                                            }

                                            previousState
                                        }
                                        else -> previousState
                                    }
                                })
                        .filter { state -> !state.initial }
                        .map { state -> state.toResult() }
                        .observeOn(AndroidSchedulers.mainThread())
            }
}