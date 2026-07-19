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
    // Appended to the bound term before it reaches the compiled statement's GLOB placeholder -
    // "" for an exact ('=') tier, "*" for a prefix ('GLOB ?') tier. Deliberately NOT done as
    // `GLOB ? || '*'` in the SQL itself: SQLite's LIKE/GLOB prefix optimization (turning the scan
    // into an index range seek) only fires when the pattern reaching the opcode is a plain bound
    // parameter, not a computed expression - concatenating the wildcard in SQL silently defeats it
    // and forces a full table scan of SearchTerm (or SearchSuffix) on every prefix/substring tier,
    // i.e. on nearly every search. Building the full pattern here keeps the bind position a plain
    // string parameter, so the index gets used. See the query-plan audit this fixed.
    private final String patternSuffix;
    private final Romkan romkan;
    final int order;

    BasicQueryStatement(final PersistentDatabase aDB,
                                int aRef, int aOrder,
                                final PersistentLanguageSettings aLanguageSettings,
                                final SupportSQLiteStatement aStatement,
                                final SupportSQLiteStatement aBackupStatement,
                                boolean aKana, final String aPatternSuffix, final Romkan aRomkan) {
        super(aDB, aRef, aLanguageSettings, aStatement, aBackupStatement);

        kana = aKana;
        patternSuffix = aPatternSuffix;
        romkan = aRomkan;
        order = aOrder;
    }

    @WorkerThread
    @Override
    long execute(final String term, final List<Object> parameters) {
        final ValueHolder<Long> returnValue = new ValueHolder<>(Long.valueOf(-1));

        String bindTerm = escapeTerm(term);
        long insert = -1;
        long backupInsert = -1;

        if (kana) {
            bindTerm = romkan.to_katakana(romkan.to_hepburn(bindTerm));
        }

        final String matchTerm = bindTerm + patternSuffix;

        statement.bindLong(1, ref);
        statement.bindLong(2, order);
        statement.bindString(3, term);
        statement.bindString(4, matchTerm);

        bind(statement, parameters, 5);

        insert = statement.executeInsert();

        if (backupStatement != null) {
            backupStatement.bindLong(1, ref);
            backupStatement.bindLong(2, order);
            backupStatement.bindString(3, term);
            backupStatement.bindString(4, matchTerm);

            bind(backupStatement, parameters, 5);

            backupInsert = backupStatement.executeInsert();

            returnValue.setValue(Math.max(backupInsert, insert));
        } else {
            returnValue.setValue(insert);
        }

        return returnValue.getValue();
    }
}
