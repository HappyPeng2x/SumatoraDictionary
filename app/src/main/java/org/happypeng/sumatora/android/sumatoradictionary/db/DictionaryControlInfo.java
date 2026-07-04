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

package org.happypeng.sumatora.android.sumatoradictionary.db;

// Holds the contents of jmdict.db's DictionaryControl table, read once at startup.
public class DictionaryControlInfo {
    public static final int SUPPORTED_FORMAT_VERSION = 1;

    public long buildTimestamp = 0;
    public int formatVersion = 0;
    public int entryCount = 0;

    public boolean isSupported() {
        return formatVersion <= SUPPORTED_FORMAT_VERSION;
    }
}
