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
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.AttachedDatabases;
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

    public String getAlias() {
        String alias = type;

        if (type.equals("jmdict_translation")) {
            alias = lang;
        } else if (type.equals("tatoeba")) {
            alias = "examples_" + lang;
        }

        return alias;
    }

    public boolean isAttached(RoomDatabase aDB) {
        List<String> attachedDatabases = AttachedDatabases.getAttachedDatabases(aDB);
        String alias = getAlias();
        boolean attached = false;

        for (String n : attachedDatabases) {
            if (n.equals(alias)) {
                attached = true;
            }
        }

        return attached;
    }

    @WorkerThread
    public void attach(RoomDatabase aDB) {
        if (!isAttached(aDB)) {
            SupportSQLiteDatabase db = aDB.getOpenHelper().getWritableDatabase();

            db.execSQL("ATTACH '" + file + "' AS " + getAlias());

            if (BuildConfig.DEBUG_DB_MIGRATION) {
                m_log.info("Attaching " + file + " as " + getAlias());
            }
        }
    }

    public boolean delete() {
        File f = new File(file);

        return f.delete();
    }

    @WorkerThread
    public void detach(RoomDatabase aDB) {
        if (isAttached(aDB)) {
            SupportSQLiteDatabase db = aDB.getOpenHelper().getWritableDatabase();

            db.execSQL("DETACH " + getAlias());

            if (BuildConfig.DEBUG_DB_MIGRATION) {
                m_log.info("Detaching  " + getAlias());
            }
        }
    }
}
