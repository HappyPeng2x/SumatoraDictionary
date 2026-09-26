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

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.browser.customtabs.CustomTabsIntent
import androidx.work.Data
import androidx.work.WorkInfo
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import dagger.hilt.android.AndroidEntryPoint
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Completable
import io.reactivex.rxjava3.core.Maybe
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.schedulers.Schedulers
import org.happypeng.sumatora.android.sumatoradictionary.R
import org.happypeng.sumatora.android.sumatoradictionary.sync.NextcloudCredentialStore
import org.happypeng.sumatora.android.sumatoradictionary.sync.NextcloudCredentials
import org.happypeng.sumatora.android.sumatoradictionary.sync.NextcloudLoginFlow
import org.happypeng.sumatora.android.sumatoradictionary.sync.NextcloudSyncWorker
import org.slf4j.LoggerFactory
import java.io.IOException
import java.util.concurrent.TimeoutException
import javax.inject.Inject

// Connect/disconnect + manual "Sync now" for Nextcloud bookmark sync (see the nextcloud-sync
// plan). Deliberately a single simple screen, not a list-based UI like
// DictionariesManagementActivity - there's only one account to manage.
@AndroidEntryPoint
class NextcloudSyncActivity : AppCompatActivity() {

    companion object {
        private val log = LoggerFactory.getLogger(NextcloudSyncActivity::class.java)

        // Nextcloud's Login Flow v2 token is valid for roughly 20 minutes; poll a bit under that.
        private const val POLL_TIMEOUT_MS = 18 * 60 * 1000L
        private const val POLL_INTERVAL_MS = 3_000L
    }

    @Inject
    lateinit var credentialStore: NextcloudCredentialStore

    private val disposables = CompositeDisposable()

    private lateinit var accountStatus: TextView
    private lateinit var serverInput: TextInputEditText
    private lateinit var connectButton: MaterialButton
    private lateinit var disconnectButton: MaterialButton
    private lateinit var syncNowButton: MaterialButton
    private lateinit var syncSpinner: ProgressBar
    private lateinit var syncStatus: TextView

    // EncryptedSharedPreferences.load()/save()/clear() all touch the Keystore-backed prefs file
    // on disk (StrictMode flags this on the main thread), so every read/write of it happens on
    // Schedulers.io() and the result is cached here - the UI thread never calls credentialStore
    // directly.
    private var lastKnownCredentials: NextcloudCredentials? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nextcloud_sync)

        val toolbar = findViewById<Toolbar>(R.id.activity_nextcloud_sync_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        accountStatus = findViewById(R.id.activity_nextcloud_sync_account_status)
        serverInput = findViewById(R.id.activity_nextcloud_sync_server_input)
        connectButton = findViewById(R.id.activity_nextcloud_sync_connect)
        disconnectButton = findViewById(R.id.activity_nextcloud_sync_disconnect)
        syncNowButton = findViewById(R.id.activity_nextcloud_sync_now)
        syncSpinner = findViewById(R.id.activity_nextcloud_sync_spinner)
        syncStatus = findViewById(R.id.activity_nextcloud_sync_status)

        connectButton.setOnClickListener { onConnectClicked() }
        disconnectButton.setOnClickListener { onDisconnectClicked() }
        syncNowButton.setOnClickListener { NextcloudSyncWorker.enqueueNow(this) }

        syncStatus.text = getString(R.string.nextcloud_sync_last_sync_never)
        syncNowButton.isEnabled = false

        disposables.add(
            Maybe.create<NextcloudCredentials> { emitter ->
                val credentials = credentialStore.load()
                if (credentials != null) emitter.onSuccess(credentials) else emitter.onComplete()
            }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { credentials -> refreshAccountUi(credentials) },
                    { error -> log.warn("Failed to load Nextcloud credentials", error) },
                    { refreshAccountUi(null) }
                )
        )
        observeSyncStatus()
    }

    override fun onDestroy() {
        disposables.clear()
        super.onDestroy()
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }

    private fun refreshAccountUi(credentials: NextcloudCredentials?) {
        lastKnownCredentials = credentials
        if (credentials == null) {
            accountStatus.text = getString(R.string.nextcloud_sync_not_connected)
            serverInput.setText("")
            connectButton.visibility = View.VISIBLE
            disconnectButton.visibility = View.GONE
            syncNowButton.isEnabled = false
        } else {
            accountStatus.text = getString(
                R.string.nextcloud_sync_connected_as, credentials.loginName, credentials.server
            )
            connectButton.visibility = View.GONE
            disconnectButton.visibility = View.VISIBLE
            syncNowButton.isEnabled = true
        }
    }

    private fun onConnectClicked() {
        val server = normalizeServerUrl(serverInput.text?.toString().orEmpty())
        if (server.isEmpty()) {
            return
        }

        connectButton.isEnabled = false
        accountStatus.text = getString(R.string.nextcloud_sync_connecting)

        disposables.add(
            Single.fromCallable { NextcloudLoginFlow.initiate(server) }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSuccess { init -> openLoginPage(init.loginUrl) }
                .observeOn(Schedulers.io())
                .flatMap { init -> Single.fromCallable { pollUntilComplete(init.pollEndpoint, init.token) } }
                .doOnSuccess { credentials -> credentialStore.save(credentials) }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { credentials ->
                        NextcloudSyncWorker.enqueuePeriodic(this)
                        connectButton.isEnabled = true
                        refreshAccountUi(credentials)
                    },
                    { error ->
                        log.warn("Nextcloud connect failed", error)
                        connectButton.isEnabled = true
                        // A failed (re)connect must not be treated as a disconnect - restore
                        // whatever was already known to be configured before this attempt.
                        refreshAccountUi(lastKnownCredentials)
                        val message = if (error is TimeoutException) {
                            getString(R.string.nextcloud_sync_connect_timed_out)
                        } else {
                            getString(R.string.nextcloud_sync_connect_failed, error.message ?: error.javaClass.simpleName)
                        }
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                    }
                )
        )
    }

    private fun onDisconnectClicked() {
        disconnectButton.isEnabled = false
        disposables.add(
            Completable.fromAction { credentialStore.clear() }
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe {
                    NextcloudSyncWorker.cancelPeriodic(this)
                    disconnectButton.isEnabled = true
                    refreshAccountUi(null)
                }
        )
    }

    private fun openLoginPage(loginUrl: String) {
        CustomTabsIntent.Builder().build().launchUrl(this, Uri.parse(loginUrl))
    }

    @Throws(IOException::class, TimeoutException::class)
    private fun pollUntilComplete(pollEndpoint: String, token: String): NextcloudCredentials {
        val deadline = System.currentTimeMillis() + POLL_TIMEOUT_MS

        while (System.currentTimeMillis() < deadline) {
            val credentials = NextcloudLoginFlow.poll(pollEndpoint, token)
            if (credentials != null) {
                return credentials
            }
            Thread.sleep(POLL_INTERVAL_MS)
        }

        throw TimeoutException("Login flow timed out")
    }

    // Adds a scheme if the user typed a bare hostname, and drops any trailing slash so
    // NextcloudLoginFlow/NextcloudWebDavClient can append paths without a doubled "//".
    private fun normalizeServerUrl(input: String): String {
        val trimmed = input.trim().trimEnd('/')
        if (trimmed.isEmpty()) {
            return ""
        }
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }
    }

    private fun observeSyncStatus() {
        NextcloudSyncWorker.manualSyncStatus(this).observe(this) { workInfos ->
            val info = workInfos.firstOrNull() ?: return@observe

            syncSpinner.visibility = if (info.state == WorkInfo.State.RUNNING) View.VISIBLE else View.GONE
            syncNowButton.isEnabled = info.state != WorkInfo.State.RUNNING && lastKnownCredentials != null

            if (info.state == WorkInfo.State.SUCCEEDED) {
                syncStatus.text = describeOutcome(info.outputData)
            }
        }
    }

    private fun describeOutcome(data: Data): String {
        return when (data.getString(NextcloudSyncWorker.KEY_OUTCOME)) {
            NextcloudSyncWorker.OUTCOME_SUCCESS -> {
                val count = data.getInt(NextcloudSyncWorker.KEY_BOOKMARK_COUNT, 0)
                getString(R.string.nextcloud_sync_last_sync_success, count)
            }
            NextcloudSyncWorker.OUTCOME_FAILURE -> {
                val message = data.getString(NextcloudSyncWorker.KEY_ERROR_MESSAGE) ?: ""
                getString(R.string.nextcloud_sync_last_sync_failed, message)
            }
            else -> getString(R.string.nextcloud_sync_last_sync_failed, "conflict, try again")
        }
    }
}
