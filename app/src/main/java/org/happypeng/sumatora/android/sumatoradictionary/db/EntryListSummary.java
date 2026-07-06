/* Sumatora Dictionary
        Copyright (C) 2026 Nicolas Centa

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

import java.util.ArrayList;
import java.util.List;

// Lean per-entry summary for a search-result list row, assembled from the entry_id carried on
// DictionarySearchElement (schema v2's "Display Assembly Query" pattern) - not a full detail
// assembly (that's EntryDetailBottomSheet's job), just what one card needs to render.
public class EntryListSummary {
    public static class FuriganaSegment {
        public final String base;
        @Nullable public final String ruby;

        public FuriganaSegment(String aBase, @Nullable String aRuby) {
            base = aBase;
            ruby = aRuby;
        }
    }

    public boolean isName;

    @Nullable public String primaryText;
    @Nullable public String primaryReading;
    public List<FuriganaSegment> furiganaSegments = new ArrayList<>();

    // Word entries: pos/misc/field/dialect tag codes for the entry's first sense group.
    public List<String> tagCodes = new ArrayList<>();
    @Nullable public String glossPreview;
    public boolean usedBackupLang;

    // Name entries: name_type tag codes, plus the flat NameTranslation list.
    public List<String> nameTypeCodes = new ArrayList<>();
    public List<String> translations = new ArrayList<>();
}
