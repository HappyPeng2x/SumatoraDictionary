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

package org.happypeng.sumatora.android.sumatoradictionary.db.tools;

import androidx.annotation.WorkerThread;
import androidx.sqlite.db.SupportSQLiteStatement;

import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentDatabase;
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentLanguageSettings;

import java.io.IOException;
import java.util.List;

public abstract class QueryStatement {
    final int ref;
    final PersistentLanguageSettings languageSettings;
    final PersistentDatabase database;
    final SupportSQLiteStatement statement;
    final SupportSQLiteStatement backupStatement;

    QueryStatement(final PersistentDatabase aDB,
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
    abstract long execute(String term, List<Object> parameters);

    @WorkerThread
    public void close() throws IOException {
        statement.close();

        if (backupStatement != null) {
            backupStatement.close();
        }
    }

    protected static void bind(final SupportSQLiteStatement statement,
                               final List<Object> parameters,
                               final int start) {
        if (parameters == null) {
            return;
        }

        int i = start;

        for (Object parameter : parameters) {
            if (parameter == null) {
              statement.bindNull(i);
            } else if (parameter instanceof Long) {
                statement.bindLong(i, (Long) parameter);
            } else if (parameter instanceof Integer) {
                statement.bindLong(i, (Integer) parameter);
            } else if (parameter instanceof String) {
                statement.bindString(i, (String) parameter);
            } else if (parameter instanceof Boolean) {
                statement.bindLong(i, (Boolean) parameter ? 1 : 0);
            }

            i++;
        }
    }

    protected static String escapeTerm(final String term) {
        return term.replaceAll("[\"()]", "");
    }
}