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

package org.happypeng.sumatora.android.sumatoradictionary.db

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DictionaryChangelogDao {
    @Query("SELECT * FROM DictionaryChangelog ORDER BY version DESC")
    fun getAllOrderByVersionDesc(): LiveData<List<DictionaryChangelog>>

    // Synchronous - called from DictionaryUpdateChecker.checkAndEnqueue, itself @WorkerThread and
    // already making other blocking DAO calls in the same loop (see installedDictionaryDao()).
    @Query("SELECT EXISTS(SELECT 1 FROM DictionaryChangelog WHERE version = :version)")
    fun hasVersion(version: Int): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(entry: DictionaryChangelog)
}
