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

import androidx.recyclerview.widget.DiffUtil;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

@Entity(primaryKeys = {"seq", "lang"})
public class DictionaryEntry {
    public long seq;
    public String readings;
    public String writings;
    @NonNull public String lang;
    public String gloss;
    public String bookmark;

    public DictionaryEntry() {
    }

    public static DiffUtil.ItemCallback<DictionaryEntry> DIFF_CALLBACK = new  DiffUtil.ItemCallback<DictionaryEntry>() {
        @Override
        public boolean areItemsTheSame(@NonNull DictionaryEntry oldItem, @NonNull DictionaryEntry newItem) {
            return oldItem.seq == newItem.seq && oldItem.lang.equals(newItem.lang);
        }

        @Override
        public boolean areContentsTheSame(@NonNull DictionaryEntry oldItem, @NonNull DictionaryEntry newItem) {
            return oldItem.readings.equals(newItem.readings) &&
                    oldItem.writings.equals(newItem.writings) &&
                    oldItem.lang.equals(newItem.lang) &&
                    oldItem.gloss.equals(newItem.gloss) &&
                    oldItem.bookmark.equals(newItem.bookmark);
        }
    };

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        DictionaryEntry dictionaryEntry = (DictionaryEntry) obj;

        return readings.equals(dictionaryEntry.readings) &&
                writings.equals(dictionaryEntry.writings) &&
                lang.equals(dictionaryEntry.lang) &&
                gloss.equals(dictionaryEntry.gloss) &&
                bookmark.equals(dictionaryEntry.bookmark);
    }
}
