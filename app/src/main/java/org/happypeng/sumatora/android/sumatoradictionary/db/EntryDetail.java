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

// Full per-entry assembly for the detail bottom sheet (schema v2's "Display Assembly Query"):
// headline form, senses grouped and filtered by the matched form_id, cross-references (already
// resolved at pipeline build time), examples, and pitch pattern for the matched/primary form.
public class EntryDetail {
    public boolean isName;

    @Nullable public String primaryText;
    @Nullable public String primaryReading;
    public boolean isPriority;
    public List<EntryListSummary.FuriganaSegment> furiganaSegments = new ArrayList<>();

    // word entries
    public List<Integer> pitchPatterns = new ArrayList<>();
    public List<SenseGroup> senseGroups = new ArrayList<>();
    public List<Example> examples = new ArrayList<>();

    // name entries
    public List<String> nameTypeCodes = new ArrayList<>();
    public List<String> translations = new ArrayList<>();

    public static class SenseGroup {
        public List<String> posTagCodes = new ArrayList<>();
        public List<String> miscTagCodes = new ArrayList<>();
        public List<String> fieldTagCodes = new ArrayList<>();
        public List<String> dialectTagCodes = new ArrayList<>();
        public List<Sense> senses = new ArrayList<>();
    }

    public static class Sense {
        public int displayIndex;
        @Nullable public String glossText;
        public List<String> notes = new ArrayList<>();
        public List<Xref> xrefs = new ArrayList<>();
        public List<Xref> antonyms = new ArrayList<>();
        public List<LanguageSource> languageSources = new ArrayList<>();
    }

    public static class Xref {
        public final String displayText;
        @Nullable public final Long targetEntryId;
        @Nullable public final Integer targetSenseNumber;
        @Nullable public final String previewText;

        public Xref(String aDisplayText, @Nullable Long aTargetEntryId,
                    @Nullable Integer aTargetSenseNumber, @Nullable String aPreviewText) {
            displayText = aDisplayText;
            targetEntryId = aTargetEntryId;
            targetSenseNumber = aTargetSenseNumber;
            previewText = aPreviewText;
        }
    }

    public static class LanguageSource {
        public final String lang;
        @Nullable public final String text;
        public final boolean wasei;

        public LanguageSource(String aLang, @Nullable String aText, boolean aWasei) {
            lang = aLang;
            text = aText;
            wasei = aWasei;
        }
    }

    public static class Example {
        public List<EntryListSummary.FuriganaSegment> segments = new ArrayList<>();
        @Nullable public String translation;
        @Nullable public String matchedText;
    }
}
