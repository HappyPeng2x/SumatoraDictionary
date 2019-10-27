package org.happypeng.sumatora.android.sumatoradictionary.db;

import android.content.res.AssetManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;

import org.happypeng.sumatora.android.sumatoradictionary.BuildConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.List;

@Entity(primaryKeys = {"type", "lang"})
public class InstalledDictionary {
    private static String ASSET_PREFIX = "file:///android_asset/";

    public String file;
    public String description;
    @NonNull public String type;
    @NonNull public String lang;
    public int version;
    public int date;

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
    }

    private static void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while((read = in.read(buffer)) != -1){
            out.write(buffer, 0, read);
        }
    }

    private static boolean copyAsset(@NonNull final AssetManager aAssetManager, String aName,
                                  File aOutput) {
        try {
            InputStream in = aAssetManager.open(aName);
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

    public boolean install(final AssetManager aAssetManager,
                           final String aDatabaseDir,
                           final InstalledDictionaryDao aDao) {
        if (file == null) {
            return false;
        }

        if (file.startsWith(ASSET_PREFIX)) {
            if (aAssetManager == null) {
                return false;
            }

            File sourceFile = new File(file.substring(ASSET_PREFIX.length()));
            String fileName = sourceFile.getName();

            File destFile = new File(aDatabaseDir, fileName);

            if (copyAsset(aAssetManager,
                    sourceFile.toString(),
                    destFile)) {
                InstalledDictionary insertDir = new InstalledDictionary(destFile.toString(),
                        description, type, lang, version, date);

                aDao.insert(insertDir);

                if (BuildConfig.DEBUG_DB_MIGRATION) {
                    m_log.info("Successfully installed dictionary " +
                            destFile);
                }

                return true;
            }
        }

        if (BuildConfig.DEBUG_DB_MIGRATION) {
            m_log.info("Failed to install dictionary " + file);
        }

        // Other features not supported at the moment

        return false;
    }

    public static List<InstalledDictionary> fromXML(final InputStream aStream) {
        List<InstalledDictionary> result = new LinkedList<>();

        try {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            XmlPullParser xpp = factory.newPullParser();

            xpp.setInput(aStream, null);

            int eventType = xpp.getEventType();
            int level = 0;
            String parent = null;

            while (eventType != XmlPullParser.END_DOCUMENT) {
                if(eventType == XmlPullParser.START_TAG) {
                    level++;

                    if (xpp.getName().equals("repository")) {
                        parent = "repository";
                    }

                    if (level == 2 && parent != null && parent.equals("repository") &&
                            xpp.getName().equals("dictionary")) {
                        result.add(new InstalledDictionary(xpp.getAttributeValue(null, "uri"),
                                xpp.getAttributeValue(null, "description"),
                                xpp.getAttributeValue(null, "type"),
                                xpp.getAttributeValue(null, "lang"),
                                Integer.valueOf(xpp.getAttributeValue(null, "version")),
                                Integer.valueOf(xpp.getAttributeValue(null, "date"))));
                    }
                } else if(eventType == XmlPullParser.END_TAG) {
                    level--;
                }

                eventType = xpp.next();
            }
        } catch (Exception e) {
            e.printStackTrace();

            return null;
        }

        return result;
    }
}
