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
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.ValueHolder;
import org.happypeng.sumatora.jromkan.Romkan;

import java.io.IOException;
import java.util.List;
import java.util.Set;

public class BaseFragmentModel extends AndroidViewModel {
    // Display related SQL queries
    static private final String SQL_QUERY_INSERT_DISPLAY_ELEMENT =
            "INSERT OR IGNORE INTO DictionaryDisplayElement "
                    + "SELECT ? AS ref, "
                        + "? AS entryOrder, "
                        + "DictionaryEntry.seq, "
                        + "DictionaryEntry.readingsPrio, "
                        + "DictionaryEntry.readings, "
                        + "DictionaryEntry.writingsPrio, "
                        + "DictionaryEntry.writings, "
                        + "DictionaryEntry.pos, "
                        + "DictionaryEntry.xref, "
                        + "DictionaryEntry.ant, "
                        + "DictionaryEntry.misc, "
                        + "DictionaryEntry.lsource, "
                        + "DictionaryEntry.dial, "
                        + "DictionaryEntry.s_inf, "
                        + "DictionaryEntry.field, "
                        + "? AS lang, "
                        + "? AS lang_setting, "
                        + "DictionaryTranslation.gloss, "
                        + "null as example_sentences "
                    + "FROM jmdict.DictionaryEntry, "
                        + "%s.DictionaryTranslation "
                    + "WHERE DictionaryEntry.seq = DictionaryTranslation.seq AND "
                        + "DictionaryEntry.seq IN (%s) %s";

    static private final String SQL_QUERY_DELETE =
            "DELETE FROM DictionaryDisplayElement WHERE DictionaryDisplayElement.ref = ?";

    // Query related SQL queries
    static private final String SQL_QUERY_ALL =
            "SELECT DictionaryEntry.seq "
                    + "FROM jmdict.DictionaryEntry";

    static private final String SQL_QUERY_EXACT_WRITING_PRIO =
            "SELECT DictionaryIndex.`rowid` AS seq "
                    + "FROM jmdict.DictionaryIndex "
                    + "WHERE writingsPrio MATCH ?";

    static private final String SQL_QUERY_EXACT_READING_PRIO =
            "SELECT DictionaryIndex.`rowid` AS seq "
                    + "FROM jmdict.DictionaryIndex "
                    + "WHERE readingsPrioKana MATCH ?";

    static private final String SQL_QUERY_EXACT_WRITING_NONPRIO =
            "SELECT DictionaryIndex.`rowid` AS seq "
                    + "FROM jmdict.DictionaryIndex "
                    + "WHERE writings MATCH ?";

    static private final String SQL_QUERY_EXACT_READING_NONPRIO =
            "SELECT DictionaryIndex.`rowid` AS seq "
                    + "FROM jmdict.DictionaryIndex "
                    + "WHERE readingsKana MATCH ?";

    static private final String SQL_QUERY_BEGIN_WRITING_PRIO =
            "SELECT DictionaryIndex.`rowid` AS seq "
                    + "FROM jmdict.DictionaryIndex "
                    + "WHERE writingsPrio MATCH ? || '*'";

    static private final String SQL_QUERY_BEGIN_READING_PRIO =
            "SELECT DictionaryIndex.`rowid` AS seq "
                    + "FROM jmdict.DictionaryIndex "
                    + "WHERE readingsPrioKana MATCH ? || '*'";

    static private final String SQL_QUERY_BEGIN_WRITING_NONPRIO =
            "SELECT DictionaryIndex.`rowid` AS seq "
                    + "FROM jmdict.DictionaryIndex "
                    + "WHERE writings MATCH ? || '*'";

    static private final String SQL_QUERY_BEGIN_READING_NONPRIO =
            "SELECT DictionaryIndex.`rowid` AS seq "
                    + "FROM jmdict.DictionaryIndex "
                    + "WHERE readingsKana MATCH ? || '*'";

    static private final String SQL_QUERY_PARTS_WRITING_PRIO =
            "SELECT DictionaryIndex.`rowid` AS seq "
                    + "FROM jmdict.DictionaryIndex "
                    + "WHERE writingsPrioParts MATCH ? || '*'";

    static private final String SQL_QUERY_PARTS_READING_PRIO =
            "SELECT DictionaryIndex.`rowid` AS seq "
                    + "FROM jmdict.DictionaryIndex "
                    + "WHERE readingsPrioKanaParts MATCH ? || '*'";

    static private final String SQL_QUERY_PARTS_WRITING_NONPRIO =
            "SELECT DictionaryIndex.`rowid` AS seq "
                    + "FROM jmdict.DictionaryIndex "
                    + "WHERE writingsParts MATCH ? || '*'";

    static private final String SQL_QUERY_PARTS_READING_NONPRIO =
            "SELECT DictionaryIndex.`rowid` AS seq "
                    + "FROM jmdict.DictionaryIndex "
                    + "WHERE readingsKanaParts MATCH ? || '*'";

    // Query related classes
    private static abstract class QueryStatement {
        final int ref;
        final int order;
        final PersistantLanguageSettings languageSettings;
        final PersistentDatabase database;
        final SupportSQLiteStatement statement;
        final SupportSQLiteStatement backupStatement;

        private QueryStatement(final PersistentDatabase aDB,
                               int aRef, int aOrder,
                               final PersistantLanguageSettings aLanguageSettings,
                               final SupportSQLiteStatement aStatement,
                               final SupportSQLiteStatement aBackupStatement) {
            ref = aRef;
            order = aOrder;
            statement = aStatement;
            backupStatement = aBackupStatement;
            languageSettings = aLanguageSettings;
            database = aDB;
        }

        @WorkerThread
        abstract long execute(String term);

        public int getOrder() {
            return order;
        }

        @WorkerThread
        public void close() throws IOException {
            statement.close();

            if (backupStatement != null) {
                backupStatement.close();
            }
        }
    }

    public static class BasicQueryStatement extends QueryStatement {
        private final boolean kana;
        private final Romkan romkan;

        private BasicQueryStatement(final PersistentDatabase aDB,
                                    int aRef, int aOrder,
                                    final PersistantLanguageSettings aLanguageSettings,
                                    final SupportSQLiteStatement aStatement,
                                    final SupportSQLiteStatement aBackupStatement,
                                    boolean aKana, final Romkan aRomkan) {
            super(aDB, aRef, aOrder, aLanguageSettings, aStatement, aBackupStatement);

            kana = aKana;
            romkan = aRomkan;
        }

        @WorkerThread
        long execute(final String term) {
            final ValueHolder<Long> returnValue = new ValueHolder<>(Long.valueOf(-1));

            database.runInTransaction(new Runnable() {
                @Override
                public void run() {
                    String bindTerm = term;
                    long insert = -1;
                    long backupInsert = -1;

                    if (kana) {
                        bindTerm = romkan.to_katakana(romkan.to_hepburn(term));
                    }

                    statement.bindLong(1, ref);
                    statement.bindLong(2, order);
                    statement.bindString(3, languageSettings.lang);
                    statement.bindString(4, languageSettings.lang);
                    statement.bindString(5, bindTerm);

                    insert = statement.executeInsert();

                    if (backupStatement != null) {
                        backupStatement.bindLong(1, ref);
                        backupStatement.bindLong(2, order);
                        backupStatement.bindString(3, languageSettings.backupLang);
                        backupStatement.bindString(4, languageSettings.lang);
                        backupStatement.bindString(5, bindTerm);

                        backupInsert = backupStatement.executeInsert();

                        returnValue.setValue(Math.max(backupInsert, insert));
                    } else {
                        returnValue.setValue(insert);
                    }
                }
            });

            return returnValue.getValue();
        }
    }

    public static class QueryAllStatement extends QueryStatement {
        private QueryAllStatement(final PersistentDatabase aDB,
                                    int aRef, int aOrder,
                                    final PersistantLanguageSettings aLanguageSettings,
                                    final SupportSQLiteStatement aStatement,
                                    final SupportSQLiteStatement aBackupStatement) {
            super(aDB, aRef, aOrder, aLanguageSettings, aStatement, aBackupStatement);
        }

        @WorkerThread
        long execute(final String term) {
            final ValueHolder<Long> returnValue = new ValueHolder<>(Long.valueOf(-1));

            database.runInTransaction(new Runnable() {
                @Override
                public void run() {
                    long insert = -1;
                    long backupInsert = -1;

                    statement.bindLong(1, ref);
                    statement.bindLong(2, order);
                    statement.bindString(3, languageSettings.lang);
                    statement.bindString(4, languageSettings.lang);

                    insert = statement.executeInsert();

                    if (backupStatement != null) {
                        backupStatement.bindLong(1, ref);
                        backupStatement.bindLong(2, order);
                        backupStatement.bindString(3, languageSettings.backupLang);
                        backupStatement.bindString(4, languageSettings.lang);

                        backupInsert = backupStatement.executeInsert();

                        returnValue.setValue(Math.max(backupInsert, insert));
                    } else {
                        returnValue.setValue(insert);
                    }
                }
            });

            return returnValue.getValue();
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

    // Lang selection menu
    private LiveData<List<InstalledDictionary>> mInstalledDictionariesLive;
    private List<InstalledDictionary> mInstalledDictionaries;

    // Language and backup language
    private PersistantLanguageSettings mLanguageSettings;

    // Query related elements
    private QueryStatementsContainer mQueryStatements;

    private Romkan mRomkan;
    private QueryNextStatementResult mQueryResult;

    // Other status elements
    PersistentDatabase mCurrentDatabase;

    private final String mSearchSet;
    private final boolean mAllowQueryAll;
    private final String mTableObserve;

    final DictionaryApplication mApp;

    final int mKey;

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
                mQueryStatements != null && mQueryStatements.statements.length == 14) {
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
                    insertQueryAll(mCurrentDatabase, mAllowQueryAll, mKey, mLanguageSettings,
                            mQueryStatements, new QueryNextStatementCallback() {
                        @Override
                        public void callback(QueryNextStatementResult aResult) {
                            mQueryResult = aResult;

                            if (!mQueryResult.found) {
                                if (mState != STATUS_NO_RESULTS_FOUND_ENDED) {
                                    mState = STATUS_NO_RESULTS_FOUND_ENDED;
                                    mStatus.setValue(mState);
                                }
                            } else if (mState != STATUS_RESULTS_FOUND_ENDED) {
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

    private void initialize() {
        mCurrentDatabase = null;

        // Queries related initialization
        final InvalidationTracker.Observer observer = new InvalidationTracker.Observer(mTableObserve) {
            @Override
            public void onInvalidated(@NonNull Set<String> tables) {
                processQueryInitial();
            }
        };

        // Create new statements and perform query
        final Function<Void, Void> processQueryStatements = new Function<Void, Void>() {
            @Override
            public Void apply(Void input) {
                if (mCurrentDatabase != null && mLanguageSettings != null) {
                    createQueryStatements(mCurrentDatabase, mKey, mAllowQueryAll, mSearchSet,
                            mLanguageSettings, mRomkan,
                            new QueryStatementsCallback() {
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

        final Function<Void, Void> resetQueryStatements = new Function<Void, Void>() {
            @Override
            public Void apply(Void input) {
                if (mQueryStatements != null) {
                    // Clear query, close statements, create new statements and perform query
                    resetQuery(mCurrentDatabase, mKey, mQueryStatements, new QueryNextStatementCallback() {
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

                        resetQueryStatements.apply(null);

                        if (mCurrentDatabase != null) {
                            if (!"".equals(mTableObserve)) {
                                mCurrentDatabase.getInvalidationTracker().addObserver(observer);
                            }
                        }
                    }
                });

        // Language changed
        mStatus.addSource(mApp.getPersistentLanguageSettings(),
                new Observer<PersistantLanguageSettings>() {
                    @Override
                    public void onChanged(final PersistantLanguageSettings s) {
                        mLanguageSettings = s;

                        if (mQueryStatements != null && mLanguageSettings == null) {
                                resetQuery(mCurrentDatabase, mKey, mQueryStatements, new QueryNextStatementCallback() {
                                    @Override
                                    public void callback(QueryNextStatementResult aResult) {
                                        mQueryResult = aResult;

                                        final QueryStatementsContainer statements = mQueryStatements;
                                        mQueryStatements = null;

                                        updateInitializationStatus();

                                        closeQueryStatements(statements, null);
                                    }
                                });
                        }

                        if (mQueryStatements == null && mLanguageSettings != null) {
                                createQueryStatements(mCurrentDatabase, mKey, mAllowQueryAll, mSearchSet,
                                        mLanguageSettings, mRomkan,
                                        new QueryStatementsCallback() {
                                            @Override
                                            public void callback(QueryStatementsContainer statements) {
                                                mQueryStatements = statements;

                                                updateInitializationStatus();

                                                processQueryInitial();
                                            }
                                        });
                        }

                        if (mQueryStatements != null && mLanguageSettings != null) {
                            resetQuery(mCurrentDatabase, mKey, mQueryStatements, new QueryNextStatementCallback() {
                                @Override
                                public void callback(QueryNextStatementResult aResult) {
                                    mQueryResult = aResult;

                                    final QueryStatementsContainer statements = mQueryStatements;
                                    mQueryStatements = null;

                                    updateInitializationStatus();

                                    closeQueryStatements(statements, new QueryStatementsCallback() {
                                        @Override
                                        public void callback(QueryStatementsContainer statements) {
                                            createQueryStatements(mCurrentDatabase, mKey, mAllowQueryAll, mSearchSet,
                                                    mLanguageSettings, mRomkan,
                                                    new QueryStatementsCallback() {
                                                        @Override
                                                        public void callback(QueryStatementsContainer statements) {
                                                            mQueryStatements = statements;

                                                            updateInitializationStatus();

                                                            processQueryInitial();
                                                        }
                                                    });
                                        }
                                    });
                                }
                            });
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
                    }
                });
    }

    public BaseFragmentModel(Application aApp,
                      final int aKey, final String aSearchSet,
                      final boolean aAllowSearchAll,
                      final @NonNull String aTableObserve) {
        super(aApp);

        mApp = (DictionaryApplication) aApp;
        mRomkan = mApp.getRomkan();

        mStatus = new MediatorLiveData<>();

        mTerm = "";
        mState = STATUS_PRE_INITIALIZED;

        mKey = aKey;
        mSearchSet = aSearchSet;
        mAllowQueryAll = aAllowSearchAll;
        mTableObserve = aTableObserve;

        initialize();
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

            processQueryInitial();
        }
    }

    public String getTerm() {
        return mTerm;
    }

    // Query related methods

    // Initialization: mKey, mSearchSet, mRomkan, mCurrentDatabase -> mQueries, mLang -> end
    static class QueryStatementsContainer {
        QueryStatement[] statements;
        QueryStatement insertAllStatement;
        SupportSQLiteStatement deleteStatement;
    }

    interface QueryStatementsCallback {
        void callback(QueryStatementsContainer statements);
    }

    @MainThread
    private static void createQueryStatements(final PersistentDatabase aDatabase, final int aKey, final boolean aAllowQueryAll,
                                              final String aSearchSet, final PersistantLanguageSettings aLanguageSettings,
                                              final Romkan aRomkan, final QueryStatementsCallback aCallback) {
        new AsyncTask<Void, Void, QueryStatementsContainer>() {
            @Override
            protected QueryStatementsContainer doInBackground(Void... voids) {
                QueryStatementsContainer container = new QueryStatementsContainer();
                SupportSQLiteDatabase db = aDatabase.getOpenHelper().getWritableDatabase();

                String searchSet = aSearchSet == null ? "" : "AND DictionaryEntry.seq IN (" + aSearchSet + ")";

                if (aAllowQueryAll) {
                    container.insertAllStatement = new QueryAllStatement(aDatabase, aKey, 1, aLanguageSettings,
                            db.compileStatement(String.format(SQL_QUERY_INSERT_DISPLAY_ELEMENT,
                                    aLanguageSettings.lang, SQL_QUERY_ALL, searchSet)),
                            aLanguageSettings.backupLang == null ? db.compileStatement(String.format(SQL_QUERY_INSERT_DISPLAY_ELEMENT,
                                    aLanguageSettings.backupLang, SQL_QUERY_ALL, searchSet)) : null);
                }

                container.deleteStatement = db.compileStatement(SQL_QUERY_DELETE);

                final SupportSQLiteStatement queryExactPrioWriting =
                        db.compileStatement(String.format(SQL_QUERY_INSERT_DISPLAY_ELEMENT,
                                aLanguageSettings.lang, SQL_QUERY_EXACT_WRITING_PRIO, searchSet));
                final SupportSQLiteStatement queryExactPrioReading =
                        db.compileStatement(String.format(SQL_QUERY_INSERT_DISPLAY_ELEMENT,
                                aLanguageSettings.lang, SQL_QUERY_EXACT_READING_PRIO, searchSet));
                final SupportSQLiteStatement queryExactNonPrioWriting =
                        db.compileStatement(String.format(SQL_QUERY_INSERT_DISPLAY_ELEMENT,
                                aLanguageSettings.lang, SQL_QUERY_EXACT_WRITING_NONPRIO, searchSet));
                final SupportSQLiteStatement queryExactNonPrioReading =
                        db.compileStatement(String.format(SQL_QUERY_INSERT_DISPLAY_ELEMENT,
                                aLanguageSettings.lang, SQL_QUERY_EXACT_READING_NONPRIO, searchSet));
                final SupportSQLiteStatement queryBeginPrioWriting =
                        db.compileStatement(String.format(SQL_QUERY_INSERT_DISPLAY_ELEMENT,
                                aLanguageSettings.lang, SQL_QUERY_BEGIN_WRITING_PRIO, searchSet));
                final SupportSQLiteStatement queryBeginPrioReading =
                        db.compileStatement(String.format(SQL_QUERY_INSERT_DISPLAY_ELEMENT,
                                aLanguageSettings.lang, SQL_QUERY_BEGIN_READING_PRIO, searchSet));
                final SupportSQLiteStatement queryBeginNonPrioWriting =
                        db.compileStatement(String.format(SQL_QUERY_INSERT_DISPLAY_ELEMENT,
                                aLanguageSettings.lang, SQL_QUERY_BEGIN_WRITING_NONPRIO, searchSet));
                final SupportSQLiteStatement queryBeginNonPrioReading =
                        db.compileStatement(String.format(SQL_QUERY_INSERT_DISPLAY_ELEMENT,
                                aLanguageSettings.lang, SQL_QUERY_BEGIN_READING_NONPRIO, searchSet));
                final SupportSQLiteStatement queryPartsPrioWriting =
                        db.compileStatement(String.format(SQL_QUERY_INSERT_DISPLAY_ELEMENT,
                                aLanguageSettings.lang, SQL_QUERY_PARTS_WRITING_PRIO, searchSet));
                final SupportSQLiteStatement queryPartsPrioReading =
                        db.compileStatement(String.format(SQL_QUERY_INSERT_DISPLAY_ELEMENT,
                                aLanguageSettings.lang, SQL_QUERY_PARTS_READING_PRIO, searchSet));
                final SupportSQLiteStatement queryPartsNonPrioWriting =
                        db.compileStatement(String.format(SQL_QUERY_INSERT_DISPLAY_ELEMENT,
                                aLanguageSettings.lang, SQL_QUERY_PARTS_WRITING_NONPRIO, searchSet));
                final SupportSQLiteStatement queryPartsNonPrioReading =
                        db.compileStatement(String.format(SQL_QUERY_INSERT_DISPLAY_ELEMENT,
                                aLanguageSettings.lang, SQL_QUERY_PARTS_READING_NONPRIO, searchSet));

                SupportSQLiteStatement queryExactPrioWritingBackup = null;
                SupportSQLiteStatement queryExactPrioReadingBackup = null;
                SupportSQLiteStatement queryExactNonPrioWritingBackup = null;
                SupportSQLiteStatement queryExactNonPrioReadingBackup = null;
                SupportSQLiteStatement queryBeginPrioWritingBackup = null;
                SupportSQLiteStatement queryBeginPrioReadingBackup = null;
                SupportSQLiteStatement queryBeginNonPrioWritingBackup = null;
                SupportSQLiteStatement queryBeginNonPrioReadingBackup = null;
                SupportSQLiteStatement queryPartsPrioWritingBackup = null;
                SupportSQLiteStatement queryPartsPrioReadingBackup = null;
                SupportSQLiteStatement queryPartsNonPrioWritingBackup = null;
                SupportSQLiteStatement queryPartsNonPrioReadingBackup = null;

                if (aLanguageSettings.backupLang != null) {
                    queryExactPrioWritingBackup =
                            db.compileStatement(String.format(SQL_QUERY_INSERT_DISPLAY_ELEMENT,
                                    aLanguageSettings.backupLang, SQL_QUERY_EXACT_WRITING_PRIO, searchSet));
                    queryExactPrioReadingBackup =
                            db.compileStatement(String.format(SQL_QUERY_INSERT_DISPLAY_ELEMENT,
                                    aLanguageSettings.backupLang, SQL_QUERY_EXACT_READING_PRIO, searchSet));
                    queryExactNonPrioWritingBackup =
                            db.compileStatement(String.format(SQL_QUERY_INSERT_DISPLAY_ELEMENT,
                                    aLanguageSettings.backupLang, SQL_QUERY_EXACT_WRITING_NONPRIO, searchSet));
                    queryExactNonPrioReadingBackup =
                            db.compileStatement(String.format(SQL_QUERY_INSERT_DISPLAY_ELEMENT,
                                    aLanguageSettings.backupLang, SQL_QUERY_EXACT_READING_NONPRIO, searchSet));
                    queryBeginPrioWritingBackup =
                            db.compileStatement(String.format(SQL_QUERY_INSERT_DISPLAY_ELEMENT,
                                    aLanguageSettings.backupLang, SQL_QUERY_BEGIN_WRITING_PRIO, searchSet));
                    queryBeginPrioReadingBackup =
                            db.compileStatement(String.format(SQL_QUERY_INSERT_DISPLAY_ELEMENT,
                                    aLanguageSettings.backupLang, SQL_QUERY_BEGIN_READING_PRIO, searchSet));
                    queryBeginNonPrioWritingBackup =
                            db.compileStatement(String.format(SQL_QUERY_INSERT_DISPLAY_ELEMENT,
                                    aLanguageSettings.backupLang, SQL_QUERY_BEGIN_READING_PRIO, searchSet));
                    queryBeginNonPrioReadingBackup =
                            db.compileStatement(String.format(SQL_QUERY_INSERT_DISPLAY_ELEMENT,
                                    aLanguageSettings.backupLang, SQL_QUERY_BEGIN_READING_NONPRIO, searchSet));
                    queryPartsPrioWritingBackup =
                            db.compileStatement(String.format(SQL_QUERY_INSERT_DISPLAY_ELEMENT,
                                    aLanguageSettings.backupLang, SQL_QUERY_PARTS_WRITING_PRIO, searchSet));
                    queryPartsPrioReadingBackup =
                            db.compileStatement(String.format(SQL_QUERY_INSERT_DISPLAY_ELEMENT,
                                    aLanguageSettings.backupLang, SQL_QUERY_PARTS_READING_PRIO, searchSet));
                    queryPartsNonPrioWritingBackup =
                            db.compileStatement(String.format(SQL_QUERY_INSERT_DISPLAY_ELEMENT,
                                    aLanguageSettings.backupLang, SQL_QUERY_PARTS_WRITING_NONPRIO, searchSet));
                    queryPartsNonPrioReadingBackup =
                            db.compileStatement(String.format(SQL_QUERY_INSERT_DISPLAY_ELEMENT,
                                    aLanguageSettings.backupLang, SQL_QUERY_PARTS_READING_NONPRIO, searchSet));
                }

                container.statements = new QueryStatement[12];

                container.statements[0] = new BasicQueryStatement(aDatabase, aKey, 1, aLanguageSettings, queryExactPrioWriting, queryExactPrioWritingBackup, false, aRomkan);
                container.statements[1] = new BasicQueryStatement(aDatabase, aKey, 2, aLanguageSettings, queryExactPrioReading, queryExactPrioReadingBackup, true, aRomkan);
                container.statements[2] = new BasicQueryStatement(aDatabase, aKey, 3, aLanguageSettings, queryExactNonPrioWriting, queryExactNonPrioWritingBackup, false, aRomkan);
                container.statements[3] = new BasicQueryStatement(aDatabase, aKey, 4, aLanguageSettings, queryExactNonPrioReading, queryExactNonPrioReadingBackup, true, aRomkan);
                container.statements[4] = new BasicQueryStatement(aDatabase, aKey, 5, aLanguageSettings, queryBeginPrioWriting, queryBeginPrioWritingBackup, false, aRomkan);
                container.statements[5] = new BasicQueryStatement(aDatabase, aKey, 6, aLanguageSettings, queryBeginPrioReading, queryBeginPrioReadingBackup, true, aRomkan);
                container.statements[6] = new BasicQueryStatement(aDatabase, aKey, 7, aLanguageSettings, queryBeginNonPrioWriting, queryBeginNonPrioWritingBackup, false, aRomkan);
                container.statements[7] = new BasicQueryStatement(aDatabase, aKey, 8, aLanguageSettings, queryBeginNonPrioReading, queryBeginNonPrioReadingBackup, true, aRomkan);
                container.statements[8] = new BasicQueryStatement(aDatabase, aKey, 9, aLanguageSettings, queryPartsPrioWriting, queryPartsPrioWritingBackup, false, aRomkan);
                container.statements[9] = new BasicQueryStatement(aDatabase, aKey, 10, aLanguageSettings, queryPartsPrioReading, queryPartsPrioReadingBackup, true, aRomkan);
                container.statements[10] = new BasicQueryStatement(aDatabase, aKey, 11, aLanguageSettings, queryPartsNonPrioWriting, queryPartsNonPrioWritingBackup, false, aRomkan);
                container.statements[11] = new BasicQueryStatement(aDatabase, aKey, 12, aLanguageSettings, queryPartsNonPrioReading, queryPartsNonPrioReadingBackup, true, aRomkan);

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
                                s.close();
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

                if (aCallback != null) {
                    aCallback.callback(null);
                }
            }
        }.execute();
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
                                       final PersistantLanguageSettings aLanguageSettings,
                                       final QueryStatementsContainer aStatements,
                                       final QueryNextStatementCallback aCallback) {
        if (aDatabase == null || aStatements == null) {
            return;
        }

        // No need to go to async task if there is no statement
        if (aStatements.insertAllStatement == null && aCallback != null) {
            final QueryNextStatementResult result = new QueryNextStatementResult();

            result.found = false;
            result.nextStatement = 0;

            aCallback.callback(result);

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
                        if (aAllowQueryAll) {
                            if (aStatements.insertAllStatement.execute(null) > 0) {
                                result.found = true;
                            }
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
}
