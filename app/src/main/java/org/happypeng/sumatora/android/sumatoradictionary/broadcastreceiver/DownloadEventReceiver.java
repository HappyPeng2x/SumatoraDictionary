/* Sumatora Dictionary
        Copyright (C) 2019 Nicolas Centa

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

package org.happypeng.sumatora.android.sumatoradictionary.broadcastreceiver;

import android.app.DownloadManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;

import androidx.annotation.NonNull;

import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryAction;
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentDatabase;

import java.util.List;

// Receives notifications for downloads, matches them with actions and performs them
public class DownloadEventReceiver extends BroadcastReceiver {
    private final PersistentDatabase mDB;

    public DownloadEventReceiver(@NonNull final PersistentDatabase aDB) {
        mDB = aDB;
    }

    @Override
    public void onReceive(Context context, final Intent intent) {
        final long downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, 0);

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                mDB.runInTransaction(new Runnable() {
                    @Override
                    public void run() {
                        List<DictionaryAction> actions = mDB.dictionaryActionDao().getAllForDownloadId(downloadId);

                        for (DictionaryAction d: actions) {
                            System.out.println("Matching action: " + d.getType() + " " + d.getLang());

                            d.setDownloadId(0);
                            mDB.dictionaryActionDao().update(d);
                        }
                    }
                });

                return null;
            }
        }.execute();
    }
}
