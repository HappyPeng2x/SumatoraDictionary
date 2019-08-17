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
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentDatabase;
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.BookmarkTool;
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.DisplayStatus;

import androidx.arch.core.util.Function;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.Transformations;

public class DictionaryBookmarkFragmentModel extends AndroidViewModel {
    private DictionaryApplication mApp;

    final private MediatorLiveData<DisplayStatus> m_status;
    private BookmarkTool m_bookmarkTool;

    public LiveData<DisplayStatus> getStatus() { return m_status; }

    public DictionaryBookmarkFragmentModel(Application aApp) {
        super(aApp);

        mApp = (DictionaryApplication) aApp;
        m_status = DisplayStatus.create(mApp, 1);

        m_bookmarkTool = null;

        final LiveData<BookmarkTool> bookmarkTool =
                Transformations.switchMap(mApp.getPersistentDatabase(),
                        new Function<PersistentDatabase, LiveData<BookmarkTool>>() {
                            @Override
                            public LiveData<BookmarkTool> apply(PersistentDatabase input) {
                                return BookmarkTool.create(input, 1);
                            }
                        });

        m_status.addSource(bookmarkTool,
                new Observer<BookmarkTool>() {
                    @Override
                    public void onChanged(BookmarkTool bookmarkTool) {
                        m_bookmarkTool = bookmarkTool;

                        if (m_bookmarkTool != null) {
                            bookmarkTool.performBookmarkInsertion();
                        }
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
                        if (m_bookmarkTool != null) {
                            m_bookmarkTool.performBookmarkInsertion();
                        }
                    }
                });
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
