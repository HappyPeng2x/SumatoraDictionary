/* Sumatora Dictionary
        Copyright (C) 2019 Nicolas Centa

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

import io.reactivex.rxjava3.annotations.NonNull
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.ObservableSource
import io.reactivex.rxjava3.core.ObservableTransformer

class ScanConcatMap<U, D>(private val operator: (D?, U) -> Observable<D>) : ObservableTransformer<U, D> {
    private class ScanConcatMapImpl<U, D>(val operator: (D?, U) -> Observable<D>) : ObservableTransformer<U, D> {
        var lastStatus: D? = null
        override fun apply(upstream: @NonNull Observable<U>?): @NonNull ObservableSource<D>? {
            return upstream!!.map { up: U ->
                operator.invoke(lastStatus, up)
                        .doOnNext { status -> lastStatus = status }
            }.concatMap { obs -> obs }
        }
    }

    override fun apply(upstream: @NonNull Observable<U>?): @NonNull ObservableSource<D>? {
        return upstream!!.compose(ScanConcatMapImpl(operator))
    }
}