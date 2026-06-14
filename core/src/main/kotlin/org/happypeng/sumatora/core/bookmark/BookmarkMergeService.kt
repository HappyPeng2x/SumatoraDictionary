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
