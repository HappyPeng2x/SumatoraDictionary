/* Sumatora Dictionary
        Copyright (C) 2020 Nicolas Centa

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

package org.happypeng.sumatora.android.sumatoradictionary.model.status;

import org.happypeng.sumatora.android.sumatoradictionary.db.InstalledDictionary;
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentLanguageSettings;
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.BookmarkImportQueryTool;

import java.util.List;

public class BookmarkImportStatus extends MVIStatus {
    final private int key;
    final private BookmarkImportQueryTool queryTool;
    final private boolean executed;
    final private PersistentLanguageSettings persistentLanguageSettings;

    public BookmarkImportStatus(final int key,
                                final BookmarkImportQueryTool queryTool,
                                final boolean executed,
                                final PersistentLanguageSettings persistentLanguageSettings,
                                final boolean close) {
        super(close);

        this.key = key;
        this.queryTool = queryTool;
        this.executed = executed;
        this.persistentLanguageSettings = persistentLanguageSettings;
    }

    public int getKey() { return key; }
    public BookmarkImportQueryTool getQueryTool() { return queryTool; }
    public boolean getExecuted() { return executed; }
    public PersistentLanguageSettings getPersistentLanguageSettings() { return persistentLanguageSettings; }
}
