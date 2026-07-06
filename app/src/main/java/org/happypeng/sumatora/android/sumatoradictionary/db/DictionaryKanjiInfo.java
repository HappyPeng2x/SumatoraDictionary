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

import java.util.ArrayList;
import java.util.List;

// A character's KanjiEntry/KanjiReading/KanjiMeaning rows, assembled for display.
public class DictionaryKanjiInfo {
    public String character;
    public List<String> onReadings = new ArrayList<>();
    public List<String> kunReadings = new ArrayList<>();
    public List<String> meanings = new ArrayList<>();

    @Nullable public Integer strokes;
    @Nullable public Integer grade;
    @Nullable public Integer jlpt;
    @Nullable public Integer freq;
    @Nullable public Integer radical;
}
