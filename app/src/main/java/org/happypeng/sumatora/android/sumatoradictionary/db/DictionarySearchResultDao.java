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

import java.util.List;

import androidx.paging.DataSource;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

@Dao
public interface DictionarySearchResultDao {
    @Query("SELECT * FROM DictionarySearchResult ORDER BY entryOrder, seq")
    DataSource.Factory<Integer, DictionarySearchResult> getAllPaged();

    @Query("SELECT DictionarySearchResult.entryOrder, DictionarySearchResult.seq, " +
            "DictionarySearchResult.readingsPrio, DictionarySearchResult.readings, " +
            "DictionarySearchResult.writingsPrio, DictionarySearchResult.writings, " +
            "DictionarySearchResult.lang, DictionarySearchResult.gloss, " +
            "DictionaryBookmark.bookmark " +
            "FROM DictionarySearchResult LEFT JOIN DictionaryBookmark " +
            "ON DictionarySearchResult.seq = DictionaryBookmark.seq " +
            "ORDER BY DictionarySearchResult.entryOrder, DictionarySearchResult.seq")
    DataSource.Factory<Integer, DictionarySearchElement> getAllBookmarkedPaged();

    @Query("SELECT DictionarySearchResult.entryOrder, DictionarySearchResult.seq, " +
            "DictionarySearchResult.readingsPrio, DictionarySearchResult.readings, " +
            "DictionarySearchResult.writingsPrio, DictionarySearchResult.writings, " +
            "DictionarySearchResult.lang, DictionarySearchResult.gloss, " +
            "DictionaryBookmark.bookmark " +
            "FROM DictionarySearchResult LEFT JOIN DictionaryBookmark " +
            "ON DictionarySearchResult.seq = DictionaryBookmark.seq " +
            "WHERE (DictionarySearchResult.entryOrder = :entryOrder AND DictionarySearchResult.seq > :seq) OR " +
            " DictionarySearchResult.entryOrder > :entryOrder " +
            "ORDER BY DictionarySearchResult.entryOrder, DictionarySearchResult.seq " +
            "LIMIT :count OFFSET :offset")
    List<DictionarySearchElement> getAllBookmarkedSeqCount(int entryOrder, long seq, int count, int offset);

    @Query("SELECT COUNT(DictionarySearchResult.seq) " +
            "FROM DictionarySearchResult LEFT JOIN DictionaryBookmark " +
            "ON DictionarySearchResult.seq = DictionaryBookmark.seq " +
            "WHERE (DictionarySearchResult.entryOrder = :entryOrder AND DictionarySearchResult.seq < :seq) OR " +
            " DictionarySearchResult.entryOrder < :entryOrder ")
    int countBefore(int entryOrder, long seq);

    @Query("SELECT DictionarySearchResult.entryOrder, DictionarySearchResult.seq, " +
            "DictionarySearchResult.readingsPrio, DictionarySearchResult.readings, " +
            "DictionarySearchResult.writingsPrio, DictionarySearchResult.writings, " +
            "DictionarySearchResult.lang, DictionarySearchResult.gloss, " +
            "DictionaryBookmark.bookmark " +
            "FROM DictionarySearchResult LEFT JOIN DictionaryBookmark " +
            "ON DictionarySearchResult.seq = DictionaryBookmark.seq " +
            "WHERE (DictionarySearchResult.entryOrder = :entryOrder AND DictionarySearchResult.seq < :seq) OR " +
            " DictionarySearchResult.entryOrder < :entryOrder " +
            "ORDER BY DictionarySearchResult.entryOrder, DictionarySearchResult.seq")
    List<DictionarySearchElement> getAllBefore(int entryOrder, long seq);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertResult(DictionarySearchResult aResult);
}
