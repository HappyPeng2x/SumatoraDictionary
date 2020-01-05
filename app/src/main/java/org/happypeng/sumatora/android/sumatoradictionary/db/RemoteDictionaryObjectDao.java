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
            " (SELECT InstalledDictionary.lang FROM InstalledDictionary) AND " +
            "RemoteDictionaryObject.version IN " +
            " (SELECT InstalledDictionary.version FROM InstalledDictionary WHERE InstalledDictionary.type == 'jmdict') AND " +
            "RemoteDictionaryObject.date IN " +
            " (SELECT InstalledDictionary.date FROM InstalledDictionary WHERE InstalledDictionary.type == 'jmdict') AND " +
            "(RemoteDictionaryObject.downloadId IS NULL OR RemoteDictionaryObject.downloadId == -1) " +
            "ORDER BY RemoteDictionaryObject.lang")
    LiveData<List<RemoteDictionaryObject>> getInstallableLive();

    @Query("SELECT * FROM RemoteDictionaryObject WHERE RemoteDictionaryObject.downloadId > 0")
    LiveData<List<RemoteDictionaryObject>> getActiveDownloads();

    @Query("SELECT downloadId FROM remotedictionaryobject WHERE type = :type AND lang = :lang")
    int getDownloadId(String type, String lang);

    @Query("SELECT * FROM RemoteDictionaryObject WHERE " +
            "(RemoteDictionaryObject.version NOT IN " +
            " (SELECT InstalledDictionary.version FROM InstalledDictionary WHERE InstalledDictionary.type == 'jmdict')) OR " +
            "(RemoteDictionaryObject.date NOT IN " +
            " (SELECT InstalledDictionary.date FROM InstalledDictionary WHERE InstalledDictionary.type == 'jmdict')) " +
            "ORDER BY RemoteDictionaryObject.type, RemoteDictionaryObject.lang")
    LiveData<List<RemoteDictionaryObject>> getUpdatableLive();

    @Query("SELECT COUNT(description) FROM RemoteDictionaryObject WHERE " +
            "(RemoteDictionaryObject.version NOT IN " +
            " (SELECT InstalledDictionary.version FROM InstalledDictionary WHERE InstalledDictionary.type == 'jmdict')) OR " +
            "(RemoteDictionaryObject.date NOT IN " +
            " (SELECT InstalledDictionary.date FROM InstalledDictionary WHERE InstalledDictionary.type == 'jmdict'))")
    int getUpdatableCount();

    @Query("SELECT * FROM RemoteDictionaryObject WHERE " +
            "(RemoteDictionaryObject.version NOT IN " +
            " (SELECT InstalledDictionary.version FROM InstalledDictionary WHERE InstalledDictionary.type == 'jmdict')) OR " +
            "(RemoteDictionaryObject.date NOT IN " +
            " (SELECT InstalledDictionary.date FROM InstalledDictionary WHERE InstalledDictionary.type == 'jmdict')) " +
            "AND (RemoteDictionaryObject.localFile == '' OR RemoteDictionaryObject.downloadId > -1) " +
            "ORDER BY RemoteDictionaryObject.type, RemoteDictionaryObject.lang")
    List<RemoteDictionaryObject> getUpdatableRemaining();

    @Delete
    void deleteMany(List<RemoteDictionaryObject> aActions);

    @Delete
    void delete(RemoteDictionaryObject aObject);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(RemoteDictionaryObject aAction);

    // No installs except if explicitely required by the user
}
