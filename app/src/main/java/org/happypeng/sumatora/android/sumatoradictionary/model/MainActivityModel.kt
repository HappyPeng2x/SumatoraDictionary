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
import io.reactivex.rxjava3.subjects.PublishSubject
import io.reactivex.rxjava3.subjects.Subject
import org.happypeng.sumatora.android.sumatoradictionary.model.intent.*
import org.happypeng.sumatora.android.sumatoradictionary.model.state.MainActivityNavigationStatus
import org.happypeng.sumatora.android.sumatoradictionary.model.state.MainActivityState

class MainActivityModel(private val state: SavedStateHandle) : ViewModel() {
    private val intentSubject: Subject<MainActivityIntent> = PublishSubject.create()

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
                                        drawerOpen = false,
                                        navigate = true,
                                        changeTerm = false,
                                        backDestination = null)
                            is MainActivityNavigateSearchIntent ->
                                mainActivityState.copy(navigationStatus = MainActivityNavigationStatus.SEARCH,
                                        searchTerm = mainActivityState.searchTerms[MainActivityNavigationStatus.SEARCH] ?: "",
                                        drawerOpen = false,
                                        navigate = true,
                                        changeTerm = false,
                                        backDestination = null)
                            is MainActivityNavigateSettingsIntent ->
                                mainActivityState.copy(navigationStatus = MainActivityNavigationStatus.SETTINGS,
                                        drawerOpen = false,
                                        navigate = true,
                                        changeTerm = false,
                                        backDestination = null)
                            is MainActivityNavigateTagsIntent ->
                                mainActivityState.copy(navigationStatus = MainActivityNavigationStatus.TAGS,
                                        drawerOpen = false,
                                        navigate = true,
                                        changeTerm = false,
                                        backDestination = null)
                            is MainActivityNavigateAboutIntent ->
                                mainActivityState.copy(drawerOpen = false,
                                        navigate = false,
                                        changeTerm = false)
                            is MainActivityCloseIntent ->
                                mainActivityState.copy(closed = true,
                                        navigate = false,
                                        changeTerm = false)
                            is MainActivitySearchIntent ->
                                mainActivityState.copy(searchTerm = mainActivityIntent.term,
                                        changeTerm = true,
                                        navigate = true,
                                        backDestination = null,
                                        searchTerms = mapOf(MainActivityNavigationStatus.BOOKMARKS to
                                                if (mainActivityState.navigationStatus == MainActivityNavigationStatus.BOOKMARKS)
                                                { mainActivityIntent.term } else { mainActivityState.searchTerms[MainActivityNavigationStatus.BOOKMARKS] ?: "" },
                                                MainActivityNavigationStatus.SEARCH to
                                                        if (mainActivityState.navigationStatus == MainActivityNavigationStatus.SEARCH)
                                                        { mainActivityIntent.term } else { mainActivityState.searchTerms[MainActivityNavigationStatus.SEARCH] ?: "" }))
                            is MainActivitySetSearchFragmentSearchIntent ->
                                mainActivityState.copy(searchTerm = mainActivityIntent.term,
                                        navigate = mainActivityState.navigationStatus != MainActivityNavigationStatus.SEARCH,
                                        changeTerm = true,
                                        navigationStatus = MainActivityNavigationStatus.SEARCH,
                                        backDestination = null,
                                        searchTerms = mapOf(MainActivityNavigationStatus.BOOKMARKS to
                                                run { mainActivityState.searchTerms[MainActivityNavigationStatus.BOOKMARKS] ?: "" },
                                                MainActivityNavigationStatus.SEARCH to run { mainActivityIntent.term }))
                            is MainActivitySearchFromTagsIntent ->
                                mainActivityState.copy(
                                        navigationStatus = MainActivityNavigationStatus.SEARCH,
                                        searchTerm = "#${mainActivityIntent.tag}",
                                        navigate = true,
                                        changeTerm = true,
                                        backDestination = MainActivityNavigationStatus.TAGS,
                                        searchTerms = mainActivityState.searchTerms +
                                                mapOf(MainActivityNavigationStatus.SEARCH to "#${mainActivityIntent.tag}"))
                            is MainActivityHomePressedIntent ->
                                mainActivityState.copy(drawerOpen = true,
                                        changeTerm = false, navigate = false)
                            is MainActivityDrawerClosedIntent ->
                                mainActivityState.copy(drawerOpen = false,
                                        changeTerm = false, navigate = false)
                            is MainActivityBackPressedIntent -> {
                                val dest = mainActivityState.backDestination
                                when {
                                    mainActivityState.navigationStatus == MainActivityNavigationStatus.SEARCH && dest != null ->
                                        mainActivityState.copy(
                                                navigate = true,
                                                changeTerm = false,
                                                finished = false,
                                                navigationStatus = dest,
                                                backDestination = null)
                                    mainActivityState.navigationStatus == MainActivityNavigationStatus.SEARCH ->
                                        mainActivityState.copy(
                                                navigate = false,
                                                changeTerm = false,
                                                finished = true,
                                                navigationStatus = MainActivityNavigationStatus.SEARCH,
                                                searchTerm = mainActivityState.searchTerms[MainActivityNavigationStatus.SEARCH] ?: "")
                                    else ->
                                        mainActivityState.copy(
                                                navigate = true,
                                                changeTerm = false,
                                                finished = false,
                                                navigationStatus = MainActivityNavigationStatus.SEARCH,
                                                backDestination = null,
                                                searchTerm = mainActivityState.searchTerms[MainActivityNavigationStatus.SEARCH] ?: "")
                                }
                            }
                        }
                    })
                    .takeUntil { it.closed }
                    .replay(1)
                    .autoConnect(0)

    init {
        stateObservable.subscribe {
            state.set(STATUS_KEY, it)
        }
    }

    companion object {
        val initialStatus = MainActivityState(closed = false,
                navigate = true,
                navigationStatus = MainActivityNavigationStatus.SEARCH, searchTerm = "",
                drawerOpen = false, finished = false,
                searchTerms = mapOf(MainActivityNavigationStatus.SEARCH to "",
                        MainActivityNavigationStatus.BOOKMARKS to ""),
                changeTerm = false,
                backDestination = null)

        const val STATUS_KEY = "STATUS"
    }

    override fun onCleared() {
        sendIntent(MainActivityCloseIntent)

        super.onCleared()
    }
}