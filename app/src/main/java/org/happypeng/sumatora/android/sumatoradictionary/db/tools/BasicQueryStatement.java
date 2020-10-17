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
import org.happypeng.sumatora.jromkan.Romkan;

import java.util.Iterator;
import java.util.List;

public class BasicQueryStatement extends QueryStatement {
    private final boolean kana;
    private final Romkan romkan;
    final int order;

    BasicQueryStatement(final PersistentDatabase aDB,
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
    @Override
    long execute(final String term, final List<Object> parameters) {
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

        bind(statement, parameters, 6);

        insert = statement.executeInsert();

        if (backupStatement != null) {
            backupStatement.bindLong(1, ref);
            backupStatement.bindLong(2, order);
            backupStatement.bindString(3, languageSettings.backupLang);
            backupStatement.bindString(4, languageSettings.lang);
            backupStatement.bindString(5, bindTerm);

            bind(backupStatement, parameters, 6);

            backupInsert = backupStatement.executeInsert();

            returnValue.setValue(Math.max(backupInsert, insert));
        } else {
            returnValue.setValue(insert);
        }

        return returnValue.getValue();
    }
}
