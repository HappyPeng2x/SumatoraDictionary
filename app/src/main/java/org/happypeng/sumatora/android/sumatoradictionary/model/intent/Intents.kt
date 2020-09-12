package org.happypeng.sumatora.android.sumatoradictionary.model.intent

import android.net.Uri
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentLanguageSettings

sealed class MVIIntent

sealed class BaseQueryIntent : MVIIntent()

object ViewDestroyedIntent : BaseQueryIntent()
object ScrollIntent : BaseQueryIntent()
object CloseIntent : BaseQueryIntent()
object BookmarkIntent : BaseQueryIntent()
object BookmarkImportCommitIntent : BaseQueryIntent()
object BookmarkImportCancelIntent : BaseQueryIntent()

class SearchIntent(val term: String) : BaseQueryIntent()
class FilterMemosIntent(val filter: Boolean) : BaseQueryIntent()
class FilterBookmarksIntent(val filter: Boolean) : BaseQueryIntent()
class BookmarkImportFileOpenIntent(val uri: Uri) : BaseQueryIntent()

sealed class LanguageSettingIntent(val languageSettings: PersistentLanguageSettings) : BaseQueryIntent()

class LanguageSettingDetachedIntent(languageSettings: PersistentLanguageSettings) : LanguageSettingIntent(languageSettings)
class LanguageSettingAttachedIntent(languageSettings: PersistentLanguageSettings) : LanguageSettingIntent(languageSettings)