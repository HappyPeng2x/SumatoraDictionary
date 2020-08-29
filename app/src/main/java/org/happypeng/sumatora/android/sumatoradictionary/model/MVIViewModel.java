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

import org.happypeng.sumatora.android.sumatoradictionary.model.intent.CloseIntent;
import org.happypeng.sumatora.android.sumatoradictionary.model.intent.MVIIntent;
import org.happypeng.sumatora.android.sumatoradictionary.model.status.MVIStatus;
import org.happypeng.sumatora.android.sumatoradictionary.operator.ScanConcatMap;

import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subjects.Subject;

public abstract class MVIViewModel<S extends MVIStatus> extends ViewModel {
    final private Subject<MVIIntent> intentSubject = PublishSubject.create();
    final private Subject<S> statusSubject = BehaviorSubject.create();

    public MVIViewModel() {
    }

    @NonNull protected abstract List<Observable<MVIIntent>> getIntentObservablesToMerge();
    @NonNull public abstract S getInitialStatus();
    @NonNull public abstract Observable<S> transformStatus(S previousStatus, MVIIntent intent);

    protected void connectIntents() {
        final List<Observable<MVIIntent>> observableList = getIntentObservablesToMerge();

        observableList.add(intentSubject);

        Observable.merge(observableList)
                .compose(new ScanConcatMap<>(new ScanConcatMap.ScanOperator<MVIIntent, S>() {
                    @Override
                    public Observable<S> apply(S lastStatus, MVIIntent newUpstream) {
                        return transformStatus(lastStatus == null ? getInitialStatus() : lastStatus,
                                newUpstream);
                    }
                }))
                .takeUntil(MVIStatus::getClosed)
                .subscribeWith(statusSubject);
    }

    public void sendIntent(final MVIIntent intent) { intentSubject.onNext(intent); }

    @Override
    protected void onCleared() {
        sendIntent(new CloseIntent());
        intentSubject.onComplete();
    }

    public Observable<S> getStatusObservable() {
        return statusSubject;
    }
}
