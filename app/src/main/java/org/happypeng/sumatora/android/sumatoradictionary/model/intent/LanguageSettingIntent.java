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

package org.happypeng.sumatora.android.sumatoradictionary.model.intent;

import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentLanguageSettings;
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.DictionarySearchQueryTool;

public class LanguageSettingIntent extends MVIIntent {
    final private PersistentLanguageSettings languageSettings;
    final private boolean attached;

    public LanguageSettingIntent(final PersistentLanguageSettings languageSettings,
                                 final boolean attached) {
        this.languageSettings = languageSettings;
        this.attached = attached;
    }

    public PersistentLanguageSettings getLanguageSettings() {
        return languageSettings;
    }
    public boolean getAttached() { return attached; }
}
