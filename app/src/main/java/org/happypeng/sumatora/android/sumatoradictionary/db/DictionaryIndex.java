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

import androidx.room.Entity;
import androidx.room.Fts4;
import androidx.room.PrimaryKey;

@Fts4(contentEntity = DictionaryEntry.class)
@Entity
public class DictionaryIndex {
    @PrimaryKey public int rowid;
    public String readingsPrio;
    public String readingsPrioParts;
    public String readings;
    public String readingsParts;
    public String writingsPrio;
    public String writingsPrioParts;
    public String writings;
    public String writingsParts;

    public  DictionaryIndex() {}
}
