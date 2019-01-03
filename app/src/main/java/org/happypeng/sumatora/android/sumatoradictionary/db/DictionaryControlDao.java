package org.happypeng.sumatora.android.sumatoradictionary.db;

import androidx.lifecycle.LiveData;
import androidx.paging.DataSource;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface DictionaryControlDao {
    @Query("REPLACE INTO DictionaryControl VALUES (:control, :value)")
    public abstract void set(String control, long value);

    @Query("SELECT value FROM DictionaryControl WHERE control = :control")
    public abstract LiveData<Long> get(String control);

    @Query("DELETE FROM DictionaryControl WHERE control = :control")
    public void delete(String control);
}
