/* Sumatora Dictionary
        Copyright (C) 2026 Nicolas Centa

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

package org.happypeng.sumatora.desktop.ui

import org.happypeng.sumatora.desktop.db.DatabaseManager
import org.happypeng.sumatora.desktop.repository.BookmarkRepositoryImpl
import org.happypeng.sumatora.desktop.repository.SearchRepository
import org.happypeng.sumatora.desktop.repository.SettingsRepository
import org.happypeng.sumatora.desktop.repository.TagRepositoryImpl
import java.io.File

class AppContext(
    val db: DatabaseManager,
    val search: SearchRepository,
    val bookmarks: BookmarkRepositoryImpl,
    val tags: TagRepositoryImpl,
    val settings: SettingsRepository,
    val availableDbs: MutableMap<String, File>
) {
    fun changeLanguage(newLang: String) {
        val oldLang = search.lang
        if (oldLang == newLang) return
        val attached = db.getAttachedDatabases()
        if (attached.contains(oldLang)) db.detachDictionary(oldLang)
        availableDbs[newLang]?.let { db.attachDictionary(it, newLang) }
        search.lang = newLang
        settings.setLanguage(newLang)
    }

    /** Attach any newly installed .db files not yet attached to the connection. */
    fun rescanAndAttach(dictDir: File) {
        dictDir.listFiles { f -> f.extension == "db" }?.forEach { file ->
            val alias = file.nameWithoutExtension
            availableDbs[alias] = file
            if (!db.getAttachedDatabases().contains(alias)) {
                try { db.attachDictionary(file, alias) } catch (_: Exception) { }
            }
        }
    }
}
