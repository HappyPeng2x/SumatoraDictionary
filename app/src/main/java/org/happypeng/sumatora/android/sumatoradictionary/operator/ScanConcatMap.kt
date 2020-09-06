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

package org.happypeng.sumatora.android.sumatoradictionary.operator;

import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.ObservableSource;
import io.reactivex.rxjava3.core.ObservableTransformer;

public class ScanConcatMap<U, D> implements ObservableTransformer<U, D> {
    public static abstract class ScanOperator<U, D> {
        public abstract Observable<D> apply(D lastStatus, U newUpstream);
    }

    private static class ScanConcatMapImpl<U, D> implements ObservableTransformer<U, D> {
        D lastStatus;
        final ScanOperator<U, D> operator;

        private ScanConcatMapImpl(final ScanOperator<U, D> operator) {
            this.operator = operator;
        }

        @Override
        public @NonNull ObservableSource<D> apply(@NonNull Observable<U> upstream) {
            return upstream.map(up -> operator.apply(lastStatus, up)
                    .doOnNext(status -> lastStatus = status)).concatMap(obs -> obs);
        }
    }

    final ScanOperator<U, D> operator;

    public ScanConcatMap(final ScanOperator<U, D> operator) {
        this.operator = operator;
    }

    @Override
    public @NonNull ObservableSource<D> apply(@NonNull Observable<U> upstream) {
        return upstream.compose(new ScanConcatMapImpl<>(operator));
    }
}