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

package org.happypeng.sumatora.android.sumatoradictionary.db

import androidx.room.Entity

// One row per changelog.json fetched by DictionaryUpdateChecker (see changelog-pipeline.md) -
// stored verbatim rather than normalized into columns, since it's a few KB (per the weekly
// entry-count analysis that motivated this feature) and only ever read back to render "recent
// updates", never queried by field. Parsed into per-category counts on read, in
// DictionaryChangelogActivity.
@Entity(primaryKeys = ["version"])
data class DictionaryChangelog(
    val version: Int,
    val date: Int,
    val json: String,
    val fetchedAt: Long
)
