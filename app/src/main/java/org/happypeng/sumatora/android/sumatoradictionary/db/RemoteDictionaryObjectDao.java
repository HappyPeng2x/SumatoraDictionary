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
        along with this program.  If not, see <http://www.gnu.org/licenses/>. */

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
public interface RemoteDictionaryObjectDao {
    @Query("SELECT COUNT(downloadId) FROM RemoteDictionaryObject WHERE RemoteDictionaryObject.downloadId > -1")
    LiveData<Integer> getDownloadCountLive();

    @Query("SELECT COUNT(downloadId) FROM RemoteDictionaryObject WHERE RemoteDictionaryObject.downloadId > -1")
    int getDownloadCount();


    @Query("SELECT * FROM RemoteDictionaryObject WHERE RemoteDictionaryObject.downloadId=:id")
    List<RemoteDictionaryObject> getAllForDownloadId(long id);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertMany(List<RemoteDictionaryObject> aActions);

    @Query("SELECT * FROM RemoteDictionaryObject")
    List<RemoteDictionaryObject> getAll();

    @Query("SELECT * FROM RemoteDictionaryObject")
    LiveData<List<RemoteDictionaryObject>> getAllLive();

    @Query("SELECT * FROM RemoteDictionaryObject WHERE " +
            "RemoteDictionaryObject.type == 'jmdict_translation' AND " +
            "RemoteDictionaryObject.lang NOT IN " +
            "(SELECT InstalledDictionary.lang FROM InstalledDictionary) ORDER BY RemoteDictionaryObject.lang")
    LiveData<List<RemoteDictionaryObject>> getInstallableLive();

    @Delete
    void deleteMany(List<RemoteDictionaryObject> aActions);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void update(RemoteDictionaryObject aAction);

    // No installs except if explicitely required by the user
}
