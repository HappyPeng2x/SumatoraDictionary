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
import org.happypeng.sumatora.android.sumatoradictionary.component.DictionaryTagsComponent
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryTagName
import org.happypeng.sumatora.android.sumatoradictionary.model.action.*
import org.happypeng.sumatora.android.sumatoradictionary.model.result.DictionaryTagsActivityResult

class DictionaryTagsActivityProcessorHolder(private val dictionaryTagsComponent: DictionaryTagsComponent) {
    data class State(val close: Boolean,
                     val dictionaryTagNames: List<DictionaryTagName>?,
                     val add: Boolean) {
        fun toResult(): DictionaryTagsActivityResult {
            return DictionaryTagsActivityResult(close, dictionaryTagNames, add)
        }
    }

    internal val actionProcessor = ObservableTransformer<DictionaryTagsActivityAction, DictionaryTagsActivityResult> {
        it.observeOn(Schedulers.io())
                .scan(State(close = false, dictionaryTagNames = null, add = false),
                        {
                            previousState, action ->
                            when (action) {
                                is DictionaryTagsActivityUpdateTagNamesAction -> previousState.copy(dictionaryTagNames = action.tagNames)
                                DictionaryTagsActivityCloseAction -> previousState.copy(close = true)
                                DictionaryTagsActivityAddAction -> previousState.copy(add = true)
                                DictionaryTagsActivityAddCancelAction -> previousState.copy(add = false)
                                is DictionaryTagsActivityCreateTagNameAction -> run {
                                    dictionaryTagsComponent.createTagName(action.tagName)
                                    previousState.copy(add = false)
                                }
                            }
                        })
                .observeOn(AndroidSchedulers.mainThread())
                .map { state -> state.toResult() }
    }
}