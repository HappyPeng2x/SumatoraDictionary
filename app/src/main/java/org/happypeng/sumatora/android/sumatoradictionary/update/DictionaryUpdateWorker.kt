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
        along with this program.  If not, see <http://www.gnu.org/licenses/>. */

package org.happypeng.sumatora.android.sumatoradictionary.update

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
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
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import org.happypeng.sumatora.android.sumatoradictionary.R
import org.happypeng.sumatora.android.sumatoradictionary.component.PersistentDatabaseComponent
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.Settings
import java.util.concurrent.TimeUnit

// Background half of update-pipeline.md: periodically (and on manual "Check Now") fetches the
// dictionary manifest and enqueues downloads for any already-installed pack that's out of date.
// Installing the result happens later, in DictionaryDownloadCompleteReceiver, once each download
// finishes - this worker only decides what's worth fetching.
@HiltWorker
class DictionaryUpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val persistentDatabaseComponent: PersistentDatabaseComponent
) : Worker(context, params) {

    override fun doWork(): Result {
        val db = persistentDatabaseComponent.database
        val manifestUrl = db.persistentSettingsDao().getValueDirect(Settings.REPOSITORY_URL)
            ?: applicationContext.getString(R.string.dictionaries_url)

        val enqueued = DictionaryUpdateChecker.checkAndEnqueue(applicationContext, db, manifestUrl)
        Log.i(TAG, "Dictionary update check complete, enqueued $enqueued download(s)")

        _lastManualResult.postValue(enqueued)

        // A manifest fetch failure (no network, host unreachable) isn't a worker failure worth
        // retrying aggressively - the next periodic run will just try again.
        return Result.success()
    }

    companion object {
        private const val TAG = "DictionaryUpdateWorker"
        private const val UNIQUE_PERIODIC_NAME = "dictionary_update_check"
        private const val UNIQUE_MANUAL_NAME = "dictionary_update_check_manual"

        // Exposed so DictionariesManagementActivity can show a toast after a manual check
        // completes. Null means "no check has run yet" — enqueueNow() resets it so the observer
        // can distinguish a fresh run from a stale cached value. The periodic worker also posts
        // here, but since it runs in the background with no observer, that's harmless.
        private val _lastManualResult = MutableLiveData<Int>()
        val lastManualResult: LiveData<Int> = _lastManualResult

        @JvmStatic
        fun enqueuePeriodic(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.UNMETERED)
                .setRequiresBatteryNotLow(true)
                .build()

            val request = PeriodicWorkRequestBuilder<DictionaryUpdateWorker>(7, TimeUnit.DAYS)
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_PERIODIC_NAME, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }

        // "Check Now" in Settings, and the one-time catch-up fired from
        // PersistentDatabaseComponent when the APK version changes - allowed over metered
        // connections since both are either user-initiated or a rare one-off.
        @JvmStatic
        fun enqueueNow(context: Context) {
            // Reset so the observer in DictionariesManagementActivity can distinguish a fresh
            // run from a stale cached value (null = "not yet run this cycle").
            _lastManualResult.value = null

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = OneTimeWorkRequestBuilder<DictionaryUpdateWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_MANUAL_NAME, ExistingWorkPolicy.REPLACE, request
            )
        }

        // Lets Settings/DictionariesManagementActivity show progress for a manual check without
        // this class needing to know anything about that UI.
        fun manualCheckStatus(context: Context): LiveData<List<WorkInfo>> =
            WorkManager.getInstance(context).getWorkInfosForUniqueWorkLiveData(UNIQUE_MANUAL_NAME)
    }
}
