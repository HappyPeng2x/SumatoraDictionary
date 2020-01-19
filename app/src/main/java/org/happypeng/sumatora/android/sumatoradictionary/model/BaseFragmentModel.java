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
import android.view.View;

import org.happypeng.sumatora.android.sumatoradictionary.DictionaryApplication;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryBookmark;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchElement;
import org.happypeng.sumatora.android.sumatoradictionary.db.InstalledDictionary;
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentDatabase;
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.QueryTool;
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.DisplayStatus;
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.Settings;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.arch.core.util.Function;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.Transformations;
import androidx.paging.PagedList;
import androidx.room.InvalidationTracker;

import java.util.List;
import java.util.Set;

public class BaseFragmentModel extends AndroidViewModel {
    // Lang selection menu

    public class LangSelectionMenuStatus {
        public View attachView;
        public List<InstalledDictionary> installedDictionaries;

        public LangSelectionMenuStatus(final View aAttachView,
                                       final List<InstalledDictionary> aInstalledDictionaries) {
            attachView = aAttachView;
            installedDictionaries = aInstalledDictionaries;
        }
    }

    private final MediatorLiveData<LangSelectionMenuStatus> m_langSelectionMenuStatus;

    public LiveData<LangSelectionMenuStatus> getLangSelectionMenuStatus() { return m_langSelectionMenuStatus; }

    public void setLangSelectionMenuStatusView(final View aView) {
        LangSelectionMenuStatus status = m_langSelectionMenuStatus.getValue();
        List<InstalledDictionary> installedDictionaries = null;

        if (status != null) {
            installedDictionaries = status.installedDictionaries;
        }

        m_langSelectionMenuStatus.setValue(new LangSelectionMenuStatus(aView, installedDictionaries));
    }

    private void setLangSelectionMenuStatusInstalledDictionaries(final List<InstalledDictionary> aInstalledDictionaries) {
        LangSelectionMenuStatus status = m_langSelectionMenuStatus.getValue();
        View attachView = null;

        if (status != null) {
            attachView = status.attachView;
        }

        m_langSelectionMenuStatus.setValue(new LangSelectionMenuStatus(attachView, aInstalledDictionaries));
    }

    // Language and backup language

    public class LangSelectionStatus {
        public String lang;
        public String backupLang;

        public LangSelectionStatus(final String aLang,
                                   final String aBackupLang) {
            lang = aLang;
            backupLang = aBackupLang;
        }
    }

    private final MediatorLiveData<LangSelectionStatus> m_langSelectionStatus;

    private void setLangSelectionStatusLang(final String aLang) {
        LangSelectionStatus status = m_langSelectionStatus.getValue();
        String backupLang = null;

        if (status != null) {
            backupLang = status.backupLang;
        }

        m_langSelectionStatus.setValue(new LangSelectionStatus(aLang, backupLang));
    }

    private void setLangSelectionStatusBackupLang(final String aBackupLang) {
        LangSelectionStatus status = m_langSelectionStatus.getValue();
        String lang = null;

        if (status != null) {
            lang = status.lang;
        }

        m_langSelectionStatus.setValue(new LangSelectionStatus(lang, aBackupLang));
    }

    public LiveData<LangSelectionStatus> getLangSelectionStatus() {
        return m_langSelectionStatus;
    }

    DictionaryApplication mApp;
    int mKey;

    private MediatorLiveData<DisplayStatus> m_status;

    private QueryTool m_queryTool;

    private LiveData<PagedList<DictionarySearchElement>> m_elementList;

    public LiveData<PagedList<DictionarySearchElement>> getElementList() { return m_elementList; }
    public LiveData<DisplayStatus> getStatus() { return m_status; }

    private Integer m_bookmarkToolStatus;

    private String m_lang;
    private String m_term;

    PersistentDatabase m_currentDatabase;

    public Integer getBookmarkToolStatus() { return m_bookmarkToolStatus; }

    public void initialize(final int aKey, final String aSearchSet, final boolean aAllowSearchAll,
                           final @NonNull String aTableObserve) {
        if (m_status != null) {
            return;
        }

        mKey = aKey;
        m_status = DisplayStatus.create(mApp, aKey);

        m_currentDatabase = null;

        final InvalidationTracker.Observer observer = new InvalidationTracker.Observer(aTableObserve) {
            @Override
            public void onInvalidated(@NonNull Set<String> tables) {
                if (m_queryTool != null) {
                    m_queryTool.setTerm(m_queryTool.getTerm(), true);
                }
            }
        };

        final LiveData<QueryTool> bookmarkTool =
                Transformations.switchMap(mApp.getPersistentDatabase(),
                        new Function<PersistentDatabase, LiveData<QueryTool>>() {
                            @Override
                            public LiveData<QueryTool> apply(PersistentDatabase input) {
                                if (m_currentDatabase != null) {
                                    m_currentDatabase.getInvalidationTracker().removeObserver(observer);
                                }

                                m_currentDatabase = input;

                                if (input != null) {
                                    if (!"".equals(aTableObserve)) {
                                        input.getInvalidationTracker().addObserver(observer);
                                    }

                                    return QueryTool.create(input, aKey, aSearchSet, aAllowSearchAll, mApp.getRomkan());
                                }

                                return null;
                            }
                        });

        m_elementList =
                Transformations.switchMap(bookmarkTool,
                        new Function<QueryTool, LiveData<PagedList<DictionarySearchElement>>>() {
                            @Override
                            public LiveData<PagedList<DictionarySearchElement>> apply(QueryTool input) {
                                if (input != null) {
                                    return input.getDisplayElements();
                                }

                                return null;
                            }
                        });

        m_status.addSource(bookmarkTool,
                new Observer<QueryTool>() {
                    @Override
                    public void onChanged(QueryTool queryTool) {
                        m_queryTool = queryTool;

                        if (m_queryTool != null) {
                            if (m_term != null) {
                                queryTool.setTerm(m_term, true);

                                m_term = null;
                            } else {
                                queryTool.setTerm(queryTool.getTerm(), true);
                            }

                            if (m_lang != null) {
                                queryTool.setLang(m_lang);
                            }
                        }
                    }
                });

        final LiveData<Integer> bookmarkToolStatus =
                Transformations.switchMap(bookmarkTool,
                        new Function<QueryTool, LiveData<Integer>>() {
                            @Override
                            public LiveData<Integer> apply(QueryTool input) {
                                if (input != null) {
                                    return input.getStatus();
                                }

                                return null;
                            }
                        });

        m_status.addSource(bookmarkToolStatus,
                new Observer<Integer>() {
                    @Override
                    public void onChanged(Integer integer) {
                        m_bookmarkToolStatus = integer;

                        if (m_status != null) {
                            m_status.setValue(m_status.getValue());
                        }
                    }
                });

        LiveData<String> lang = mApp.getSettings().getValue(Settings.LANG);
        LiveData<String> backupLang = mApp.getSettings().getValue(Settings.BACKUP_LANG);

        m_status.addSource(lang,
                new Observer<String>() {
                    @Override
                    public void onChanged(String s) {
                        if (s != null) {
                            m_lang = s;

                            if (m_queryTool != null) {
                                m_queryTool.setLang(s);
                            }
                        }
                    }
                });

        m_langSelectionStatus.addSource(lang,
                new Observer<String>() {
                    @Override
                    public void onChanged(String s) {
                        setLangSelectionStatusLang(s);
                    }
                });

        m_langSelectionStatus.addSource(backupLang,
                new Observer<String>() {
                    @Override
                    public void onChanged(String s) {
                        setLangSelectionStatusBackupLang(s);
                    }
                });

        LiveData<List<InstalledDictionary>> installedDictionariesLiveData =
                Transformations.switchMap(mApp.getPersistentDatabase(),
                        new Function<PersistentDatabase, LiveData<List<InstalledDictionary>>>() {
                            @Override
                            public LiveData<List<InstalledDictionary>> apply(PersistentDatabase input) {
                                if (input == null) {
                                    return null;
                                }

                                return input.installedDictionaryDao().getAllLive();
                            }
                        });

        m_langSelectionMenuStatus.addSource(installedDictionariesLiveData,
                new Observer<List<InstalledDictionary>>() {
                    @Override
                    public void onChanged(List<InstalledDictionary> installedDictionaries) {
                        setLangSelectionMenuStatusInstalledDictionaries(installedDictionaries);
                    }
                });
    }

    public BaseFragmentModel(Application aApp) {
        super(aApp);

        mApp = (DictionaryApplication) aApp;

        m_bookmarkToolStatus = null;
        m_queryTool = null;

        m_langSelectionMenuStatus = new MediatorLiveData<>();
        m_langSelectionStatus = new MediatorLiveData<>();
    }

    public void updateBookmark(final long seq, final long bookmark) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... aParams) {
                if (mApp.getPersistentDatabase().getValue() != null) {
                    if (bookmark != 0) {
                        mApp.getPersistentDatabase().getValue().dictionaryBookmarkDao().insert(new DictionaryBookmark(seq, bookmark));
                    } else {
                        mApp.getPersistentDatabase().getValue().dictionaryBookmarkDao().delete(seq);
                    }
                }

                return null;
            }
        }.execute();
    }

    public DictionaryApplication getDictionaryApplication() { return mApp; }

    @MainThread
    public void setTerm(@NonNull String aTerm) {
        if (m_queryTool != null) {
            m_queryTool.setTerm(aTerm, true);
        } else {
            m_term = aTerm;
        }
    }

    public String getTerm() {
        if (m_queryTool != null) {
            return m_queryTool.getTerm();
        }

        if (m_term != null) {
            return m_term;
        }

        return "";
    }
}
