package org.happypeng.sumatora.android.sumatoradictionary;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.AssetManager;

import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.Cursor;

import android.util.Log;

import android.app.AlertDialog;

import android.content.DialogInterface;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Created by happypeng on 2/1/18.
 */

public class DatabaseTool {
    private static String JMDICT_DB_ASSET = "JMdict.db";
    private static String JMDICT_DB_FILE = "JMdict.db";
    private static String JMDICT_DB_VERSION = "01";

    private static void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }

    private static void gunzipAsset(Context a_context) throws IOException {
        AssetManager am = a_context.getAssets();
        InputStream db_asset = am.open(JMDICT_DB_ASSET);

        FileOutputStream db_file = new FileOutputStream(a_context.getApplicationInfo().dataDir + "/" + JMDICT_DB_FILE);

        copyFile(db_asset, db_file);

        db_file.flush();
        db_file.close();

        db_asset.close();
    }

    private static String getVersion(SQLiteDatabase a_db)
    {
        try {
            String[] cols = {"version"};
            Cursor cur = a_db.query("version", cols, null, null, null, null, null, null);

            if (cur.getCount() == 0) {
                return "";
            }

            cur.moveToNext();

            String ver = cur.getString(0);

            cur.close();

            return ver;
        } catch (SQLiteException e) {
            return "";
        }
    }

    private static SQLiteDatabase loadDBfromFile(Context a_context)
    {
        SQLiteDatabase db = null;

        try {
            db = SQLiteDatabase.openDatabase(a_context.getApplicationInfo().dataDir + "/" + JMDICT_DB_FILE, null, SQLiteDatabase.OPEN_READONLY);

            System.err.println("Database could be opened from file.");
        } catch (SQLiteException e) {
            System.err.println("Database could not be opened from file.");
        }

        return db;
    }

    static SQLiteDatabase getDB(Context a_context)
    {
        SQLiteDatabase db = loadDBfromFile(a_context);

        if (db == null)
            try {
                gunzipAsset(a_context);

                db = loadDBfromFile(a_context);
            } catch (IOException e) {
                System.err.println("Could not gunzip the asset... Printing stack trace.");

                e.printStackTrace();
            }
            // We are loading from asset, no need for version check.
        else if (!getVersion(db).equals(JMDICT_DB_VERSION)) {
            System.err.println("Version mismatch, reloading from asset.");

            db.close();

            db = null;

            // Version mismatch, reloading from asset.

            try {
                gunzipAsset(a_context);

                db = loadDBfromFile(a_context);
            } catch (IOException e) {
                System.err.println("Could not gunzip the asset... Printing stack trace.");

                e.printStackTrace();
            }
        }

        return db;
    }
}
