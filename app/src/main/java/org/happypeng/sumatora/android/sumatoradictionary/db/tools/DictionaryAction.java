package org.happypeng.sumatora.android.sumatoradictionary.db.tools;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;

import org.happypeng.sumatora.android.sumatoradictionary.db.InstalledDictionary;

public class DictionaryAction {
    public static final int DICTIONARY_ACTION_DELETE = 1;
    public static final int DICTIONARY_ACTION_DOWNLOAD = 2;
    public static final int DICTIONARY_ACTION_UPDATE = 3;
    public static final int DICTIONARY_ACTION_VERSION = 8;
    public static final int DICTIONARY_ACTION_MISMATCH = 9;

    private InstalledDictionary mInstalledDictionary;
    private InstalledDictionary mDownloadDictionary;
    private final @NonNull String mType;
    private final @NonNull String mLang;
    private int mAppVersion;

    public DictionaryAction(final InstalledDictionary aInstalledDictionary,
                            final InstalledDictionary aDownloadDictionary,
                            @NonNull final String aType, @NonNull final String aLang,
                            int aAppVersion) {
        mInstalledDictionary = aInstalledDictionary;
        mDownloadDictionary = aDownloadDictionary;
        mType = aType;
        mLang = aLang;
        mAppVersion = aAppVersion;
    }

    public int getAction() {
        if (mInstalledDictionary == null &&
            mDownloadDictionary == null) {
            return DICTIONARY_ACTION_MISMATCH;
        }

        if (mInstalledDictionary != null) {
            if (mInstalledDictionary.version != mAppVersion) {
                return DICTIONARY_ACTION_VERSION;
            }

            if (!mType.equals(mInstalledDictionary.type) ||
                !mLang.equals(mInstalledDictionary.lang)) {
                return DICTIONARY_ACTION_MISMATCH;
            }

            if (mDownloadDictionary != null) {
                if (mDownloadDictionary.version != mAppVersion) {
                    return DICTIONARY_ACTION_VERSION;
                }

                if (mDownloadDictionary.date > mInstalledDictionary.date) {
                    return DICTIONARY_ACTION_UPDATE;
                }
            }

            return DICTIONARY_ACTION_DELETE;
        }

        if (mDownloadDictionary.version != mAppVersion) {
            return DICTIONARY_ACTION_VERSION;
        }

        return DICTIONARY_ACTION_DOWNLOAD;
    }

    public InstalledDictionary getInstalledDictionary() {
        return mInstalledDictionary;
    }

    public void setInstalledDictionary(final InstalledDictionary aInstalledDictionary) {
        mInstalledDictionary = aInstalledDictionary;
    }

    public InstalledDictionary getDownloadDictionary() {
        return mDownloadDictionary;
    }

    public void setDownloadDictionary(final InstalledDictionary aDownloadDictionary) {
        mDownloadDictionary = aDownloadDictionary;
    }

    public String getType() {
        return mType;
    }

    public String getLang() {
        return mLang;
    }

    private final static DiffUtil.ItemCallback<DictionaryAction> DIFF_UTIL =
            new DiffUtil.ItemCallback<DictionaryAction>() {
                @Override
                public boolean areItemsTheSame(@NonNull DictionaryAction oldItem, @NonNull DictionaryAction newItem) {
                    return oldItem.getType().equals(newItem.getType()) &&
                            oldItem.getLang().equals(newItem.getLang());
                }

                @Override
                public boolean areContentsTheSame(@NonNull DictionaryAction oldItem, @NonNull DictionaryAction newItem) {
                    InstalledDictionary oldItemDownloadDictionary = oldItem.getDownloadDictionary();
                    InstalledDictionary newItemDownloadDictionary = newItem.getDownloadDictionary();
                    InstalledDictionary oldItemInstalledDictionary = oldItem.getInstalledDictionary();
                    InstalledDictionary newItemInstalledDictionary = newItem.getInstalledDictionary();

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
