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

import android.app.DownloadManager
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.WorkInfo
import com.google.android.material.button.MaterialButton
import dagger.hilt.android.AndroidEntryPoint
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.happypeng.sumatora.android.sumatoradictionary.R
import org.happypeng.sumatora.android.sumatoradictionary.adapter.DictionaryObjectAdapter
import org.happypeng.sumatora.android.sumatoradictionary.component.PersistentDatabaseComponent
import org.happypeng.sumatora.android.sumatoradictionary.db.InstalledDictionary
import org.happypeng.sumatora.android.sumatoradictionary.db.OptionalDictionaryCatalog
import org.happypeng.sumatora.android.sumatoradictionary.db.RemoteDictionaryObject
import org.happypeng.sumatora.android.sumatoradictionary.update.DictionaryUpdateWorker
import org.happypeng.sumatora.android.sumatoradictionary.viewholder.rendering.DictionaryManagementRenderer
import java.io.File
import javax.inject.Inject

// Lets the user download the optional search_suffix/names packs (not bundled - see
// android-app-to-jitendex.md/update-pipeline.md) and remove them again. Reuses the
// RemoteDictionaryObject -> LocalDictionaryObject -> InstalledDictionary pipeline; the actual
// install-on-download-complete step happens in DictionaryDownloadCompleteReceiver.
@AndroidEntryPoint
class DictionariesManagementActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "DictMgmtActivity"
    }

    @Inject
    lateinit var persistentDatabaseComponent: PersistentDatabaseComponent

    private val disposables = CompositeDisposable()

    private lateinit var installAdapter: DictionaryObjectAdapter<RemoteDictionaryObject>
    private lateinit var removeAdapter: DictionaryObjectAdapter<InstalledDictionary>
    private lateinit var installEmpty: TextView
    private lateinit var removeEmpty: TextView
    private lateinit var installedContainer: LinearLayout
    private lateinit var checkUpdatesButton: MaterialButton
    private lateinit var checkUpdatesSpinner: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dictionaries_management)

        val toolbar = findViewById<Toolbar>(R.id.activity_dictionaries_management_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.manage_dictionaries_title)

        installEmpty = findViewById(R.id.activity_dictionaries_management_install_empty)
        removeEmpty = findViewById(R.id.activity_dictionaries_management_remove_empty)
        installedContainer = findViewById(R.id.activity_dictionaries_management_installed_container)
        checkUpdatesButton = findViewById(R.id.activity_dictionaries_management_check_updates)
        checkUpdatesSpinner = findViewById(R.id.activity_dictionaries_management_check_updates_spinner)

        checkUpdatesButton.setOnClickListener {
            checkUpdatesButton.isEnabled = false
            checkUpdatesButton.setText(R.string.checking_for_updates)
            checkUpdatesSpinner.visibility = View.VISIBLE
            DictionaryUpdateWorker.enqueueNow(this)
        }

        DictionaryUpdateWorker.manualCheckStatus(this).observe(this) { workInfos ->
            if (workInfos.any { it.state.isFinished }) {
                checkUpdatesButton.isEnabled = true
                checkUpdatesButton.setText(R.string.check_for_updates)
                checkUpdatesSpinner.visibility = View.GONE
                refresh()
            }
        }

        installAdapter = DictionaryObjectAdapter(
            true, false,
            { entry -> startDownload(entry) },
            null
        )
        findViewById<RecyclerView>(R.id.activity_dictionaries_management_install_rv).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = installAdapter
        }

        removeAdapter = DictionaryObjectAdapter(
            false, true,
            null,
            { entry -> removeInstalled(entry) }
        )
        findViewById<RecyclerView>(R.id.activity_dictionaries_management_remove_rv).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = removeAdapter
        }

        refresh()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun refresh() {
        disposables.add(
            Single.fromCallable {
                val db = persistentDatabaseComponent.database
                val installed = db.installedDictionaryDao().all
                val installedTypes = installed.map { it.type }.toSet()
                val downloadingTypes = db.remoteDictionaryObjectDao().all
                    .filter { it.downloadId > -1 }
                    .map { it.type }
                    .toSet()

                val installedCore = installed.firstOrNull { it.type == "core" }
                val cachedManifest = db.cachedManifestEntryDao().getAll()
                val catalog = OptionalDictionaryCatalog.resolve(installedCore, cachedManifest)

                val available = catalog
                    .filter { it.type !in installedTypes && it.type !in downloadingTypes }
                    .map {
                        RemoteDictionaryObject(it.url, it.description, it.type, "", it.version, it.date, it.sha256)
                    }
                val installedOptional = installed.filter { row ->
                    row.type in OptionalDictionaryCatalog.OPTIONAL_TYPES
                }

                Triple(available, installedOptional, installed)
            }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { (available, installedOptional, installed) ->
                        installAdapter.submitList(available)
                        installEmpty.visibility = if (available.isEmpty()) View.VISIBLE else View.GONE

                        removeAdapter.submitList(installedOptional)
                        removeEmpty.visibility = if (installedOptional.isEmpty()) View.VISIBLE else View.GONE

                        DictionaryManagementRenderer.buildInstalledRows(installedContainer, installed)
                    },
                    { e -> Log.e(TAG, "Failed to refresh dictionary lists", e) }
                )
        )
    }

    private fun startDownload(entry: RemoteDictionaryObject) {
        disposables.add(
            Completable.fromAction {
                val db = persistentDatabaseComponent.database
                val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                // DownloadManager.setDestinationUri() only accepts app-specific *external*
                // storage (or public external dirs) - internal storage (filesDir) throws
                // SecurityException: Unsupported path.
                val downloadDir = File(getExternalFilesDir(null), "downloads").apply { mkdirs() }

                entry.download(downloadManager, downloadDir)
                db.remoteDictionaryObjectDao().insert(entry)
            }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { refresh() },
                    { e -> Log.e(TAG, "Failed to start download for ${entry.type}", e) }
                )
        )
    }

    private fun removeInstalled(entry: InstalledDictionary) {
        disposables.add(
            Completable.fromAction {
                val db = persistentDatabaseComponent.database
                entry.detach(db)
                entry.delete()
                db.installedDictionaryDao().delete(entry)
            }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { refresh() },
                    { e -> Log.e(TAG, "Failed to remove ${entry.type}", e) }
                )
        )
    }

    override fun onDestroy() {
        disposables.clear()
        super.onDestroy()
    }
}
