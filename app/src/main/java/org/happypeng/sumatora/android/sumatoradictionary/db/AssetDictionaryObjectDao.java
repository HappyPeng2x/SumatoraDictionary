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
public interface AssetDictionaryObjectDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertMany(List<AssetDictionaryObject> aActions);

    @Query("SELECT * FROM AssetDictionaryObject")
    List<AssetDictionaryObject> getAll();

    @Query("SELECT * FROM AssetDictionaryObject")
    LiveData<List<AssetDictionaryObject>> getAllLive();

    @Delete
    void deleteMany(List<AssetDictionaryObject> aActions);

    @Update
    void update(AssetDictionaryObject aAction);

    // Only updates, and jmdict and english translation if not installed are to be installed
    @Query("SELECT AssetDictionaryObject.description, " +
            "AssetDictionaryObject.type, AssetDictionaryObject.lang, " +
            "AssetDictionaryObject.version, AssetDictionaryObject.date, " +
            "AssetDictionaryObject.file " +
            "FROM AssetDictionaryObject INNER JOIN InstalledDictionary " +
            "ON AssetDictionaryObject.type == InstalledDictionary.type " +
            "AND AssetDictionaryObject.lang == InstalledDictionary.lang " +
//            "AND AssetDictionaryObject.date > InstalledDictionary.date " +
            "AND AssetDictionaryObject.version > InstalledDictionary.version")
    List<AssetDictionaryObject> getInstallObjects();

    @Query("SELECT * FROM AssetDictionaryObject WHERE AssetDictionaryObject.type == :aType AND AssetDictionaryObject.lang == :aLang LIMIT 1")
    AssetDictionaryObject getForTypeLang(String aType, String aLang);
}
