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
import android.net.Uri;
import android.os.AsyncTask;

import org.happypeng.sumatora.android.sumatoradictionary.DictionaryApplication;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryBookmarkImport;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryDatabase;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchElement;
import org.happypeng.sumatora.android.sumatoradictionary.xml.DictionaryBookmarkXML;

import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.sqlite.db.SupportSQLiteDatabase;

public class DictionaryBookmarkImportActivityModel extends AndroidViewModel {
    private DictionaryApplication mApp;

    private LiveData<List<DictionarySearchElement>> m_bookmarkElements;
    private MutableLiveData<Integer> mError;

    private MediatorLiveData<List<DictionarySearchElement>> mBookmarkElements;
    private LiveData<List<DictionarySearchElement>> mBookmarkElementsLive;

    public LiveData<List<DictionarySearchElement>> getBookmarks() { return mBookmarkElements; }

    public DictionaryApplication getDictionaryApplication() { return mApp; }

    public DictionaryBookmarkImportActivityModel(Application aApp) {
        super(aApp);

        mApp = (DictionaryApplication) aApp;

        mError = new MutableLiveData<>();

        mBookmarkElements = new MediatorLiveData<>();

        mBookmarkElements.addSource(mApp.getDictionaryDatabase(),
                new Observer<DictionaryDatabase>() {
                    @Override
                    public void onChanged(DictionaryDatabase dictionaryDatabase) {
                        if (dictionaryDatabase != null && mApp.getSettings().getLang().getValue() != null) {
                            mBookmarkElementsLive = dictionaryDatabase.dictionaryBookmarkImportDao().getAllDetailsLive(mApp.getSettings().getLang().getValue(),
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
                            mBookmarkElementsLive = mApp.getDictionaryDatabase().getValue().dictionaryBookmarkImportDao().getAllDetailsLive(mApp.getSettings().getLang().getValue(),
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

        mBookmarkElements.addSource(mApp.getSettings().getBackupLang(),
                new Observer<String>() {
                    @Override
                    public void onChanged(String s) {
                        if (mBookmarkElementsLive != null) {
                            mBookmarkElements.removeSource(mBookmarkElementsLive);
                            mBookmarkElementsLive = null;
                        }

                        if (mApp.getDictionaryDatabase().getValue() != null) {
                            mBookmarkElementsLive = mApp.getDictionaryDatabase().getValue().dictionaryBookmarkImportDao().getAllDetailsLive(mApp.getSettings().getLang().getValue(),
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

    public LiveData<Integer> getError() {
        return mError;
    }

    public void commitBookmarks() {
        if (mApp.getDictionaryDatabase().getValue() == null) {
            System.err.println("Trying to commit bookmarks without DB");

            return;
        }

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                try {
                    final DictionaryDatabase db = mApp.getDictionaryDatabase().getValue();

                    db.beginTransaction();

                    SupportSQLiteDatabase sqldb = db.getOpenHelper().getWritableDatabase();

                    sqldb.execSQL("INSERT OR IGNORE INTO DictionaryBookmark SELECT * FROM DictionaryBookmarkImport");

                    db.setTransactionSuccessful();
                    db.endTransaction();

                    db.dictionaryBookmarkImportDao().deleteAll();
                } catch(Exception e) {
                    System.err.println(e.toString());
                }

                return null;
            }
        }.execute();
    }

    public void deleteAll() {
        if (mApp.getDictionaryDatabase().getValue() == null) {
            System.err.println("Trying to commit bookmarks without DB");

            return;
        }

        new AsyncTask<Void, Void, Void>() {

            @Override
            protected Void doInBackground(Void... voids) {
                mApp.getDictionaryDatabase().getValue().dictionaryBookmarkImportDao().deleteAll();

                return null;
            }
        }.execute();
    }

    public void importBookmarks(final Uri aUri) {
        if (mApp.getDictionaryDatabase().getValue() == null) {
            System.err.println("Trying to commit bookmarks without DB");

            return;
        }

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                try {
                    final DictionaryDatabase db = mApp.getDictionaryDatabase().getValue();

                    db.dictionaryBookmarkImportDao().deleteAll();

                    InputStream is = getApplication().getContentResolver().openInputStream(aUri);
                    List<Long> seqs = DictionaryBookmarkXML.readXML(is);
                    is.close();

                    List<DictionaryBookmarkImport> dbiList = new LinkedList<DictionaryBookmarkImport>();

                    if (seqs == null) {
                        mError.postValue(1);

                        return null;
                    }

                    for (Long seq : seqs) {
                        dbiList.add(new DictionaryBookmarkImport(seq, 1));
                    }

                    db.dictionaryBookmarkImportDao().insertMany(dbiList);
                } catch(Exception e) {
                    mError.postValue(2);

                    System.err.println(e.toString());
                }

                return null;
            }
        }.execute();
    }
}
