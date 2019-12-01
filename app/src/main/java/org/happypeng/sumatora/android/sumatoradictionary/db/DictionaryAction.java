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

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.room.Embedded;
import androidx.room.Entity;
import androidx.room.Ignore;

@Entity(primaryKeys = {"type", "lang"})
public class DictionaryAction {
    public static final int DICTIONARY_ACTION_UNINITIALIZED = 0;
    public static final int DICTIONARY_ACTION_DELETE = 1;
    public static final int DICTIONARY_ACTION_DOWNLOAD = 2;
    public static final int DICTIONARY_ACTION_UPDATE = 3;
    public static final int DICTIONARY_ACTION_VERSION = 8;
    public static final int DICTIONARY_ACTION_MISMATCH = 9;

    @NonNull String type;
    @NonNull String lang;

    @Embedded(prefix = "installed") InstalledDictionary installedDictionary;
    @Embedded(prefix = "download") InstalledDictionary downloadDictionary;

    int action;

    public void setDownloadId(long downloadId) {
        this.downloadId = downloadId;
    }

    long downloadId;

    @Ignore
    public long getDownloadId() {
        return downloadId;
    }

    @Ignore
    public int getAction() {
        return action;
    }

    public void setDownloadDictionary(InstalledDictionary downloadDictionary) {
        this.downloadDictionary = downloadDictionary;
    }

    @Ignore
    @NonNull
    public String getType() {
        return type;
    }

    @Ignore
    @NonNull
    public String getLang() {
        return lang;
    }

    @Ignore
    public InstalledDictionary getInstalledDictionary() {
        return installedDictionary;
    }

    @Ignore
    public InstalledDictionary getDownloadDictionary() {
        return downloadDictionary;
    }

    @Ignore
    public int getAppVersion() {
        return appVersion;
    }

    int appVersion;

    public DictionaryAction(final InstalledDictionary aInstalledDictionary,
                            final InstalledDictionary aDownloadDictionary,
                            @NonNull final String aType, @NonNull final String aLang,
                            int aAppVersion) {
        installedDictionary = aInstalledDictionary;
        downloadDictionary = aDownloadDictionary;
        type = aType;
        lang = aLang;
        appVersion = aAppVersion;
        action = DICTIONARY_ACTION_UNINITIALIZED;
    }

    public DictionaryAction() {
        type = "";
        lang = "";
    }

    public void calculateAction() {
        if (installedDictionary == null &&
                downloadDictionary == null) {
            action = DICTIONARY_ACTION_MISMATCH;

            return;
        }

        if (installedDictionary != null) {
            if (installedDictionary.version != appVersion) {
                action = DICTIONARY_ACTION_VERSION;
                return;
            }

            if (!type.equals(installedDictionary.type) ||
                    !lang.equals(installedDictionary.lang)) {
                action = DICTIONARY_ACTION_MISMATCH;

                return;
            }

            if (downloadDictionary != null) {
                if (downloadDictionary.version != appVersion) {
                    action = DICTIONARY_ACTION_VERSION;

                    return;
                }

                if (downloadDictionary.date > installedDictionary.date) {
                    action = DICTIONARY_ACTION_UPDATE;

                    return;
                }
            }

            action = DICTIONARY_ACTION_DELETE;

            return;
        }

        if (downloadDictionary.version != appVersion) {
            action = DICTIONARY_ACTION_VERSION;

            return;
        }

        action = DICTIONARY_ACTION_DOWNLOAD;
    }

    private final static DiffUtil.ItemCallback<DictionaryAction> DIFF_UTIL =
            new DiffUtil.ItemCallback<DictionaryAction>() {
                @Override
                public boolean areItemsTheSame(@NonNull DictionaryAction oldItem, @NonNull DictionaryAction newItem) {
                    return oldItem.type.equals(newItem.type) &&
                            oldItem.lang.equals(newItem.lang);
                }

                @Override
                public boolean areContentsTheSame(@NonNull DictionaryAction oldItem, @NonNull DictionaryAction newItem) {
                    InstalledDictionary oldItemDownloadDictionary = oldItem.downloadDictionary;
                    InstalledDictionary newItemDownloadDictionary = newItem.downloadDictionary;
                    InstalledDictionary oldItemInstalledDictionary = oldItem.installedDictionary;
                    InstalledDictionary newItemInstalledDictionary = newItem.installedDictionary;

                    return ((oldItemDownloadDictionary == null && newItemDownloadDictionary == null) ||
                            (oldItemDownloadDictionary != null && newItemDownloadDictionary != null &&
                                    InstalledDictionary.areContentsTheSame(oldItemDownloadDictionary, newItemDownloadDictionary))) &&
                            ((oldItemInstalledDictionary == null && newItemInstalledDictionary == null) ||
                                    (oldItemInstalledDictionary != null && newItemInstalledDictionary != null &&
                                            InstalledDictionary.areContentsTheSame(oldItemInstalledDictionary, newItemInstalledDictionary)));
                }
            };

    public static DiffUtil.ItemCallback<DictionaryAction> getDiffUtil() {
        return DIFF_UTIL;
    }
}
