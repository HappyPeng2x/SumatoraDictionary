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

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface DictionaryBookmarkImportDao {
    @Query("SELECT 1 as entryOrder, DictionaryBookmarkImport.seq, "
            + "DictionaryEntry.readingsPrio, DictionaryEntry.readings, "
            + "DictionaryEntry.writingsPrio, DictionaryEntry.writings, "
            + "DictionaryTranslation.lang, DictionaryTranslation.gloss, "
            + "DictionaryBookmarkImport.bookmark "
            + "FROM DictionaryBookmarkImport, DictionaryEntry, DictionaryTranslation "
            + "WHERE DictionaryBookmarkImport.seq = DictionaryEntry.seq AND "
            + " DictionaryTranslation.seq = DictionaryBookmarkImport.seq AND DictionaryTranslation.lang = :lang "
            + "ORDER BY DictionaryBookmarkImport.seq")
    LiveData<List<DictionarySearchElement>> getAllDetailsLive(String lang);

    @Query("DELETE FROM DictionaryBookmarkImport")
    void deleteAll();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertMany(List<DictionaryBookmarkImport> aBookmarkList);
}
