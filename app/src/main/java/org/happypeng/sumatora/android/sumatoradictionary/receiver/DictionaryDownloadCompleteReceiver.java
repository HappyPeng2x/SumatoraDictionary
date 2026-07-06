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

package org.happypeng.sumatora.android.sumatoradictionary.receiver;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.util.Log;

import androidx.annotation.WorkerThread;

import org.happypeng.sumatora.android.sumatoradictionary.component.PersistentDatabaseComponent;
import org.happypeng.sumatora.android.sumatoradictionary.db.InstalledDictionary;
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentDatabase;
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentDatabaseParameters;
import org.happypeng.sumatora.android.sumatoradictionary.db.RemoteDictionaryObject;

import java.io.File;
import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

// Installs an optional dictionary pack (search_suffix/names, see DictionariesManagementActivity)
// once its DownloadManager download finishes. Manifest-registered so this fires even if the app
// process was killed while the download ran (DownloadManager itself is a system service and
// survives that). The InstalledDictionary row is inserted either way; live-attaching it here is
// just an optimization for "app already running" - if this receiver runs in a short-lived
// separate process, the next app launch's normal attach loop (PersistentDatabaseInitialization)
// picks up the persisted row regardless.
@AndroidEntryPoint
public class DictionaryDownloadCompleteReceiver extends BroadcastReceiver {
    private static final String TAG = "DictDownloadReceiver";

    @Inject
    PersistentDatabaseComponent persistentDatabaseComponent;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!DownloadManager.ACTION_DOWNLOAD_COMPLETE.equals(intent.getAction())) {
            return;
        }

        long downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1);
        if (downloadId < 0) {
            return;
        }

        final PendingResult pendingResult = goAsync();
        final Context appContext = context.getApplicationContext();

        new Thread(() -> {
            try {
                handleDownloadComplete(appContext, downloadId);
            } catch (Exception e) {
                Log.e(TAG, "Failed to handle completed download " + downloadId, e);
            } finally {
                pendingResult.finish();
            }
        }).start();
    }

    @WorkerThread
    private void handleDownloadComplete(Context context, long downloadId) {
        final DownloadManager downloadManager =
                (DownloadManager) context.getSystemService(Context.DOWNLOAD_SERVICE);
        final boolean succeeded = downloadSucceeded(downloadManager, downloadId);

        final PersistentDatabase db = persistentDatabaseComponent.getDatabase();
        final List<RemoteDictionaryObject> matches =
                db.remoteDictionaryObjectDao().getAllForDownloadId(downloadId);

        for (RemoteDictionaryObject remote : matches) {
            if (succeeded) {
                install(context, db, remote);
            } else {
                Log.e(TAG, "Download failed for " + remote.type + "/" + remote.lang);
            }

            if (!remote.localFile.isEmpty()) {
                new File(remote.localFile).delete();
            }
            db.remoteDictionaryObjectDao().delete(remote);
        }
    }

    @WorkerThread
    private void install(Context context, PersistentDatabase db, RemoteDictionaryObject remote) {
        File databaseRoot = context.getDatabasePath(PersistentDatabaseParameters.DATABASE_NAME).getParentFile();
        File installDir = new File(databaseRoot, "dictionaries");
        if (!installDir.exists()) {
            installDir.mkdirs();
        }

        InstalledDictionary installed = remote.getLocalDictionaryObject().install(installDir);
        if (installed == null) {
            Log.e(TAG, "Failed to decompress downloaded pack for " + remote.type + "/" + remote.lang);
            return;
        }

        db.installedDictionaryDao().insert(installed);
        installed.attach(db);
    }

    @WorkerThread
    private boolean downloadSucceeded(DownloadManager downloadManager, long downloadId) {
        DownloadManager.Query query = new DownloadManager.Query().setFilterById(downloadId);
        try (Cursor cur = downloadManager.query(query)) {
            if (cur == null || !cur.moveToFirst()) {
                return false;
            }
            int statusIdx = cur.getColumnIndex(DownloadManager.COLUMN_STATUS);
            return statusIdx >= 0 && cur.getInt(statusIdx) == DownloadManager.STATUS_SUCCESSFUL;
        }
    }
}
