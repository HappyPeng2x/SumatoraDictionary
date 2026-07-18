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

import org.happypeng.sumatora.core.dict.DictionaryQueryResult;

// Per-search-session cache of query hits: entry_id/form_id + match metadata (schema v2's
// "Query Result Shape"), not a fully assembled display row - the display layer assembles
// Entry/EntryForm/Sense/... separately by entry_id/form_id (see EntryDetailBottomSheet).
@Entity(primaryKeys = {"ref", "entry_id"})
public class DictionarySearchElement implements DictionaryQueryResult {
    public int ref;
    public int entryOrder;
    public long entry_id;
    public long seq;
    @Nullable public Long form_id;
    @Nullable public String match_kind;
    @Nullable public String matched_text;
    @Nullable public String original_query;
    @Nullable public String dictionary_form;
    @Nullable public String deinflection_label;
    public int rank;
    public long bookmark;
    @Nullable public String memo;
    @Nullable public String tags;

    public DictionarySearchElement() { }

    public int getEntryOrder() {
        return entryOrder;
    }

    public long getEntryId() { return entry_id; }
    public long getSeq() { return seq; }
    public Long getFormId() { return form_id; }
    public String getMatchKind() { return match_kind; }
    public String getMatchedText() { return matched_text; }
    public String getOriginalQuery() { return original_query; }
    public String getDictionaryForm() { return dictionary_form; }
    public String getDeinflectionLabel() { return deinflection_label; }
    public int getRank() { return rank; }

    public long getBookmark() { return bookmark; }
    public String getMemo() { return memo; }
    public String getTags() { return tags; }
}
