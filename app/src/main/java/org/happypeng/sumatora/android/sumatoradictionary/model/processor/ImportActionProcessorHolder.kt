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
import org.happypeng.sumatora.android.sumatoradictionary.component.BookmarkImportComponent
import org.happypeng.sumatora.android.sumatoradictionary.component.PersistentDatabaseComponent
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentLanguageSettings
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.BookmarkImportQueryTool
import org.happypeng.sumatora.android.sumatoradictionary.model.action.*
import org.happypeng.sumatora.android.sumatoradictionary.model.result.ImportResult

class ImportActionProcessorHolder(private val databaseComponent: PersistentDatabaseComponent,
                                  private val bookmarkImportComponent: BookmarkImportComponent,
                                  private val key: Int) {
    data class State(val bookmarkImportQueryTool: BookmarkImportQueryTool?,
                     val executed: Boolean,
                     val persistentLanguageSettings: PersistentLanguageSettings?,
                     val close: Boolean,
                     val processing: Boolean) {
        fun toResult(): ImportResult {
            return ImportResult(executed, persistentLanguageSettings, close, false)
        }
    }

    internal val actionProcessor =
            ObservableTransformer<ImportAction, ImportResult> {
                it.observeOn(Schedulers.io())
                        .scan(State(null, false, null, close = false, processing = false),
                                { previousState: State, action: ImportAction ->
                                    when (action) {
                                        is ImportLanguageSettingDetachedAction -> run {
                                            previousState.bookmarkImportQueryTool?.close()
                                            previousState.copy(bookmarkImportQueryTool = null, executed = false,
                                                    persistentLanguageSettings = null)
                                        }
                                        is ImportLanguageSettingAttachedAction -> run {
                                            val queryTool = BookmarkImportQueryTool(databaseComponent, key, action.persistentLanguageSettings)

                                            databaseComponent.database.runInTransaction {
                                                queryTool.delete()
                                                queryTool.execute()
                                            }

                                            previousState.copy(bookmarkImportQueryTool = queryTool, executed = true,
                                                    persistentLanguageSettings = action.persistentLanguageSettings)
                                        }
                                        is ImportCommitAction -> run {
                                            bookmarkImportComponent.commitBookmarks(key)

                                            previousState.copy(close = true)
                                        }
                                        is ImportCancelAction -> run {
                                            bookmarkImportComponent.cancelImport(key)

                                            previousState.copy(close = true)
                                        }
                                        is ImportFileAction -> run {
                                            bookmarkImportComponent.processURI(action.uri, key)

                                            var executed = false

                                            if (previousState.bookmarkImportQueryTool != null) {
                                                val bookmarkImportQueryTool = previousState.bookmarkImportQueryTool

                                                databaseComponent.database.runInTransaction {
                                                    bookmarkImportQueryTool.delete()
                                                    bookmarkImportQueryTool.execute()
                                                }

                                                executed = true
                                            }

                                            previousState.copy(processing = false, executed = executed)
                                        }
                                        is ImportSetProcessingAction -> previousState.copy(processing = true)
                                    }
                                })
                        .observeOn(AndroidSchedulers.mainThread())
                        .map { state -> state.toResult() }
            }
}