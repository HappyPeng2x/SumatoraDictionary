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
package org.happypeng.sumatora.android.sumatoradictionary.model

import androidx.hilt.Assisted
import androidx.hilt.lifecycle.ViewModelInject
import androidx.lifecycle.SavedStateHandle
import androidx.paging.PagedList.BoundaryCallback
import org.happypeng.sumatora.android.sumatoradictionary.component.BookmarkComponent
import org.happypeng.sumatora.android.sumatoradictionary.component.BookmarkShareComponent
import org.happypeng.sumatora.android.sumatoradictionary.component.LanguageSettingsComponent
import org.happypeng.sumatora.android.sumatoradictionary.component.PersistentDatabaseComponent
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchElement
import org.happypeng.sumatora.android.sumatoradictionary.model.state.QueryState

class BookmarkFragmentModel @ViewModelInject constructor(bookmarkComponent: BookmarkComponent,
                                                         persistentDatabaseComponent: PersistentDatabaseComponent,
                                                         languageSettingsComponent: LanguageSettingsComponent,
                                                         bookmarkShareComponent: BookmarkShareComponent,
                                                         @Assisted savedStateHandle: SavedStateHandle) : BaseQueryFragmentModel(bookmarkComponent,
        persistentDatabaseComponent,
        languageSettingsComponent,
        bookmarkShareComponent,
        { component: PersistentDatabaseComponent, callback: BoundaryCallback<DictionarySearchElement?>? -> component.getSearchElements(KEY, callback) },
        KEY,
        true,
        true,
        TITLE,
        true,
        true,
        false,
        false,
        savedStateHandle.get(QueryFragmentModel.STATUS_KEY)
) {
    companion object {
        const val KEY = 2
        const val TITLE = "Bookmarks"
        const val STATUS_KEY = "STATUS"
    }

    init {
        states().subscribe {
            savedStateHandle.set(QueryFragmentModel.STATUS_KEY, it)
        }
    }
}