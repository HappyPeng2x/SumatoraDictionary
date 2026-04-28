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
package org.happypeng.sumatora.android.sumatoradictionary.operator

import androidx.lifecycle.LiveData
import androidx.lifecycle.Observer
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.PublishSubject
import io.reactivex.rxjava3.subjects.Subject

object LiveDataWrapper {
    // Add : Any to T to ensure it matches LiveData's bounds
    fun <T : Any, C : Any> wrap(
        liveData: LiveData<T>,
        close: Observable<C>
    ): Observable<T> {
        val subject: Subject<T> = PublishSubject.create()

        // Store the observer in a variable so we can remove it later
        val observer = Observer<T> { t ->
            subject.onNext(t)
        }

        liveData.observeForever(observer)

        return subject
            .takeUntil(close)
            .doOnTerminate {
                // Use the stored reference to properly remove the observer
                liveData.removeObserver(observer)
            }
    }
}