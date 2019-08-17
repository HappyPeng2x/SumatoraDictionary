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

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Query;

import java.util.List;

@Dao
public interface DictionaryDisplayElementDao {
    @Query("SELECT entryOrder, seq, readingsPrio, readings, writingsPrio, writings, "
            + "pos, xref, ant, "
            + "misc, lsource, dial, "
            + "s_inf, field, 1 as bookmark, "
            + "lang, gloss FROM DictionaryDisplayElement WHERE ref=:ref")
    LiveData<List<DictionarySearchElement>> getAllDetailsLive(int ref);

    @Query("DELETE FROM DictionaryDisplayElement")
    void deleteAll();
}
