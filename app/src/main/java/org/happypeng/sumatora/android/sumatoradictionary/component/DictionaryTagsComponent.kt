/* Sumatora Dictionary
        Copyright (C) 2020 Nicolas Centa

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

import androidx.annotation.WorkerThread
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.BehaviorSubject
import io.reactivex.rxjava3.subjects.PublishSubject
import io.reactivex.rxjava3.subjects.Subject
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryTag
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryTagName
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DictionaryTagsComponent @Inject internal constructor(private val persistentDatabaseComponent: PersistentDatabaseComponent) {
    private val dictionaryTagsSubject: Subject<List<Pair<DictionaryTagName, List<Long>>>> = BehaviorSubject.create()
    val dictionaryTags = dictionaryTagsSubject as Observable<List<Pair<DictionaryTagName, List<Long>>>>

    @WorkerThread
    private fun getTagsData(): List<Pair<DictionaryTagName, List<Long>>> {
        val tagNames = persistentDatabaseComponent.database.dictionaryTagNameDao().all
        val tags = persistentDatabaseComponent.database.dictionaryTagDao().all

        return tagNames.map { tagName ->
                Pair(tagName, tags.filter { tag -> tag.tagId == tagName.tagId }.map { tag -> tag.seq })
        }
    }

    fun createTag(seq: Long, tagId: Int) {
        Observable.create<List<Pair<DictionaryTagName, List<Long>>>> {
            persistentDatabaseComponent.database.dictionaryTagDao().insert(DictionaryTag(seq, tagId))
            it.onNext(getTagsData())
            it.onComplete() }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(dictionaryTagsSubject::onNext)
    }

    fun deleteTag(tag: DictionaryTag) {
        Observable.create<List<Pair<DictionaryTagName, List<Long>>>> {
            persistentDatabaseComponent.database.dictionaryTagDao().delete(tag)
            it.onNext(getTagsData())
            it.onComplete() }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(dictionaryTagsSubject::onNext)
    }

    fun createTagName(tagName: String) {
        Observable.create<List<Pair<DictionaryTagName, List<Long>>>> {
            persistentDatabaseComponent.database.dictionaryTagNameDao().insert(DictionaryTagName(tagName))
            it.onNext(getTagsData())
            it.onComplete() }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(dictionaryTagsSubject::onNext)
    }

    fun deleteTagNames(tags: List<DictionaryTagName>) {
        Observable.create<List<Pair<DictionaryTagName, List<Long>>>> {
            persistentDatabaseComponent.database.run {
                for (tag in tags) {
                    persistentDatabaseComponent.database.dictionaryTagDao().deleteMany(
                            persistentDatabaseComponent.database.dictionaryTagDao().getById(tag.tagId))
                }

                persistentDatabaseComponent.database.dictionaryTagNameDao().deleteMany(tags)
            }

            it.onNext(getTagsData())
            it.onComplete() }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(dictionaryTagsSubject::onNext)
    }

    init {
        Observable.create<List<Pair<DictionaryTagName, List<Long>>>> {
            it.onNext(getTagsData())
            it.onComplete() }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(dictionaryTagsSubject::onNext)
    }
}