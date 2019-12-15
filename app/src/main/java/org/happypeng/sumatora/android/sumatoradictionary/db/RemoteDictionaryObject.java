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

package org.happypeng.sumatora.android.sumatoradictionary.db;

import android.app.DownloadManager;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.recyclerview.widget.DiffUtil;
import androidx.room.Entity;

import org.happypeng.sumatora.android.sumatoradictionary.db.tools.BaseDictionaryObject;

import java.io.File;

@Entity(primaryKeys = {"type", "lang"})
public class RemoteDictionaryObject extends BaseDictionaryObject {
    public @NonNull String localFile;
    public long downloadId;

    public RemoteDictionaryObject() {
        super();

        localFile = "";
        downloadId = 0;
    }

    public void setLocalFile(@NonNull String aLocalFile) {
        localFile = aLocalFile;
    }

    public void setDownloadId(long aDownloadId) {
        downloadId = aDownloadId;
    }

    public RemoteDictionaryObject(final @NonNull String aUrl,
                                  final String aDescription,
                                  final @NonNull String aType,
                                  final @NonNull String aLang,
                                  int aVersion,
                                  int aDate) {
        file = aUrl;
        description = aDescription;
        type = aType;
        lang = aLang;
        version = aVersion;
        date = aDate;

        localFile = "";
    }

    @WorkerThread
    public void download(final @NonNull DownloadManager aDownloadManager,
                         final @NonNull File aDownloadDir) {
        File fLocalFile = new File(aDownloadDir,type + "-" + lang + ".db.gz");

        localFile = fLocalFile.getAbsolutePath();

        DownloadManager.Request request=new DownloadManager.Request(Uri.parse(file))
                .setTitle(description)// Title of the Download Notification
                .setDescription("Downloading")// Description of the Download Notification
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)// Visibility of the download Notification
                .setDestinationUri(Uri.fromFile(fLocalFile))// Uri of the destination file
                .setAllowedOverRoaming(false);

        downloadId =  aDownloadManager.enqueue(request);
    }

    public LocalDictionaryObject getLocalDictionaryObject() {
        return new LocalDictionaryObject(this);
    }

    private final static DiffUtil.ItemCallback<RemoteDictionaryObject> DIFF_UTIL =
            new DiffUtil.ItemCallback<RemoteDictionaryObject>() {
                @Override
                public boolean areItemsTheSame(@NonNull RemoteDictionaryObject oldItem, @NonNull RemoteDictionaryObject newItem) {
                    return oldItem.type.equals(newItem.type) &&
                            oldItem.lang.equals(newItem.lang);
                }

                @Override
                public boolean areContentsTheSame(@NonNull RemoteDictionaryObject oldItem, @NonNull RemoteDictionaryObject newItem) {
                    return oldItem.date == newItem.date && oldItem.version == newItem.version &&
                            oldItem.file.equals(newItem.file);
                }
            };

    public static DiffUtil.ItemCallback<RemoteDictionaryObject> getDiffUtil() {
        return DIFF_UTIL;
    }
}
