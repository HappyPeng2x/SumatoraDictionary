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

import androidx.annotation.Nullable;
import androidx.room.Entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import org.happypeng.sumatora.core.bookmark.Bookmark;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Entity(primaryKeys = {"seq"})
public class DictionaryBookmark {
    @JsonProperty("seq")
    public long seq;

    @JsonProperty("bookmark")
    public long bookmark;

    @JsonProperty("memo")
    @Nullable public String memo;

    @JsonProperty("tags")
    @Nullable public String tags;

    public DictionaryBookmark() {}

    public DictionaryBookmark(long aSeq, long aBookmark, String aMemo) {
        seq = aSeq;
        bookmark = aBookmark;
        memo = aMemo;
    }

    public DictionaryBookmark(long aSeq, long aBookmark, String aMemo, String aTags) {
        seq = aSeq;
        bookmark = aBookmark;
        memo = aMemo;
        tags = aTags;
    }

    public Bookmark toBookmark() {
        return new Bookmark(seq, bookmark, memo, tags);
    }

    public static DictionaryBookmark fromBookmark(Bookmark b) {
        return new DictionaryBookmark(b.seq, b.bookmark, b.memo, b.tags);
    }
}
