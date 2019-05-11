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
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryDatabase;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchElement;

import java.util.List;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

public class DictionaryBookmarkFragmentModel extends AndroidViewModel {
    private DictionaryApplication mApp;

    private MediatorLiveData<List<DictionarySearchElement>> mBookmarkElements;
    private LiveData<List<DictionarySearchElement>> mBookmarkElementsLive;

    public LiveData<List<DictionarySearchElement>> getBookmarks() { return mBookmarkElements; }

    public DictionaryBookmarkFragmentModel(Application aApp) {
        super(aApp);

        mApp = (DictionaryApplication) aApp;

        mBookmarkElements = new MediatorLiveData<>();

        mBookmarkElements.addSource(mApp.getDictionaryDatabase(),
                new Observer<DictionaryDatabase>() {
                    @Override
                    public void onChanged(DictionaryDatabase dictionaryDatabase) {
                        if (dictionaryDatabase != null && mApp.getSettings().getLang().getValue() != null) {
                            mBookmarkElementsLive = dictionaryDatabase.dictionaryBookmarkDao().getAllDetailsLive(mApp.getSettings().getLang().getValue(),
                                    mApp.getSettings().getBackupLang().getValue());

                            mBookmarkElements.addSource(mBookmarkElementsLive,
                                    new Observer<List<DictionarySearchElement>>() {
                                        @Override
                                        public void onChanged(List<DictionarySearchElement> dictionarySearchElements) {
                                            mBookmarkElements.setValue(dictionarySearchElements);
                                        }
                                    });
                        } else {
                            if (mBookmarkElementsLive != null) {
                                mBookmarkElements.removeSource(mBookmarkElementsLive);
                                mBookmarkElementsLive = null;
                            }
                        }
                    }
                });

        mBookmarkElements.addSource(mApp.getSettings().getLang(),
                new Observer<String>() {
                    @Override
                    public void onChanged(String s) {
                        if (mBookmarkElementsLive != null) {
                            mBookmarkElements.removeSource(mBookmarkElementsLive);
                            mBookmarkElementsLive = null;
                        }

                        if (mApp.getDictionaryDatabase().getValue() != null) {
                            mBookmarkElementsLive = mApp.getDictionaryDatabase().getValue().dictionaryBookmarkDao().getAllDetailsLive(mApp.getSettings().getLang().getValue(),
                                    mApp.getSettings().getBackupLang().getValue());

                            mBookmarkElements.addSource(mBookmarkElementsLive,
                                    new Observer<List<DictionarySearchElement>>() {
                                        @Override
                                        public void onChanged(List<DictionarySearchElement> dictionarySearchElements) {
                                            mBookmarkElements.setValue(dictionarySearchElements);
                                        }
                                    });
                        }
                    }
                });
    }

    public void deleteBookmark(final long aSeq) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                if (mApp.getDictionaryDatabase().getValue() != null) {
                    mApp.getDictionaryDatabase().getValue().dictionaryBookmarkDao().delete(aSeq);
                }

                return null;
            }
        }.execute();
    }

    public DictionaryApplication getDictionaryApplication() { return mApp; }
}
