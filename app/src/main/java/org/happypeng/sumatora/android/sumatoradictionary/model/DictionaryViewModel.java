package org.happypeng.sumatora.android.sumatoradictionary.model;

import android.app.Activity;
import android.database.DatabaseUtils;
import android.os.AsyncTask;
import android.util.Log;
import android.util.TimingLogger;
import android.util.Xml;

import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryControlDao;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryEntry;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryEntryDao;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Arrays;

import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;
import androidx.paging.LivePagedListBuilder;
import androidx.paging.PagedList;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;

public class DictionaryViewModel extends ViewModel {
    public LiveData<PagedList<DictionaryEntry>> searchEntries;
    public LiveData<Long> imported;
    public LiveData<Long> date;

    public DictionaryViewModel() {
    }

    public void init(DictionaryControlDao controlDao) {
        imported = controlDao.get("imported");
        date = controlDao.get("date");
    }

    public boolean updateDictionaryEntriesFromAssets(final RoomDatabase aDB, final Activity aActivity,
                                                     final DictionaryControlDao aControlDao,
                                                     final DictionaryEntryDao aEntryDao) {

        if (date == null) { // Need to initialize before running
            return false;
        }

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {

                aEntryDao.deleteAll();
                aControlDao.set("date", 0);

                aControlDao.set("imported", 0);

                InputStream stream = null;
                XmlPullParser parser = Xml.newPullParser();

                try {
                    stream = aActivity.getAssets().open("JMdict.xml");
                    parser.setInput(aActivity.getAssets().open("JMdict.xml"), "utf-8");

                    int eventType = parser.getEventType();
                    boolean endParsing = false;
                    Long newDate = null;

                    String name;
                    Long seq = null;
                    String readings = "";
                    String writings = "";
                    String sense = "";
                    String lang = "";

                    DictionaryEntry[] buffer = null;
                    int bufferPos = 0;

                    while (eventType != XmlPullParser.END_DOCUMENT && !endParsing) {
                        eventType = parser.next();

                        switch (eventType) {
                            case XmlPullParser.START_DOCUMENT:
                                //System.out.println("Start processing XML...");
                                break;
                            case XmlPullParser.START_TAG:
                                name = parser.getName();

                                if (name.equals("dict")) {
                                    String xmlDate = parser.getAttributeValue("", "date");

                                    if (date.getValue() != null &&
                                            Long.valueOf(xmlDate) <= date.getValue()) {
                                        endParsing = true;
                                    } else {
                                        newDate = Long.valueOf(xmlDate);
                                        aEntryDao.deleteAll();
                                        buffer = new DictionaryEntry[20000];
                                    }
                                } else if (name.equals("entry")) {
                                    seq = Long.valueOf(parser.getAttributeValue("", "seq"));
                                    writings = parser.getAttributeValue("", "readings");
                                    readings = parser.getAttributeValue("", "writings");
                                } else if (name.equals("sense")) {
                                    lang = parser.getAttributeValue("", "lang");
                                    sense = "";
                                }

                                break;
                            case XmlPullParser.TEXT:
                                sense = sense + parser.getText();
                                break;
                            case XmlPullParser.END_TAG:
                                name = parser.getName();

                                if (name.equals("sense")) {
                                    // Insert entry in DB
                                    DictionaryEntry entry = new DictionaryEntry();

                                    entry.seq = seq;
                                    entry.writings = writings;
                                    entry.readings = readings;
                                    entry.lang = lang;
                                    entry.gloss = sense;
                                    entry.bookmark = "";

                                    if (bufferPos < 20000) {
                                        buffer[bufferPos] = entry;
                                        bufferPos = bufferPos + 1;
                                    } else {
                                        aEntryDao.insertMany(buffer);

                                        Arrays.fill(buffer, null);
                                        bufferPos = 0;
                                    }
                                } else if (name.equals("entry")) {
                                    seq = null;
                                    writings = "";
                                    readings = "";
                                    lang = "";
                                    sense = "";
                                }

                                break;
                            case XmlPullParser.END_DOCUMENT:
                                //System.out.println("End processing XML...");

                                for (DictionaryEntry insertEntry : buffer) {
                                    if (insertEntry != null) {
                                        aEntryDao.insert(insertEntry);
                                    }
                                }

                                aEntryDao.insertMany(Arrays.copyOf(buffer, bufferPos));

                                Arrays.fill(buffer, null);
                                bufferPos = 0;

                                aControlDao.set("date", newDate);
                                aControlDao.set("imported", 1);

                                break;
                        }
                    }
                } catch (Exception e) {
                    System.err.println(e.toString());
                }

                if (stream != null) {
                    try {
                        stream.close();
                    } catch (Exception e) {
                        System.err.println(e.toString());
                    }
                }

                return null;
            }
        }.execute();

        return true;
    }

    public LiveData<PagedList<DictionaryEntry>> search(LifecycleOwner owner, DictionaryEntryDao entryDao, String expr, String lang) {
        if (searchEntries != null) {
            searchEntries.removeObservers(owner);
            searchEntries = null;
        }

        PagedList.Config pagedListConfig =
                (new PagedList.Config.Builder()).setEnablePlaceholders(true)
                        .setPrefetchDistance(10)
                        .setPageSize(20).build();

        searchEntries = (new LivePagedListBuilder(entryDao.search(expr, lang), pagedListConfig))
                .build();

        return searchEntries;
    }

    public void searchReset(LifecycleOwner owner) {
        if (searchEntries != null) {
            searchEntries.removeObservers(owner);
            searchEntries = null;
        }
    }
}
