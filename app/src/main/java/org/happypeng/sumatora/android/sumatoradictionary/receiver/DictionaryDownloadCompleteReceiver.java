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
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.util.Log;

import androidx.annotation.WorkerThread;

import org.happypeng.sumatora.android.sumatoradictionary.R;
import org.happypeng.sumatora.android.sumatoradictionary.component.PersistentDatabaseComponent;
import org.happypeng.sumatora.android.sumatoradictionary.db.InstalledDictionary;
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentDatabase;
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentDatabaseParameters;
import org.happypeng.sumatora.android.sumatoradictionary.db.RemoteDictionaryObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.List;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

// Installs a downloaded dictionary pack once its DownloadManager download finishes - covers both
// a brand-new optional pack (suffix/names, see DictionariesManagementActivity) and a background
// update to an already-installed pack (see update-pipeline.md, DictionaryUpdateWorker). Manifest-
// registered so this fires even if the app process was killed while the download ran (DownloadManager
// itself is a system service and survives that).
@AndroidEntryPoint
public class DictionaryDownloadCompleteReceiver extends BroadcastReceiver {
    private static final String TAG = "DictDownloadReceiver";
    private static final String UPDATE_CHANNEL_ID = "dictionary_updates";

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
            if (!succeeded) {
                Log.e(TAG, "Download failed for " + remote.type + "/" + remote.lang);
            } else if (!verifyChecksum(remote)) {
                Log.e(TAG, "Checksum mismatch for " + remote.type + "/" + remote.lang + ", discarding download");
            } else {
                install(context, db, remote);
            }

            if (!remote.localFile.isEmpty()) {
                new File(remote.localFile).delete();
            }
            db.remoteDictionaryObjectDao().delete(remote);
        }
    }

    @WorkerThread
    private void install(Context context, PersistentDatabase db, RemoteDictionaryObject remote) {
        File databaseRoot =
                context.getDatabasePath(PersistentDatabaseParameters.PERSISTENT_DATABASE_NAME).getParentFile();
        File installDir = new File(databaseRoot, "dictionaries");
        if (!installDir.exists()) {
            installDir.mkdirs();
        }

        InstalledDictionary existing = db.installedDictionaryDao().getForTypeLang(remote.type, remote.lang);

        if (existing == null) {
            // Brand-new pack (e.g. an optional suffix/names install) - nothing is attached yet for
            // this type/lang, so it's safe to install and attach immediately.
            InstalledDictionary installed = remote.getLocalDictionaryObject().install(installDir);
            if (installed == null) {
                Log.e(TAG, "Failed to decompress downloaded pack for " + remote.type + "/" + remote.lang);
                return;
            }

            db.installedDictionaryDao().insert(installed);
            installed.attach(db);
            return;
        }

        // Updating a pack that may already be ATTACHed to the live connection - decompress under a
        // version-suffixed name and defer the swap to the next cold start (see update-pipeline.md,
        // PersistentDatabaseInitialization's pending-update promotion step).
        InstalledDictionary pending = remote.getLocalDictionaryObject().install(installDir, true);
        if (pending == null) {
            Log.e(TAG, "Failed to decompress downloaded update for " + remote.type + "/" + remote.lang);
            return;
        }

        existing.pendingFile = pending.file;
        existing.pendingVersion = pending.version;
        existing.pendingDate = pending.date;
        db.installedDictionaryDao().insert(existing);

        postUpdateReadyNotification(context, remote);
    }

    @WorkerThread
    private boolean verifyChecksum(RemoteDictionaryObject remote) {
        if (remote.sha256.isEmpty()) {
            // No checksum published for this entry yet - trust DownloadManager's success status.
            return true;
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            try (InputStream in = new FileInputStream(remote.localFile)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }

            StringBuilder hex = new StringBuilder();
            for (byte b : digest.digest()) {
                hex.append(String.format("%02x", b));
            }

            return hex.toString().equalsIgnoreCase(remote.sha256);
        } catch (Exception e) {
            Log.e(TAG, "Failed to verify checksum for " + remote.type + "/" + remote.lang, e);
            return false;
        }
    }

    @WorkerThread
    private void postUpdateReadyNotification(Context context, RemoteDictionaryObject remote) {
        NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
        if (notificationManager == null) {
            return;
        }

        notificationManager.createNotificationChannel(new NotificationChannel(
                UPDATE_CHANNEL_ID, "Dictionary updates", NotificationManager.IMPORTANCE_DEFAULT));

        Notification notification = new Notification.Builder(context, UPDATE_CHANNEL_ID)
                .setContentTitle(context.getString(R.string.dictionary_update_ready_title))
                .setContentText(context.getString(R.string.dictionary_update_ready_text, remote.description))
                .setSmallIcon(R.drawable.ic_sumatora_icon)
                .setAutoCancel(true)
                .build();

        notificationManager.notify(remote.type.hashCode() ^ remote.lang.hashCode(), notification);
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
