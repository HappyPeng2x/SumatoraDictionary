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
import org.xmlpull.v1.XmlSerializer;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
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
    }
}
