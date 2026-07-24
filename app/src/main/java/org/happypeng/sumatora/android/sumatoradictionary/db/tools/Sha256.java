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

package org.happypeng.sumatora.android.sumatoradictionary.db.tools;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

// Shared by DictionaryDownloadCompleteReceiver (pack downloads) and DictionaryUpdateChecker
// (changelog.json downloads) - both verify a downloaded file against a sha256 attribute published
// in dictionaries.xml before trusting it.
public final class Sha256 {
    private Sha256() {}

    @WorkerThread
    @NonNull
    public static String hexDigest(File aFile) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        try (InputStream in = new FileInputStream(aFile)) {
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

        return hex.toString();
    }

    // False on any read/digest failure, not just a mismatch - a caller can't tell the difference
    // between "wrong content" and "couldn't check", and should treat both as untrusted.
    @WorkerThread
    public static boolean matches(File aFile, @NonNull String aExpectedHex) {
        try {
            return hexDigest(aFile).equalsIgnoreCase(aExpectedHex);
        } catch (Exception e) {
            return false;
        }
    }
}
