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

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.room.Entity;

import org.happypeng.sumatora.android.sumatoradictionary.db.tools.BaseDictionaryObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;

@Entity(primaryKeys = {"type", "lang"})
public class LocalDictionaryObject extends BaseDictionaryObject {
    public LocalDictionaryObject() {
        super();
    }

    public LocalDictionaryObject(final RemoteDictionaryObject aRemoteDictionaryObject) {
        description = aRemoteDictionaryObject.description;
        type = aRemoteDictionaryObject.type;
        lang = aRemoteDictionaryObject.lang;
        version = aRemoteDictionaryObject.version;
        date = aRemoteDictionaryObject.date;
        file = aRemoteDictionaryObject.localFile;
    }

    private static boolean copyGZipFile(File aInput, File aOutput) {
        try {
            GZIPInputStream in = new GZIPInputStream(new FileInputStream(aInput));
            OutputStream out = new FileOutputStream(aOutput);

            copyFile(in, out);

            in.close();
            in = null;

            out.flush();
            out.close();
            out = null;

            return true;
        } catch (Exception e) {
            Log.e("tag", "Failed to copy downloaded file: " + aInput.getPath(), e);
        }

        return false;
    }

    public InstalledDictionary install(final File aDatabaseDir) {
        File sourceFile = new File(file);
        File destFile = new File(aDatabaseDir, type + "-" + lang + ".db");

        if (copyGZipFile(sourceFile, destFile)) {
            return new InstalledDictionary(destFile.toString(),
                    description, type, lang, version, date);

        }

        return null;
    }
}
