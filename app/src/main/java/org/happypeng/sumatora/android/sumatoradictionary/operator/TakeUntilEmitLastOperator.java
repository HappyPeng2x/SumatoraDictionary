package org.happypeng.sumatora.android.sumatoradictionary.operator;

import io.reactivex.rxjava3.annotations.NonNull;
import io.reactivex.rxjava3.core.ObservableOperator;
import io.reactivex.rxjava3.core.Observer;
import io.reactivex.rxjava3.disposables.Disposable;

public class TakeUntilEmitLastOperator<T> implements ObservableOperator<T, T> {
    public static abstract class Predicate<T> {
        public abstract boolean apply(T object);
    }

    final private @NonNull Predicate<T> predicate;

    public TakeUntilEmitLastOperator(final @NonNull Predicate<T> predicate) {
        this.predicate = predicate;
    }

    @Override
    public @NonNull Observer<? super T> apply(@NonNull Observer<? super T> observer) throws Throwable {
        return new Observer<T>() {
            @Override
            public void onSubscribe(@NonNull Disposable d) {
                observer.onSubscribe(d);
            }

            @Override
            public void onNext(@NonNull T t) {
                observer.onNext(t);

                if (predicate.apply(t)) {
                    observer.onComplete();
                }
            }

            @Override
            public void onError(@NonNull Throwable e) {
                observer.onError(e);
            }

            @Override
            public void onComplete() {
                observer.onComplete();
            }
        };
    }
}
