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
import org.happypeng.sumatora.android.sumatoradictionary.model.intent.LanguageSettingIntent;
import org.happypeng.sumatora.android.sumatoradictionary.model.intent.MVIIntent;
import org.happypeng.sumatora.android.sumatoradictionary.model.status.BookmarkImportStatus;

import java.util.LinkedList;
import java.util.List;

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;

public class BookmarkImportModel extends BaseFragmentModel<MVIIntent, BookmarkImportStatus, BookmarkImportStatus> {
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
        intentSubject.onNext(new BookmarkImportFileOpenIntent(uri));
    }

    public void bookmarkImportCommit() {
        intentSubject.onNext(new BookmarkImportCommitIntent());
    }

    public void bookmarkImportCancel() {
        intentSubject.onNext(new BookmarkImportCancelIntent());
    }

    @NonNull
    @Override
    protected List<Observable<MVIIntent>> getIntentObservablesToMerge() {
        final List<Observable<MVIIntent>> observables = new LinkedList<>();

        observables.add(intentSubject);
        observables.add(languageSettingsComponent.getPersistentLanguageSettings()
                .observeOn(AndroidSchedulers.mainThread())
                .map(p -> new LanguageSettingIntent(p.first, p.second)));

        return observables;
    }

    @NonNull
    @Override
    public BookmarkImportStatus getInitialStatus() {
        return new BookmarkImportStatus(3, null, false, null, false);
    }

    @NonNull
    @Override
    public BookmarkImportStatus transformStatus(BookmarkImportStatus previousStatus, MVIIntent intent) {
        if (intent instanceof BookmarkImportFileOpenIntent) {
            bookmarkImportComponent.processURI(((BookmarkImportFileOpenIntent) intent).getUri(),
                    previousStatus.getKey());

            final BookmarkImportStatus bookmarkImportStatus =  new BookmarkImportStatus(previousStatus.getKey(),
                    previousStatus.getQueryTool(),
                    false,
                    previousStatus.getPersistentLanguageSettings(),
                    false);

            updateView(bookmarkImportStatus);

            return bookmarkImportStatus;
        }

        if (intent instanceof BookmarkImportCommitIntent) {
            if (!previousStatus.getExecuted()) {
                return previousStatus;
            }

            bookmarkImportComponent.commitBookmarks(previousStatus.getKey());

            final BookmarkImportStatus bookmarkImportStatus =  new BookmarkImportStatus(previousStatus.getKey(),
                    previousStatus.getQueryTool(),
                    previousStatus.getExecuted(),
                    previousStatus.getPersistentLanguageSettings(),
                    true);

            updateView(bookmarkImportStatus);

            return bookmarkImportStatus;
        }

        if (intent instanceof BookmarkImportCancelIntent) {
            bookmarkImportComponent.cancelImport(previousStatus.getKey());

            final BookmarkImportStatus bookmarkImportStatus =  new BookmarkImportStatus(previousStatus.getKey(),
                    previousStatus.getQueryTool(),
                    previousStatus.getExecuted(),
                    previousStatus.getPersistentLanguageSettings(),
                    true);

            updateView(bookmarkImportStatus);

            return bookmarkImportStatus;
        }

        final PersistentLanguageSettings persistentLanguageSettings = intent instanceof LanguageSettingIntent ? ((LanguageSettingIntent) intent).getLanguageSettings() : previousStatus.getPersistentLanguageSettings();
        final BookmarkImportQueryTool queryTool = intent instanceof LanguageSettingIntent ? (((LanguageSettingIntent) intent).getAttached() ? new BookmarkImportQueryTool(persistentDatabaseComponent,
                previousStatus.getKey(), persistentLanguageSettings) : null) : previousStatus.getQueryTool();
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

            final BookmarkImportStatus bookmarkImportStatus = new BookmarkImportStatus(previousStatus.getKey(),
                    queryTool, true,
                    persistentLanguageSettings,
                    false);

            updateView(bookmarkImportStatus);

            return bookmarkImportStatus;
        }

        final BookmarkImportStatus bookmarkImportStatus = new BookmarkImportStatus(previousStatus.getKey(),
                queryTool, previousStatus.getExecuted(),
                persistentLanguageSettings, false);

        updateView(bookmarkImportStatus);

        return bookmarkImportStatus;
    }

    @Override
    protected LiveData<PagedList<DictionarySearchElement>> getPagedList(final PagedList.BoundaryCallback<DictionarySearchElement> boundaryCallback) {
        return persistentDatabaseComponent.getSearchElements(getInitialStatus().getKey(), boundaryCallback);
    }
}
