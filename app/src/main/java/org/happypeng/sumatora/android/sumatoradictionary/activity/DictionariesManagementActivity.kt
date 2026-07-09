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
import androidx.core.content.ContextCompat
import androidx.lifecycle.MediatorLiveData
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
    private lateinit var downloadingCard: View
    private lateinit var downloadingContainer: LinearLayout
    private lateinit var statusPill: TextView
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
        downloadingCard = findViewById(R.id.activity_dictionaries_management_downloading_card)
        downloadingContainer = findViewById(R.id.activity_dictionaries_management_downloading_container)
        statusPill = findViewById(R.id.activity_dictionaries_management_status)
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
                // No explicit refresh() needed - the manifest fetch this triggered writes to
                // CachedManifestEntry, which the LiveData mediator below already reacts to.
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

        // Every write this screen can trigger (a fresh install, a background update landing, a
        // manifest re-fetch) lands in one of these three tables - react to any of them changing
        // instead of manually calling refresh() after each individual action. This also means a
        // download that completes via DictionaryDownloadCompleteReceiver (a separate broadcast
        // receiver, possibly while this screen was backgrounded) is picked up automatically.
        val db = persistentDatabaseComponent.database
        val invalidated = MediatorLiveData<Unit>()
        invalidated.addSource(db.installedDictionaryDao().getAllLive()) { invalidated.value = Unit }
        invalidated.addSource(db.remoteDictionaryObjectDao().getAllLive()) { invalidated.value = Unit }
        invalidated.addSource(db.cachedManifestEntryDao().getAllLive()) { invalidated.value = Unit }
        invalidated.observe(this) { refresh() }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private data class RenderState(
        val available: List<RemoteDictionaryObject>,
        val installedOptional: List<InstalledDictionary>,
        val installed: List<InstalledDictionary>,
        val downloading: List<RemoteDictionaryObject>,
        val pendingUpdate: Boolean
    )

    private fun refresh() {
        disposables.add(
            Single.fromCallable {
                val db = persistentDatabaseComponent.database
                val installed = db.installedDictionaryDao().all
                val installedTypes = installed.map { it.type }.toSet()
                val downloading = db.remoteDictionaryObjectDao().all.filter { it.downloadId > -1 }
                val downloadingTypes = downloading.map { it.type }.toSet()

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

                RenderState(
                    available, installedOptional, installed, downloading,
                    pendingUpdate = installed.any { it.hasPendingUpdate() }
                )
            }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { state ->
                        installAdapter.submitList(state.available)
                        installEmpty.visibility = if (state.available.isEmpty()) View.VISIBLE else View.GONE

                        removeAdapter.submitList(state.installedOptional)
                        removeEmpty.visibility = if (state.installedOptional.isEmpty()) View.VISIBLE else View.GONE

                        DictionaryManagementRenderer.buildInstalledRows(installedContainer, state.installed)

                        downloadingCard.visibility = if (state.downloading.isEmpty()) View.GONE else View.VISIBLE
                        DictionaryManagementRenderer.buildDownloadingRows(downloadingContainer, state.downloading)

                        statusPill.text = getString(
                            if (state.pendingUpdate) R.string.dictionary_status_update_ready
                            else R.string.dictionary_status_up_to_date
                        )
                        statusPill.setTextColor(ContextCompat.getColor(
                            this, if (state.pendingUpdate) R.color.dict_status_pending else R.color.dict_status_ok
                        ))
                        statusPill.background = ContextCompat.getDrawable(
                            this,
                            if (state.pendingUpdate) R.drawable.bg_status_pill_pending else R.drawable.bg_status_pill_ok
                        )
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
                    {},
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
                    {},
                    { e -> Log.e(TAG, "Failed to remove ${entry.type}", e) }
                )
        )
    }

    override fun onDestroy() {
        disposables.clear()
        super.onDestroy()
    }
}
