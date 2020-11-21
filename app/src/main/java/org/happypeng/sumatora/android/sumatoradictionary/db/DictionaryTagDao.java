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

package org.happypeng.sumatora.android.sumatoradictionary.db;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface DictionaryTagDao {
    @Query("SELECT tagId FROM DictionaryTag WHERE seq = :seq")
    List<Integer> getTagIds(long seq);

    @Query("SELECT * FROM DictionaryTag WHERE seq = :seq")
    List<DictionaryTag> getBySeq(long seq);

    @Query("SELECT * FROM DictionaryTag WHERE tagId = :tagId")
    List<DictionaryTag> getById(int tagId);

    @Delete
    void delete(DictionaryTag tag);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(DictionaryTag tag);
}
