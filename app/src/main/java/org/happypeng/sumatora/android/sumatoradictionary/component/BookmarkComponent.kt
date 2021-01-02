/* Sumatora Dictionary
        Copyright (C) 2019 Nicolas Centa

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
package org.happypeng.sumatora.android.sumatoradictionary.component

import androidx.annotation.MainThread
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.PublishSubject
import io.reactivex.rxjava3.subjects.Subject
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryBookmark
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookmarkComponent @Inject internal constructor(persistentDatabaseComponent: PersistentDatabaseComponent) {
    private val bookmarkChangesSubject: Subject<List<DictionaryBookmark>> =  PublishSubject.create()

    val bookmarkChanges: Observable<List<DictionaryBookmark>> = bookmarkChangesSubject.observeOn(Schedulers.io()).map { l: List<DictionaryBookmark> ->
        val persistentDatabase = persistentDatabaseComponent.database
        val dictionaryBookmarkDao = persistentDatabase.dictionaryBookmarkDao()
        //val tagNameDao = persistentDatabase.dictionaryTagNameDao()
        //val tagDao = persistentDatabase.dictionaryTagDao()

        persistentDatabase.runInTransaction {
            for (b in l) {
//                val existingTags = tagDao.getBySeq(b.seq)
//
//                for (existingTag in existingTags) {
//                    tagDao.delete(existingTag)
//                }
//
//                val existingTagNames = tagNameDao.all
//
//                for (existingTagName in existingTagNames) {
//                    if (existingTagName != null) {
//                        if (tagDao.getById(existingTagName.tagId).size == 0) {
//                            tagNameDao.delete(existingTagName)
//                        }
//                    }
//                }

                if (b.bookmark > 0 || b.memo != null && "" != b.memo) {
//                    if (b.memo != null) {
//                        for (hashtagMatch in hashtagRegex.findAll(b.memo)) {
//                            val hashtag = hashtagMatch.value.substring(1)
//
//                            val tagName = DictionaryTagName()
//                            tagName.tagName = hashtag
//
//                            tagNameDao.insert(tagName)
//
//                            val tagId = tagNameDao.getTagId(hashtag)
//
//                            val tag = DictionaryTag()
//                            tag.tagId = tagId
//                            tag.seq = b.seq
//
//                            tagDao.insert(tag)
//                        }
//                    }

                    dictionaryBookmarkDao.insert(b)
                } else {
                    dictionaryBookmarkDao.delete(b)
                }
            }
        }
        l
    }.observeOn(AndroidSchedulers.mainThread()).publish().autoConnect()

    @MainThread
    fun updateBookmark(bookmark: DictionaryBookmark) {
        val list = ArrayList<DictionaryBookmark>(1)
        list.add(bookmark)
        bookmarkChangesSubject.onNext(list)
    }

//    companion object {
//        val hashtagRegex = Regex("#[^#\\s]+")
//    }
}