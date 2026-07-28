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

import android.Manifest
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.lifecycle.MediatorLiveData
import com.google.android.material.button.MaterialButton
import dagger.hilt.android.AndroidEntryPoint
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.slf4j.LoggerFactory
import java.net.DatagramSocket
import org.happypeng.sumatora.android.sumatoradictionary.R
import org.happypeng.sumatora.android.sumatoradictionary.component.PersistentDatabaseComponent
import org.happypeng.sumatora.android.sumatoradictionary.db.InstalledDictionary
import org.happypeng.sumatora.android.sumatoradictionary.db.OptionalDictionaryCatalog
import org.happypeng.sumatora.android.sumatoradictionary.db.RemoteDictionaryObject
import org.happypeng.sumatora.android.sumatoradictionary.update.DictionaryUpdateWorker
import org.happypeng.sumatora.android.sumatoradictionary.viewholder.rendering.DictionaryManagementRenderer
import org.happypeng.sumatora.android.sumatoradictionary.viewholder.rendering.DictionaryManagementRow
import java.io.File
import javax.inject.Inject

// Lets the user download the optional search_suffix/names/gloss/tatoeba packs (not bundled - see
// android-app-to-jitendex.md/update-pipeline.md) and remove them again. Reuses the
// RemoteDictionaryObject -> LocalDictionaryObject -> InstalledDictionary pipeline; the actual
// install-on-download-complete step happens in DictionaryDownloadCompleteReceiver.
@AndroidEntryPoint
class DictionariesManagementActivity : AppCompatActivity() {

    companion object {
        private val log = LoggerFactory.getLogger(DictionariesManagementActivity::class.java)
    }

    @Inject
    lateinit var persistentDatabaseComponent: PersistentDatabaseComponent

    private val disposables = CompositeDisposable()

    private lateinit var container: LinearLayout
    private lateinit var statusPill: TextView
    private lateinit var warningBanner: TextView
    private lateinit var checkUpdatesButton: MaterialButton
    private lateinit var checkUpdatesSpinner: ProgressBar

    // Without this, DictionaryDownloadCompleteReceiver's success/failure notifications silently
    // no-op on API 33+ - the on-screen failed/retry state still works either way, but a background
    // download's outcome would otherwise never reach a user who navigated away from this screen.
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dictionaries_management)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val toolbar = findViewById<Toolbar>(R.id.activity_dictionaries_management_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = getString(R.string.manage_dictionaries_title)

        container = findViewById(R.id.activity_dictionaries_management_container)
        statusPill = findViewById(R.id.activity_dictionaries_management_status)
        warningBanner = findViewById(R.id.activity_dictionaries_management_warning)
        checkUpdatesButton = findViewById(R.id.activity_dictionaries_management_check_updates)
        checkUpdatesSpinner = findViewById(R.id.activity_dictionaries_management_check_updates_spinner)

        checkUpdatesButton.setOnClickListener {
            checkUpdatesButton.isEnabled = false
            checkUpdatesButton.setText(R.string.checking_for_updates)
            checkUpdatesSpinner.visibility = View.VISIBLE

            // WorkManager's NetworkType.CONNECTED constraint tracks whether a validated network
            // exists system-wide, not whether this specific app can actually reach it - so on
            // GrapheneOS with Network off, the enqueued work just sits there forever (confirmed via
            // dumpsys jobscheduler: Ready stays false, and the worker's doWork() never even starts),
            // leaving this button stuck on "Checking for updates..." with no way out. Probe the same
            // real socket capability used before a download and fail fast instead of enqueueing work
            // that will never run.
            disposables.add(
                Single.fromCallable { hasSocketCapability() }
                    .subscribeOn(Schedulers.io())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe { hasNetwork ->
                        if (hasNetwork) {
                            DictionaryUpdateWorker.enqueueNow(this)
                        } else {
                            checkUpdatesButton.isEnabled = true
                            checkUpdatesButton.setText(R.string.check_for_updates)
                            checkUpdatesSpinner.visibility = View.GONE
                            showNetworkPermissionRequiredDialog()
                        }
                    }
            )
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

        // Show a toast when a manual check completes so the user knows whether anything was
        // found. lastManualResult is reset to null in enqueueNow(); non-null means the worker
        // finished and posted a result.
        DictionaryUpdateWorker.lastManualResult.observe(this) { count ->
            if (count != null) {
                if (count > 0) {
                    Toast.makeText(
                        this,
                        getString(R.string.dictionary_check_updates_found, count),
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    Toast.makeText(
                        this,
                        getString(R.string.dictionary_check_up_to_date),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
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
        val rows: List<DictionaryManagementRow>,
        val pendingUpdate: Boolean
    )

    private fun refresh() {
        disposables.add(
            Single.fromCallable {
                val db = persistentDatabaseComponent.database
                val installed = db.installedDictionaryDao().all
                val installedKeys = installed.map { it.type to it.lang }.toSet()
                val allRemote = db.remoteDictionaryObjectDao().all
                val downloading = allRemote.filter { it.downloadId > -1 }
                val downloadingKeys = downloading.map { it.type to it.lang }.toSet()
                val failed = allRemote.filter { it.downloadId <= -1 && it.failed }
                val failedKeys = failed.map { it.type to it.lang }.toSet()

                val installedCore = installed.firstOrNull { it.type == "core" }
                val cachedManifest = db.cachedManifestEntryDao().getAll()
                val catalog = OptionalDictionaryCatalog.resolve(installedCore, cachedManifest)

                val available = catalog
                    .filter { (it.type to it.lang) !in installedKeys && (it.type to it.lang) !in downloadingKeys
                            && (it.type to it.lang) !in failedKeys }
                    .map {
                        RemoteDictionaryObject(it.url, it.description, it.type, it.lang, it.version, it.date, it.sha256)
                    }

                val rows = buildList {
                    for (row in installed) {
                        add(DictionaryManagementRow(
                            row.type, row.lang, row.description, row.version, row.date,
                            installed = row, remote = null,
                            downloading = (row.type to row.lang) in downloadingKeys
                        ))
                    }
                    for (entry in downloading.filter { (it.type to it.lang) !in installedKeys }) {
                        add(DictionaryManagementRow(
                            entry.type, entry.lang, entry.description, entry.version, entry.date,
                            installed = null, remote = entry, downloading = true
                        ))
                    }
                    for (entry in failed.filter { (it.type to it.lang) !in installedKeys }) {
                        add(DictionaryManagementRow(
                            entry.type, entry.lang, entry.description, entry.version, entry.date,
                            installed = null, remote = entry, downloading = false, failed = true
                        ))
                    }
                    for (entry in available) {
                        add(DictionaryManagementRow(
                            entry.type, entry.lang, entry.description, entry.version, entry.date,
                            installed = null, remote = entry, downloading = false
                        ))
                    }
                }

                RenderState(rows, pendingUpdate = installed.any { it.hasPendingUpdate() })
            }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { state ->
                        DictionaryManagementRenderer.buildRows(
                            container, state.rows,
                            onInstall = { entry -> startDownload(entry) },
                            onDelete = { entry -> removeInstalled(entry) },
                            onRetry = { entry -> startDownload(entry) }
                        )

                        val incompatiblePacks = persistentDatabaseComponent
                            .dictionaryControlInfo.incompatiblePacks
                        val needsUpdate = incompatiblePacks.isNotEmpty()

                        statusPill.text = getString(
                            when {
                                state.pendingUpdate -> R.string.dictionary_status_update_ready
                                needsUpdate -> R.string.dictionary_status_update_required
                                else -> R.string.dictionary_status_up_to_date
                            }
                        )
                        statusPill.setTextColor(ContextCompat.getColor(
                            this,
                            when {
                                state.pendingUpdate -> R.color.dict_status_pending
                                needsUpdate -> R.color.dict_status_warning
                                else -> R.color.dict_status_ok
                            }
                        ))
                        statusPill.background = ContextCompat.getDrawable(
                            this,
                            when {
                                state.pendingUpdate -> R.drawable.bg_status_pill_pending
                                needsUpdate -> R.drawable.bg_status_pill_warning
                                else -> R.drawable.bg_status_pill_ok
                            }
                        )

                        warningBanner.visibility = if (needsUpdate) View.VISIBLE else View.GONE
                    },
                    { e -> log.error("Failed to refresh dictionary lists", e) }
                )
        )
    }

    // GrapheneOS's per-app "Network" toggle (Settings -> Apps -> Sumatora -> Permissions) doesn't
    // go through the normal runtime-permission grant table that checkSelfPermission(INTERNET) reads
    // - dumpsys package still shows it as a tracked runtime permission, but checkSelfPermission kept
    // reporting GRANTED in testing even with the toggle off. What actually changes is the process's
    // supplementary groups: with the toggle off, the app process is launched without gid 3003 (inet),
    // so any socket() call fails with EACCES - which is exactly what silently makes
    // DownloadManager.enqueue() return -1 (see RemoteDictionaryObject.download()). Opening a throwaway
    // DatagramSocket is a cheap, portable way to probe that real capability directly instead of
    // trusting a permission API that doesn't reflect it here.
    private fun hasSocketCapability(): Boolean {
        return try {
            DatagramSocket().close()
            true
        } catch (e: Exception) {
            false
        }
    }

    private class NetworkPermissionDeniedException : Exception()

    private fun startDownload(entry: RemoteDictionaryObject) {
        log.info("Install tapped for {}/{}, url={}", entry.type, entry.lang, entry.file)

        disposables.add(
            Completable.fromAction {
                if (!hasSocketCapability()) {
                    throw NetworkPermissionDeniedException()
                }

                val db = persistentDatabaseComponent.database
                val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                // DownloadManager.setDestinationUri() only accepts app-specific *external*
                // storage (or public external dirs) - internal storage (filesDir) throws
                // SecurityException: Unsupported path.
                val externalDir = getExternalFilesDir(null)
                log.info("getExternalFilesDir(null) = {}", externalDir)
                val downloadDir = File(externalDir, "downloads").apply { mkdirs() }

                entry.download(downloadManager, downloadDir)
                db.remoteDictionaryObjectDao().insert(entry)
                log.info(
                    "Enqueued download for {}/{}, DownloadManager id={}",
                    entry.type, entry.lang, entry.downloadId
                )
            }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    {},
                    { e ->
                        if (e is NetworkPermissionDeniedException) {
                            log.warn("No socket capability, blocking download for {}/{}", entry.type, entry.lang)
                            showNetworkPermissionRequiredDialog()
                            return@subscribe
                        }

                        log.error("Failed to start download for {}/{}", entry.type, entry.lang, e)
                        // entry.download() throwing before enqueue (e.g. no external storage)
                        // never reaches DictionaryDownloadCompleteReceiver, so nothing else would
                        // ever tell the user the tap didn't do anything - surface it here instead.
                        Toast.makeText(
                            this, getString(R.string.dictionary_download_failed_text, entry.description),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                )
        )
    }

    private fun showNetworkPermissionRequiredDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.network_permission_required_title)
            .setMessage(R.string.network_permission_required_text)
            .setCancelable(false)
            .setPositiveButton(R.string.network_permission_required_open_settings) { _, _ ->
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(Uri.fromParts("package", packageName, null))
                )
            }
            .setNegativeButton(R.string.network_permission_required_cancel, null)
            .show()
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
                    { e -> log.error("Failed to remove {}/{}", entry.type, entry.lang, e) }
                )
        )
    }

    override fun onDestroy() {
        disposables.clear()
        super.onDestroy()
    }
}
