package org.happypeng.sumatora.android.sumatoradictionary.db;

import androidx.paging.DataSource;
import androidx.room.Dao;
import androidx.room.Query;

@Dao
public interface DictionarySearchElementDao {
    @Query("SELECT * FROM DictionarySearchElement "
            + "WHERE ref=:ref "
            + "ORDER BY entryOrder, seq")
    DataSource.Factory<Integer, DictionarySearchElement> getAllDetailsLivePaged(int ref);

    @Query("DELETE FROM DictionarySearchElement")
    void deleteAll();
}
