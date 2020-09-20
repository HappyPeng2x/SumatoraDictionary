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

package org.happypeng.sumatora.android.sumatoradictionary.transformer

import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.ObservableEmitter
import io.reactivex.rxjava3.core.ObservableSource
import io.reactivex.rxjava3.core.ObservableTransformer
import org.happypeng.sumatora.android.sumatoradictionary.model.action.*
import org.happypeng.sumatora.android.sumatoradictionary.model.intent.*

class ImportIntentTransformer: ObservableTransformer<ImportIntent, ImportAction> {
    override fun apply(upstream: Observable<ImportIntent>): ObservableSource<ImportAction> {
        return upstream.publish {
            it.publish { shared ->
                Observable.merge(
                        listOf(
                                shared.filter { intent -> intent is ImportCommitIntent }.map { ImportCommitAction },
                                shared.filter { intent -> intent is ImportCancelIntent }.map { ImportCancelAction },
                                shared.filter { intent -> intent is ImportLanguageSettingDetachedIntent }.map { ImportLanguageSettingDetachedAction },
                                shared.ofType(ImportLanguageSettingAttachedIntent::class.java).map { intent ->
                                    ImportLanguageSettingAttachedAction(intent.persistentLanguageSettings)
                                },
                                shared.ofType(ImportFileIntent::class.java).flatMap { intent ->
                                    Observable.create { emitter: ObservableEmitter<ImportAction> ->
                                        emitter.onNext(ImportSetProcessingAction)
                                        emitter.onNext(ImportFileAction(intent.uri))
                                        emitter.onComplete()
                                    }
                                }
                        ))
            }
        }
    }
}
