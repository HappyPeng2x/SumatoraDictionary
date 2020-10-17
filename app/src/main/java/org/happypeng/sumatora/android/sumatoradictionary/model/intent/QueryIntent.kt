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

package org.happypeng.sumatora.android.sumatoradictionary.model.intent

import android.net.Uri
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentLanguageSettings
import org.happypeng.sumatora.android.sumatoradictionary.mvibase.MviIntent

sealed class QueryIntent : MviIntent

class ScrollIntent(val entryOrder: Int) : QueryIntent()
object QueryCloseIntent : QueryIntent()
object BookmarkIntent : QueryIntent()

class SearchIntent(val term: String) : QueryIntent()
object CloseSearchBoxIntent : QueryIntent()
object OpenSearchBoxIntent : QueryIntent()

sealed class QueryLanguageSettingIntent(val languageSettings: PersistentLanguageSettings) : QueryIntent()

class QueryLanguageSettingDetachedIntent(languageSettings: PersistentLanguageSettings) : QueryLanguageSettingIntent(languageSettings)
class QueryLanguageSettingAttachedIntent(languageSettings: PersistentLanguageSettings) : QueryLanguageSettingIntent(languageSettings)