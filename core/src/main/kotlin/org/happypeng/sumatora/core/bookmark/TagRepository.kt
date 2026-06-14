package org.happypeng.sumatora.core.bookmark

interface TagRepository {
    fun getAllTags(): List<String>
    fun getTagsForSeq(seq: Long): List<String>
    fun insertTag(tag: BookmarkTag)
    fun insertManyTags(tags: List<BookmarkTag>)
    fun deleteTagsForSeq(seq: Long)
    fun deleteTag(seq: Long, tag: String)
}
