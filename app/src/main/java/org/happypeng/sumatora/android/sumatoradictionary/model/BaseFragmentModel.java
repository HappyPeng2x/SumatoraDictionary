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
import android.os.Parcelable;

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
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteStatement;

import org.happypeng.sumatora.android.sumatoradictionary.DictionaryApplication;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryBookmark;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchElement;
import org.happypeng.sumatora.android.sumatoradictionary.db.InstalledDictionary;
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentDatabase;
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentLanguageSettings;
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.ValueHolder;
import org.happypeng.sumatora.jromkan.Romkan;

import java.io.IOException;
import java.util.List;

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
                        + "json_group_array(DictionaryTranslation.gloss) AS gloss, "
                        + "null as example_sentences "
                    + "FROM jmdict.DictionaryEntry, "
                        + "%s.DictionaryTranslation "
                    + "WHERE DictionaryEntry.seq = DictionaryTranslation.seq AND "
                        + "DictionaryEntry.seq IN (%s) %s "
                    + "GROUP BY DictionaryEntry.seq";

    static private final String SQL_REVERSE_QUERY_INSERT_ELEMENT =
            "INSERT OR IGNORE INTO DictionaryElement "
                    + "SELECT ? AS ref, "
                    + "(1-min(1,max(length(DictionaryEntry.readingsPrio), length(DictionaryEntry.writingsPrio))))*10000+"
                    + "100*DictionaryTranslation.gloss_id+gloss_offset AS entryOrder, "
                    + "DictionaryEntry.seq "
                    + "FROM jmdict.DictionaryEntry, "
                    + "%s.DictionaryTranslation, "
                    + "(%s) AS DictionaryTranslationSelect "
                    + "WHERE DictionaryEntry.seq = DictionaryTranslation.seq AND "
                    + "DictionaryTranslation.rowid = DictionaryTranslationSelect.gloss_docid %s";

    static private final String SQL_REVERSE_QUERY_DELETE_ELEMENTS =
            "DELETE FROM DictionaryElement WHERE ref = ?";

    static private final String SQL_REVERSE_QUERY_EXACT =
            "SELECT DictionaryTranslationIndex.docid AS gloss_docid, split_offsets(offsets(DictionaryTranslationIndex), ' ', 2, 500) AS gloss_offset FROM %s.DictionaryTranslationIndex WHERE DictionaryTranslationIndex.gloss MATCH ?";

    static private final String SQL_REVERSE_QUERY_BEGIN =
            "SELECT DictionaryTranslationIndex.docid AS gloss_docid, split_offsets(offsets(DictionaryTranslationIndex), ' ', 2, 500) AS gloss_offset FROM %s.DictionaryTranslationIndex WHERE DictionaryTranslationIndex.gloss MATCH ? || '*'";

    static private final String SQL_QUERY_DICTIONARY_ELEMENT_DISPLAY =
            "INSERT OR IGNORE INTO DictionaryDisplayElement SELECT "
                    + "DictionaryElement.ref, "
                    + "DictionaryElement.entryOrder, "
                    + "DictionaryElement.seq, "
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
                    + "json_group_array(DictionaryTranslation.gloss) AS gloss, "
                    + "null as example_sentences "
                    + "FROM DictionaryElement, "
                    + "jmdict.DictionaryEntry, "
                    + "%s.DictionaryTranslation "
                    + "WHERE DictionaryElement.seq = DictionaryEntry.seq AND "
                    + "DictionaryElement.seq = DictionaryTranslation.seq AND "
                    + "DictionaryElement.ref = ? "
                    + "GROUP BY DictionaryEntry.seq";

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

    @Override
    protected void onCleared() {
        super.onCleared();
    }

    // Query related classes
    private static abstract class QueryStatement {
        final int ref;
        final PersistentLanguageSettings languageSettings;
        final PersistentDatabase database;
        final SupportSQLiteStatement statement;
        final SupportSQLiteStatement backupStatement;

        private QueryStatement(final PersistentDatabase aDB,
                               int aRef,
                               final PersistentLanguageSettings aLanguageSettings,
                               final SupportSQLiteStatement aStatement,
                               final SupportSQLiteStatement aBackupStatement) {
            ref = aRef;
            statement = aStatement;
            backupStatement = aBackupStatement;
            languageSettings = aLanguageSettings;
            database = aDB;
        }

        @WorkerThread
        abstract long execute(String term);

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
        final int order;

        private BasicQueryStatement(final PersistentDatabase aDB,
                                    int aRef, int aOrder,
                                    final PersistentLanguageSettings aLanguageSettings,
                                    final SupportSQLiteStatement aStatement,
                                    final SupportSQLiteStatement aBackupStatement,
                                    boolean aKana, final Romkan aRomkan) {
            super(aDB, aRef, aLanguageSettings, aStatement, aBackupStatement);

            kana = aKana;
            romkan = aRomkan;
            order = aOrder;
        }

        @WorkerThread
        long execute(final String term) {
            final ValueHolder<Long> returnValue = new ValueHolder<>(Long.valueOf(-1));

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

            return returnValue.getValue();
        }
    }

    public static class ReverseQueryStatement extends QueryStatement {
        final SupportSQLiteStatement displayStatement;
        final SupportSQLiteStatement displayBackupStatement;
        final SupportSQLiteStatement deleteElementsStatement;

        private ReverseQueryStatement(final PersistentDatabase aDB,
                                      int aRef,
                                      final PersistentLanguageSettings aLanguageSettings,
                                      final SupportSQLiteStatement aStatement,
                                      final SupportSQLiteStatement aBackupStatement,
                                      final SupportSQLiteStatement aDisplayStatement,
                                      final SupportSQLiteStatement aDisplayBackupStatement,
                                      final SupportSQLiteStatement aDeleteElementStatement) {
            super(aDB, aRef, aLanguageSettings, aStatement, aBackupStatement);

            displayStatement = aDisplayStatement;
            displayBackupStatement = aDisplayBackupStatement;
            deleteElementsStatement = aDeleteElementStatement;
        }

        @WorkerThread
        long execute(final String term) {
            long returnValue = -1;

            long insert = -1;
            long backupInsert = -1;

            statement.bindLong(1, ref);
            statement.bindString(2, term);

            insert = statement.executeInsert();

            if (backupStatement != null) {
                backupStatement.bindLong(1, ref);
                backupStatement.bindString(2, term);

                backupInsert = backupStatement.executeInsert();

                returnValue = Math.max(backupInsert, insert);
            } else {
                returnValue = insert;
            }

            displayStatement.bindString(1, languageSettings.lang);
            displayStatement.bindString(2, languageSettings.lang);
            displayStatement.bindLong(3, ref);

            displayStatement.executeInsert();

            if (displayBackupStatement != null) {
                displayBackupStatement.bindString(1, languageSettings.backupLang);
                displayBackupStatement.bindString(2, languageSettings.lang);
                displayBackupStatement.bindLong(3, ref);

                displayBackupStatement.executeInsert();
            }

            deleteElementsStatement.bindLong(1, ref);

            deleteElementsStatement.executeUpdateDelete();

            return returnValue;
        }
    }

    public static class QueryAllStatement extends QueryStatement {
        final int order;

        private QueryAllStatement(final PersistentDatabase aDB,
                                  int aRef, int aOrder,
                                  final PersistentLanguageSettings aLanguageSettings,
                                  final SupportSQLiteStatement aStatement,
                                  final SupportSQLiteStatement aBackupStatement) {
            super(aDB, aRef, aLanguageSettings, aStatement, aBackupStatement);

            order = aOrder;
        }

        @WorkerThread
        long execute(final String term) {
            final ValueHolder<Long> returnValue = new ValueHolder<>(Long.valueOf(-1));

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
    private PersistentLanguageSettings mLanguageSettings;

    // Query related elements
    private QueryStatementsContainer mQueryStatements;

    private Romkan mRomkan;
    private QueryNextStatementResult mQueryResult;

    // Other status elements
    PersistentDatabase mCurrentDatabase;

    private Parcelable mLayoutManagerState;

    private String mSearchSet;
    private boolean mAllowQueryAll;
    private LiveData<Long> mTableObserve;

    final DictionaryApplication mApp;

    final int mKey;

    private String mTerm;

    // Status
    private MediatorLiveData<Integer> mStatus;

    // Getters
    public LiveData<Integer> getStatus() {
        return mStatus;
    }

    public LiveData<List<InstalledDictionary>> getInstalledDictionaries() {
        return mInstalledDictionariesLive;
    }

    public LiveData<PersistentLanguageSettings> getLanguageSettingsLive() {
        return mApp.getPersistentLanguageSettings();
    }

    public int getQueryPosition() {
        if (mQueryResult != null && mQueryResult.nextStatement > 0) {
            return mQueryResult.nextStatement - 1;
        }

        return 0;
    }

    public Parcelable getLayoutManagerState() {
        return mLayoutManagerState;
    }

    public void setLayoutManagerState(final Parcelable aLayoutManagerState) {
        mLayoutManagerState = aLayoutManagerState;
    }

    // Query
    @MainThread
    private void processQueryInitial()  {
        mStatus.setValue(STATUS_SEARCHING);

        executeQueryInitial(mCurrentDatabase, mTerm, mQueryResult, mQueryStatements, new QueryNextStatementCallback() {
            @Override
            public void callback(QueryNextStatementResult aResult) {
                mQueryResult = aResult;

                if (!mQueryResult.found) {
                    mStatus.setValue(STATUS_NO_RESULTS_FOUND_ENDED);
                } else {
                    if (aResult.nextStatement >= mQueryStatements.statements.length) {
                        mStatus.setValue(STATUS_RESULTS_FOUND_ENDED);
                    } else {
                        mStatus.setValue(STATUS_RESULTS_FOUND);
                    }
                }
            }
        });
    }

    // Delete all elements in table then perform query in a single transaction
    @MainThread
    private void processResetAndQueryInitial()  {
        mStatus.setValue(STATUS_SEARCHING);

        executeResetAndQueryInitial(mKey, mCurrentDatabase, mTerm, null, mQueryStatements, new QueryNextStatementCallback() {
            @Override
            public void callback(QueryNextStatementResult aResult) {
                mQueryResult = aResult;

                if (!mQueryResult.found) {
                    mStatus.setValue(STATUS_NO_RESULTS_FOUND_ENDED);
                } else {
                    if (aResult.nextStatement >= mQueryStatements.statements.length) {
                        mStatus.setValue(STATUS_RESULTS_FOUND_ENDED);
                    } else {
                        mStatus.setValue(STATUS_RESULTS_FOUND);
                    }
                }
            }
        });
    }

    // Delete all elements in table in a transaction, then perform query in another transaction (it resets scroll)
    private void processResetThenQueryInitial()  {
        mStatus.setValue(STATUS_SEARCHING);

        resetQuery(mCurrentDatabase, mKey, mQueryStatements, new QueryNextStatementCallback() {
            @Override
            public void callback(QueryNextStatementResult aResult) {
                mQueryResult = aResult;

                processQueryInitial();
            }
        });
    }

    private void initialize() {
        // setParameters not done
        if (mKey == -1) {
            return;
        }

        mCurrentDatabase = null;

        // Database changed
        mStatus.addSource(mApp.getPersistentDatabase(),
                new Observer<PersistentDatabase>() {
                    @Override
                    public void onChanged(PersistentDatabase persistentDatabase) {
                        performInitialization(mLanguageSettings, persistentDatabase);
                    }
                });

        // Language changed
        mStatus.addSource(mApp.getPersistentLanguageSettings(),
                new Observer<PersistentLanguageSettings>() {
                    @Override
                    public void onChanged(final PersistentLanguageSettings s) {
                        performInitialization(s, mCurrentDatabase);
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

    @MainThread
    private void performInitialization(final PersistentLanguageSettings aLanguageSettings,
                                       final PersistentDatabase aDatabase) {
        final QueryStatementsCallback callback =
                new QueryStatementsCallback() {
            @Override
            public void callback(QueryStatementsContainer oldStatements) {
                mCurrentDatabase = aDatabase;

                createQueryStatements(mCurrentDatabase, mKey, mAllowQueryAll, mSearchSet,
                        aLanguageSettings, mRomkan,
                        new QueryStatementsCallback() {
                            @Override
                            public void callback(QueryStatementsContainer statements) {
                                mLanguageSettings = aLanguageSettings;
                                mQueryStatements = statements;

                                if (mTableObserve != null) {
                                    mStatus.addSource(mTableObserve,
                                            new Observer<Long>() {
                                                @Override
                                                public void onChanged(Long aLong) {
                                                    processResetAndQueryInitial();
                                                }
                                            });
                                }

                                mStatus.setValue(STATUS_INITIALIZED);

                                processQueryInitial();
                            }
                        });
            }
        };

        mStatus.setValue(STATUS_PRE_INITIALIZED);

        if (mQueryStatements != null) {
            resetQuery(mCurrentDatabase, mKey, mQueryStatements, new QueryNextStatementCallback() {
                @Override
                public void callback(QueryNextStatementResult aResult) {
                    mQueryResult = aResult;

                    final QueryStatementsContainer statements = mQueryStatements;
                    mQueryStatements = null;

                    if (mTableObserve != null) {
                        mStatus.removeSource(mTableObserve);
                    }

                    closeQueryStatements(statements, (aLanguageSettings == null || aDatabase == null) ? null : callback);
                }
            });
        }

        if (mQueryStatements == null && aLanguageSettings != null && aDatabase != null) {
            callback.callback(null);
        }

        if (aLanguageSettings == null || aDatabase == null) {
            mLanguageSettings = aLanguageSettings;
            mCurrentDatabase = aDatabase;
        }
    }

    public BaseFragmentModel(Application aApp,
                      final int aKey, final String aSearchSet,
                      final boolean aAllowSearchAll,
                      final LiveData<Long> aTableObserve) {
        super(aApp);

        mApp = (DictionaryApplication) aApp;
        mRomkan = mApp.getRomkan();

        mStatus = new MediatorLiveData<>();

        mTerm = "";

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

            processResetThenQueryInitial();
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
                                              final String aSearchSet, final PersistentLanguageSettings aLanguageSettings,
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
                            aLanguageSettings.backupLang != null ? db.compileStatement(String.format(SQL_QUERY_INSERT_DISPLAY_ELEMENT,
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
                final SupportSQLiteStatement reverseQueryExact =
                        db.compileStatement(String.format(SQL_REVERSE_QUERY_INSERT_ELEMENT,
                                aLanguageSettings.lang, String.format(SQL_REVERSE_QUERY_EXACT, aLanguageSettings.lang), searchSet));
                final SupportSQLiteStatement reverseQueryBegin =
                        db.compileStatement(String.format(SQL_REVERSE_QUERY_INSERT_ELEMENT,
                                aLanguageSettings.lang, String.format(SQL_REVERSE_QUERY_BEGIN, aLanguageSettings.lang), searchSet));

                final SupportSQLiteStatement reverseQueryDisplayElement =
                        db.compileStatement(String.format(SQL_QUERY_DICTIONARY_ELEMENT_DISPLAY,
                                aLanguageSettings.lang));
                final SupportSQLiteStatement reverseQueryDeleteElements =
                        db.compileStatement(SQL_REVERSE_QUERY_DELETE_ELEMENTS);

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
                SupportSQLiteStatement reverseQueryExactBackup = null;
                SupportSQLiteStatement reverseQueryBeginBackup = null;
                SupportSQLiteStatement reverseQueryDisplayBackupElement = null;

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
                    reverseQueryExactBackup =
                            db.compileStatement(String.format(SQL_REVERSE_QUERY_INSERT_ELEMENT,
                                    aLanguageSettings.backupLang, String.format(SQL_REVERSE_QUERY_EXACT, aLanguageSettings.backupLang), searchSet));
                    reverseQueryBeginBackup =
                            db.compileStatement(String.format(SQL_REVERSE_QUERY_INSERT_ELEMENT,
                                    aLanguageSettings.backupLang, String.format(SQL_REVERSE_QUERY_BEGIN, aLanguageSettings.backupLang), searchSet));
                    reverseQueryDisplayBackupElement =
                            db.compileStatement(String.format(SQL_QUERY_DICTIONARY_ELEMENT_DISPLAY,
                                    aLanguageSettings.backupLang));
                }

                container.statements = new QueryStatement[14];

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
                container.statements[12] = new ReverseQueryStatement(aDatabase, aKey, aLanguageSettings, reverseQueryExact, reverseQueryExactBackup, reverseQueryDisplayElement, reverseQueryDisplayBackupElement, reverseQueryDeleteElements);
                container.statements[13] = new ReverseQueryStatement(aDatabase, aKey, aLanguageSettings, reverseQueryBegin, reverseQueryBeginBackup, reverseQueryDisplayElement, reverseQueryDisplayBackupElement, reverseQueryDeleteElements);

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

    @WorkerThread
    private static QueryNextStatementResult performInsertQueryAll(final QueryStatementsContainer aStatements) {
        final QueryNextStatementResult result = new QueryNextStatementResult();

        result.nextStatement = 0;
        result.found = false;

        if (aStatements.insertAllStatement != null && aStatements.insertAllStatement.execute(null) > 0) {
            result.found = true;
        }

        return result;
    }

    @WorkerThread
    private static QueryNextStatementResult performResetQuery(final int mKey,
                                                              final QueryStatementsContainer aStatements) {
        final QueryNextStatementResult result = new QueryNextStatementResult();

        result.nextStatement = 0;
        result.found = false;

        aStatements.deleteStatement.bindLong(1, mKey);
        aStatements.deleteStatement.executeUpdateDelete();

        return result;
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

                aDatabase.runInTransaction(new Runnable() {
                    @Override
                    public void run() {
                        final QueryNextStatementResult newResult =
                            performResetQuery(mKey, aStatements);

                        result.nextStatement = newResult.nextStatement;
                        result.found = newResult.found;
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

    @WorkerThread
    private static QueryNextStatementResult performQueryInitial(final String aTerm,
                                                                final QueryNextStatementResult aPreviousResult,
                                                                final QueryStatementsContainer aStatements) {
        if ("".equals(aTerm)) {
            return performInsertQueryAll(aStatements);
        } else {
            return performExecuteQueryNextStatement(aTerm, aPreviousResult, aStatements);
        }
    }

    @WorkerThread
    private static QueryNextStatementResult performResetAndQueryInitial(final int aKey,
                                                                        final String aTerm,
                                                                        final QueryNextStatementResult aPreviousResult,
                                                                        final QueryStatementsContainer aStatements) {
        QueryNextStatementResult result = performResetQuery(aKey, aStatements);
        return performQueryInitial(aTerm, result, aStatements);
    }

    @WorkerThread
    private static QueryNextStatementResult performExecuteQueryNextStatement(final String aTerm,
                                                                             final QueryNextStatementResult aPreviousResult,
                                                                             final QueryStatementsContainer aStatements) {
        long lastInsert = -1;
        final QueryNextStatementResult result = new QueryNextStatementResult();

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

        return result;
    }

    @MainThread
    private static void executeQueryInitial(final PersistentDatabase aDatabase,
                                            final String aTerm,
                                            final QueryNextStatementResult aPreviousResult,
                                            final QueryStatementsContainer aStatements,
                                            final QueryNextStatementCallback aCallback) {
        if (aDatabase == null || aTerm == null || aStatements == null ||
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
                        final QueryNextStatementResult newResult =
                                performQueryInitial(aTerm, aPreviousResult, aStatements);

                        result.found = newResult.found;
                        result.nextStatement = newResult.nextStatement;
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
    private static void executeResetAndQueryInitial(final int aKey,
                                                    final PersistentDatabase aDatabase,
                                                    final String aTerm,
                                                    final QueryNextStatementResult aPreviousResult,
                                                    final QueryStatementsContainer aStatements,
                                                    final QueryNextStatementCallback aCallback) {
        if (aDatabase == null || aTerm == null || aStatements == null ||
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
                        final QueryNextStatementResult newResult =
                                performResetAndQueryInitial(aKey, aTerm, aPreviousResult, aStatements);

                        result.found = newResult.found;
                        result.nextStatement = newResult.nextStatement;
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
                                (input.dictionaryDisplayElementDao().getAllDetailsLivePaged(mKey), pagedListConfig)
                                .setBoundaryCallback(new PagedList.BoundaryCallback<DictionarySearchElement>() {
                                    @Override
                                    public void onItemAtEndLoaded(@NonNull DictionarySearchElement itemAtEnd) {
                                        if (!"".equals(mTerm)) {
                                            executeQueryInitial(mCurrentDatabase, mTerm, mQueryResult, mQueryStatements, new QueryNextStatementCallback() {
                                                @Override
                                                public void callback(QueryNextStatementResult aResult) {
                                                    mQueryResult = aResult;

                                                    if (mQueryStatements != null && mQueryResult.nextStatement >= mQueryStatements.statements.length) {
                                                        mStatus.setValue(STATUS_RESULTS_FOUND_ENDED);
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
