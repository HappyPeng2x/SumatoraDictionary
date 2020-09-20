package org.happypeng.sumatora.android.sumatoradictionary.model.state

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize
import org.happypeng.sumatora.android.sumatoradictionary.mvibase.MviViewState

enum class MainActivityNavigationStatus {
    SEARCH, BOOKMARKS, SETTINGS
}

@Parcelize
data class MainActivityState(val closed: Boolean,
                             val navigationStatus: MainActivityNavigationStatus,
                             val searchTerm: String?,
                             val drawerOpen: Boolean,
                             val finished: Boolean) : MviViewState, Parcelable