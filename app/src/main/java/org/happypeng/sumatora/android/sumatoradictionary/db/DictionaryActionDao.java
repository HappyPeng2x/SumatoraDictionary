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
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface DictionaryActionDao {
    @Query("SELECT * FROM DictionaryAction WHERE DictionaryAction.`action`=" + DictionaryAction.DICTIONARY_ACTION_DELETE)
    LiveData<List<DictionaryAction>> getDeleteActions();

    @Query("SELECT * FROM DictionaryAction WHERE DictionaryAction.`action`=" + DictionaryAction.DICTIONARY_ACTION_UPDATE)
    LiveData<List<DictionaryAction>> getUpdateActions();

    @Query("SELECT * FROM DictionaryAction WHERE DictionaryAction.`action`=" + DictionaryAction.DICTIONARY_ACTION_DOWNLOAD)
    LiveData<List<DictionaryAction>> getDownloadActions();

    @Query("SELECT * FROM DictionaryAction WHERE DictionaryAction.`action`=" + DictionaryAction.DICTIONARY_ACTION_VERSION)
    LiveData<List<DictionaryAction>> getVersionActions();

    @Query("SELECT * FROM DictionaryAction WHERE DictionaryAction.`action`=" + DictionaryAction.DICTIONARY_ACTION_UNINITIALIZED)
    LiveData<List<DictionaryAction>> getUninitializedActions();

    @Query("SELECT * FROM DictionaryAction WHERE DictionaryAction.`action`=" + DictionaryAction.DICTIONARY_ACTION_MISMATCH)
    LiveData<List<DictionaryAction>> getMismatchActions();

    @Query("SELECT * FROM DictionaryAction")
    List<DictionaryAction> getAll();

    @Query("SELECT * FROM DictionaryAction WHERE DictionaryAction.downloadId=:id")
    List<DictionaryAction> getAllForDownloadId(long id);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertMany(List<DictionaryAction> aActions);

    @Update
    void updateMany(List<DictionaryAction> aActions);

    @Update
    void update(DictionaryAction aAction);

    @Delete
    void deleteMany(List<DictionaryAction> aActions);

    @Query("SELECT COUNT(*) FROM DictionaryAction WHERE DictionaryAction.downloadId != 0")
    LiveData<Integer> getDownloadCountLive();
}
