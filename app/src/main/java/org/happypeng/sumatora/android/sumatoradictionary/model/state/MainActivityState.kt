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

package org.happypeng.sumatora.android.sumatoradictionary.model.state

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize
import org.happypeng.sumatora.android.sumatoradictionary.mvibase.MviViewState

enum class MainActivityNavigationStatus {
    SEARCH, BOOKMARKS, SETTINGS
}

@Parcelize
data class MainActivityState(val closed: Boolean,
                             val navigate: Boolean,
                             val navigationStatus: MainActivityNavigationStatus,
                             val changeTerm: Boolean,
                             val searchTerm: String,
                             val drawerOpen: Boolean,
                             val finished: Boolean,
                             val searchTerms: Map<MainActivityNavigationStatus, String>) : MviViewState, Parcelable