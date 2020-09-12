package org.happypeng.sumatora.android.sumatoradictionary.model

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.subjects.BehaviorSubject
import io.reactivex.rxjava3.subjects.Subject
import org.happypeng.sumatora.android.sumatoradictionary.model.intent.*
import org.happypeng.sumatora.android.sumatoradictionary.model.status.MainActivityNavigationStatus
import org.happypeng.sumatora.android.sumatoradictionary.model.status.MainActivityStatus

class MainActivityModel(private val state: SavedStateHandle) : ViewModel() {
    private val intentSubject: Subject<MainActivityIntent> = BehaviorSubject.create()

    fun sendIntent(intent: MainActivityIntent) {
        intentSubject.onNext(intent)
    }

    val statusObservable: Observable<MainActivityStatus> =
            intentSubject.scan(state.get(STATUS_KEY) ?: initialStatus,
                    { mainActivityStatus: MainActivityStatus,
                      mainActivityIntent: MainActivityIntent ->
                        mainActivityStatus.copy(
                                navigationStatus = when(mainActivityIntent) {
                                    MainActivityNavigateBookmarksIntent -> MainActivityNavigationStatus.BOOKMARKS
                                    MainActivityNavigateSearchIntent -> MainActivityNavigationStatus.SEARCH
                                    MainActivityNavigateSettingsIntent -> MainActivityNavigationStatus.SETTINGS
                                    MainActivityBackPressedIntent -> MainActivityNavigationStatus.SEARCH
                                    else -> mainActivityStatus.navigationStatus
                                },
                                closed = when(mainActivityIntent) {
                                    MainActivityCloseIntent -> true
                                    else -> false
                                },
                                searchTerm = when(mainActivityIntent) {
                                    is MainActivitySearchIntent -> mainActivityIntent.term
                                    else -> mainActivityStatus.searchTerm
                                },
                                drawerOpen = when(mainActivityIntent) {
                                    MainActivityHomePressedIntent -> true
                                    is MainActivityNavigationIntent -> false
                                    else -> mainActivityStatus.drawerOpen
                                },
                                finished = when(mainActivityIntent) {
                                    MainActivityBackPressedIntent ->
                                        when(mainActivityStatus.navigationStatus) {
                                            MainActivityNavigationStatus.SEARCH -> true
                                            else -> false
                                        }
                                    else -> false
                                }
                        )
                    }).takeUntil { it.closed }

    init {
        statusObservable.subscribe {
            state.set(STATUS_KEY, it)
        }
    }

    companion object {
        val initialStatus = MainActivityStatus(closed = false,
                navigationStatus = MainActivityNavigationStatus.SEARCH, searchTerm = null,
                drawerOpen = false, finished = false)

        const val STATUS_KEY = "STATUS"
    }

    override fun onCleared() {
        sendIntent(MainActivityCloseIntent)

        super.onCleared()
    }
}