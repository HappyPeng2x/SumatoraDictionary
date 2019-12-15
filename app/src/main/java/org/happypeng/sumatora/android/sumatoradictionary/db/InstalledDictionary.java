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

import android.content.res.AssetManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.recyclerview.widget.DiffUtil;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;

import org.happypeng.sumatora.android.sumatoradictionary.BuildConfig;
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.BaseDictionaryObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.zip.GZIPInputStream;

@Entity(primaryKeys = {"type", "lang"})
public class InstalledDictionary extends BaseDictionaryObject {
    private static String ASSET_PREFIX = "file:///android_asset/";

    @Ignore private Logger m_log;

    public InstalledDictionary() {
        if (BuildConfig.DEBUG_DB_MIGRATION) {
            m_log = LoggerFactory.getLogger(this.getClass());
        }

        type = "";
        lang = "";
    }

    public InstalledDictionary(final String aFile,
                               final String aDescription,
                               @NonNull final String aType,
                               @NonNull final String aLang,
                               int aVersion,
                               int aDate) {
        if (BuildConfig.DEBUG_DB_MIGRATION) {
            m_log = LoggerFactory.getLogger(this.getClass());
        }

        file = aFile;
        description = aDescription;
        type = aType;
        lang = aLang;
        version = aVersion;
        date = aDate;
    }

    public boolean isSame(final InstalledDictionary aDictionary) {
        return (lang.equals(aDictionary.lang) && type.equals(aDictionary.type));
    }

    public boolean isSuperiorVersion(final InstalledDictionary aDictionary) {
        return (version > aDictionary.version ||
                (version >= aDictionary.version && date > aDictionary.date));
    }

    public void attach(RoomDatabase aDB) {
        SupportSQLiteDatabase db = aDB.getOpenHelper().getWritableDatabase();

        String alias = type;

        if (type.equals("jmdict_translation")) {
            alias = lang;
        }

        db.execSQL("ATTACH '" + file + "' AS " + alias);
    }

    /*
    public static List<InstalledDictionary> calculateUpdateList(final List<InstalledDictionary> aInstalledList,
                                                                final List<InstalledDictionary> aAvailableList) {
        LinkedList<InstalledDictionary> updateList = new LinkedList<>();

        for (InstalledDictionary i : aInstalledList) {
            for (InstalledDictionary a : aAvailableList) {
                if (i.isSame(a)) {
                    if (a.isSuperiorVersion(i)) {
                        updateList.add(a);
                    }
                }
            }
        }

        // In the future we should manage the case where several dictionaries are available for an installed dictionary

        return updateList;
    } */





    /*
    public static boolean areItemsTheSame(@NonNull InstalledDictionary aOldItem,
                                          @NonNull InstalledDictionary aNewItem) {
        return (aOldItem.file == null && aNewItem.file == null) ||
                (aOldItem.file != null && aOldItem.file.equals(aNewItem.file));
    }

    public static boolean areContentsTheSame(@NonNull InstalledDictionary aOldItem,
                                             @NonNull InstalledDictionary aNewItem) {
        return aOldItem.type.equals(aNewItem.type) &&
                aOldItem.lang.equals(aNewItem.lang) &&
                aOldItem.version == aNewItem.version &&
                aOldItem.date == aNewItem.date &&
                ((aOldItem.description == null && aNewItem.description == null) ||
                        (aOldItem.description != null && aOldItem.description.equals(aNewItem.description)));

    }

    private final static DiffUtil.ItemCallback<InstalledDictionary> DIFF_UTIL =
            new DiffUtil.ItemCallback<InstalledDictionary>() {
                @Override
                public boolean areItemsTheSame(@NonNull InstalledDictionary oldItem, @NonNull InstalledDictionary newItem) {
                    return InstalledDictionary.areItemsTheSame(oldItem, newItem);
                }

                @Override
                public boolean areContentsTheSame(@NonNull InstalledDictionary oldItem, @NonNull InstalledDictionary newItem) {
                    return InstalledDictionary.areContentsTheSame(oldItem, newItem);
                }
            };

    public static DiffUtil.ItemCallback<InstalledDictionary> getDiffUtil() {
        return DIFF_UTIL;
    }

     */
}
