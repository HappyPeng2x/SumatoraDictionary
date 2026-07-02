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
import androidx.room.Entity;

import org.happypeng.sumatora.android.sumatoradictionary.db.tools.BaseDictionaryObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;

@Entity(primaryKeys = {"type", "lang"})
public class AssetDictionaryObject extends BaseDictionaryObject {
    public AssetDictionaryObject() {
        super();
    }

    public AssetDictionaryObject(final @NonNull String aFile,
                          final String aDescription,
                          final @NonNull String aType,
                          final @NonNull String aLang,
                          int aVersion,
                          int aDate) {
        file = aFile;
        description = aDescription;
        type = aType;
        lang = aLang;
        version = aVersion;
        date = aDate;
    }

    @WorkerThread
    private static boolean copyAsset(@NonNull final AssetManager aAssetManager, String aName,
                                     File aOutput, boolean aGzip) {
        try {
            InputStream raw = aAssetManager.open(aName);
            InputStream in = aGzip ? new GZIPInputStream(raw) : raw;
            OutputStream out = new FileOutputStream(aOutput);
            copyFile(in, out);
            in.close();
            in = null;
            out.flush();
            out.close();
            out = null;

            return true;
        } catch(IOException e) {
            Log.e("tag", "Failed to copy asset file: " + aName, e);
        }

        return false;
    }

    @WorkerThread
    public boolean install(final AssetManager aAssetManager,
                           final String aDatabaseDir,
                           final InstalledDictionaryDao aDao) {
        if (aAssetManager == null) {
            return false;
        }

        File sourceFile = new File(file);
        String sourceName = sourceFile.getName();
        boolean isGzip = sourceName.endsWith(".gz");
        // Destination is always a plain .db file (strip .gz if present)
        String destName = isGzip ? sourceName.substring(0, sourceName.length() - 3) : sourceName;

        File destFile = new File(aDatabaseDir, destName);

        // Try the asset name as listed in the URI first.
        // If that fails, try the alternative: AAPT may strip .gz from gzipped assets
        // (making them accessible as plain .db), or may keep them as .gz requiring decompression.
        boolean success = copyAsset(aAssetManager, sourceFile.toString(), destFile, isGzip);
        if (!success) {
            if (isGzip) {
                // AAPT stripped .gz: asset stored as plain bytes under the non-.gz name
                String plainName = sourceFile.toString();
                plainName = plainName.substring(0, plainName.length() - 3);
                success = copyAsset(aAssetManager, plainName, destFile, false);
            } else {
                // Asset stored gzipped under a .gz name; decompress on copy
                success = copyAsset(aAssetManager, sourceFile.toString() + ".gz", destFile, true);
            }
        }

        if (success) {
            InstalledDictionary insertDir = new InstalledDictionary(destFile.toString(),
                    description, type, lang, version, date);

            aDao.insert(insertDir);

            return true;
        }

        return false;
    }
}
