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
import javafx.geometry.Insets
import javafx.scene.Scene
import javafx.scene.control.Label
import javafx.scene.control.ScrollPane
import javafx.scene.layout.VBox
import javafx.scene.paint.Color
import javafx.stage.Stage
import org.happypeng.sumatora.desktop.db.DatabaseManager
import java.io.File

class DesktopApp : Application() {

    private var db: DatabaseManager? = null

    override fun start(primaryStage: Stage) {
        val dataDir = File(System.getProperty("user.home"), ".sumatora")
        val lines = mutableListOf<Pair<String, Boolean>>() // text, isError

        // Open user database
        try {
            db = DatabaseManager(dataDir)
            lines += "Data directory : ${dataDir.absolutePath}" to false
            lines += "User database  : OK (${File(dataDir, "sumatora.db").absolutePath})" to false
        } catch (e: Exception) {
            lines += "FAILED to open user database: ${e.message}" to true
            buildAndShow(primaryStage, lines)
            return
        }

        // Discover available dictionary .db files (do NOT attach all — SQLite max is 10)
        val dictDir = File(dataDir, "dictionaries")
        val availableDbs: Map<String, File> = if (dictDir.isDirectory) {
            dictDir.listFiles { f -> f.extension == "db" }
                ?.associate { it.nameWithoutExtension to it } ?: emptyMap()
        } else {
            emptyMap()
        }

        if (availableDbs.isEmpty()) {
            lines += "Dictionaries   : none found in ${dictDir.absolutePath}" to false
            lines += "               → place unzipped .db files there to enable search" to false
        } else {
            lines += "Available DBs  : ${availableDbs.keys.sorted().joinToString()}" to false
        }

        // Attach only jmdict for the sanity check; translation DBs are attached on demand
        availableDbs["jmdict"]?.let { jmdictFile ->
            try {
                db!!.attachDictionary(jmdictFile, "jmdict")
                val count = db!!.rawQuery("SELECT count(*) AS cnt FROM jmdict.DictionaryEntry")
                    .firstOrNull()?.get("cnt") ?: 0
                lines += "jmdict entries : $count" to false
                db!!.detachDictionary("jmdict")
            } catch (e: Exception) {
                lines += "jmdict check failed: ${e.message}" to true
            }
        }

        buildAndShow(primaryStage, lines)
    }

    private fun buildAndShow(stage: Stage, lines: List<Pair<String, Boolean>>) {
        val vbox = VBox(5.0).apply {
            padding = Insets(16.0)
            children.addAll(lines.map { (text, isError) ->
                Label(text).apply {
                    style = "-fx-font-family: monospace; -fx-font-size: 13px;"
                    if (isError) textFill = Color.CRIMSON
                }
            })
        }
        stage.title = "Sumatora Dictionary"
        stage.scene = Scene(ScrollPane(vbox).apply { isFitToWidth = true }, 620.0, 340.0)
        stage.show()
    }

    override fun stop() {
        db?.close()
    }
}
