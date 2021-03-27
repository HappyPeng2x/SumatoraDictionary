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
package org.happypeng.sumatora.android.sumatoradictionary.model

import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.ViewModel
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.PublishSubject
import io.reactivex.rxjava3.subjects.Subject
import org.happypeng.sumatora.android.sumatoradictionary.component.DictionaryTagsComponent
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryTagName
import org.happypeng.sumatora.android.sumatoradictionary.model.intent.*
import org.happypeng.sumatora.android.sumatoradictionary.model.processor.DictionaryTagsActivityProcessorHolder
import org.happypeng.sumatora.android.sumatoradictionary.model.result.DictionaryTagsActivityResult
import org.happypeng.sumatora.android.sumatoradictionary.model.state.DictionaryTagsActivityState
import org.happypeng.sumatora.android.sumatoradictionary.model.transformer.DictionaryTagsActivityIntentTransformer
import org.happypeng.sumatora.android.sumatoradictionary.mvibase.MviViewModel

class DictionaryTagsModel @ViewModelInject constructor(private val dictionaryTagsComponent: DictionaryTagsComponent): ViewModel(),
        MviViewModel<DictionaryTagsActivityIntent, DictionaryTagsActivityState> {
    private val intentsSubject: PublishSubject<DictionaryTagsActivityIntent> = PublishSubject.create()
    private val statesObservable: Observable<DictionaryTagsActivityState> = compose()
    private val clearedSubject: Subject<Unit> = PublishSubject.create()
    private val clearedObservable = clearedSubject as Observable<Unit>

    private fun compose(): Observable<DictionaryTagsActivityState> {
        val actionProcessorHolder = DictionaryTagsActivityProcessorHolder(dictionaryTagsComponent)

        return intentsSubject
                .compose(DictionaryTagsActivityIntentTransformer())
                .compose(actionProcessorHolder.actionProcessor)
                .scan(DictionaryTagsActivityState(closed = false,
                        dictionaryTagNames = null, add = false, edit = false,
                        editCommitConfirm = false, seq = null), this::transformStatus)
                .distinctUntilChanged()
                .replay(1)
                .autoConnect(0)
    }

    private fun transformStatus(previousState: DictionaryTagsActivityState, result: DictionaryTagsActivityResult): DictionaryTagsActivityState {
        return DictionaryTagsActivityState(closed = result.close,
                dictionaryTagNames = result.dictionaryTagNames,
                add = result.add, edit = result.edit, editCommitConfirm = result.editCommitConfirm,
                seq = null)
    }

    override fun states(): Observable<DictionaryTagsActivityState> = statesObservable

    override fun processIntents(intents: Observable<DictionaryTagsActivityIntent>) {
        intents.takeUntil(clearedObservable).subscribe(intentsSubject::onNext)
    }

    override fun onCleared() {
        clearedSubject.onNext(Unit)
        clearedSubject.onComplete()

        super.onCleared()
    }

    fun addTag() {
        processIntents(Observable.just(DictionaryTagsActivityAddIntent))
    }

    fun commitEditMode() {
        processIntents(Observable.just(DictionaryTagsActivityEditCommitIntent))
    }

    fun commitConfirmEditMode() {
        processIntents(Observable.just(DictionaryTagsActivityEditCommitConfirmIntent))
    }

    fun enterEditMode() {
        processIntents(Observable.just(DictionaryTagsActivityEditIntent))
    }

    fun exitEditMode() {
        processIntents(Observable.just(DictionaryTagsActivityEditCancelIntent))
    }

    fun selectForDeletion(selected: Boolean, tagName: DictionaryTagName) {
        processIntents(Observable.just(DictionaryTagsActivityEditSelectForDeletionIntent(tagName, selected)))
    }

    fun toggleSelect(tagName: DictionaryTagName) {
        processIntents(Observable.just(DictionaryTagsActivityToggleSelectIntent(tagName)))
    }

    fun createTagName(createTagName: String) {
        processIntents((Observable.just(DictionaryTagsActivityCreateTagNameIntent(createTagName))))
    }

    init {
        processIntents(dictionaryTagsComponent.dictionaryTags.map { DictionaryTagsActivityUpdateTagsIntent(it) })
    }
}