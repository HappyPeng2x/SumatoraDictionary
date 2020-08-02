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

package org.happypeng.sumatora.android.sumatoradictionary.model;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;

import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import io.reactivex.rxjava3.subjects.Subject;

public abstract class MVIViewModel<I, S, V> extends ViewModel {
    final Subject<V> viewStatusSubject;

    final CompositeDisposable compositeDisposable = new CompositeDisposable();

    public MVIViewModel() {
        final Subject<V> statusSubjectUnsafe = BehaviorSubject.create();
        viewStatusSubject = statusSubjectUnsafe.toSerialized();
    }

    @NonNull protected abstract List<Observable<I>> getIntentObservablesToMerge();
    @NonNull public abstract S getInitialStatus();
    @NonNull public abstract S transformStatus(S previousStatus, I intent);

    protected void connectIntents() {
        compositeDisposable.add(Observable.merge(getIntentObservablesToMerge())
                .observeOn(Schedulers.io())
                .scan(getInitialStatus(), this::transformStatus)
                .subscribe());
    }

    public void updateView(final V viewStatus) {
        viewStatusSubject.onNext(viewStatus);
    }

    @Override
    protected void onCleared() {
        compositeDisposable.dispose();
    }

    public Observable<V> getStatusObservable() {
        return viewStatusSubject.observeOn(AndroidSchedulers.mainThread());
    }
}
