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

package org.happypeng.sumatora.android.sumatoradictionary.xml;

import android.util.Xml;

import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchElement;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;
import org.xmlpull.v1.XmlSerializer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;

public class DictionaryBookmarkXML {
    public static void writeXML(File aOutputFile, List<DictionarySearchElement> aBookmarks) throws IOException {
        FileOutputStream fos = new FileOutputStream(aOutputFile);
        XmlSerializer serializer = Xml.newSerializer();

        serializer.setFeature("http://xmlpull.org/v1/doc/features.html#indent-output", true);

        serializer.setOutput(fos, "utf-8");

        serializer.startDocument("utf-8", true);
        serializer.startTag(null, "bookmarks");

        for (DictionarySearchElement ele : aBookmarks) {
            serializer.startTag(null, "bookmark");
            serializer.attribute(null, "seq", Long.toString(ele.getSeq()));

            serializer.endTag(null, "bookmark");
        }

        serializer.endTag(null, "bookmarks");
        serializer.endDocument();

        serializer.flush();

        fos.close();
    }

    public static List<Long> readXML(InputStream aStream) {
        List<Long> result = new LinkedList<Long>();

        try {
            XmlPullParserFactory factory = XmlPullParserFactory.newInstance();
            XmlPullParser xpp = factory.newPullParser();

            xpp.setInput(aStream, null);

            boolean documentStart = false;
            boolean bookmarksOpen = false;

            int eventType = xpp.getEventType();

            while (eventType != XmlPullParser.END_DOCUMENT) {
                if(eventType == XmlPullParser.START_DOCUMENT) {
                    documentStart = true;
                } else if(eventType == XmlPullParser.START_TAG) {
                    if (documentStart && !bookmarksOpen) {
                        if (xpp.getName().equals("bookmarks")) {
                            bookmarksOpen = true;
                        } else {
                            System.err.println("Unkown tag: " + xpp.getName());
                            return null;
                        }
                    } else if (bookmarksOpen) {
                        if (xpp.getName().equals("bookmark")) {
                            String seqStr = xpp.getAttributeValue(null, "seq");

                            if (seqStr != null) {
                                result.add(Long.valueOf(seqStr));
                            } else {
                                System.err.println("seq attribute not found for line " + xpp.getLineNumber());
                            }
                        } else {
                            System.err.println("Unkown tag: " + xpp.getName());
                            return null;
                        }
                    }
                } else if(eventType == XmlPullParser.END_TAG) {
                    if (bookmarksOpen && xpp.getName().equals("bookmarks")) {
                        bookmarksOpen = false;
                    }
                }

                eventType = xpp.next();
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println(e.toString());

            return null;
        }

        return result;
    }
}
