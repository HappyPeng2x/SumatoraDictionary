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

package org.happypeng.sumatora.desktop.model

import org.happypeng.sumatora.core.dict.DictionaryResult

data class SearchResult(
    override val seq: Long,
    override val readingsPrio: String?,
    override val readings: String?,
    override val writingsPrio: String?,
    override val writings: String?,
    override val pos: String?,
    override val xref: String?,
    override val ant: String?,
    override val misc: String?,
    override val lsource: String?,
    override val dial: String?,
    override val s_inf: String?,
    override val field: String?,
    override val lang: String?,
    override val gloss: String?,
    override val exampleSentences: String?,
    override val exampleTranslations: String?,
    override val bookmark: Long,
    override val memo: String?,
    override val tags: String?
) : DictionaryResult
