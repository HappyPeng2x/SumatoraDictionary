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

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.arch.core.util.Function;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.Transformations;
import androidx.paging.LivePagedListBuilder;
import androidx.paging.PagedList;
import androidx.room.InvalidationTracker;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteStatement;

import org.happypeng.sumatora.android.sumatoradictionary.DictionaryApplication;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryBookmark;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchElement;
import org.happypeng.sumatora.android.sumatoradictionary.db.InstalledDictionary;
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistantLanguageSettings;
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentDatabase;
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.RoomFactoryWrapper;
import org.happypeng.sumatora.jromkan.Romkan;

import java.io.IOException;
import java.util.List;
import java.util.Set;

public class BaseFragmentModel extends AndroidViewModel {
    // Display related SQL queries
    static private final String SQL_QUERY_INSERT_DISPLAY_ELEMENT =
            "INSERT OR IGNORE INTO DictionaryDisplayElement SELECT DictionaryElement.ref, DictionaryElement.entryOrder, DictionaryElement.seq, "
                    + "DictionaryEntry.readingsPrio, DictionaryEntry.readings, "
                    + "DictionaryEntry.writingsPrio, DictionaryEntry.writings, "
                    + "DictionaryEntry.pos, DictionaryEntry.xref, DictionaryEntry.ant, "
                    + "DictionaryEntry.misc, DictionaryEntry.lsource, DictionaryEntry.dial, "
                    + "DictionaryEntry.s_inf, DictionaryEntry.field, "
                    + "?, "
                    + "DictionaryTranslation.gloss, "
                    + "null as example_sentences "
                    + "FROM DictionaryElement, "
                    + " jmdict.DictionaryEntry, "
                    + " %s.DictionaryTranslation "
                    + "WHERE DictionaryElement.seq = DictionaryEntry.seq AND "
                    + " DictionaryElement.seq = DictionaryTranslation.seq AND "
                    + " DictionaryElement.ref = ?";

    static private final String SQL_QUERY_INSERT_DISPLAY_ELEMENT_EXAMPLES =
            "INSERT OR IGNORE INTO DictionaryDisplayElement SELECT DictionaryElement.ref, DictionaryElement.entryOrder, DictionaryElement.seq, "
                    + "DictionaryEntry.readingsPrio, DictionaryEntry.readings, "
                    + "DictionaryEntry.writingsPrio, DictionaryEntry.writings, "
                    + "DictionaryEntry.pos, DictionaryEntry.xref, DictionaryEntry.ant, "
                    + "DictionaryEntry.misc, DictionaryEntry.lsource, DictionaryEntry.dial, "
                    + "DictionaryEntry.s_inf, DictionaryEntry.field, "
                    + "?, "
                    + "DictionaryTranslation.gloss, "
                    + "json_group_array(examples.sentence) as example_sentences "
                    + "FROM DictionaryElement, "
                    + " (jmdict.DictionaryEntry LEFT JOIN (%s.Examples, %s.ExamplesIndex) ON "
                    + " ExamplesIndex.seq = DictionaryEntry.seq AND ExamplesIndex.id = Examples.id), "
                    + " %s.DictionaryTranslation "
                    + "WHERE DictionaryElement.seq = DictionaryEntry.seq AND "
                    + " DictionaryElement.seq = DictionaryTranslation.seq AND "
                    + " DictionaryElement.ref = ? "
                    + "GROUP BY DictionaryElement.seq";

    static private final String SQL_QUERY_DELETE_DISPLAY_ELEMENT =
            "DELETE FROM DictionaryDisplayElement WHERE DictionaryDisplayElement.ref = ?";

    // Query related SQL queries
    public static final int ORDER_MULTIPLIER = 10000;

    static private final String SQL_QUERY_DELETE =
            "DELETE FROM DictionaryElement WHERE ref = ?";

    static private final String SQL_QUERY_ALL =
            "INSERT OR IGNORE INTO DictionaryElement SELECT ? AS ref, 0 AS entryOrder, DictionaryEntry.seq "
                    + "FROM jmdict.DictionaryEntry";

    static private final String SQL_QUERY_EXACT_WRITING_PRIO =
            "INSERT OR IGNORE INTO DictionaryElement SELECT ? AS ref, ? AS entryOrder, DictionaryIndex.`rowid` AS seq "
                    + "FROM jmdict.DictionaryIndex "
                    + "WHERE writingsPrio MATCH ?";

    static private final String SQL_QUERY_EXACT_READING_PRIO =
            "INSERT OR IGNORE INTO DictionaryElement SELECT ? AS ref, ? AS entryOrder, DictionaryIndex.`rowid` AS seq "
                    + "FROM jmdict.DictionaryIndex "
                    + "WHERE readingsPrioKana MATCH ?";


    static private final String SQL_QUERY_EXACT_WRITING_NONPRIO =
            "INSERT OR IGNORE INTO DictionaryElement SELECT ? AS ref, ? AS entryOrder, DictionaryIndex.`rowid` AS seq "
                    + "FROM jmdict.DictionaryIndex "
                    + "WHERE writings MATCH ?";

    static private final String SQL_QUERY_EXACT_READING_NONPRIO =
            "INSERT OR IGNORE INTO DictionaryElement SELECT ? AS ref, ? AS entryOrder, DictionaryIndex.`rowid` AS seq "
                    + "FROM jmdict.DictionaryIndex "
                    + "WHERE readingsKana MATCH ?";

    static private final String SQL_QUERY_BEGIN_WRITING_PRIO =
            "INSERT OR IGNORE INTO DictionaryElement SELECT ? AS ref, ? AS entryOrder, DictionaryIndex.`rowid` AS seq "
                    + "FROM jmdict.DictionaryIndex "
                    + "WHERE writingsPrio MATCH ? || '*'";

    static private final String SQL_QUERY_BEGIN_READING_PRIO =
            "INSERT OR IGNORE INTO DictionaryElement SELECT ? AS ref, ? AS entryOrder, DictionaryIndex.`rowid` AS seq "
                    + "FROM jmdict.DictionaryIndex "
                    + "WHERE readingsPrioKana MATCH ? || '*'";

    static private final String SQL_QUERY_BEGIN_WRITING_NONPRIO =
            "INSERT OR IGNORE INTO DictionaryElement SELECT ? AS ref, ? AS entryOrder, DictionaryIndex.`rowid` AS seq "
                    + "FROM jmdict.DictionaryIndex "
                    + "WHERE writings MATCH ? || '*'";

    static private final String SQL_QUERY_BEGIN_READING_NONPRIO =
            "INSERT OR IGNORE INTO DictionaryElement SELECT ? AS ref, ? AS entryOrder, DictionaryIndex.`rowid` AS seq "
                    + "FROM jmdict.DictionaryIndex "
                    + "WHERE readingsKana MATCH ? || '*'";

    static private final String SQL_QUERY_PARTS_WRITING_PRIO =
            "INSERT OR IGNORE INTO DictionaryElement SELECT ? AS ref, ? AS entryOrder, DictionaryIndex.`rowid` AS seq "
                    + "FROM jmdict.DictionaryIndex "
                    + "WHERE writingsPrioParts MATCH ? || '*'";

    static private final String SQL_QUERY_PARTS_READING_PRIO =
            "INSERT OR IGNORE INTO DictionaryElement SELECT ? AS ref, ? AS entryOrder, DictionaryIndex.`rowid` AS seq "
                    + "FROM jmdict.DictionaryIndex "
                    + "WHERE readingsPrioKanaParts MATCH ? || '*'";

    static private final String SQL_QUERY_PARTS_WRITING_NONPRIO =
            "INSERT OR IGNORE INTO DictionaryElement SELECT ? AS ref, ? AS entryOrder, DictionaryIndex.`rowid` AS seq "
                    + "FROM jmdict.DictionaryIndex "
                    + "WHERE writingsParts MATCH ? || '*'";

    static private final String SQL_QUERY_PARTS_READING_NONPRIO =
            "INSERT OR IGNORE INTO DictionaryElement SELECT ? AS ref, ? AS entryOrder, DictionaryIndex.`rowid` AS seq "
                    + "FROM jmdict.DictionaryIndex "
                    + "WHERE readingsKanaParts MATCH ? || '*'";

    // Query related classes
    private static abstract class QueryStatement {
        final int ref;
        final int order;
        final SupportSQLiteStatement statement;

        private QueryStatement(int aRef, int aOrder, final SupportSQLiteStatement aStatement) {
            ref = aRef;
            order = aOrder;
            statement = aStatement;
        }

        @WorkerThread
        abstract long execute(String term);

        public int getOrder() {
            return order;
        }
    }

    public static class BasicQueryStatement extends QueryStatement {
        private final boolean kana;
        private final Romkan romkan;

        private BasicQueryStatement(int aRef, int aOrder, final SupportSQLiteStatement aStatement,
                                    boolean aKana, final Romkan aRomkan) {
            super(aRef, aOrder, aStatement);

            kana = aKana;
            romkan = aRomkan;
        }

        @WorkerThread
        long execute(String term) {
            String bindTerm = term;

            if (kana) {
                bindTerm = romkan.to_katakana(romkan.to_hepburn(term));
            }

            statement.bindLong(1, ref);
            statement.bindLong(2, order * ORDER_MULTIPLIER);
            statement.bindString(3, bindTerm);

            return statement.executeInsert();
        }
    }

    public static class ReverseQueryStatement extends QueryStatement {
        static private final String SQL_QUERY_TRANSLATION_START =
                "INSERT OR IGNORE INTO DictionaryElement SELECT ? AS ref, ?+split_offsets(offsets(DictionaryTranslationIndex), ' ', 2, " + (ORDER_MULTIPLIER - 1) + ") AS entryOrder, DictionaryTranslationIndex.`rowid` AS seq "
                        + "FROM ";
        static private final String SQL_QUERY_TRANSLATION_END =
                ".DictionaryTranslationIndex WHERE gloss MATCH ?";
        static private final String SQL_QUERY_TRANSLATION_BEGIN_END =
                ".DictionaryTranslationIndex WHERE gloss MATCH ? || '*'";

        private ReverseQueryStatement(int aRef, int aOrder, final SupportSQLiteStatement aStatement) {
            super(aRef, aOrder, aStatement);
        }

        @WorkerThread
        static SupportSQLiteStatement createStatementExact(final PersistentDatabase aDB, final String aLang) {
            String query = SQL_QUERY_TRANSLATION_START + aLang + SQL_QUERY_TRANSLATION_END;

            return aDB.compileStatement(query);
        }

        @WorkerThread
        static SupportSQLiteStatement createStatementBegin(final PersistentDatabase aDB, final String aLang) {
            String query = SQL_QUERY_TRANSLATION_START + aLang + SQL_QUERY_TRANSLATION_BEGIN_END;

            return aDB.compileStatement(query);
        }

        @Override
        long execute(String term) {
            String bindTerm = term;

            statement.bindLong(1, ref);
            statement.bindLong(2, order * ORDER_MULTIPLIER);
            statement.bindString(3, bindTerm);

            return statement.executeInsert();
        }
    }

    // Query related settings
    private static final int PAGE_SIZE = 30;
    private static final int PREFETCH_DISTANCE = 50;

    public static final int STATUS_PRE_INITIALIZED = 0;
    public static final int STATUS_INITIALIZED = 1;
    public static final int STATUS_SEARCHING = 2;
    public static final int STATUS_RESULTS_FOUND = 3;
    public static final int STATUS_NO_RESULTS_FOUND_ENDED = 4;
    public static final int STATUS_RESULTS_FOUND_ENDED = 5;

    // Display related SQL statements
    private DisplayStatementsContainer mDisplayStatements;

    // Lang selection menu
    private LiveData<List<InstalledDictionary>> mInstalledDictionariesLive;
    private List<InstalledDictionary> mInstalledDictionaries;

    // Language and backup language
    private PersistantLanguageSettings mLanguageSettings;

    // Query related elements
    private QueryStatementsContainer mQueryStatements;

    private Romkan mRomkan;
    private QueryNextStatementResult mQueryResult;
    private String mSearchSet;
    private boolean mAllowQueryAll;

    // Other status elements
    DictionaryApplication mApp;
    PersistentDatabase mCurrentDatabase;
    int mKey;

    private String mTerm;

    private int mState;

    // Status
    private MediatorLiveData<Integer> mStatus;

    // Getters
    public LiveData<Integer> getStatus() {
        return mStatus;
    }

    public LiveData<List<InstalledDictionary>> getInstalledDictionaries() {
        return mInstalledDictionariesLive;
    }

    public LiveData<PersistantLanguageSettings> getLanguageSettingsLive() {
        return mApp.getPersistentLanguageSettings();
    }

    // Updaters
    @MainThread
    private void updateInitializationStatus() {
        int state = STATUS_PRE_INITIALIZED;

        if (mCurrentDatabase != null && mLanguageSettings != null && mInstalledDictionaries != null &&
                mQueryStatements != null && mQueryStatements.statements.length == 14 && mDisplayStatements != null) {
            state = STATUS_INITIALIZED;
        }

        if (state != mState) {
            mState = state;

            mStatus.setValue(mState);
        }
    }

    // Query
    @MainThread
    private void processQueryInitial()  {
        if (mCurrentDatabase == null || mTerm == null || mQueryStatements == null) {
            return;
        }

        resetQuery(mCurrentDatabase, mKey, mQueryStatements, new QueryNextStatementCallback() {
            @Override
            public void callback(QueryNextStatementResult aResult) {
                mQueryResult = aResult;

                if (mState != STATUS_SEARCHING) {
                    mState = STATUS_SEARCHING;
                    mStatus.setValue(mState);
                }

                if ("".equals(mTerm)) {
                    insertQueryAll(mCurrentDatabase, mAllowQueryAll, mKey, mQueryStatements, new QueryNextStatementCallback() {
                        @Override
                        public void callback(QueryNextStatementResult aResult) {
                            mQueryResult = aResult;

                            if (mState != STATUS_RESULTS_FOUND_ENDED) {
                                mState = STATUS_RESULTS_FOUND_ENDED;
                                mStatus.setValue(mState);
                            }
                        }
                    });
                } else {
                    executeQueryNextStatement(mCurrentDatabase, mTerm, mQueryResult, mQueryStatements, new QueryNextStatementCallback() {
                        @Override
                        public void callback(QueryNextStatementResult aResult) {
                            mQueryResult = aResult;

                            if (!mQueryResult.found) {
                                if (mState != STATUS_NO_RESULTS_FOUND_ENDED) {
                                    mState = STATUS_NO_RESULTS_FOUND_ENDED;
                                    mStatus.setValue(mState);
                                }
                            } else {
                                if (aResult.nextStatement >= mQueryStatements.statements.length) {
                                    if (mState != STATUS_RESULTS_FOUND_ENDED) {
                                        mState = STATUS_RESULTS_FOUND_ENDED;
                                        mStatus.setValue(mState);
                                    }
                                } else {
                                    if (mState != STATUS_RESULTS_FOUND) {
                                        mState = STATUS_RESULTS_FOUND;
                                        mStatus.setValue(mState);
                                    }
                                }
                            }
                        }
                    });
                }
            }
        });
    }

    public void initialize(final int aKey, final String aSearchSet, final boolean aAllowSearchAll,
                           final @NonNull String aTableObserve) {
        mKey = aKey;
        mSearchSet = aSearchSet;
        mAllowQueryAll = aAllowSearchAll;

        mCurrentDatabase = null;

        // Queries related initialization
        final InvalidationTracker.Observer observer = new InvalidationTracker.Observer(aTableObserve) {
            @Override
            public void onInvalidated(@NonNull Set<String> tables) {
                // re-display results with same terms
            }
        };

        // Display
        final Function<Void, Void> processDisplayStatements = new Function<Void, Void>() {
            @Override
            public Void apply(Void input) {
                if (mCurrentDatabase != null && mLanguageSettings != null && mInstalledDictionaries != null) {
                    createDisplayStatements(mCurrentDatabase, mLanguageSettings, mInstalledDictionaries,
                            new DisplayStatementsCallback() {
                                @Override
                                public void callback(DisplayStatementsContainer aContainer) {
                                    mDisplayStatements = aContainer;

                                    updateInitializationStatus();

                                    insertDisplayElements(mCurrentDatabase, mKey, mLanguageSettings, aContainer);
                                }
                            });
                }

                return null;
            }
        };

        final Function<Void, Void> processQueryLanguage = new Function<Void, Void>() {
            @Override
            public Void apply(Void input) {
                if (mCurrentDatabase != null && mLanguageSettings != null) {
                    setQueryLang(mCurrentDatabase, mLanguageSettings.lang, mKey, mQueryStatements, new QueryStatementsCallback() {
                        @Override
                        public void callback(QueryStatementsContainer statements) {
                            mQueryStatements = statements;

                            updateInitializationStatus();

                            processQueryInitial();
                        }
                    });
                }
                return null;
            }
        };

        // Create new statements and perform query
        final Function<Void, Void> processQueryStatements = new Function<Void, Void>() {
            @Override
            public Void apply(Void input) {
                if (mCurrentDatabase != null) {
                    createQueryStatements(mCurrentDatabase, mKey, mAllowQueryAll, mSearchSet, mRomkan,
                            new QueryStatementsCallback() {
                                @Override
                                public void callback(QueryStatementsContainer statements) {
                                    mQueryStatements = statements;

                                    updateInitializationStatus();

                                    processQueryLanguage.apply(null);
                                }
                            });
                }

                return null;
            }
        };

        // Database changed
        mStatus.addSource(mApp.getPersistentDatabase(),
                new Observer<PersistentDatabase>() {
                    @Override
                    public void onChanged(PersistentDatabase persistentDatabase) {
                        if (mCurrentDatabase != null) {
                            mCurrentDatabase.getInvalidationTracker().removeObserver(observer);
                        }

                        mCurrentDatabase = persistentDatabase;

                        if (mQueryStatements != null) {
                            // Clear query, close statements, create new statements and perform query
                            resetQuery(mCurrentDatabase, aKey, mQueryStatements, new QueryNextStatementCallback() {
                                @Override
                                public void callback(QueryNextStatementResult aResult) {
                                    mQueryResult = aResult;

                                    closeQueryStatements(mQueryStatements, new QueryStatementsCallback() {
                                        @Override
                                        public void callback(QueryStatementsContainer statements) {
                                            processQueryStatements.apply(null);
                                        }
                                    });
                                }
                            });
                        } else {
                            // Create new statements and perform query
                            processQueryStatements.apply(null);
                        }

                        if (mDisplayStatements != null) {
                            closeDisplayStatements(mDisplayStatements, new DisplayStatementsCallback() {
                                @Override
                                public void callback(DisplayStatementsContainer aContainer) {
                                    processDisplayStatements.apply(null);
                                }
                            });
                        } else {
                            processDisplayStatements.apply(null);
                        }

                        if (mCurrentDatabase != null) {
                            if (!"".equals(aTableObserve)) {
                                mCurrentDatabase.getInvalidationTracker().addObserver(observer);
                            }
                        }
                    }
                });

        // Language changed
        mStatus.addSource(mApp.getPersistentLanguageSettings(),
                new Observer<PersistantLanguageSettings>() {
                    @Override
                    public void onChanged(PersistantLanguageSettings s) {
                        mLanguageSettings = s;

                        // Query
                        processQueryLanguage.apply(null);

                        // Display
                        if (mDisplayStatements != null) {
                            closeDisplayStatements(mDisplayStatements, new DisplayStatementsCallback() {
                                @Override
                                public void callback(DisplayStatementsContainer aContainer) {
                                    processDisplayStatements.apply(null);
                                }
                            });
                        } else {
                            processDisplayStatements.apply(null);
                        }
                    }
                });

        // Installed dictionaries list
        mInstalledDictionariesLive =
                Transformations.switchMap(mApp.getPersistentDatabase(),
                        new Function<PersistentDatabase, LiveData<List<InstalledDictionary>>>() {
                            @Override
                            public LiveData<List<InstalledDictionary>> apply(PersistentDatabase input) {
                                if (input != null) {
                                    return input.installedDictionaryDao().getAllLive();
                                }

                                return null;
                            }
                        });

        // list of installed dictionaries changed
        mStatus.addSource(mInstalledDictionariesLive,
                new Observer<List<InstalledDictionary>>() {
                    @Override
                    public void onChanged(List<InstalledDictionary> installedDictionaries) {
                        mInstalledDictionaries = installedDictionaries;

                        // Display
                        if (mDisplayStatements != null) {
                            closeDisplayStatements(mDisplayStatements, new DisplayStatementsCallback() {
                                @Override
                                public void callback(DisplayStatementsContainer aContainer) {
                                    processDisplayStatements.apply(null);
                                }
                            });
                        } else {
                            processDisplayStatements.apply(null);
                        }
                    }
                });

        // Query results
        final LiveData<Long> elements =
                Transformations.switchMap(mApp.getPersistentDatabase(),
                        new Function<PersistentDatabase, LiveData<Long>>() {
                            @Override
                            public LiveData<Long> apply(PersistentDatabase input) {
                                if (input != null) {
                                    return input.dictionaryElementDao().getFirstLive();
                                }

                                return null;
                            }
                        });

        // Query results changed
        mStatus.addSource(elements,
                new Observer<Long>() {
                    @Override
                    public void onChanged(Long aLong) {
                        insertDisplayElements(mCurrentDatabase, mKey, mLanguageSettings, mDisplayStatements);
                    }
                });
    }

    BaseFragmentModel(Application aApp) {
        super(aApp);

        mApp = (DictionaryApplication) aApp;
        mRomkan = mApp.getRomkan();

        mStatus = new MediatorLiveData<>();

        mTerm = "";
        mState = STATUS_PRE_INITIALIZED;
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

    public DictionaryApplication getDictionaryApplication() {
        return mApp;
    }

    @MainThread
    public void setTerm(@NonNull String aTerm) {
        if (!aTerm.equals(mTerm)) {
            mTerm = aTerm;

            deleteDisplayElements(mCurrentDatabase, mKey, mDisplayStatements,
                    new DisplayCallback() {
                        @Override
                        public void callback() {
                            processQueryInitial();
                        }
                    });
        }
    }

    public String getTerm() {
        return mTerm;
    }

    // Display related methods
    static class DisplayStatementsContainer {
        SupportSQLiteStatement insertDisplayElementStatement;
        SupportSQLiteStatement insertDisplayElementStatementBackup;
        SupportSQLiteStatement deleteDisplayElement;
    }

    interface DisplayStatementsCallback {
        void callback(DisplayStatementsContainer aContainer);
    }

    interface DisplayCallback {
        void callback();
    }

    @MainThread
    private static void deleteDisplayElements(final PersistentDatabase aDatabase,
                                              final int aKey,
                                              final DisplayStatementsContainer aContainer,
                                              final DisplayCallback aCallback) {
        if (aDatabase == null || aContainer == null) {
            return;
        }

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                aDatabase.runInTransaction(new Runnable() {
                    @Override
                    public void run() {
                        aContainer.deleteDisplayElement.bindLong(1, aKey);
                        aContainer.deleteDisplayElement.execute();
                    }
                });

                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                super.onPostExecute(aVoid);

                if (aCallback != null) {
                    aCallback.callback();
                }
            }
        }.execute();
    }

    @MainThread
    private static void insertDisplayElements(final PersistentDatabase aDatabase,
                                              final int aKey,
                                              final PersistantLanguageSettings aLanguageSettings,
                                              final DisplayStatementsContainer aContainer) {
        if (aDatabase == null || aLanguageSettings == null || aContainer == null) {
            return;
        }

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                aDatabase.runInTransaction(new Runnable() {
                    @Override
                    public void run() {
                        aContainer.deleteDisplayElement.bindLong(1, aKey);
                        aContainer.deleteDisplayElement.execute();

                        aContainer.insertDisplayElementStatement.bindString(1, aLanguageSettings.lang);
                        aContainer.insertDisplayElementStatement.bindLong(2, aKey);
                        aContainer.insertDisplayElementStatement.execute();

                        if (aContainer.insertDisplayElementStatementBackup != null) {
                            aContainer.insertDisplayElementStatementBackup.bindString(1, aLanguageSettings.backupLang);
                            aContainer.insertDisplayElementStatementBackup.bindLong(2, aKey);
                            aContainer.insertDisplayElementStatementBackup.execute();
                        }
                    }
                });

                return null;
            }
        }.execute();
    }

    // NO BACKUP LANGUAGE STATEMENT YET!
    @MainThread
    private static void createDisplayStatements(final PersistentDatabase aDatabase, final PersistantLanguageSettings aLanguageSettings,
                                                final List<InstalledDictionary> aDictionaries, final DisplayStatementsCallback aCallback) {
        new AsyncTask<Void, Void, DisplayStatementsContainer>() {
            @Override
            protected DisplayStatementsContainer doInBackground(Void... voids) {
                DisplayStatementsContainer container = new DisplayStatementsContainer();
                SupportSQLiteDatabase db = aDatabase.getOpenHelper().getWritableDatabase();

                boolean hasExamples = false;
                boolean hasDictionary = false;
                boolean hasBackupDictionary = false;

                for (InstalledDictionary d : aDictionaries) {
                    if ("jmdict_translation".equals(d.type)) {
                        if (aLanguageSettings.lang.equals(d.lang)) {
                            hasDictionary = true;
                        }

                        if (aLanguageSettings.backupLang != null && aLanguageSettings.backupLang.equals(d.lang)) {
                            hasBackupDictionary = true;
                        }
                    } else if ("tatoeba".equals(d.type) && aLanguageSettings.lang.equals(d.lang)) {
                        hasExamples = true;
                    }
                }

                if (!hasDictionary) {
                    System.err.println("Dictionary for " + aLanguageSettings.lang + " is not installed.");
                }

                if (aLanguageSettings.backupLang != null && !hasBackupDictionary) {
                    System.err.println("Dictionary for backup language " + aLanguageSettings.backupLang + " is not installed.");
                }

                if (hasExamples) {
                    container.insertDisplayElementStatement =
                            db.compileStatement(String.format(SQL_QUERY_INSERT_DISPLAY_ELEMENT_EXAMPLES, "examples_" + aLanguageSettings.lang,
                                    "examples_" + aLanguageSettings.lang, aLanguageSettings.lang));
                } else {
                    container.insertDisplayElementStatement =
                            db.compileStatement(String.format(SQL_QUERY_INSERT_DISPLAY_ELEMENT, aLanguageSettings.lang));
                }

                container.deleteDisplayElement = db.compileStatement(SQL_QUERY_DELETE_DISPLAY_ELEMENT);

                return container;
            }

            @Override
            protected void onPostExecute(DisplayStatementsContainer displayStatementsContainer) {
                super.onPostExecute(displayStatementsContainer);

                aCallback.callback(displayStatementsContainer);
            }
        }.execute();


    }

    @MainThread
    private static void closeDisplayStatements(final DisplayStatementsContainer aContainer,
                                               final DisplayStatementsCallback aCallback) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                if (aContainer.insertDisplayElementStatement != null) {
                    try {
                        aContainer.insertDisplayElementStatement.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    aContainer.insertDisplayElementStatement = null;
                }

                if (aContainer.insertDisplayElementStatementBackup != null) {
                    try {
                        aContainer.insertDisplayElementStatementBackup.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    aContainer.insertDisplayElementStatementBackup = null;
                }

                if (aContainer.deleteDisplayElement != null) {
                    try {
                        aContainer.deleteDisplayElement.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    aContainer.deleteDisplayElement = null;
                }

                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                super.onPostExecute(aVoid);

                aCallback.callback(null);
            }
        }.execute();
    }

    // Query related methods

    // Initialization: mKey, mSearchSet, mRomkan, mCurrentDatabase -> mQueries, mLang -> end
    static class QueryStatementsContainer {
        QueryStatement[] statements;
        SupportSQLiteStatement insertAllStatement;
        SupportSQLiteStatement deleteStatement;
    }

    interface QueryStatementsCallback {
        void callback(QueryStatementsContainer statements);
    }

    @MainThread
    private static void createQueryStatements(final PersistentDatabase aDatabase, final int aKey, final boolean aAllowQueryAll,
                                              final String aSearchSet, final Romkan aRomkan, final QueryStatementsCallback aCallback) {
        new AsyncTask<Void, Void, QueryStatementsContainer>() {
            @Override
            protected QueryStatementsContainer doInBackground(Void... voids) {
                QueryStatementsContainer container = new QueryStatementsContainer();
                SupportSQLiteDatabase db = aDatabase.getOpenHelper().getWritableDatabase();

                if (aAllowQueryAll) {
                    container.insertAllStatement = db.compileStatement(aSearchSet == null ? SQL_QUERY_ALL :
                            SQL_QUERY_ALL + " WHERE DictionaryEntry.seq IN (" + aSearchSet + ")");
                }

                container.deleteStatement = db.compileStatement(SQL_QUERY_DELETE);

                final SupportSQLiteStatement queryExactPrioWriting =
                        db.compileStatement(aSearchSet == null ? SQL_QUERY_EXACT_WRITING_PRIO :
                                SQL_QUERY_EXACT_WRITING_PRIO + " AND DictionaryIndex.`rowid` IN (" + aSearchSet + ")");
                final SupportSQLiteStatement queryExactPrioReading =
                        db.compileStatement(aSearchSet == null ? SQL_QUERY_EXACT_READING_PRIO :
                                SQL_QUERY_EXACT_READING_PRIO + " AND DictionaryIndex.`rowid` IN (" + aSearchSet + ")");
                final SupportSQLiteStatement queryExactNonPrioWriting =
                        db.compileStatement(aSearchSet == null ? SQL_QUERY_EXACT_WRITING_NONPRIO :
                                SQL_QUERY_EXACT_WRITING_NONPRIO + " AND DictionaryIndex.`rowid` IN (" + aSearchSet + ")");
                final SupportSQLiteStatement queryExactNonPrioReading =
                        db.compileStatement(aSearchSet == null ? SQL_QUERY_EXACT_READING_NONPRIO :
                                SQL_QUERY_EXACT_READING_NONPRIO + " AND DictionaryIndex.`rowid` IN (" + aSearchSet + ")");
                final SupportSQLiteStatement queryBeginPrioWriting =
                        db.compileStatement(aSearchSet == null ? SQL_QUERY_BEGIN_WRITING_PRIO :
                                SQL_QUERY_BEGIN_WRITING_PRIO + " AND DictionaryIndex.`rowid` IN (" + aSearchSet + ")");
                final SupportSQLiteStatement queryBeginPrioReading =
                        db.compileStatement(aSearchSet == null ? SQL_QUERY_BEGIN_READING_PRIO :
                                SQL_QUERY_BEGIN_READING_PRIO + " AND DictionaryIndex.`rowid` IN (" + aSearchSet + ")");
                final SupportSQLiteStatement queryBeginNonPrioWriting =
                        db.compileStatement(aSearchSet == null ? SQL_QUERY_BEGIN_WRITING_NONPRIO :
                                SQL_QUERY_BEGIN_WRITING_NONPRIO + " AND DictionaryIndex.`rowid` IN (" + aSearchSet + ")");
                final SupportSQLiteStatement queryBeginNonPrioReading =
                        db.compileStatement(aSearchSet == null ? SQL_QUERY_BEGIN_READING_NONPRIO :
                                SQL_QUERY_BEGIN_READING_NONPRIO + " AND DictionaryIndex.`rowid` IN (" + aSearchSet + ")");
                final SupportSQLiteStatement queryPartsPrioWriting =
                        db.compileStatement(aSearchSet == null ? SQL_QUERY_PARTS_WRITING_PRIO :
                                SQL_QUERY_PARTS_WRITING_PRIO + " AND DictionaryIndex.`rowid` IN (" + aSearchSet + ")");
                final SupportSQLiteStatement queryPartsPrioReading =
                        db.compileStatement(aSearchSet == null ? SQL_QUERY_PARTS_READING_PRIO :
                                SQL_QUERY_PARTS_READING_PRIO + " AND DictionaryIndex.`rowid` IN (" + aSearchSet + ")");
                final SupportSQLiteStatement queryPartsNonPrioWriting =
                        db.compileStatement(aSearchSet == null ? SQL_QUERY_PARTS_WRITING_NONPRIO :
                                SQL_QUERY_PARTS_WRITING_NONPRIO + " AND DictionaryIndex.`rowid` IN (" + aSearchSet + ")");
                final SupportSQLiteStatement queryPartsNonPrioReading =
                        db.compileStatement(aSearchSet == null ? SQL_QUERY_PARTS_READING_NONPRIO :
                                SQL_QUERY_PARTS_READING_NONPRIO + " AND DictionaryIndex.`rowid` IN (" + aSearchSet + ")");

                container.statements = new QueryStatement[12];

                container.statements[0] = new BasicQueryStatement(aKey, 1, queryExactPrioWriting, false, aRomkan);
                container.statements[1] = new BasicQueryStatement(aKey, 2, queryExactPrioReading, true, aRomkan);
                container.statements[2] = new BasicQueryStatement(aKey, 3, queryExactNonPrioWriting, false, aRomkan);
                container.statements[3] = new BasicQueryStatement(aKey, 4, queryExactNonPrioReading, true, aRomkan);
                container.statements[4] = new BasicQueryStatement(aKey, 5, queryBeginPrioWriting, false, aRomkan);
                container.statements[5] = new BasicQueryStatement(aKey, 6, queryBeginPrioReading, true, aRomkan);
                container.statements[6] = new BasicQueryStatement(aKey, 7, queryBeginNonPrioWriting, false, aRomkan);
                container.statements[7] = new BasicQueryStatement(aKey, 8, queryBeginNonPrioReading, true, aRomkan);
                container.statements[8] = new BasicQueryStatement(aKey, 9, queryPartsPrioWriting, false, aRomkan);
                container.statements[9] = new BasicQueryStatement(aKey, 10, queryPartsPrioReading, true, aRomkan);
                container.statements[10] = new BasicQueryStatement(aKey, 11, queryPartsNonPrioWriting, false, aRomkan);
                container.statements[11] = new BasicQueryStatement(aKey, 12, queryPartsNonPrioReading, true, aRomkan);

                return container;
            }

            @Override
            protected void onPostExecute(QueryStatementsContainer queryStatements) {
                super.onPostExecute(queryStatements);

                aCallback.callback(queryStatements);
            }

        }.execute();
    }

    @MainThread
    private static void closeQueryStatements(final QueryStatementsContainer aContainer,
                                             final QueryStatementsCallback aCallback) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                if (aContainer.insertAllStatement != null) {
                    try {
                        aContainer.insertAllStatement.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    aContainer.insertAllStatement = null;
                }

                if (aContainer.deleteStatement != null) {
                    try {
                        aContainer.deleteStatement.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                    aContainer.deleteStatement = null;
                }

                if (aContainer.statements != null) {
                    for (QueryStatement s : aContainer.statements) {
                        if (s != null) {
                            try {
                                s.statement.close();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }

                aContainer.statements = null;

                return null;
            }

            @Override
            protected void onPostExecute(Void aVoid) {
                super.onPostExecute(aVoid);

                aCallback.callback(null);
            }
        }.execute();
    }

    @MainThread
    private static void setQueryLang(final PersistentDatabase aDatabase, final String aLang, final int aKey,
                                     final QueryStatementsContainer aContainer, final QueryStatementsCallback aCallback) {
        if (aDatabase == null || aLang == null || aContainer == null) {
            return;
        }

        new AsyncTask<Void, Void, ReverseQueryStatement[]>() {
            @Override
            protected ReverseQueryStatement[] doInBackground(Void... voids) {
                ReverseQueryStatement[] statements = new ReverseQueryStatement[2];

                statements[0] =
                        new ReverseQueryStatement(aKey, 13, ReverseQueryStatement.createStatementExact(aDatabase, aLang));

                statements[1] =
                        new ReverseQueryStatement(aKey, 14, ReverseQueryStatement.createStatementBegin(aDatabase, aLang));

                return statements;
            }

            @Override
            protected void onPostExecute(ReverseQueryStatement[] reverseQueryStatements) {
                super.onPostExecute(reverseQueryStatements);

                QueryStatementsContainer container = new QueryStatementsContainer();

                container.statements = new QueryStatement[14];

                for (int i = 0; i < 12; i++) {
                    container.statements[i] = aContainer.statements[i];
                }

                container.statements[12] = reverseQueryStatements[0];
                container.statements[13] = reverseQueryStatements[1];

                container.deleteStatement = aContainer.deleteStatement;
                container.insertAllStatement = aContainer.insertAllStatement;

                if (aCallback != null) {
                    aCallback.callback(container);
                }
            }
        }.execute();
    }

    // The display itself
    public
    LiveData<PagedList<DictionarySearchElement>> getDisplayElements() {
        final PagedList.Config pagedListConfig =
                (new PagedList.Config.Builder()).setEnablePlaceholders(false)
                        .setPrefetchDistance(PAGE_SIZE)
                        .setPageSize(PREFETCH_DISTANCE).build();

        return Transformations.switchMap(mApp.getPersistentDatabase(),
                new Function<PersistentDatabase, LiveData<PagedList<DictionarySearchElement>>>() {
                    @Override
                    public LiveData<PagedList<DictionarySearchElement>> apply(PersistentDatabase input) {
                        return new LivePagedListBuilder<>
                                (new RoomFactoryWrapper<>(input.dictionaryDisplayElementDao().getAllDetailsLivePaged(mKey)), pagedListConfig)
                                .setBoundaryCallback(new PagedList.BoundaryCallback<DictionarySearchElement>() {
                                    @Override
                                    public void onItemAtEndLoaded(@NonNull DictionarySearchElement itemAtEnd) {
                                        if (!"".equals(mTerm)) {
                                            executeQueryNextStatement(mCurrentDatabase, mTerm, mQueryResult, mQueryStatements, new QueryNextStatementCallback() {
                                                @Override
                                                public void callback(QueryNextStatementResult aResult) {
                                                    mQueryResult = aResult;

                                                    if (mQueryStatements != null && mQueryResult.nextStatement >= mQueryStatements.statements.length) {
                                                        if (mState != STATUS_RESULTS_FOUND_ENDED && mState != STATUS_NO_RESULTS_FOUND_ENDED) {
                                                            mState = STATUS_RESULTS_FOUND_ENDED;
                                                            mStatus.setValue(mState);
                                                        }
                                                    }
                                                }
                                            });
                                        }

                                        super.onItemAtEndLoaded(itemAtEnd);
                                    }
                                }).build();
                    }
                });
    }

    // Query execution
    static class QueryNextStatementResult {
        int nextStatement;
        boolean found;
    }

    interface QueryNextStatementCallback {
        void callback(QueryNextStatementResult aResult);
    }

    @MainThread
    private static void insertQueryAll(final PersistentDatabase aDatabase,
                                       final boolean aAllowQueryAll,
                                       final int aKey,
                                       final QueryStatementsContainer aStatements,
                                       final QueryNextStatementCallback aCallback) {
        if (aDatabase == null || aStatements == null) {
            return;
        }

        new AsyncTask<Void, Void, QueryNextStatementResult>() {
            @Override
            protected QueryNextStatementResult doInBackground(Void... voids) {
                final QueryNextStatementResult result = new QueryNextStatementResult();

                result.nextStatement = 0;
                result.found = true;

                aDatabase.runInTransaction(new Runnable() {
                    @Override
                    public void run() {
                        if (aAllowQueryAll) {
                            aStatements.insertAllStatement.bindLong(1, aKey);
                            aStatements.insertAllStatement.executeInsert();
                        }
                    }
                });

                return result;
            }

            @Override
            protected void onPostExecute(QueryNextStatementResult queryNextStatementResult) {
                super.onPostExecute(queryNextStatementResult);

                if (aCallback != null) {
                    aCallback.callback(queryNextStatementResult);
                }
            }
        }.execute();
    }

    @MainThread
    private static void resetQuery(final PersistentDatabase aDatabase,
                                   final int mKey,
                                   final QueryStatementsContainer aStatements,
                                   final QueryNextStatementCallback aCallback) {
        if (aDatabase == null || aStatements == null) {
            return;
        }

        new AsyncTask<Void, Void, QueryNextStatementResult>() {
            @Override
            protected QueryNextStatementResult doInBackground(Void... voids) {
                final QueryNextStatementResult result = new QueryNextStatementResult();

                result.nextStatement = 0;
                result.found = false;

                aDatabase.runInTransaction(new Runnable() {
                    @Override
                    public void run() {
                        aStatements.deleteStatement.bindLong(1, mKey);
                        aStatements.deleteStatement.executeUpdateDelete();
                    }
                });

                return result;
            }

            @Override
            protected void onPostExecute(QueryNextStatementResult queryNextStatementResult) {
                super.onPostExecute(queryNextStatementResult);

                if (aCallback != null) {
                    aCallback.callback(queryNextStatementResult);
                }
            }
        }.execute();
    }

    @MainThread
    private static void executeQueryNextStatement(final PersistentDatabase aDatabase,
                                                  final String aTerm,
                                                  final QueryNextStatementResult aPreviousResult,
                                                  final QueryStatementsContainer aStatements,
                                                  final QueryNextStatementCallback aCallback) {
        if (aDatabase == null || aTerm == null || aTerm.equals("") || aStatements == null ||
                (aPreviousResult != null && aPreviousResult.nextStatement >= aStatements.statements.length)) {
            return;
        }

        new AsyncTask<Void, Void, QueryNextStatementResult>() {
            @Override
            protected QueryNextStatementResult doInBackground(Void... voids) {
                final QueryNextStatementResult result = new QueryNextStatementResult();

                aDatabase.runInTransaction(new Runnable() {
                    @Override
                    public void run() {
                        long lastInsert = -1;
                        result.nextStatement = aPreviousResult == null ? 0 : aPreviousResult.nextStatement;

                        while (lastInsert == -1 && result.nextStatement < aStatements.statements.length) {
                            if (aStatements.statements[result.nextStatement] != null) {
                                lastInsert = aStatements.statements[result.nextStatement].execute(aTerm);
                            }

                            result.nextStatement = result.nextStatement + 1;
                        }

                        if (lastInsert >= 0) {
                            result.found = true;
                        }
                    }
                });

                return result;
            }

            @Override
            protected void onPostExecute(QueryNextStatementResult queryNextStatementResult) {
                super.onPostExecute(queryNextStatementResult);

                if (aCallback != null) {
                    aCallback.callback(queryNextStatementResult);
                }
            }
        }.execute();
    }
}
