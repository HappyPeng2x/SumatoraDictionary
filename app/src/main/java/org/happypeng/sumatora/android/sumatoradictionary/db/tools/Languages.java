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
        along with this program.  If not, see <http://www.gnu.org/licenses/>. */

package org.happypeng.sumatora.android.sumatoradictionary.db.tools;

import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryLanguage;

import java.util.LinkedList;
import java.util.List;

public class Languages {
    public static List<DictionaryLanguage> getLanguages() {
        List<DictionaryLanguage> languages = new LinkedList<>();

        languages.add(new DictionaryLanguage("eng", "English"));
        languages.add(new DictionaryLanguage("ger", "Deutsch"));
        languages.add(new DictionaryLanguage("rus", "русский язык"));
        languages.add(new DictionaryLanguage("spa", "Español"));
        languages.add(new DictionaryLanguage("dut", "Nederlands"));
        languages.add(new DictionaryLanguage("hun", "Magyar nyelv"));
        languages.add(new DictionaryLanguage("swe", "Svenska"));
        languages.add(new DictionaryLanguage("fre", "Français"));
        languages.add(new DictionaryLanguage("slv", "Slovenski jezik"));

        return languages;
    }
}
