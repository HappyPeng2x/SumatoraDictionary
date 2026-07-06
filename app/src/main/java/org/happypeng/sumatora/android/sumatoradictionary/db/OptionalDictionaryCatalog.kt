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

// The two optional packs (schema v2's search_suffix/names) are large enough (~98M/~140M
// compressed) that they're downloaded on demand instead of bundled - see
// android-app-to-jitendex.md / update-pipeline.md. This is a small, static list rather than a
// fetched remote manifest (that's the larger Phase 0c generalization, not needed for just these
// two known packs).
//
// version/date match the bundled dictionaries.xml's repository version so "is this installed
// pack current" compares the same way as bundled packs (InstalledDictionary.isSuperiorVersion).
//
// TODO: canonical dictionary hosting has moved to SumatoraIndex (see its release-pipeline.md) -
// R.string.dictionaries_url already points there. These two URLs still point at the old
// HappyPeng2x/SumatoraDictionary dictionaries-v8 release, which is still real/valid, so nothing
// is broken today. Once SumatoraIndex's first automated release ships (v9+), update
// RELEASE_BASE_URL/version/date here to match it - or, better, drop this static list entirely and
// drive the initial-install options from the same dictionaries.xml manifest RemoteManifestFetcher
// already fetches, so there's one source of truth instead of two.
object OptionalDictionaryCatalog {
    private const val RELEASE_BASE_URL =
        "https://github.com/HappyPeng2x/SumatoraDictionary/releases/download/dictionaries-v8"

    data class Entry(
        val type: String,
        val description: String,
        val url: String,
        val version: Int,
        val date: Int
    )

    val ALL: List<Entry> = listOf(
        Entry(
            type = "suffix",
            description = "Substring search",
            url = "$RELEASE_BASE_URL/sumatora_search_suffix.db.gz",
            version = 8,
            date = 20260705
        ),
        Entry(
            type = "names",
            description = "Proper names (JMnedict)",
            url = "$RELEASE_BASE_URL/sumatora_names.db.gz",
            version = 8,
            date = 20260705
        )
    )
}
