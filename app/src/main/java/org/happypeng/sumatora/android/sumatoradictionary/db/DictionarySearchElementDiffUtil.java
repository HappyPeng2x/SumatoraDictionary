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

public class DictionarySearchElementDiffUtil {
    private final static DiffUtil.ItemCallback<DictionarySearchElement> DIFF_UTIL =
            new DiffUtil.ItemCallback<DictionarySearchElement>() {
                @Override
                public boolean areItemsTheSame(@NonNull DictionarySearchElement oldItem, @NonNull DictionarySearchElement newItem) {
                    return oldItem.getSeq() == newItem.getSeq();
                }

                @Override
                public boolean areContentsTheSame(@NonNull DictionarySearchElement oldItem, @NonNull DictionarySearchElement newItem) {
                    return oldItem.getSeq() == newItem.getSeq() && oldItem.getLang().equals(newItem.getLang()) &&
                            oldItem.getBookmark() == newItem.getBookmark();
                }
            };

    public static DiffUtil.ItemCallback<DictionarySearchElement> getDiffUtil() {
        return DIFF_UTIL;
    }
}
