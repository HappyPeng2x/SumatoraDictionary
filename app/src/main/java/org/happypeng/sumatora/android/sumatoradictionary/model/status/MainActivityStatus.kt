package org.happypeng.sumatora.android.sumatoradictionary.model.status

enum class MainActivityNavigationStatus {
    SEARCH, BOOKMARKS, SETTINGS
}

data class MainActivityStatus(val isClosed: Boolean,
    val navigationStatus: MainActivityNavigationStatus,
    val searchTerm: String?,
    val drawerOpen: Boolean) : MVIStatus(isClosed)
