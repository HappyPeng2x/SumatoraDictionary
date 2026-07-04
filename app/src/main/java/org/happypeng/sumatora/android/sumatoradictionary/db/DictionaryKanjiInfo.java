/* Sumatora Dictionary
        Copyright (C) 2026 Nicolas Centa

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

import androidx.annotation.Nullable;

// A single row from kanjidic2.db's KanjiEntry table.
public class DictionaryKanjiInfo {
    public String character;
    public String on;
    public String kun;
    public String meanings; // JSON array of strings

    @Nullable public Integer strokes;
    @Nullable public Integer grade;
    @Nullable public Integer jlpt;
    @Nullable public Integer freq;
    @Nullable public Integer radical;
}
