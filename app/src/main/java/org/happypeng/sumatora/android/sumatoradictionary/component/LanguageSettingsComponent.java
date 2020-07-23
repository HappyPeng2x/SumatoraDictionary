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

package org.happypeng.sumatora.android.sumatoradictionary.component;

import androidx.annotation.MainThread;
import androidx.core.util.Pair;

import org.happypeng.sumatora.android.sumatoradictionary.db.InstalledDictionary;
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentDatabase;
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentLanguageSettings;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Singleton;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.ObservableOnSubscribe;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.BehaviorSubject;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subjects.Subject;

@Singleton
public class LanguageSettingsComponent {
    private final Subject<PersistentLanguageSettings> persistentLanguageSettingsSubject;
    private final Observable<Pair<PersistentLanguageSettings, Boolean>> persistentLanguageSettingsStatus;

    @Inject
    LanguageSettingsComponent(final PersistentDatabaseComponent persistentDatabaseComponent) {
        this.persistentLanguageSettingsSubject = PublishSubject.create();

        final Observable<PersistentLanguageSettings> persistentLanguageSettingsWithInitial =
                Observable.concat(Observable.fromCallable(() ->
                        persistentDatabaseComponent.getDatabase().persistentLanguageSettingsDao().getLanguageSettingsDirect(0))
                                .subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()),
                        persistentLanguageSettingsSubject);

        this.persistentLanguageSettingsStatus =
                persistentLanguageSettingsWithInitial.observeOn(Schedulers.io())
                .concatMap(s ->
                    Observable.create((ObservableOnSubscribe<Pair<PersistentLanguageSettings, Boolean>>) emitter -> {
                        emitter.onNext(new Pair<>(s, false));

                        final PersistentDatabase database = persistentDatabaseComponent.getDatabase();
                        final List<InstalledDictionary> dictionaries = database.installedDictionaryDao().getAll();

                        for (InstalledDictionary d : dictionaries) {
                            if (d.type.equals("jmdict_translation") || d.type.equals("tatoeba")) {
                                if (d.lang.equals(s.lang) ||
                                        (d.lang.equals(s.backupLang))) {
                                    d.attach(database);
                                } else {
                                    d.detach(database);
                                }
                            }
                        }

                        database.runInTransaction(() -> {
                            database.persistentLanguageSettingsDao().update(s);
                        });

                        emitter.onNext(new Pair<>(s, true));
                        emitter.onComplete();
                    })
                ).share().replay(1).autoConnect();
    }

    public Observable<Pair<PersistentLanguageSettings, Boolean>> getPersistentLanguageSettings() {
        return persistentLanguageSettingsStatus;
    }

    @MainThread
    public void updatePersistentLanguageSettings(final PersistentLanguageSettings persistentLanguageSettings) {
        persistentLanguageSettingsSubject.onNext(persistentLanguageSettings);
    }
}
