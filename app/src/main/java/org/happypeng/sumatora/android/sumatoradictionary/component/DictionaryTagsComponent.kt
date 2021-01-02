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

import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.schedulers.Schedulers
import io.reactivex.rxjava3.subjects.PublishSubject
import io.reactivex.rxjava3.subjects.Subject
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryTagName
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DictionaryTagsComponent @Inject internal constructor(private val persistentDatabaseComponent: PersistentDatabaseComponent) {
    private val dictionaryTagNamesSubject: Subject<List<DictionaryTagName>> = PublishSubject.create()
    val dictionaryTagNames = dictionaryTagNamesSubject as Observable<List<DictionaryTagName>>

    fun createTagName(tagName: String) {
        Observable.create<List<DictionaryTagName>> {
            persistentDatabaseComponent.database.dictionaryTagNameDao().insert(DictionaryTagName(tagName))
            it.onNext(persistentDatabaseComponent.database.dictionaryTagNameDao().all)
            it.onComplete() }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(dictionaryTagNamesSubject::onNext)
    }

    init {
        Observable.create<List<DictionaryTagName>> {
            it.onNext(persistentDatabaseComponent.database.dictionaryTagNameDao().all)
            it.onComplete() }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(dictionaryTagNamesSubject::onNext)
    }
}