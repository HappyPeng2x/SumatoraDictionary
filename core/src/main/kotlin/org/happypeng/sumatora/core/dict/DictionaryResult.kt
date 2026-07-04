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

package org.happypeng.sumatora.core.dict

interface DictionaryResult {
    val seq: Long
    val readingsPrio: String?
    val readings: String?
    val writingsPrio: String?
    val writings: String?
    val pos: String?
    val xref: String?
    val ant: String?
    val misc: String?
    val lsource: String?
    val dial: String?
    val s_inf: String?
    val field: String?
    val lang: String?
    val gloss: String?
    val exampleSentences: String?
    val exampleTranslations: String?
    val bookmark: Long
    val memo: String?
    val tags: String?
    val furigana: String?
    val score: Int
    val stagk: String?
    val stagr: String?
    val exampleMatchedTokens: String?
    val deinflectionLabel: String?
    val isProperNoun: Boolean
    val properNounTypes: String?
}
