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

package org.happypeng.sumatora.core.search

// A guessed dictionary form recovered from a conjugated surface form, tagged with the
// Yomitan-compatible rule code it assumes (must match DictionaryEntry.rules after lookup) and
// a human-readable label describing the conjugation applied.
data class DeinflectionCandidate(val dictionaryForm: String, val ruleCode: String, val label: String)

// Client-side deinflection engine (Gap 4). Given a conjugated surface form, generates candidate
// dictionary forms by reversing common Japanese verb/adjective conjugations. Coverage is "solid
// common forms" (negative, past, te-form, potential, passive, causative, causative-passive,
// imperative, volitional, conditional, desiderative, polite, progressive) for v1/v5/vk/vs/vz/adj-i
// — not an exhaustive/chained deinflector. Candidates are deliberately generated liberally: the
// caller is expected to verify each candidate against DictionaryEntry.rules (see
// DictionarySearchQueryTool.executeDeinflection), so an over-broad guess here is harmless — it
// simply won't match a real entry and gets discarded downstream.
object Deinflector {
    // suffix: the conjugated ending to strip from the surface form.
    // stem: text appended to the remaining base to reconstruct the dictionary form (often the
    // verb's own dictionary-ending kana; empty when the base already is the dictionary form).
    private data class Rule(val suffix: String, val stem: String, val ruleCode: String, val label: String)

    private const val V1 = "v1"
    private const val V5 = "v5"
    private const val VK = "vk"
    private const val VS = "vs"
    private const val VZ = "vz"
    private const val ADJ_I = "adj-i"

    private val V1_RULES: List<Rule> = listOf(
        Rule("ない", "る", V1, "negative"),
        Rule("ません", "る", V1, "negative (polite)"),
        Rule("た", "る", V1, "past"),
        Rule("ました", "る", V1, "past (polite)"),
        Rule("なかった", "る", V1, "negative past"),
        Rule("ませんでした", "る", V1, "negative past (polite)"),
        Rule("て", "る", V1, "te-form"),
        Rule("ます", "る", V1, "polite"),
        Rule("られる", "る", V1, "potential/passive"),
        Rule("れる", "る", V1, "potential/passive (colloquial)"),
        Rule("させる", "る", V1, "causative"),
        Rule("さす", "る", V1, "causative (colloquial)"),
        Rule("させられる", "る", V1, "causative-passive"),
        Rule("ろ", "る", V1, "imperative"),
        Rule("よ", "る", V1, "imperative (formal)"),
        Rule("よう", "る", V1, "volitional"),
        Rule("れば", "る", V1, "conditional (ba)"),
        Rule("たら", "る", V1, "conditional (tara)"),
        Rule("たい", "る", V1, "want to"),
        Rule("ている", "る", V1, "progressive"),
        Rule("てる", "る", V1, "progressive (colloquial)"),
        Rule("ないで", "る", V1, "negative te-form"),
        Rule("なくて", "る", V1, "negative te-form")
    )

    // Godan conjugation rows keyed by dictionary-form ending kana.
    private class GodanRow(val ending: Char, val a: String, val i: String, val e: String,
                           val o: String, val te: String, val ta: String)

    private val GODAN_ROWS = listOf(
        GodanRow('う', "わ", "い", "え", "お", "って", "った"),
        GodanRow('く', "か", "き", "け", "こ", "いて", "いた"),
        GodanRow('ぐ', "が", "ぎ", "げ", "ご", "いで", "いだ"),
        GodanRow('す', "さ", "し", "せ", "そ", "して", "した"),
        GodanRow('つ', "た", "ち", "て", "と", "って", "った"),
        GodanRow('ぬ', "な", "に", "ね", "の", "んで", "んだ"),
        GodanRow('ぶ', "ば", "び", "べ", "ぼ", "んで", "んだ"),
        GodanRow('む', "ま", "み", "め", "も", "んで", "んだ"),
        GodanRow('る', "ら", "り", "れ", "ろ", "って", "った")
    )

    private fun godanRules(): List<Rule> = GODAN_ROWS.flatMap { row ->
        val ending = row.ending.toString()
        listOf(
            Rule(row.a + "ない", ending, V5, "negative"),
            Rule(row.i + "ません", ending, V5, "negative (polite)"),
            Rule(row.ta, ending, V5, "past"),
            Rule(row.i + "ました", ending, V5, "past (polite)"),
            Rule(row.a + "なかった", ending, V5, "negative past"),
            Rule(row.i + "ませんでした", ending, V5, "negative past (polite)"),
            Rule(row.te, ending, V5, "te-form"),
            Rule(row.i + "ます", ending, V5, "polite"),
            Rule(row.e + "る", ending, V5, "potential"),
            Rule(row.a + "れる", ending, V5, "passive"),
            Rule(row.a + "せる", ending, V5, "causative"),
            Rule(row.a + "せられる", ending, V5, "causative-passive"),
            Rule(row.a + "される", ending, V5, "causative-passive (colloquial)"),
            Rule(row.e, ending, V5, "imperative"),
            Rule(row.o + "う", ending, V5, "volitional"),
            Rule(row.e + "ば", ending, V5, "conditional (ba)"),
            Rule(row.ta + "ら", ending, V5, "conditional (tara)"),
            Rule(row.i + "たい", ending, V5, "want to"),
            Rule(row.te + "いる", ending, V5, "progressive"),
            Rule(row.te + "る", ending, V5, "progressive (colloquial)"),
            Rule(row.a + "ないで", ending, V5, "negative te-form"),
            Rule(row.a + "なくて", ending, V5, "negative te-form")
        )
    } + listOf(
        // 行く (iku) is the one common godan verb whose te/ta-form is irregular (行って/行った
        // instead of the regular く-row's 行いて/行いた).
        Rule("行って", "行く", V5, "te-form"),
        Rule("行った", "行く", V5, "past"),
        Rule("行ったら", "行く", V5, "conditional (tara)"),
        Rule("行っている", "行く", V5, "progressive"),
        Rule("行ってる", "行く", V5, "progressive (colloquial)")
    )

    // 来る/くる (kuru) is irregular: the 来 kanji is read differently per conjugation, so each
    // known conjugated form is matched exactly (no shared stem-stripping pattern), producing
    // both the kanji and kana dictionary-form spellings.
    private val VK_CONJUGATIONS = listOf(
        "こない" to "negative", "きません" to "negative (polite)",
        "きた" to "past", "きました" to "past (polite)",
        "こなかった" to "negative past", "きませんでした" to "negative past (polite)",
        "きて" to "te-form", "きます" to "polite",
        "こられる" to "potential/passive", "これる" to "potential/passive (colloquial)",
        "こさせる" to "causative", "こさせられる" to "causative-passive",
        "こい" to "imperative", "こよう" to "volitional",
        "くれば" to "conditional (ba)", "きたら" to "conditional (tara)",
        "きたい" to "want to", "きている" to "progressive", "きてる" to "progressive (colloquial)",
        "こないで" to "negative te-form", "こなくて" to "negative te-form"
    )

    private fun vkRules(): List<Rule> = VK_CONJUGATIONS.flatMap { (suffix, label) ->
        listOf(Rule(suffix, "来る", VK, label), Rule(suffix, "くる", VK, label))
    }

    private val VS_RULES: List<Rule> = listOf(
        Rule("しない", "する", VS, "negative"),
        Rule("しません", "する", VS, "negative (polite)"),
        Rule("した", "する", VS, "past"),
        Rule("しました", "する", VS, "past (polite)"),
        Rule("しなかった", "する", VS, "negative past"),
        Rule("しませんでした", "する", VS, "negative past (polite)"),
        Rule("して", "する", VS, "te-form"),
        Rule("します", "する", VS, "polite"),
        Rule("できる", "する", VS, "potential"),
        Rule("される", "する", VS, "passive"),
        Rule("させる", "する", VS, "causative"),
        Rule("させられる", "する", VS, "causative-passive"),
        Rule("しろ", "する", VS, "imperative"),
        Rule("せよ", "する", VS, "imperative (formal)"),
        Rule("しよう", "する", VS, "volitional"),
        Rule("すれば", "する", VS, "conditional (ba)"),
        Rule("したら", "する", VS, "conditional (tara)"),
        Rule("したい", "する", VS, "want to"),
        Rule("している", "する", VS, "progressive"),
        Rule("してる", "する", VS, "progressive (colloquial)"),
        Rule("しないで", "する", VS, "negative te-form"),
        Rule("しなくて", "する", VS, "negative te-form")
    )

    private val VZ_RULES: List<Rule> = listOf(
        Rule("じない", "ずる", VZ, "negative"),
        Rule("じません", "ずる", VZ, "negative (polite)"),
        Rule("じた", "ずる", VZ, "past"),
        Rule("じました", "ずる", VZ, "past (polite)"),
        Rule("じなかった", "ずる", VZ, "negative past"),
        Rule("じて", "ずる", VZ, "te-form"),
        Rule("じます", "ずる", VZ, "polite"),
        Rule("じられる", "ずる", VZ, "potential/passive"),
        Rule("じさせる", "ずる", VZ, "causative"),
        Rule("じろ", "ずる", VZ, "imperative"),
        Rule("じよう", "ずる", VZ, "volitional"),
        Rule("ずれば", "ずる", VZ, "conditional (ba)"),
        Rule("じたら", "ずる", VZ, "conditional (tara)"),
        Rule("じたい", "ずる", VZ, "want to")
    )

    private val ADJ_I_RULES: List<Rule> = listOf(
        Rule("くない", "い", ADJ_I, "negative"),
        Rule("くなかった", "い", ADJ_I, "negative past"),
        Rule("かった", "い", ADJ_I, "past"),
        Rule("くて", "い", ADJ_I, "te-form"),
        Rule("く", "い", ADJ_I, "adverbial"),
        Rule("ければ", "い", ADJ_I, "conditional (ba)"),
        Rule("かったら", "い", ADJ_I, "conditional (tara)"),
        Rule("くないです", "い", ADJ_I, "negative (polite)"),
        Rule("くありません", "い", ADJ_I, "negative (polite)"),
        Rule("かったです", "い", ADJ_I, "past (polite)"),
        Rule("です", "", ADJ_I, "polite")
    )

    private val ALL_RULES: List<Rule> by lazy {
        V1_RULES + godanRules() + vkRules() + VS_RULES + VZ_RULES + ADJ_I_RULES
    }

    fun deinflect(surface: String): List<DeinflectionCandidate> {
        if (surface.isBlank()) return emptyList()

        val candidates = mutableListOf<DeinflectionCandidate>()
        for (rule in ALL_RULES) {
            if (surface.length >= rule.suffix.length && surface.endsWith(rule.suffix)) {
                val base = surface.substring(0, surface.length - rule.suffix.length)
                val dictionaryForm = base + rule.stem
                if (dictionaryForm.isNotEmpty() && dictionaryForm != surface) {
                    candidates.add(DeinflectionCandidate(dictionaryForm, rule.ruleCode, rule.label))
                }
            }
        }

        return candidates.distinctBy { it.dictionaryForm to it.ruleCode }
    }
}
