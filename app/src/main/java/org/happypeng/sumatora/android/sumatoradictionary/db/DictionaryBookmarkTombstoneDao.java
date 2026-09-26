/* Sumatora Dictionary
        Copyright (C) 2026 Nicolas Centa

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
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

@Dao
public interface DictionaryBookmarkTombstoneDao {
    @Query("SELECT * FROM DictionaryBookmarkTombstone")
    List<DictionaryBookmarkTombstone> getAll();

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(DictionaryBookmarkTombstone aTombstone);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertMany(List<DictionaryBookmarkTombstone> aTombstones);

    @Query("DELETE FROM DictionaryBookmarkTombstone WHERE seq = :seq")
    void delete(long seq);

    @Query("DELETE FROM DictionaryBookmarkTombstone")
    void deleteAll();

    // Sync's local pruning step: a tombstone older than the grace period is dropped once a sync
    // round-trip has confirmed the seq is dead on the remote copy too (see NextcloudSyncComponent)
    // - it then simply stops being re-uploaded on the next sync.
    @Query("DELETE FROM DictionaryBookmarkTombstone WHERE deletedAt < :cutoff")
    void deleteOlderThan(long cutoff);
}
