package org.happypeng.sumatora.android.sumatoradictionary.db;

import androidx.paging.DataSource;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

@Dao
public interface DictionaryEntryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public void insert(DictionaryEntry... entries);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    public void insertMany(DictionaryEntry[] entries);

    @Query("DELETE FROM DictionaryEntry")
    public void deleteAll();

    @Query("SELECT * FROM DictionaryEntry WHERE (readings LIKE '%' || :expr || '%' OR writings LIKE '%' || :expr || '%') AND lang=:lang")
    public DataSource.Factory<Integer, DictionaryEntry> search(String expr, String lang);
}
