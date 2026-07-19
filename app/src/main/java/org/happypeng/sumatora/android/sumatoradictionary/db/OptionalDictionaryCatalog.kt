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
// (Sumatora chat, 2026-07-10). Once DictionaryUpdateChecker has fetched a manifest whose
// suffix/names entries match the installed core's (version, date), resolve() sources them from
// CachedManifestEntry, keeping the two in lockstep automatically. Before that first fetch ever
// completes - including the very first app launch - there's nothing in CachedManifestEntry yet, so
// fallback() derives a same-vintage URL from the *installed* core's own version instead of a
// separately hardcoded one: a hardcoded fallback already went stale once this way (pinned to
// dictionaries-v8 on this repo while the bundled core had moved on to v11 on SumatoraIndex), and
// nothing enforces a developer updating it in lockstep with assets/dictionaries.xml. Every
// SumatoraIndex release republishes suffix/names alongside core under the same
// dictionaries-v{version} tag (see release-pipeline.md), so this always has a matching pack to
// point at.
object OptionalDictionaryCatalog {
    private const val RELEASE_BASE_URL =
        "https://github.com/HappyPeng2x/SumatoraIndex/releases/download"

    val OPTIONAL_TYPES = setOf("suffix", "names")

    data class Entry(
        val type: String,
        val description: String,
        val url: String,
        val version: Int,
        val date: Int,
        val sha256: String = ""
    )

    // No sha256: this version's manifest was never fetched, so there's nothing to verify the
    // download against. DictionaryDownloadCompleteReceiver already treats an empty sha256 as
    // "skip verification".
    private fun fallback(installedCore: InstalledDictionary): List<Entry> {
        val baseUrl = "$RELEASE_BASE_URL/dictionaries-v${installedCore.version}"

        return listOf(
            Entry(
                type = "suffix",
                description = "Substring search",
                url = "$baseUrl/sumatora_search_suffix.db.gz",
                version = installedCore.version,
                date = installedCore.date
            ),
            Entry(
                type = "names",
                description = "Proper names (JMnedict)",
                url = "$baseUrl/sumatora_names.db.gz",
                version = installedCore.version,
                date = installedCore.date
            )
        )
    }

    // installedCore is null before the first-run reconciliation ever completes - there's no core
    // version to derive a fallback from yet, so offer nothing rather than guessing.
    fun resolve(installedCore: InstalledDictionary?, cached: List<CachedManifestEntry>): List<Entry> {
        if (installedCore == null) {
            return emptyList()
        }

        val cachedOptional = cached.filter { it.type in OPTIONAL_TYPES }

        val matchesInstalledCore = cachedOptional.isNotEmpty() &&
                cachedOptional.all { it.version == installedCore.version && it.date == installedCore.date }

        return if (matchesInstalledCore) {
            cachedOptional.map { Entry(it.type, it.description ?: "", it.url, it.version, it.date, it.sha256) }
        } else {
            fallback(installedCore)
        }
    }
}
