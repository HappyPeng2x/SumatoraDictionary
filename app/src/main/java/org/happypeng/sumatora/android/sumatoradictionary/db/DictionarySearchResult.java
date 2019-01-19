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

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;

public class DictionarySearchResult {
    public int entryOrder;
    public long seq;
    public String readingsPrio;
    public String readings;
    public String writingsPrio;
    public String writings;
    public String lang;
    public String gloss;
    public Integer bookmarkFolder;

    public static DiffUtil.ItemCallback<DictionarySearchResult> DIFF_CALLBACK = new  DiffUtil.ItemCallback<DictionarySearchResult>() {
        @Override
        public boolean areItemsTheSame(@NonNull DictionarySearchResult oldItem, @NonNull DictionarySearchResult newItem) {
            return oldItem.seq == newItem.seq && oldItem.lang.equals(newItem.lang);
        }

        @Override
        public boolean areContentsTheSame(@NonNull DictionarySearchResult oldItem, @NonNull DictionarySearchResult newItem) {
            return oldItem.seq == newItem.seq && oldItem.lang.equals(newItem.lang) &&
                    ((oldItem.bookmarkFolder != null && oldItem.bookmarkFolder.equals(newItem.bookmarkFolder)) ||
                            (newItem.bookmarkFolder != null && newItem.bookmarkFolder.equals(oldItem.bookmarkFolder)));
        }
    };

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        if (!(obj instanceof DictionarySearchResult)) {
            return false;
        }

        DictionarySearchResult dictionarySearchResult = (DictionarySearchResult) obj;

        return seq == dictionarySearchResult.seq && lang.equals(dictionarySearchResult.lang) &&
                ((bookmarkFolder != null && bookmarkFolder.equals(dictionarySearchResult.bookmarkFolder)) ||
                        (dictionarySearchResult.bookmarkFolder != null && dictionarySearchResult.bookmarkFolder.equals(bookmarkFolder)));
    }
}
