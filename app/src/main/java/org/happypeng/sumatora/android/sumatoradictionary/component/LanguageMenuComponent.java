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

import android.content.Context;
import android.view.Menu;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.happypeng.sumatora.android.sumatoradictionary.db.InstalledDictionary;
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentLanguageSettings;

import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.qualifiers.ActivityContext;
import dagger.hilt.android.scopes.ActivityScoped;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.observers.DisposableSingleObserver;
import io.reactivex.rxjava3.schedulers.Schedulers;

@ActivityScoped
public class LanguageMenuComponent {
    private final Context context;
    private final PersistentDatabaseComponent persistentDatabaseComponent;
    private final LanguageSettingsComponent languageSettingsComponent;

    @Inject
    LanguageMenuComponent(@ActivityContext final Context context,
                          final PersistentDatabaseComponent persistentDatabaseComponent,
                          final LanguageSettingsComponent languageSettingsComponent) {
        this.context = context;
        this.persistentDatabaseComponent = persistentDatabaseComponent;
        this.languageSettingsComponent = languageSettingsComponent;
    }

    private static class InitializationElements {
        final List<InstalledDictionary> installedDictionaries;
        final PersistentLanguageSettings persistentLanguageSettings;

        InitializationElements(final @NonNull List<InstalledDictionary> installedDictionaries,
                               final @NonNull PersistentLanguageSettings persistentLanguageSettings) {
            this.installedDictionaries = installedDictionaries;
            this.persistentLanguageSettings = persistentLanguageSettings;
        }
    }

    public Disposable initLanguagePopupMenu(final TextView anchor) {
        CompositeDisposable compositeDisposable = new CompositeDisposable();

        compositeDisposable.add(Single.fromCallable(() ->
                new InitializationElements(persistentDatabaseComponent.getDatabase().installedDictionaryDao().getAll(),
                        persistentDatabaseComponent.getDatabase().persistentLanguageSettingsDao().getLanguageSettingsDirect(0)))
                .subscribeOn(Schedulers.io()).observeOn(AndroidSchedulers.mainThread()).subscribeWith(new DisposableSingleObserver<InitializationElements>() {
                    @Override
                    public void onSuccess(InitializationElements initializationElements) {
                        PopupMenu languagePopupMenu = new PopupMenu(context, anchor);

                        anchor.setText(initializationElements.persistentLanguageSettings.lang);

                        anchor.setOnClickListener(v -> {
                            languagePopupMenu.show();
                        });

                        Menu menu = languagePopupMenu.getMenu();

                        for (final InstalledDictionary l : initializationElements.installedDictionaries) {
                            if ("jmdict_translation".equals(l.type)) {
                                menu.add(l.description).setOnMenuItemClickListener(item -> {
                                    final PersistentLanguageSettings languageSettings = new PersistentLanguageSettings();
                                    languageSettings.lang = l.lang;
                                    languageSettings.backupLang = l.lang.equals("eng") ? null : "eng";

                                    languageSettingsComponent.updatePersistentLanguageSettings(languageSettings);

                                    return false;
                                });
                            }
                        }
                    }

                    @Override
                    public void onError(@io.reactivex.rxjava3.annotations.NonNull Throwable e) {
                        e.printStackTrace();
                    }
                }));


        compositeDisposable.add(languageSettingsComponent.getPersistentLanguageSettings().observeOn(AndroidSchedulers.mainThread())
                .subscribe(p ->{
                    if (p.first != null) {
                        anchor.setText(p.first.lang);
                    }
                }));

        return compositeDisposable;
    }
}
