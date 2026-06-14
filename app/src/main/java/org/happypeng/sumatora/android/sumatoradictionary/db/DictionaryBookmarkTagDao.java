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

import java.util.List;

import io.reactivex.rxjava3.core.Observable;

@Dao
public interface DictionaryBookmarkTagDao {
    @Query("SELECT DISTINCT tag FROM DictionaryBookmarkTag ORDER BY tag")
    List<String> getAllTags();

    @Query("SELECT DISTINCT tag FROM DictionaryBookmarkTag ORDER BY tag")
    LiveData<List<String>> getAllTagsLive();

    @Query("SELECT DISTINCT tag FROM DictionaryBookmarkTag ORDER BY tag")
    Observable<List<String>> getAllTagsObservable();

    @Query("SELECT tag FROM DictionaryBookmarkTag WHERE seq = :seq ORDER BY tag")
    List<String> getTagsForSeq(long seq);

    @Query("SELECT seq FROM DictionaryBookmarkTag WHERE tag = :tag")
    List<Long> getSeqsForTag(String tag);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insert(DictionaryBookmarkTag aTag);

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    void insertMany(List<DictionaryBookmarkTag> aTags);

    @Delete
    void delete(DictionaryBookmarkTag aTag);

    @Query("DELETE FROM DictionaryBookmarkTag WHERE seq = :seq AND tag = :tag")
    void delete(long seq, String tag);

    @Query("DELETE FROM DictionaryBookmarkTag WHERE seq = :seq")
    void deleteTagsForSeq(long seq);
}
