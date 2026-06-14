package org.happypeng.sumatora.core.bookmark

interface BookmarkRepository {
    fun getAll(): List<Bookmark>
    fun getBySeq(seq: Long): Bookmark?
    fun insert(bookmark: Bookmark)
    fun insertMany(bookmarks: List<Bookmark>)
    fun delete(seq: Long)
    fun deleteAll()
}
