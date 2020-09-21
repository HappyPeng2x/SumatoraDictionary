package org.happypeng.sumatora.android.sumatoradictionary.model.intent

sealed class MainActivityIntent

sealed class MainActivityNavigationIntent : MainActivityIntent()

object MainActivityNavigateBookmarksIntent : MainActivityNavigationIntent()
object MainActivityNavigateSearchIntent : MainActivityNavigationIntent()
object MainActivityNavigateSettingsIntent : MainActivityNavigationIntent()
object MainActivityNavigateAboutIntent : MainActivityNavigationIntent()

object MainActivityBackPressedIntent : MainActivityIntent()
object MainActivityHomePressedIntent : MainActivityIntent()

object MainActivityDrawerClosedIntent : MainActivityIntent()

object MainActivityCloseIntent : MainActivityIntent()

class MainActivitySearchIntent(val term: String) : MainActivityIntent()