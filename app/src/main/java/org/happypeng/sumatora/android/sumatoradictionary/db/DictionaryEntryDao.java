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

import androidx.paging.DataSource;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

@Dao
public interface DictionaryEntryDao {
    @Query("SELECT Min(SortOrder) AS entryOrder, DictionaryEntry.seq, DictionaryEntry.readingsPrio, DictionaryEntry.readings, "
            + "DictionaryEntry.writingsPrio, DictionaryEntry.writings, DictionaryTranslation.lang, "
            + "DictionaryTranslation.gloss "
            + "FROM DictionaryEntry, DictionaryTranslation, "
            + "("
            + "SELECT 1 AS SortOrder, DictionaryIndex.`rowid` AS seq FROM DictionaryIndex "
            + "WHERE DictionaryIndex.writingsPrio MATCH :term UNION ALL "
            + "SELECT 2 AS SortOrder, DictionaryIndex.`rowid` AS seq FROM DictionaryIndex "
            + "WHERE DictionaryIndex.readingsPrio MATCH :term UNION ALL "
            + "SELECT 3 AS SortOrder, DictionaryIndex.`rowid` AS seq FROM DictionaryIndex "
            + "WHERE DictionaryIndex.writingsPrio MATCH :term || '*' UNION ALL "
            + "SELECT 4 AS SortOrder, DictionaryIndex.`rowid` AS seq FROM DictionaryIndex "
            + "WHERE DictionaryIndex.readingsPrio MATCH :term || '*' UNION ALL "
            + "SELECT 5 AS SortOrder, DictionaryIndex.`rowid` AS seq FROM DictionaryIndex "
            + "WHERE DictionaryIndex.writingsPrioParts MATCH :term || '*' UNION ALL "
            + "SELECT 6 AS SortOrder, DictionaryIndex.`rowid` AS seq FROM DictionaryIndex "
            + "WHERE DictionaryIndex.readingsPrioParts MATCH :term || '*' UNION ALL "
            + "SELECT 7 AS SortOrder, DictionaryIndex.`rowid` AS seq FROM DictionaryIndex "
            + "WHERE DictionaryIndex.writings MATCH :term UNION ALL "
            + "SELECT 8 AS SortOrder, DictionaryIndex.`rowid` AS seq FROM DictionaryIndex "
            + "WHERE DictionaryIndex.readings MATCH :term UNION ALL "
            + "SELECT 9 AS SortOrder, DictionaryIndex.`rowid` AS seq FROM DictionaryIndex "
            + "WHERE DictionaryIndex.writings MATCH :term || '*' UNION ALL "
            + "SELECT 10 AS SortOrder, DictionaryIndex.`rowid` AS seq FROM DictionaryIndex "
            + "WHERE DictionaryIndex.readings MATCH :term || '*' UNION ALL "
            + "SELECT 11 AS SortOrder, DictionaryIndex.`rowid` AS seq FROM DictionaryIndex "
            + "WHERE DictionaryIndex.writingsParts MATCH :term || '*' UNION ALL "
            + "SELECT 12 AS SortOrder, DictionaryIndex.`rowid` AS seq FROM DictionaryIndex "
            + "WHERE DictionaryIndex.readingsParts MATCH :term || '*'"
            + ") AS A "
            + "WHERE A.seq = DictionaryEntry.seq AND A.seq = DictionaryTranslation.seq AND DictionaryTranslation.lang = :lang "
            + "GROUP BY A.seq ORDER BY Min(A.SortOrder), A.seq")
    DataSource.Factory<Integer, DictionarySearchResult> search(String term, String lang);
}
