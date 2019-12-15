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

package org.happypeng.sumatora.android.sumatoradictionary.db.tools;

import androidx.annotation.NonNull;

import org.happypeng.sumatora.android.sumatoradictionary.db.AssetDictionaryObject;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.LinkedList;
import java.util.List;

public class BaseDictionaryObject {
    public String description;
    @NonNull public String type;
    @NonNull public String lang;
    public int version;
    public int date;
    public @NonNull String file;

    public BaseDictionaryObject() {
        type = "";
        lang = "";
        file = "";
    }

    @NonNull
    public String getType() {
        return type;
    }

    @NonNull
    public String getLang() {
        return lang;
    }

    protected static void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while((read = in.read(buffer)) != -1){
            out.write(buffer, 0, read);
        }
    }

    public static boolean areContentsTheSame(@NonNull BaseDictionaryObject aOldItem,
                                             @NonNull BaseDictionaryObject aNewItem) {
        return aOldItem.type.equals(aNewItem.type) &&
                aOldItem.lang.equals(aNewItem.lang) &&
                aOldItem.version == aNewItem.version &&
                aOldItem.date == aNewItem.date &&
                ((aOldItem.description == null && aNewItem.description == null) ||
                        (aOldItem.description != null && aOldItem.description.equals(aNewItem.description)));

    }

    public interface Constructor<T extends BaseDictionaryObject> {
        public T create(final @NonNull String aFile,
                        final String aDescription,
                        final @NonNull String aType,
                        final @NonNull String aLang,
                        int aVersion,
                        int aDate);
    }

    public static <T extends BaseDictionaryObject> List<T> fromXML(final InputStream aStream,
                                                               Constructor<T> aConstructor) {
        List<T> result = new LinkedList<>();

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
                        result.add(aConstructor.create(xpp.getAttributeValue(null, "uri"),
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
