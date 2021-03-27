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
import org.happypeng.sumatora.android.sumatoradictionary.adapter.`object`.DictionaryTagNameAdapterObject
import org.happypeng.sumatora.android.sumatoradictionary.component.DictionaryTagsComponent
import org.happypeng.sumatora.android.sumatoradictionary.model.action.*
import org.happypeng.sumatora.android.sumatoradictionary.model.result.DictionaryTagsActivityResult

class DictionaryTagsActivityProcessorHolder(private val dictionaryTagsComponent: DictionaryTagsComponent) {
    data class State(val close: Boolean,
                     val dictionaryTagNames: List<DictionaryTagNameAdapterObject>?,
                     val add: Boolean,
                     val edit: Boolean,
                     val editCommitConfirm: Boolean,
                     val seq: Long?) {
        fun toResult(): DictionaryTagsActivityResult {
            return DictionaryTagsActivityResult(close, dictionaryTagNames, add, edit, editCommitConfirm, seq)
        }
    }

    internal val actionProcessor = ObservableTransformer<DictionaryTagsActivityAction, DictionaryTagsActivityResult> {
        it.observeOn(Schedulers.io())
                .scan(State(close = false, dictionaryTagNames = null, add = false, edit = false, editCommitConfirm = false, seq = null),
                        {
                            previousState, action ->
                            when (action) {
                                is DictionaryTagsActivitySetSeqAction -> previousState.copy(seq = action.seq,
                                        dictionaryTagNames = previousState.dictionaryTagNames?.map { element ->
                                            element.copy(selected = action.seq != null && element.tags.contains(action.seq))
                                        })
                                is DictionaryTagsActivityUpdateTagsAction -> previousState /* run {
                                    previousState.copy(dictionaryTagNames =
                                            action.tags.map { actionItem ->
                                                previousState.dictionaryTagNames?.first { tagNameItem ->
                                                    actionItem.first.tagId == tagNameItem.tagName.tagId }
                                            })
                                //action.tags.map { element ->
                                        /*run {
                                            var selectedForDelete = false
                                            var selected = false

                                            if (previousState.dictionaryTagNames != null) {
                                                for (e in previousState.dictionaryTagNames) {
                                                    if (e.tagName.tagId == element.first.tagId) {
                                                        selectedForDelete = e.selectedForDelete
                                                        selected = e.selected
                                                    }
                                                }
                                            }

                                            DictionaryTagNameAdapterObject(element.first,
                                                    deleteSelectionEnabled = previousState.edit,
                                                    selectedForDelete = selectedForDelete,
                                                    selected = selected,
                                                    tags = element.second)
                                        }*/
                                    })
                                }*/
                                DictionaryTagsActivityCloseAction -> previousState.copy(close = true)
                                DictionaryTagsActivityAddAction -> previousState.copy(add = true)
                                DictionaryTagsActivityAddCancelAction -> previousState.copy(add = false)
                                is DictionaryTagsActivityCreateTagNameAction -> run {
                                    dictionaryTagsComponent.createTagName(action.tagName)
                                    previousState.copy(add = false)
                                }
                                DictionaryTagsActivityEditAction -> previousState.copy(dictionaryTagNames = previousState.dictionaryTagNames?.map { element ->
                                    DictionaryTagNameAdapterObject(element.tagName, deleteSelectionEnabled = true, selectedForDelete = false, selected = element.selected,
                                            tags = element.tags)
                                }, edit = true)
                                DictionaryTagsActivityEditCommitConfirmAction ->run {
                                    if (previousState.dictionaryTagNames != null) {
                                        dictionaryTagsComponent.deleteTagNames(previousState.dictionaryTagNames.filter { element -> element.selectedForDelete }.map { element -> element.tagName })
                                    }

                                    previousState.copy(edit = false, editCommitConfirm = false)
                                }
                                DictionaryTagsActivityEditCancelAction -> previousState.copy(dictionaryTagNames = previousState.dictionaryTagNames?.map { element ->
                                    DictionaryTagNameAdapterObject(element.tagName, deleteSelectionEnabled = false, selectedForDelete = false, selected = element.selected, tags = element.tags)
                                }, edit = false)
                                DictionaryTagsActivityEditCommitAction -> previousState.copy(editCommitConfirm = true)
                                is DictionaryTagsActivityEditSelectForDeletionAction -> run {
                                    previousState.copy(dictionaryTagNames = previousState.dictionaryTagNames?.map { element ->
                                        DictionaryTagNameAdapterObject(element.tagName, deleteSelectionEnabled = element.deleteSelectionEnabled,
                                                selectedForDelete = if (action.tag.tagId == element.tagName.tagId) { action.select } else { element.selectedForDelete },
                                                selected = element.selected, tags = element.tags)
                                    })
                                }
                                is DictionaryTagsActivityToggleSelectAction -> run {
                                    previousState.copy(dictionaryTagNames = previousState.dictionaryTagNames?.map { element ->
                                        DictionaryTagNameAdapterObject(element.tagName, deleteSelectionEnabled = element.deleteSelectionEnabled,
                                                selected = if (action.tag.tagId == element.tagName.tagId) { !element.selected } else { element.selected },
                                                selectedForDelete = element.selectedForDelete, tags = element.tags)
                                    })
                                }
                            }
                        })
                .observeOn(AndroidSchedulers.mainThread())
                .map { state -> state.toResult() }
    }
}