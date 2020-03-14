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

import androidx.room.Entity;

@Entity(primaryKeys = {"ref", "seq"})
public class DictionaryDisplayElement {
    public int ref;
    public int entryOrder;
    public long seq;
    public String readingsPrio;
    public String readings;
    public String writingsPrio;
    public String writings;
    public String pos;
    public String xref;
    public String ant;
    public String misc;
    public String lsource;
    public String dial;
    public String s_inf;
    public String field;
    public String lang;
    public String gloss;
    public String example_sentences;

    public DictionaryDisplayElement() { super(); }
}
