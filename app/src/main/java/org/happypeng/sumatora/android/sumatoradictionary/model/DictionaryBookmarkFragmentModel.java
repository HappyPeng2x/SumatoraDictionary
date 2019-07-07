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

package org.happypeng.sumatora.android.sumatoradictionary.model;

import android.app.Application;
import android.os.AsyncTask;

import org.happypeng.sumatora.android.sumatoradictionary.DictionaryApplication;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchElement;
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentDatabase;
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.BookmarkTool;
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.Settings;

import java.util.List;

import androidx.arch.core.util.Function;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.Transformations;

public class DictionaryBookmarkFragmentModel extends AndroidViewModel {
    private DictionaryApplication mApp;

    public static class Status {
        public String lang;
        public String backupLang;
        public PersistentDatabase database;
        public BookmarkTool bookmarkTool;
        public List<DictionarySearchElement> bookmarkElements;

        void copyFrom(Status aStatus) {
            lang = aStatus.lang;
            backupLang = aStatus.backupLang;
            database = aStatus.database;
            bookmarkTool = aStatus.bookmarkTool;
            bookmarkElements = aStatus.bookmarkElements;
        }

        private void processChange(final MutableLiveData<Status> aLiveData) {
            if (isInitialized()) {
                aLiveData.setValue(this);
            }
        }

        private void performUpdate() {
            if (isReady()) {
                bookmarkTool.performBookmarkInsertion(lang, backupLang);
            }
        }

        private boolean isReady() {
            return lang != null && backupLang != null && database != null && bookmarkTool != null;
        }

        public boolean isInitialized() {
            return isReady() && bookmarkElements != null;
        }
    }

    final private MediatorLiveData<Status> m_status;

    public LiveData<Status> getStatus() { return m_status; }

    public DictionaryBookmarkFragmentModel(Application aApp) {
        super(aApp);

        mApp = (DictionaryApplication) aApp;

        m_status = new MediatorLiveData<>();
        final Status status = new Status();

        m_status.addSource(mApp.getBookmarkTool(),
                new Observer<BookmarkTool>() {
                    @Override
                    public void onChanged(BookmarkTool bookmarkTool) {
                        status.bookmarkTool = bookmarkTool;

                        status.processChange(m_status);
                    }
                });

        m_status.addSource(mApp.getSettings().getValue(Settings.LANG),
                new Observer<String>() {
                    @Override
                    public void onChanged(String s) {
                        status.lang = s;

                        status.performUpdate();
                        status.processChange(m_status);
                    }
                });

        m_status.addSource(mApp.getSettings().getValue(Settings.BACKUP_LANG),
                new Observer<String>() {
                    @Override
                    public void onChanged(String s) {
                        status.backupLang = s;

                        status.performUpdate();
                        status.processChange(m_status);
                    }
                });

        m_status.addSource(mApp.getPersistentDatabase(),
                new Observer<PersistentDatabase>() {
                    @Override
                    public void onChanged(PersistentDatabase dictionaryDatabase) {
                        status.database = dictionaryDatabase;

                        status.processChange(m_status);
                    }
                });

        final LiveData<Long> firstBookmark =
                Transformations.switchMap(mApp.getPersistentDatabase(),
                        new Function<PersistentDatabase, LiveData<Long>>() {
                            @Override
                            public LiveData<Long> apply(PersistentDatabase input) {
                                return input.dictionaryBookmarkDao().getFirstLive();
                            }
                        });

        m_status.addSource(firstBookmark,
                new Observer<Long>() {
                    @Override
                    public void onChanged(Long aLong) {
                        if (status.isInitialized()) {
                            status.performUpdate();
                        }
                    }
                });

        final LiveData<List<DictionarySearchElement>> bookmarkElements =
                Transformations.switchMap(mApp.getPersistentDatabase(),
                        new Function<PersistentDatabase, LiveData<List<DictionarySearchElement>>>() {
                            @Override
                            public LiveData<List<DictionarySearchElement>> apply(PersistentDatabase input) {
                                return input.dictionaryBookmarkElementDao().getAllDetailsLive();
                            }
                        });

        m_status.addSource(bookmarkElements,
                new Observer<List<DictionarySearchElement>>() {
                    @Override
                    public void onChanged(List<DictionarySearchElement> dictionarySearchElements) {
                        status.bookmarkElements = dictionarySearchElements;

                        status.processChange(m_status);
                    }
                });
    }

    public void updateStatus() {
        if (m_status.getValue() != null && m_status.getValue().isInitialized()) {
            m_status.setValue(m_status.getValue());
        }
    }

    public void deleteBookmark(final long aSeq) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                if (mApp.getPersistentDatabase().getValue() != null) {
                    mApp.getPersistentDatabase().getValue().dictionaryBookmarkDao().delete(aSeq);
                }

                return null;
            }
        }.execute();
    }

    public DictionaryApplication getDictionaryApplication() { return mApp; }
}
