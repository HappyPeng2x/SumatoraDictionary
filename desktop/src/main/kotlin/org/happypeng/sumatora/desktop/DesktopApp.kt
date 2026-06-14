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

package org.happypeng.sumatora.desktop

import javafx.application.Application
import javafx.scene.Scene
import javafx.scene.control.Alert
import javafx.stage.Stage
import org.happypeng.sumatora.desktop.db.DatabaseManager
import org.happypeng.sumatora.desktop.repository.BookmarkRepositoryImpl
import org.happypeng.sumatora.desktop.repository.SearchRepository
import org.happypeng.sumatora.desktop.repository.SettingsRepository
import org.happypeng.sumatora.desktop.repository.TagRepositoryImpl
import org.happypeng.sumatora.desktop.ui.AppContext
import org.happypeng.sumatora.desktop.ui.MainView
import java.io.File

class DesktopApp : Application() {

    private var db: DatabaseManager? = null

    override fun start(primaryStage: Stage) {
        val dataDir = File(System.getProperty("user.home"), ".sumatora")

        val dbManager = try {
            DatabaseManager(dataDir).also { db = it }
        } catch (e: Exception) {
            Alert(Alert.AlertType.ERROR).apply {
                title = "Startup error"
                headerText = "Failed to open user database"
                contentText = e.message
            }.showAndWait()
            return
        }

        val settings = SettingsRepository(dbManager)
        val lang = settings.getLanguage()

        val dictDir = File(dataDir, "dictionaries")
        val availableDbs: Map<String, File> = if (dictDir.isDirectory) {
            dictDir.listFiles { f -> f.extension == "db" }
                ?.associate { it.nameWithoutExtension to it } ?: emptyMap()
        } else emptyMap()

        // Attach the core dictionaries needed for search
        availableDbs["jmdict"]?.let { dbManager.attachDictionary(it, "jmdict") }
        availableDbs[lang]?.let { dbManager.attachDictionary(it, lang) }

        val ctx = AppContext(
            db        = dbManager,
            search    = SearchRepository(dbManager, lang),
            bookmarks = BookmarkRepositoryImpl(dbManager),
            tags      = TagRepositoryImpl(dbManager),
            settings  = settings,
            availableDbs = availableDbs
        )

        primaryStage.title = "Sumatora Dictionary"
        primaryStage.scene = Scene(MainView(ctx), 1100.0, 720.0)
        primaryStage.show()
    }

    override fun stop() {
        db?.close()
    }
}
