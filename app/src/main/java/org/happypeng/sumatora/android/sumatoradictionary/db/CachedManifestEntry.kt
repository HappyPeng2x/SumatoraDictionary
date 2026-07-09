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

// Snapshot of the dictionaries.xml manifest as of the last successful fetch by
// DictionaryUpdateChecker - see OptionalDictionaryCatalog for why this exists: it lets the
// optional-pack install screen offer packs versioned to match whatever core version is actually
// installed, instead of a hardcoded version number that goes stale the moment core is upgraded.
@Entity(primaryKeys = ["type", "lang"])
data class CachedManifestEntry(
    val type: String,
    val lang: String,
    val description: String?,
    val url: String,
    val version: Int,
    val date: Int,
    val sha256: String
)
