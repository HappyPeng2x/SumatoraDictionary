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

import androidx.room.Entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
@Entity(primaryKeys = {"ref", "seq"})
public class DictionaryBookmarkImport {
    public int ref;
    public long seq;
    public long bookmark;
    public String memo;

    public DictionaryBookmarkImport() { super(); }

    public DictionaryBookmarkImport(int aRef, long aSeq, long aBookmark, String aMemo) {
        super();

        ref = aRef;
        seq = aSeq;
        memo = aMemo;
        bookmark = aBookmark;
    }
}
