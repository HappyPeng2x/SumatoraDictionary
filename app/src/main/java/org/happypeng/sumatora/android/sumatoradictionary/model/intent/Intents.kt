package org.happypeng.sumatora.android.sumatoradictionary.model.intent

import android.net.Uri
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentLanguageSettings

sealed class MVIIntent

object ViewDestroyedIntent : MVIIntent()
object ScrollIntent : MVIIntent()
object CloseIntent : MVIIntent()
object BookmarkIntent : MVIIntent()
object BookmarkImportCommitIntent : MVIIntent()
object BookmarkImportCancelIntent : MVIIntent()

class SearchIntent(val term: String) : MVIIntent()
class FilterMemosIntent(val filter: Boolean) : MVIIntent()
class FilterBookmarksIntent(val filter: Boolean) : MVIIntent()
class BookmarkImportFileOpenIntent(val uri: Uri) : MVIIntent()

sealed class LanguageSettingIntent(val languageSettings: PersistentLanguageSettings) : MVIIntent()

class LanguageSettingDetachedIntent(languageSettings: PersistentLanguageSettings) : LanguageSettingIntent(languageSettings)
class LanguageSettingAttachedIntent(languageSettings: PersistentLanguageSettings) : LanguageSettingIntent(languageSettings)
