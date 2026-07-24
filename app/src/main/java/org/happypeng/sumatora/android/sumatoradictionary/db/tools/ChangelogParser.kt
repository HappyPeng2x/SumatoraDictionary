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

package org.happypeng.sumatora.android.sumatoradictionary.db.tools

import org.json.JSONObject

// Parses a stored DictionaryChangelog.json (see changelog-pipeline.md / SumatoraIndex's
// build-changelog.py) into display-ready per-category, per-language deltas. Walks the JSON
// generically instead of hardcoding each category's shape: every category object
// (jmdict/jmnedict/kanjidic2/tatoeba/pitch) has one "own" bucket (entries/characters/sentences)
// plus an optional "translations" object keyed by language - a bucket is any object with
// added/modified/removed arrays.
object ChangelogParser {
    data class Delta(val label: String, val added: Int, val modified: Int, val removed: Int) {
        val isEmpty get() = added == 0 && modified == 0 && removed == 0
    }

    // Order here is display order. Native names for gloss languages and English names for
    // example-sentence languages mirror OptionalDictionaryCatalog's two language tables, which
    // mirror SumatoraIndex's release-dictionaries.py - same split, same reasoning: gloss packs are
    // presented in their own language, example packs in English.
    private val CATEGORY_LABELS = linkedMapOf(
        "jmdict" to "JMdict",
        "jmnedict" to "Names (JMnedict)",
        "kanjidic2" to "Kanji",
        "tatoeba" to "Example sentences",
        "pitch" to "Pitch accent"
    )

    private val GLOSS_LANG_NAMES = mapOf(
        "eng" to "English", "ger" to "Deutsch", "rus" to "русский язык", "spa" to "Español",
        "dut" to "Nederlands", "hun" to "Magyar nyelv", "swe" to "Svenska", "fre" to "Français",
        "slv" to "Slovenski jezik"
    )

    private val EXAMPLE_LANG_NAMES = mapOf(
        "eng" to "English", "ger" to "German", "rus" to "Russian", "spa" to "Spanish",
        "dut" to "Dutch", "hun" to "Hungarian", "swe" to "Swedish", "fre" to "French"
    )

    fun parse(json: String): List<Delta> {
        val root = JSONObject(json)
        val deltas = mutableListOf<Delta>()

        for ((categoryKey, categoryLabel) in CATEGORY_LABELS) {
            val category = root.optJSONObject(categoryKey) ?: continue
            val langNames = if (categoryKey == "tatoeba") EXAMPLE_LANG_NAMES else GLOSS_LANG_NAMES

            for (kindKey in category.keys()) {
                if (kindKey == "translations") {
                    val translations = category.getJSONObject("translations")
                    for (lang in translations.keys()) {
                        val label = "$categoryLabel — ${langNames[lang] ?: lang}"
                        deltas += translations.getJSONObject(lang).toDelta(label)
                    }
                } else {
                    deltas += category.getJSONObject(kindKey).toDelta(categoryLabel)
                }
            }
        }

        return deltas.filterNot { it.isEmpty }
    }

    private fun JSONObject.toDelta(label: String): Delta {
        fun count(key: String) = optJSONArray(key)?.length() ?: 0
        return Delta(label, count("added"), count("modified"), count("removed"))
    }
}
