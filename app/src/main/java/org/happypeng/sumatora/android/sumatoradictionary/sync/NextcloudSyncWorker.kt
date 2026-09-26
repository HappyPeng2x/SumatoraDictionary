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

package org.happypeng.sumatora.android.sumatoradictionary.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.lifecycle.LiveData
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import org.happypeng.sumatora.android.sumatoradictionary.component.PersistentDatabaseComponent
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.Settings
import java.util.concurrent.TimeUnit

// Background half of Nextcloud bookmark sync, mirroring DictionaryUpdateWorker's structure:
// periodic (Wi-Fi-gated the same way dictionary downloads are, via Settings.isWifiOnly) plus a
// manual "Sync now" one-time request from NextcloudSyncActivity. The actual round-trip is
// NextcloudSyncComponent.sync() - this worker only decides when to run it and reports the result.
@HiltWorker
class NextcloudSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncComponent: NextcloudSyncComponent,
    private val persistentDatabaseComponent: PersistentDatabaseComponent
) : Worker(context, params) {

    override fun doWork(): Result {
        val db = persistentDatabaseComponent.database

        // A manual "Sync now" tap always runs; the periodic job respects the same Wi-Fi-only
        // setting dictionary downloads use, so sync doesn't silently burn metered data in the
        // background without the user having opted in.
        val isManual = tags.contains(UNIQUE_MANUAL_NAME)
        if (!isManual && Settings.isWifiOnly(db)) {
            val connectivityManager = applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE)
                    as android.net.ConnectivityManager
            val network = connectivityManager.activeNetwork
            val capabilities = network?.let { connectivityManager.getNetworkCapabilities(it) }
            val onWifi = capabilities?.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) == true
            if (!onWifi) {
                Log.i(TAG, "Skipping periodic sync - Wi-Fi-only setting and not on Wi-Fi")
                return Result.success()
            }
        }

        // Always Result.success() - same convention DictionaryUpdateWorker uses: a transient
        // failure here isn't worth WorkManager's own retry/backoff machinery when the next
        // periodic run (or the user tapping "Sync now" again) will just try again. The actual
        // outcome goes into output data so NextcloudSyncActivity can show it via
        // manualSyncStatus()'s WorkInfo, instead of it being silently swallowed.
        return when (val result = syncComponent.sync()) {
            is NextcloudSyncComponent.SyncResult.Success -> {
                Log.i(TAG, "Sync complete: ${result.bookmarkCount} bookmark(s), ${result.tombstoneCount} tombstone(s)")
                Result.success(workDataOf(
                    KEY_OUTCOME to OUTCOME_SUCCESS,
                    KEY_BOOKMARK_COUNT to result.bookmarkCount
                ))
            }
            is NextcloudSyncComponent.SyncResult.NotConfigured -> {
                Log.i(TAG, "Sync skipped - no Nextcloud account configured")
                Result.success(workDataOf(KEY_OUTCOME to OUTCOME_NOT_CONFIGURED))
            }
            is NextcloudSyncComponent.SyncResult.ConflictRetriesExhausted -> {
                Log.w(TAG, "Sync gave up after repeated conflicts")
                Result.success(workDataOf(KEY_OUTCOME to OUTCOME_CONFLICT))
            }
            is NextcloudSyncComponent.SyncResult.Failure -> {
                Log.w(TAG, "Sync failed: ${result.message}")
                Result.success(workDataOf(
                    KEY_OUTCOME to OUTCOME_FAILURE,
                    KEY_ERROR_MESSAGE to result.message
                ))
            }
        }
    }

    companion object {
        private const val TAG = "NextcloudSyncWorker"
        private const val UNIQUE_PERIODIC_NAME = "nextcloud_sync_periodic"
        private const val UNIQUE_MANUAL_NAME = "nextcloud_sync_manual"

        const val KEY_OUTCOME = "outcome"
        const val KEY_BOOKMARK_COUNT = "bookmarkCount"
        const val KEY_ERROR_MESSAGE = "errorMessage"
        const val OUTCOME_SUCCESS = "success"
        const val OUTCOME_NOT_CONFIGURED = "notConfigured"
        const val OUTCOME_CONFLICT = "conflict"
        const val OUTCOME_FAILURE = "failure"

        fun enqueuePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<NextcloudSyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }

        fun cancelPeriodic(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_PERIODIC_NAME)
        }

        // "Sync now" in NextcloudSyncActivity. Tagged so doWork() can tell a manual run apart from
        // the periodic one and skip the Wi-Fi-only gate - the user tapping the button is itself
        // the opt-in for this one run.
        fun enqueueNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<NextcloudSyncWorker>()
                .addTag(UNIQUE_MANUAL_NAME)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_MANUAL_NAME, ExistingWorkPolicy.REPLACE, request
            )
        }

        fun manualSyncStatus(context: Context): LiveData<List<WorkInfo>> =
            WorkManager.getInstance(context).getWorkInfosForUniqueWorkLiveData(UNIQUE_MANUAL_NAME)
    }
}
