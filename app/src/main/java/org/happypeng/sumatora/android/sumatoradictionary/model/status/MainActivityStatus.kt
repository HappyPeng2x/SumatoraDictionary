package org.happypeng.sumatora.android.sumatoradictionary.model.status

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize

enum class MainActivityNavigationStatus {
    SEARCH, BOOKMARKS, SETTINGS
}

@Parcelize
data class MainActivityStatus(override val closed: Boolean,
    val navigationStatus: MainActivityNavigationStatus,
    val searchTerm: String?,
    val drawerOpen: Boolean,
    val finished: Boolean) : MVIStatus, Parcelable