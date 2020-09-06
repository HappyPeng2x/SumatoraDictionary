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

import androidx.lifecycle.ViewModel
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.BehaviorSubject
import io.reactivex.rxjava3.subjects.PublishSubject
import io.reactivex.rxjava3.subjects.Subject
import org.happypeng.sumatora.android.sumatoradictionary.model.intent.CloseIntent
import org.happypeng.sumatora.android.sumatoradictionary.model.intent.MVIIntent
import org.happypeng.sumatora.android.sumatoradictionary.model.status.MVIStatus
import org.happypeng.sumatora.android.sumatoradictionary.operator.ScanConcatMap

abstract class MVIViewModel<S : MVIStatus> : ViewModel() {
    private val intentSubject: Subject<MVIIntent> = PublishSubject.create()
    private val statusSubject: Subject<S> = BehaviorSubject.create()
    protected abstract fun getIntentObservablesToMerge(): MutableList<Observable<MVIIntent>>
    abstract val initialStatus: S
    abstract fun transformStatus(previousStatus: S, intent: MVIIntent): Observable<S>

    protected fun connectIntents() {
        val observableList = getIntentObservablesToMerge()
        observableList.add(intentSubject)

        Observable.merge(observableList)
                .compose(ScanConcatMap<MVIIntent, S> { lastStatus: S?, newUpstream: MVIIntent ->
                    transformStatus(lastStatus ?: initialStatus, newUpstream)
                })
                .takeUntil(MVIStatus::closed)
                .subscribeWith(statusSubject)
    }

    fun sendIntent(intent: MVIIntent) {
        intentSubject.onNext(intent)
    }

    override fun onCleared() {
        sendIntent(CloseIntent)
        intentSubject.onComplete()
    }

    val statusObservable: Observable<S>
        get() = statusSubject
}