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
// android-app-to-jitendex.md / update-pipeline.md.
//
// suffix/names are built against core's word-id space, so an optional pack must always come from
// the same release as the installed core dictionary - see the conversation that led to this file
// (Sumatora chat, 2026-07-10). LEGACY is a one-time pin to the pre-SumatoraIndex v8 release: the
// only version where this held (the bundled assets/dictionaries.xml never lists suffix/names, and
// no fetched manifest exists yet). From v9 onward the unified dictionaries.xml already carries
// suffix/names alongside core with one shared repository version, so resolve() sources them from
// CachedManifestEntry - populated by DictionaryUpdateChecker every time it fetches that manifest -
// filtered down to whatever version the installed core dictionary actually is. That keeps the two
// versioned in lockstep automatically instead of relying on a developer remembering to bump a
// hardcoded number every release.
object OptionalDictionaryCatalog {
    private const val LEGACY_RELEASE_BASE_URL =
        "https://github.com/HappyPeng2x/SumatoraDictionary/releases/download/dictionaries-v8"

    val OPTIONAL_TYPES = setOf("suffix", "names")

    data class Entry(
        val type: String,
        val description: String,
        val url: String,
        val version: Int,
        val date: Int,
        val sha256: String = ""
    )

    private val LEGACY: List<Entry> = listOf(
        Entry(
            type = "suffix",
            description = "Substring search",
            url = "$LEGACY_RELEASE_BASE_URL/sumatora_search_suffix.db.gz",
            version = 8,
            date = 20260705
        ),
        Entry(
            type = "names",
            description = "Proper names (JMnedict)",
            url = "$LEGACY_RELEASE_BASE_URL/sumatora_names.db.gz",
            version = 8,
            date = 20260705
        )
    )

    // installedCore is null before the first-run reconciliation ever completes - treat that the
    // same as "no manifest fetch has ever happened" and fall back to LEGACY.
    fun resolve(installedCore: InstalledDictionary?, cached: List<CachedManifestEntry>): List<Entry> {
        val cachedOptional = cached.filter { it.type in OPTIONAL_TYPES }

        val matchesInstalledCore = installedCore != null && cachedOptional.isNotEmpty() &&
                cachedOptional.all { it.version == installedCore.version && it.date == installedCore.date }

        return if (matchesInstalledCore) {
            cachedOptional.map { Entry(it.type, it.description ?: "", it.url, it.version, it.date, it.sha256) }
        } else {
            LEGACY
        }
    }
}
