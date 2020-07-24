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

import java.util.List;

public class ReverseQueryStatement extends QueryStatement {
    final SupportSQLiteStatement displayStatement;
    final SupportSQLiteStatement displayBackupStatement;
    final SupportSQLiteStatement deleteElementsStatement;

    ReverseQueryStatement(final PersistentDatabase aDB,
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
    @Override
    long execute(final String term, final List<Object> parameters) {
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

        bind(displayStatement, parameters, 4);

        displayStatement.executeInsert();

        if (displayBackupStatement != null) {
            displayBackupStatement.bindString(1, languageSettings.backupLang);
            displayBackupStatement.bindString(2, languageSettings.lang);
            displayBackupStatement.bindLong(3, ref);

            bind(displayBackupStatement, parameters, 4);

            displayBackupStatement.executeInsert();
        }

        deleteElementsStatement.bindLong(1, ref);

        deleteElementsStatement.executeUpdateDelete();

        return returnValue;
    }
}