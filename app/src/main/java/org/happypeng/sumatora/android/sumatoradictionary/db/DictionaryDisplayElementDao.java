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
import androidx.paging.DataSource;
import androidx.room.Dao;
import androidx.room.Query;

import org.happypeng.sumatora.android.sumatoradictionary.db.tools.QueryTool;

import java.util.List;

@Dao
public interface DictionaryDisplayElementDao {
    @Query("SELECT DictionaryDisplayElement.entryOrder, DictionaryDisplayElement.seq, "
            + "DictionaryDisplayElement.readingsPrio, DictionaryDisplayElement.readings, "
            + "DictionaryDisplayElement.writingsPrio, DictionaryDisplayElement.writings, "
            + "DictionaryDisplayElement.pos, DictionaryDisplayElement.xref, DictionaryDisplayElement.ant, "
            + "DictionaryDisplayElement.misc, DictionaryDisplayElement.lsource, DictionaryDisplayElement.dial, "
            + "DictionaryDisplayElement.s_inf, DictionaryDisplayElement.field, DictionaryBookmark.bookmark, "
            + "DictionaryDisplayElement.lang, DictionaryDisplayElement.gloss FROM "
            + "DictionaryDisplayElement LEFT JOIN DictionaryBookmark ON DictionaryDisplayElement.seq = DictionaryBookmark.seq "
            + "WHERE ref=:ref "
            + "ORDER BY DictionaryDisplayElement.entryOrder/" + QueryTool.ORDER_MULTIPLIER + ", "
            + "(DictionaryDisplayElement.writingsPrio = '' AND DictionaryDisplayElement.readingsPrio = ''), "
            + "DictionaryDisplayElement.entryOrder-" + QueryTool.ORDER_MULTIPLIER + "*(DictionaryDisplayElement.entryOrder/" + QueryTool.ORDER_MULTIPLIER + "), "
            + "DictionaryDisplayElement.seq")
    LiveData<List<DictionarySearchElement>> getAllDetailsLive(int ref);

    @Query("SELECT DictionaryDisplayElement.entryOrder, DictionaryDisplayElement.seq, "
            + "DictionaryDisplayElement.readingsPrio, DictionaryDisplayElement.readings, "
            + "DictionaryDisplayElement.writingsPrio, DictionaryDisplayElement.writings, "
            + "DictionaryDisplayElement.pos, DictionaryDisplayElement.xref, DictionaryDisplayElement.ant, "
            + "DictionaryDisplayElement.misc, DictionaryDisplayElement.lsource, DictionaryDisplayElement.dial, "
            + "DictionaryDisplayElement.s_inf, DictionaryDisplayElement.field, DictionaryBookmark.bookmark, "
            + "DictionaryDisplayElement.lang, DictionaryDisplayElement.gloss FROM "
            + "DictionaryDisplayElement LEFT JOIN DictionaryBookmark ON DictionaryDisplayElement.seq = DictionaryBookmark.seq "
            + "WHERE ref=:ref "
            + "ORDER BY DictionaryDisplayElement.entryOrder/" + QueryTool.ORDER_MULTIPLIER + ", "
            + "(DictionaryDisplayElement.writingsPrio = '' AND DictionaryDisplayElement.readingsPrio = ''), "
            + "DictionaryDisplayElement.entryOrder-" + QueryTool.ORDER_MULTIPLIER + "*(DictionaryDisplayElement.entryOrder/" + QueryTool.ORDER_MULTIPLIER + "), "
            + "DictionaryDisplayElement.seq")    DataSource.Factory<Integer, DictionarySearchElement> getAllDetailsLivePaged(int ref);
    @Query("DELETE FROM DictionaryDisplayElement")
    void deleteAll();
}
