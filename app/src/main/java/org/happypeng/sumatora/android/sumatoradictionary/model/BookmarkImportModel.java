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

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.hilt.Assisted;
import androidx.hilt.lifecycle.ViewModelInject;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.SavedStateHandle;
import androidx.paging.PagedList;

import org.happypeng.sumatora.android.sumatoradictionary.component.BookmarkImportComponent;
import org.happypeng.sumatora.android.sumatoradictionary.component.LanguageSettingsComponent;
import org.happypeng.sumatora.android.sumatoradictionary.component.PersistentDatabaseComponent;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchElement;
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentLanguageSettings;
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.BookmarkImportQueryTool;
import org.happypeng.sumatora.android.sumatoradictionary.model.intent.BookmarkImportCancelIntent;
import org.happypeng.sumatora.android.sumatoradictionary.model.intent.BookmarkImportCommitIntent;
import org.happypeng.sumatora.android.sumatoradictionary.model.intent.BookmarkImportFileOpenIntent;
import org.happypeng.sumatora.android.sumatoradictionary.model.intent.LanguageSettingAttachedIntent;
import org.happypeng.sumatora.android.sumatoradictionary.model.intent.LanguageSettingIntent;
import org.happypeng.sumatora.android.sumatoradictionary.model.intent.MVIIntent;
import org.happypeng.sumatora.android.sumatoradictionary.model.status.BookmarkImportStatus;

import java.util.LinkedList;
import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.core.ObservableEmitter;
import io.reactivex.rxjava3.core.ObservableOnSubscribe;

public class BookmarkImportModel extends BaseFragmentModel<BookmarkImportStatus> {
    final private BookmarkImportComponent bookmarkImportComponent;

    @Override
    protected boolean disableBookmarkButton() { return true; }

    @Override
    protected boolean disableMemoEdit() { return true; }

    @ViewModelInject
    public BookmarkImportModel(final PersistentDatabaseComponent persistentDatabaseComponent,
                               final LanguageSettingsComponent languageSettingsComponent,
                               final BookmarkImportComponent bookmarkImportComponent,
                               @Assisted SavedStateHandle savedStateHandle) {
        super(persistentDatabaseComponent, languageSettingsComponent);

        this.bookmarkImportComponent = bookmarkImportComponent;

        connectIntents();
    }

    public void bookmarkImportFileOpen(final Uri uri) {
        sendIntent(new BookmarkImportFileOpenIntent(uri));
    }

    public void bookmarkImportCommit() {
        sendIntent(new BookmarkImportCommitIntent());
    }

    public void bookmarkImportCancel() {
        sendIntent(new BookmarkImportCancelIntent());
    }

    @NonNull
    @Override
    protected List<Observable<MVIIntent>> getIntentObservablesToMerge() {
        final List<Observable<MVIIntent>> observables = new LinkedList<>();

        observables.add(languageSettingsComponent.getPersistentLanguageSettings().cast(MVIIntent.class));

        return observables;
    }

    @NonNull
    @Override
    public BookmarkImportStatus getInitialStatus() {
        return new BookmarkImportStatus(3, null, false, null, false);
    }

    @NonNull
    @Override
    public Observable<BookmarkImportStatus> transformStatus(BookmarkImportStatus previousStatus, MVIIntent intent) {
        return Observable.create(new ObservableOnSubscribe<BookmarkImportStatus>() {
            @Override
            public void subscribe(@io.reactivex.rxjava3.annotations.NonNull ObservableEmitter<BookmarkImportStatus> emitter) throws Throwable {
                if (intent instanceof BookmarkImportFileOpenIntent) {
                    bookmarkImportComponent.processURI(((BookmarkImportFileOpenIntent) intent).getUri(),
                            previousStatus.getKey());

                    emitter.onNext(new BookmarkImportStatus(previousStatus.getKey(),
                            previousStatus.getQueryTool(),
                            false,
                            previousStatus.getPersistentLanguageSettings(),
                            false));
                    emitter.onComplete();

                    return;
                }

                if (intent instanceof BookmarkImportCommitIntent) {
                    if (!previousStatus.getExecuted()) {
                        emitter.onNext(previousStatus);
                        emitter.onComplete();

                        return;
                    }

                    bookmarkImportComponent.commitBookmarks(previousStatus.getKey());

                    emitter.onNext(new BookmarkImportStatus(previousStatus.getKey(),
                            previousStatus.getQueryTool(),
                            previousStatus.getExecuted(),
                            previousStatus.getPersistentLanguageSettings(),
                            true));
                    emitter.onComplete();

                    return;
                }

                if (intent instanceof BookmarkImportCancelIntent) {
                    bookmarkImportComponent.cancelImport(previousStatus.getKey());

                    emitter.onNext(new BookmarkImportStatus(previousStatus.getKey(),
                            previousStatus.getQueryTool(),
                            previousStatus.getExecuted(),
                            previousStatus.getPersistentLanguageSettings(),
                            true));
                    emitter.onComplete();

                    return;
                }

                final PersistentLanguageSettings persistentLanguageSettings = intent instanceof LanguageSettingIntent ? ((LanguageSettingIntent) intent).getLanguageSettings() : previousStatus.getPersistentLanguageSettings();
                final BookmarkImportQueryTool queryTool = intent instanceof LanguageSettingIntent ? ((intent instanceof LanguageSettingAttachedIntent ? new BookmarkImportQueryTool(persistentDatabaseComponent,
                        previousStatus.getKey(), persistentLanguageSettings) : null)) : previousStatus.getQueryTool();
                final boolean executed = !(intent instanceof LanguageSettingIntent) && previousStatus.getExecuted();

                if (intent instanceof LanguageSettingIntent && previousStatus.getQueryTool() != null) {
                    previousStatus.getQueryTool().close();
                }

                if (!executed && queryTool != null) {
                    persistentDatabaseComponent.getDatabase().runInTransaction(() -> {
                        if (intent instanceof LanguageSettingIntent) {
                            queryTool.delete();
                        }

                        queryTool.execute();
                    });

                    emitter.onNext(new BookmarkImportStatus(previousStatus.getKey(),
                            queryTool, true,
                            persistentLanguageSettings,
                            false));
                    emitter.onComplete();

                    return;
                }

                emitter.onNext(new BookmarkImportStatus(previousStatus.getKey(),
                        queryTool, previousStatus.getExecuted(),
                        persistentLanguageSettings, false));
                emitter.onComplete();

                return;
            }
        });


    }

    @Override
    protected LiveData<PagedList<DictionarySearchElement>> getPagedList(final PagedList.BoundaryCallback<DictionarySearchElement> boundaryCallback) {
        return persistentDatabaseComponent.getSearchElements(getInitialStatus().getKey(), boundaryCallback);
    }

    @Override
    protected void commitBookmarks(long seq, long bookmark, String memo) {

    }
}
