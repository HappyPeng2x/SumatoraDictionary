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

package org.happypeng.sumatora.android.sumatoradictionary.db;

public class DictionarySearchElement implements DictionarySearchElementBase {
    public int entryOrder;
    public long seq;
    public String readingsPrio;
    public String readings;
    public String writingsPrio;
    public String writings;
    public String lang;
    public String gloss;
    public long bookmark;

    public DictionarySearchElement() { }

    public int getEntryOrder() {
        return entryOrder;
    }

    public long getSeq() {
        return seq;
    }

    public String getReadingsPrio() {
        return readingsPrio;
    }

    public String getReadings() {
        return readings;
    }

    public String getWritingsPrio() {
        return writingsPrio;
    }

    public String getWritings() {
        return writings;
    }

    public String getLang() {
        return lang;
    }

    public String getGloss() {
        return gloss;
    }

    public long getBookmark() { return bookmark; }
}
