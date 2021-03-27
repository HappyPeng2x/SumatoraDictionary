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
package org.happypeng.sumatora.android.sumatoradictionary.model.transformer

import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.ObservableSource
import io.reactivex.rxjava3.core.ObservableTransformer
import org.happypeng.sumatora.android.sumatoradictionary.model.action.*
import org.happypeng.sumatora.android.sumatoradictionary.model.intent.*

class DictionaryTagsActivityIntentTransformer: ObservableTransformer<DictionaryTagsActivityIntent, DictionaryTagsActivityAction> {
    override fun apply(upstream: Observable<DictionaryTagsActivityIntent>): ObservableSource<DictionaryTagsActivityAction> {
        return upstream.publish {
            it.publish { shared ->
                Observable.merge(
                        listOf(
                                shared.ofType (DictionaryTagsActivityUpdateTagsIntent::class.java).map { intent -> DictionaryTagsActivityUpdateTagsAction(intent.tags) },
                                shared.filter { intent -> intent is DictionaryTagsActivityCloseIntent }.map { DictionaryTagsActivityCloseAction },
                                shared.filter { intent -> intent is DictionaryTagsActivityAddIntent }.map { DictionaryTagsActivityAddAction },
                                shared.filter { intent -> intent is DictionaryTagsActivityAddCancelIntent }.map { DictionaryTagsActivityAddCancelAction },
                                shared.ofType(DictionaryTagsActivityCreateTagNameIntent::class.java).map { intent -> DictionaryTagsActivityCreateTagNameAction(intent.name) },
                                shared.ofType(DictionaryTagsActivityEditSelectForDeletionIntent::class.java).map { intent -> DictionaryTagsActivityEditSelectForDeletionAction(intent.tag, intent.select) },
                                shared.filter { intent -> intent is DictionaryTagsActivityEditIntent }.map { DictionaryTagsActivityEditAction },
                                shared.filter { intent -> intent is DictionaryTagsActivityEditCancelIntent }.map { DictionaryTagsActivityEditCancelAction },
                                shared.filter { intent -> intent is DictionaryTagsActivityEditCommitIntent }.map { DictionaryTagsActivityEditCommitAction },
                                shared.filter { intent -> intent is DictionaryTagsActivityEditCommitConfirmIntent }.map { DictionaryTagsActivityEditCommitConfirmAction },
                                shared.ofType(DictionaryTagsActivityToggleSelectIntent::class.java).map { intent -> DictionaryTagsActivityToggleSelectAction(intent.tag) },
                                shared.ofType(DictionaryTagsActivitySetSeqIntent::class.java).map { intent -> DictionaryTagsActivitySetSeqAction(intent.seq) }
                                )
                )
            }
        }
    }
}