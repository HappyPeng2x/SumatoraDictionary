/* Sumatora Dictionary
        Copyright (C) 2020 Nicolas Centa

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
import androidx.room.Ignore;

@Entity(primaryKeys = {"seq", "tagId"})
public class DictionaryTag {
    public int tagId;
    public long seq;

    @Ignore
    public DictionaryTag(final long seq, final int tagId) {
        this.seq = seq;
        this.tagId = tagId;
    }

    public DictionaryTag() {
        this.seq = 0;
        this.tagId = 0;
    }
}
