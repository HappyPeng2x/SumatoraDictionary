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
    public List<FuriganaSegment> furiganaSegments = new ArrayList<>();
    // Other kanji spellings sharing primaryText's reading (e.g. 恃む/憑む next to 頼む) - shown
    // smaller/greyed alongside the headword so an alternate spelling isn't hidden until the user
    // taps into the forms table.
    public List<String> alternateTexts = new ArrayList<>();

    // Word entries: every sense group (pos/misc/field/dialect tags shared by the senses in it),
    // each with all of its senses - a gloss/reverse-search hit on any sense stays visible here,
    // not just a first-sense preview.
    public List<SenseGroupSummary> senseGroups = new ArrayList<>();
    public boolean usedBackupLang;

    // Name entries: name_type tag codes, plus the flat NameTranslation list.
    public List<String> nameTypeCodes = new ArrayList<>();
    public List<String> translations = new ArrayList<>();

    public static class SenseGroupSummary {
        public List<String> tagCodes = new ArrayList<>();
        public List<SenseSummary> senses = new ArrayList<>();
    }

    public static class SenseSummary {
        public int displayIndex;
        public String glossText;
    }
}
