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

package org.happypeng.sumatora.core.bookmark

object BookmarkMergeService {

    /**
     * Merges incoming bookmarks into the existing set using the same rules
     * as the SQL UPSERT in BookmarkImportComponent:
     *  - bookmark = MAX(existing, incoming)
     *  - memo updated only if incoming.memo is non-null and non-empty
     *  - tags updated only if incoming.tags is non-null and non-empty
     */
    @JvmStatic
    fun merge(existing: List<Bookmark>, incoming: List<Bookmark>): List<Bookmark> {
        val result = LinkedHashMap<Long, Bookmark>()
        for (b in existing) {
            result[b.seq] = Bookmark(b.seq, b.bookmark, b.memo, b.tags)
        }
        for (inc in incoming) {
            val ex = result[inc.seq]
            result[inc.seq] = if (ex == null) {
                Bookmark(inc.seq, inc.bookmark, inc.memo, inc.tags)
            } else {
                Bookmark(
                    inc.seq,
                    maxOf(ex.bookmark, inc.bookmark),
                    if (!inc.memo.isNullOrEmpty()) inc.memo else ex.memo,
                    if (!inc.tags.isNullOrEmpty()) inc.tags else ex.tags
                )
            }
        }
        return result.values.toList()
    }

    @JvmStatic
    fun splitTags(tagsString: String?): List<String> =
        tagsString?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()

    @JvmStatic
    fun joinTags(tags: List<String>): String = tags.joinToString(",")
}
