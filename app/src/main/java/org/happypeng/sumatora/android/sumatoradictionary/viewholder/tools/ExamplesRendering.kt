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

package org.happypeng.sumatora.android.sumatoradictionary.viewholder.tools

val sentenceRegex = "([^\\(\\)\\[\\]\\{\\}\\s]+)(\\(([^\\(\\)\\[\\]\\{\\}\\s]*)\\))?(\\[([^\\(\\)\\[\\]\\{\\}\\s]*)\\])?(\\{([^\\(\\)\\[\\]\\{\\}\\s]*)\\})?(~)?\\s?".toRegex()

class ExampleWord(val writing: String,
                  val reading: String,
                  val index: Int,
                  val sentence: String,
                  val verified: Boolean)

fun parseWord(aWordMatch: MatchResult): ExampleWord {
    val (writing, _, reading, _, index, _, sentence, verified) = aWordMatch.destructured

    return ExampleWord(writing, reading, if (index == "") { -1 } else { index.toInt() },
            sentence,verified == "~")
}

fun parseSentence(aSentence: String): Sequence<ExampleWord> {
    return sentenceRegex.findAll(aSentence).map { parseWord(it) }
}

fun renderSentence(aSentence: String): String {
    return parseSentence(aSentence).map {
        val writing = if (it.sentence == "") {it.writing} else {it.sentence}
        val reading = if (it.reading == writing) {""} else {it.reading}

        if (reading != "") { "<ruby>${writing}<rt>${reading}</rt></ruby>" }
            else { writing }
    }.reduce { acc, string -> acc + string }
}