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

import java.util.List;

public class QueryViewStatus {
    final private String term;
    final private boolean found;
    final private boolean preparing;
    final private boolean searching;
    final public boolean filterMemos;
    final public boolean filterBookmarks;
    final private String title;
    final private boolean searchIconifiedByDefault;
    final private boolean shareButtonVisible;
    final private PersistentLanguageSettings persistentLanguageSettings;

    public QueryViewStatus(final String term,
                           final boolean found,
                           final boolean preparing,
                           final boolean searching,
                           final boolean filterMemos,
                           final boolean filterBookmarks,
                           final String title,
                           final boolean searchIconifiedByDefault,
                           final boolean shareButtonVisible,
                           final PersistentLanguageSettings persistentLanguageSettings) {
        this.term = term;
        this.found = found;
        this.preparing = preparing;
        this.searching = searching;
        this.filterMemos = filterMemos;
        this.filterBookmarks = filterBookmarks;
        this.title = title;
        this.searchIconifiedByDefault = searchIconifiedByDefault;
        this.shareButtonVisible = shareButtonVisible;
        this.persistentLanguageSettings = persistentLanguageSettings;
    }

    public String getTerm() {
        return term;
    }
    public boolean getFound() {
        return found;
    }
    public boolean getPreparing() {
        return preparing;
    }
    public boolean getSearching() {
        return searching;
    }
    public boolean getFilterMemos() { return filterMemos; }
    public boolean getFilterBookmarks() { return filterBookmarks; }
    public String getTitle() { return title; }
    public boolean getSearchIconifiedByDefault() { return searchIconifiedByDefault; }
    public boolean getShareButtonVisible() { return shareButtonVisible; }
    public PersistentLanguageSettings getPersistentLanguageSettings() { return persistentLanguageSettings; }
}
