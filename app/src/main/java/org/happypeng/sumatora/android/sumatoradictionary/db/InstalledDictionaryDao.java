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

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import java.util.List;

import io.reactivex.rxjava3.core.Flowable;

@Dao
public interface InstalledDictionaryDao {
    @Query("SELECT * FROM InstalledDictionary")
    List<InstalledDictionary> getAll();

    @Query("SELECT * FROM InstalledDictionary")
    LiveData<List<InstalledDictionary>> getAllLive();

    // Backs the search screen's language picker (BaseQueryFragmentModel.installedDictionaries) -
    // Room re-emits automatically whenever InstalledDictionary changes (e.g. a pack downloaded via
    // DictionariesManagementActivity, a separate Activity), so a newly installed language shows up
    // in the picker without needing the fragment recreated or an explicit invalidateOptionsMenu().
    @Query("SELECT * FROM InstalledDictionary")
    Flowable<List<InstalledDictionary>> getAllFlowable();

    @Delete
    void delete(InstalledDictionary aAction);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(InstalledDictionary aSetting);

    @Query("SELECT * FROM InstalledDictionary WHERE InstalledDictionary.type == :aType AND InstalledDictionary.lang == :aLang LIMIT 1")
    InstalledDictionary getForTypeLang(String aType, String aLang);

    @Query("SELECT * FROM InstalledDictionary WHERE pendingFile IS NOT NULL")
    List<InstalledDictionary> getPendingUpdates();
}
