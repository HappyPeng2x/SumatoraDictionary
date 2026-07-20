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

package org.happypeng.sumatora.android.sumatoradictionary.diagnostic;

import android.database.Cursor;
import android.util.Log;

import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.test.internal.runner.junit4.AndroidJUnit4ClassRunner;

import org.happypeng.sumatora.android.sumatoradictionary.component.PersistentDatabaseComponent;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryKanjiInfo;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchElement;
import org.happypeng.sumatora.android.sumatoradictionary.db.EntryDetail;
import org.happypeng.sumatora.android.sumatoradictionary.db.EntryListSummary;
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentLanguageSettings;
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.DictionarySearchQueryTool;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.testing.HiltAndroidRule;
import dagger.hilt.android.testing.HiltAndroidTest;

// Not a pass/fail correctness test - a diagnostic harness that runs the schema-v2 query layer
// end to end for a handful of representative terms and logs the assembled results, so the
// migration can be eyeballed against real installed dictionary data. Read the output with:
//   adb logcat -s SchemaV2Diag
@HiltAndroidTest
@RunWith(AndroidJUnit4ClassRunner.class)
public class SchemaV2QueryDiagnosticTest {
    private static final String TAG = "SchemaV2Diag";

    @org.junit.Rule
    public HiltAndroidRule hiltRule = new HiltAndroidRule(this);

    @Inject
    PersistentDatabaseComponent dbComponent;

    private PersistentLanguageSettings languageSettings;
    private int refCounter = 9000;

    @Before
    public void setUp() {
        hiltRule.inject();
        Assume.assumeFalse(
                "skipped: no dictionary installed on this device",
                dbComponent.getDatabase().installedDictionaryDao().getAll().isEmpty());

        PersistentLanguageSettings settings = dbComponent.getDatabase()
                .persistentLanguageSettingsDao().getLanguageSettingsDirect(0);
        languageSettings = settings != null ? settings : defaultSettings();
        Log.i(TAG, "language=" + languageSettings.lang + " backup=" + languageSettings.backupLang);
    }

    private static PersistentLanguageSettings defaultSettings() {
        PersistentLanguageSettings s = new PersistentLanguageSettings();
        s.lang = PersistentLanguageSettings.LANG_DEFAULT;
        s.backupLang = null;
        return s;
    }

    @Test
    public void dumpSearchResults() {
        Log.i(TAG, "===== forward search: 食べる (exact writing, common verb) =====");
        runSearch("食べる");

        Log.i(TAG, "===== forward search: たべる (exact kana reading, same entry) =====");
        runSearch("たべる");

        Log.i(TAG, "===== deinflection: 食べた (past tense of 食べる) =====");
        runSearch("食べた");

        Log.i(TAG, "===== prefix search: 食べ (should hit 食べる and other 食べ* entries) =====");
        runSearch("食べ");

        Log.i(TAG, "===== reverse gloss search: eat =====");
        runSearch("eat");

        Log.i(TAG, "===== proper noun search: 田中 (common surname) =====");
        runSearch("田中");

        Log.i(TAG, "===== kana-only word: ありがとう (no writing form, common expression) =====");
        runSearch("ありがとう");

        Log.i(TAG, "===== katakana loanword: パソコン (personal computer) =====");
        runSearch("パソコン");

        Log.i(TAG, "===== i-adjective: 大きい (big) =====");
        runSearch("大きい");

        Log.i(TAG, "===== deinflection (polite negative): 食べません =====");
        runSearch("食べません");

        Log.i(TAG, "===== kanji detail: 食 =====");
        DictionaryKanjiInfo kanji = dbComponent.fetchKanjiInfo("食");
        if (kanji == null) {
            Log.i(TAG, "  (no kanji pack installed or character not found)");
        } else {
            Log.i(TAG, "  strokes=" + kanji.strokes + " grade=" + kanji.grade + " jlpt=" + kanji.jlpt
                    + " freq=" + kanji.freq + " radical=" + kanji.radical);
            Log.i(TAG, "  on=" + kanji.onReadings + " kun=" + kanji.kunReadings + " meanings=" + kanji.meanings);
        }
    }

    @Test
    public void dumpEntryDetail() {
        Log.i(TAG, "===== entry detail deep-dive: 掛ける (many senses/xrefs) =====");
        List<DictionarySearchElement> hits = runSearch("掛ける");
        if (hits.isEmpty()) {
            Log.i(TAG, "  no hits for 掛ける - skipping detail dump");
            return;
        }
        DictionarySearchElement top = hits.get(0);
        EntryDetail detail = dbComponent.fetchEntryDetail(top.getEntryId(), top.getFormId(), false, languageSettings);
        Log.i(TAG, "headword=" + detail.primaryText + " reading=" + detail.primaryReading
                + " isPriority=" + detail.isPriority + " pitch=" + detail.pitchPatterns);
        int senseNo = 0;
        for (EntryDetail.SenseGroup group : detail.senseGroups) {
            Log.i(TAG, "  group pos=" + group.posTagCodes + " misc=" + group.miscTagCodes
                    + " field=" + group.fieldTagCodes + " dial=" + group.dialectTagCodes);
            for (EntryDetail.Sense sense : group.senses) {
                senseNo++;
                Log.i(TAG, "    [" + sense.displayIndex + "] " + sense.glossText);
                for (String note : sense.notes) Log.i(TAG, "        note: " + note);
                for (EntryDetail.Xref xr : sense.xrefs) {
                    Log.i(TAG, "        xref: " + xr.displayText + " -> entry_id=" + xr.targetEntryId
                            + " sense#=" + xr.targetSenseNumber + " preview=" + xr.previewText);
                }
                for (EntryDetail.Xref an : sense.antonyms) {
                    Log.i(TAG, "        antonym: " + an.displayText + " -> entry_id=" + an.targetEntryId);
                }
                for (EntryDetail.LanguageSource ls : sense.languageSources) {
                    Log.i(TAG, "        lsource: " + ls.lang + "/" + ls.text + " wasei=" + ls.wasei);
                }
            }
        }
        Log.i(TAG, "  examples: " + detail.examples.size());
        for (EntryDetail.Example ex : detail.examples) {
            StringBuilder jp = new StringBuilder();
            for (EntryListSummary.FuriganaSegment seg : ex.segments) jp.append(seg.base);
            Log.i(TAG, "    jp=" + jp + " | en=" + ex.translation + " | matched=" + ex.matchedText);
        }
        if (senseNo == 0) {
            Log.i(TAG, "  (no senses assembled - check SenseGroup/Sense/gloss pack wiring)");
        }
    }

    // Runs every tier for [term] against a fresh DictionarySearchQueryTool (plus the proper-noun
    // and deinflection passes), then dumps the resulting DictionarySearchElement rows' precomputed
    // render_json. Returns the raw hits for further inspection by other tests.
    private List<DictionarySearchElement> runSearch(String term) {
        int ref = refCounter++;
        DictionarySearchQueryTool tool = new DictionarySearchQueryTool(dbComponent, ref, languageSettings);
        try {
            int max = tool.getCount(term);
            for (int i = 0; i < max; i++) {
                tool.execute(term, i, false, false);
            }
            tool.executeProperNouns(term);
            tool.executeDeinflection(term);
            // render_json is left NULL by the tier inserts now (see backfillRenderJson) - render
            // everything matched for this diagnostic ref so the logged output below still shows
            // headword/senses instead of blanks. A large bound is fine here: this is a manual
            // diagnostic run against a handful of terms, not the paged-scroll path the bound exists
            // to protect.
            tool.backfillRenderJson(10000);

            List<DictionarySearchElement> results = fetchRawResults(ref);
            if (results.isEmpty()) {
                Log.i(TAG, "  (no hits)");
            }
            int shown = 0;
            for (DictionarySearchElement r : results) {
                if (shown >= 8) {
                    Log.i(TAG, "  ... (" + (results.size() - shown) + " more)");
                    break;
                }
                EntryListSummary summary = r.render_json != null
                        ? PersistentDatabaseComponent.parsePrecomputedSummary(r.render_json)
                        : null;
                if (summary == null) {
                    summary = new EntryListSummary();
                }
                List<String> tagCodes = new ArrayList<>();
                String glossPreview = "";
                if (!summary.senseGroups.isEmpty()) {
                    EntryListSummary.SenseGroupSummary firstGroup = summary.senseGroups.get(0);
                    tagCodes = firstGroup.tagCodes;
                    if (!firstGroup.senses.isEmpty()) {
                        glossPreview = firstGroup.senses.get(0).glossText;
                    }
                }
                Log.i(TAG, "  entry_id=" + r.getEntryId() + " form_id=" + r.getFormId()
                        + " seq=" + r.getSeq() + " match=" + r.getMatchKind() + " rank=" + r.getRank()
                        + " dictForm=" + r.getDictionaryForm() + " deinflect=" + r.getDeinflectionLabel()
                        + " || headword=" + summary.primaryText + " reading=" + summary.primaryReading
                        + " tags=" + tagCodes + " gloss=" + glossPreview
                        + " nameTypes=" + summary.nameTypeCodes + " translations=" + summary.translations
                        + " backupLang=" + summary.usedBackupLang);
                shown++;
            }
            return results;
        } finally {
            tool.delete();
            tool.close();
        }
    }

    // Returns real DictionarySearchElement rows (not just the DictionaryQueryResult metadata
    // shape) since render_json - the precomputed display payload every search tier now writes at
    // insert time (see DictionarySearchQueryTool.buildRenderJsonExpr) - lives only on that entity.
    private List<DictionarySearchElement> fetchRawResults(int ref) {
        List<DictionarySearchElement> results = new ArrayList<>();
        SupportSQLiteDatabase readable = dbComponent.getDatabase().getOpenHelper().getReadableDatabase();
        Cursor cur = readable.query(
                "SELECT entry_id, seq, form_id, match_kind, matched_text, original_query, "
                        + "dictionary_form, deinflection_label, rank, bookmark, memo, tags, render_json "
                        + "FROM DictionarySearchElement WHERE ref = ? ORDER BY entryOrder, rank, entry_id",
                new Object[]{ref});
        if (cur != null) {
            while (cur.moveToNext()) {
                DictionarySearchElement element = new DictionarySearchElement();
                element.entry_id = cur.getLong(0);
                element.seq = cur.getLong(1);
                element.form_id = cur.isNull(2) ? null : cur.getLong(2);
                element.match_kind = cur.getString(3);
                element.matched_text = cur.getString(4);
                element.original_query = cur.getString(5);
                element.dictionary_form = cur.getString(6);
                element.deinflection_label = cur.getString(7);
                element.rank = cur.getInt(8);
                element.bookmark = cur.getLong(9);
                element.memo = cur.getString(10);
                element.tags = cur.getString(11);
                element.render_json = cur.getString(12);
                results.add(element);
            }
            cur.close();
        }
        return results;
    }
}
