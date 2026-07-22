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
    public boolean isPriority;
    public List<EntryListSummary.FuriganaSegment> furiganaSegments = new ArrayList<>();
    // The reading actually matched/promoted for primaryText (bold in the headword line), plus
    // any other readings the same kanji spelling can take (smaller) - furigana alone only shows
    // the matched reading, and the forms table further below requires scrolling to discover the
    // rest, so both are surfaced right on the headword line too, same as the list row.
    @Nullable public String primaryReading;
    public List<String> alternateReadings = new ArrayList<>();

    // word entries
    public List<Integer> pitchPatterns = new ArrayList<>();
    public List<SenseGroup> senseGroups = new ArrayList<>();
    // Fallback bucket: examples with no sense_id, or whose sense_id didn't survive the
    // matched-form filter - rendered in their own section rather than guessed onto a sense.
    public List<Example> examples = new ArrayList<>();
    // Every kanji+reading combination for the entry (schema-v2's EntryForm), for the "forms"
    // table - only populated when the entry has more than one distinct written form.
    public List<FormRow> forms = new ArrayList<>();

    // name entries
    public List<String> nameTypeCodes = new ArrayList<>();
    public List<String> translations = new ArrayList<>();

    public static class SenseGroup {
        public List<String> posTagCodes = new ArrayList<>();
        public List<String> miscTagCodes = new ArrayList<>();
        public List<String> fieldTagCodes = new ArrayList<>();
        public List<String> dialectTagCodes = new ArrayList<>();
        public List<Sense> senses = new ArrayList<>();
        // Sorted SenseAppliesToForm.form_id set shared by every sense in this (possibly merged)
        // group - empty when unrestricted. Part of the merge key so a restricted group never
        // silently merges with an adjacent unrestricted one.
        public List<Long> restrictedFormIds = new ArrayList<>();
        // Human-readable readings the restriction limits this group to (e.g. "ばね・バネ"),
        // null when unrestricted.
        @Nullable public String restrictionLabel;
    }

    public static class Sense {
        public long senseId;
        public int displayIndex;
        @Nullable public String glossText;
        // True when glossText came from the backup language because the main language had no
        // gloss for this specific sense - see PersistentDatabaseComponent.fetchEntryDetail.
        public boolean usedBackupLang;
        public List<String> notes = new ArrayList<>();
        public List<Xref> xrefs = new ArrayList<>();
        public List<Xref> antonyms = new ArrayList<>();
        public List<LanguageSource> languageSources = new ArrayList<>();
        public List<Example> examples = new ArrayList<>();
    }

    public static class FormRow {
        public final String text;
        @Nullable public final String reading;
        public final boolean isKanjiless;
        public final String tier;

        public FormRow(String aText, @Nullable String aReading, boolean aIsKanjiless, String aTier) {
            text = aText;
            reading = aReading;
            isKanjiless = aIsKanjiless;
            tier = aTier;
        }
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
        // Set from EntryExample.sense_id at fetch time; only used transiently to route this
        // example into its owning Sense.examples vs. the entry-level fallback list.
        @Nullable public Long senseId;
    }
}
