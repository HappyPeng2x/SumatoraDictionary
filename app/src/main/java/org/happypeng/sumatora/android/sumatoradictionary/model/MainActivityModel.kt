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

package org.happypeng.sumatora.android.sumatoradictionary.model

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.BehaviorSubject
import io.reactivex.rxjava3.subjects.Subject
import org.happypeng.sumatora.android.sumatoradictionary.activity.MainActivity
import org.happypeng.sumatora.android.sumatoradictionary.model.intent.*
import org.happypeng.sumatora.android.sumatoradictionary.model.state.MainActivityNavigationStatus
import org.happypeng.sumatora.android.sumatoradictionary.model.state.MainActivityState

class MainActivityModel(private val state: SavedStateHandle) : ViewModel() {
    private val intentSubject: Subject<MainActivityIntent> = BehaviorSubject.create()

    fun sendIntent(intent: MainActivityIntent) {
        intentSubject.onNext(intent)
    }

    val stateObservable: Observable<MainActivityState> =
            intentSubject.scan(state.get(STATUS_KEY) ?: initialStatus,
                    { mainActivityState: MainActivityState,
                      mainActivityIntent: MainActivityIntent ->
                        when (mainActivityIntent) {
                            is MainActivityNavigateBookmarksIntent ->
                                mainActivityState.copy(navigationStatus = MainActivityNavigationStatus.BOOKMARKS,
                                        searchTerm = mainActivityState.searchTerms[MainActivityNavigationStatus.BOOKMARKS] ?: "",
                                        drawerOpen = false)
                            is MainActivityNavigateSearchIntent ->
                                mainActivityState.copy(navigationStatus = MainActivityNavigationStatus.SEARCH,
                                        searchTerm = mainActivityState.searchTerms[MainActivityNavigationStatus.SEARCH] ?: "",
                                        drawerOpen = false)
                            is MainActivityNavigateSettingsIntent ->
                                mainActivityState.copy(navigationStatus = MainActivityNavigationStatus.SETTINGS,
                                        drawerOpen = false)
                            is MainActivityNavigateAboutIntent ->
                                mainActivityState.copy(drawerOpen = false)
                            is MainActivityCloseIntent ->
                                mainActivityState.copy(closed = true)
                            is MainActivitySearchIntent ->
                                mainActivityState.copy(searchTerm = mainActivityIntent.term,
                                        searchTerms = mapOf(MainActivityNavigationStatus.BOOKMARKS to
                                                if (mainActivityState.navigationStatus == MainActivityNavigationStatus.BOOKMARKS)
                                                { mainActivityIntent.term } else { mainActivityState.searchTerms[MainActivityNavigationStatus.BOOKMARKS] ?: "" },
                                                MainActivityNavigationStatus.SEARCH to
                                                        if (mainActivityState.navigationStatus == MainActivityNavigationStatus.SEARCH)
                                                        { mainActivityIntent.term } else { mainActivityState.searchTerms[MainActivityNavigationStatus.SEARCH] ?: "" }))
                            is MainActivityHomePressedIntent ->
                                mainActivityState.copy(drawerOpen = true)
                            is MainActivityDrawerClosedIntent ->
                                mainActivityState.copy(drawerOpen = false)
                            is MainActivityBackPressedIntent ->
                                mainActivityState.copy(finished =
                                mainActivityState.navigationStatus == MainActivityNavigationStatus.SEARCH,
                                        navigationStatus = MainActivityNavigationStatus.SEARCH,
                                        searchTerm = mainActivityState.searchTerms[MainActivityNavigationStatus.SEARCH] ?: "")
                        }
                    }).takeUntil { it.closed }

    init {
        stateObservable.subscribe {
            state.set(STATUS_KEY, it)
        }
    }

    companion object {
        val initialStatus = MainActivityState(closed = false,
                navigationStatus = MainActivityNavigationStatus.SEARCH, searchTerm = "",
                drawerOpen = false, finished = false,
                searchTerms = mapOf(MainActivityNavigationStatus.SEARCH to "",
                        MainActivityNavigationStatus.BOOKMARKS to ""))

        const val STATUS_KEY = "STATUS"
    }

    override fun onCleared() {
        sendIntent(MainActivityCloseIntent)

        super.onCleared()
    }
}