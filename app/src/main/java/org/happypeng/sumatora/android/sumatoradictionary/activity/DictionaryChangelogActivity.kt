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

package org.happypeng.sumatora.android.sumatoradictionary.activity

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.RecyclerView
import dagger.hilt.android.AndroidEntryPoint
import org.happypeng.sumatora.android.sumatoradictionary.R
import org.happypeng.sumatora.android.sumatoradictionary.adapter.DictionaryChangelogAdapter
import org.happypeng.sumatora.android.sumatoradictionary.component.PersistentDatabaseComponent
import javax.inject.Inject

// "Recent updates" - lists every changelog.json DictionaryUpdateChecker has fetched (see
// changelog-pipeline.md), newest first. Reached from Settings, next to "Manage dictionaries".
// Deliberately shows releases the user hasn't installed any pack for too - the fetch in
// DictionaryUpdateChecker runs unconditionally, not gated on installed packs.
@AndroidEntryPoint
class DictionaryChangelogActivity : AppCompatActivity() {

    @Inject
    lateinit var persistentDatabaseComponent: PersistentDatabaseComponent

    private lateinit var adapter: DictionaryChangelogAdapter
    private lateinit var emptyView: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dictionary_changelog)

        val toolbar = findViewById<Toolbar>(R.id.activity_dictionary_changelog_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.recent_updates_title)

        emptyView = findViewById(R.id.activity_dictionary_changelog_empty)
        adapter = DictionaryChangelogAdapter()
        findViewById<RecyclerView>(R.id.activity_dictionary_changelog_recyclerview).adapter = adapter

        val db = persistentDatabaseComponent.database
        db.dictionaryChangelogDao().getAllOrderByVersionDesc().observe(this) { rows ->
            adapter.submitList(rows)
            emptyView.visibility = if (rows.isEmpty()) View.VISIBLE else View.GONE
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}
