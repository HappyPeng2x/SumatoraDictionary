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
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryTag
import org.happypeng.sumatora.android.sumatoradictionary.model.intent.*
import org.happypeng.sumatora.android.sumatoradictionary.model.state.DictionaryTagsActivityState

class DictionaryTagsActivityProcessorHolder(private val dictionaryTagsComponent: DictionaryTagsComponent) {
    internal val actionProcessor = ObservableTransformer<DictionaryTagsActivityIntent, DictionaryTagsActivityState> {
        it.observeOn(Schedulers.io())
                .scan(DictionaryTagsActivityState(closed = false, dictionaryTagNames = null, add = false, edit = false, editCommitConfirm = false, seq = null),
                        {
                            previousState, action ->
                            when (action) {
                                is DictionaryTagsActivityUpdateTagsIntent ->
                                    previousState.copy(dictionaryTagNames =
                                    action.tags.map { tag -> run {
                                        val itemPreviousState = previousState.dictionaryTagNames?.find { item ->
                                            tag.first.tagId == item.tagName.tagId }

                                        DictionaryTagNameAdapterObject(tag.first, tag.second, previousState.edit,
                                                itemPreviousState != null && itemPreviousState.selectedForDelete,
                                                tag.second.contains(action.seq)) }
                                    }, seq = action.seq)
                                DictionaryTagsActivityCloseIntent -> previousState.copy(closed = true)
                                DictionaryTagsActivityAddIntent -> previousState.copy(add = true)
                                DictionaryTagsActivityAddCancelIntent -> previousState.copy(add = false)
                                is DictionaryTagsActivityCreateTagNameIntent -> run {
                                    dictionaryTagsComponent.createTagName(action.name)
                                    previousState.copy(add = false)
                                }
                                DictionaryTagsActivityEditIntent -> previousState.copy(dictionaryTagNames = previousState.dictionaryTagNames?.map { element ->
                                    DictionaryTagNameAdapterObject(element.tagName, deleteSelectionEnabled = true, selectedForDelete = false, selected = element.selected,
                                            tags = element.tags)
                                }, edit = true)
                                DictionaryTagsActivityEditCommitConfirmIntent ->run {
                                    if (previousState.dictionaryTagNames != null) {
                                        dictionaryTagsComponent.deleteTagNames(previousState.dictionaryTagNames.filter { element -> element.selectedForDelete }.map { element -> element.tagName })
                                    }

                                    previousState.copy(edit = false, editCommitConfirm = false)
                                }
                                DictionaryTagsActivityEditCancelIntent -> previousState.copy(dictionaryTagNames = previousState.dictionaryTagNames?.map { element ->
                                    DictionaryTagNameAdapterObject(element.tagName, deleteSelectionEnabled = false, selectedForDelete = false, selected = element.selected, tags = element.tags)
                                }, edit = false)
                                DictionaryTagsActivityEditCommitIntent -> previousState.copy(editCommitConfirm = true)
                                is DictionaryTagsActivityEditSelectForDeletionIntent -> run {
                                    previousState.copy(dictionaryTagNames = previousState.dictionaryTagNames?.map { element ->
                                        DictionaryTagNameAdapterObject(element.tagName, deleteSelectionEnabled = element.deleteSelectionEnabled,
                                                selectedForDelete = if (action.tag.tagId == element.tagName.tagId) { action.select } else { element.selectedForDelete },
                                                selected = element.selected, tags = element.tags)
                                    })
                                }
                                is DictionaryTagsActivityToggleSelectIntent -> run {
                                    if (previousState.seq != null) {
                                        val previousStateTag = previousState.dictionaryTagNames?.find { tag ->
                                            tag.tagName.tagId == action.tag.tagId
                                        }
                                        val previousSelected = previousStateTag != null && previousStateTag.selected

                                        if (previousSelected) {
                                            dictionaryTagsComponent.deleteTag(DictionaryTag(previousState.seq, action.tag.tagId))
                                        } else {
                                            dictionaryTagsComponent.createTag(previousState.seq, action.tag.tagId)
                                        }
                                    }

                                    previousState

                                    /*previousState.copy(dictionaryTagNames = previousState.dictionaryTagNames?.map { element ->
                                        DictionaryTagNameAdapterObject(element.tagName, deleteSelectionEnabled = element.deleteSelectionEnabled,
                                                selected = if (action.tag.tagId == element.tagName.tagId) { !element.selected } else { element.selected },
                                                selectedForDelete = element.selectedForDelete, tags = element.tags)
                                    })*/
                                }
                            }
                        })
                .observeOn(AndroidSchedulers.mainThread())
    }
}