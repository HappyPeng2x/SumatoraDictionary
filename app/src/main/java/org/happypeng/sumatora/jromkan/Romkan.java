/* JRomkan
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

package org.happypeng.sumatora.jromkan;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;

public class Romkan {
    private static class LengthComparator implements Comparator<String> {
        @Override
        public int compare(String arg0, String arg1) {
            if (arg0.length() < arg1.length()) {
                return 1;
            } else if (arg0.length() > arg1.length()) {
                return -1;
            }
            
            return 0;
        }
    }
    
    private RomkanData romkan_data;

    private Pattern rompat;
    private Pattern rompat_h;
    private Pattern kanpat;
    private Pattern kanpat_h;
    private Pattern kunpat;
    private Pattern heppat;

    private LengthComparator length_comparator;
    
    private static String joinString(final String a_separator, final List<String> a_elements) {
        StringBuilder buf = new StringBuilder();
        boolean first = true;

        for (String e : a_elements) {
            if (!first) {
                buf.append(a_separator);
            }

            buf.append(e);

            first = false;
        }

        return buf.toString();
    }

    public Romkan() {
        length_comparator = new LengthComparator();
        romkan_data = new RomkanData();
        
        final ArrayList<String> romkan_keys = new ArrayList<>(romkan_data.get_romkan().keySet());
        Collections.sort(romkan_keys, length_comparator);
        rompat = Pattern.compile(joinString("|", romkan_keys));

        final ArrayList<String> romkan_h_keys = new ArrayList<>(romkan_data.get_romkan_h().keySet());
        Collections.sort(romkan_h_keys, length_comparator);
        rompat_h = Pattern.compile(joinString("|", romkan_h_keys));

        final ArrayList<String> kanpat_keys = new ArrayList<>(romkan_data.get_kanrom().keySet());
        Collections.sort(kanpat_keys, length_comparator);
        kanpat = Pattern.compile(joinString("|", kanpat_keys));

        final ArrayList<String> kanpat_h_keys = new ArrayList<>(romkan_data.get_kanrom_h().keySet());
        Collections.sort(kanpat_h_keys, length_comparator);
        kanpat_h = Pattern.compile(joinString("|", kanpat_h_keys));

        final ArrayList<String> kunpat_keys = new ArrayList<>(romkan_data.get_to_hepburn().keySet());
        Collections.sort(kunpat_keys, length_comparator);
        kunpat = Pattern.compile(joinString("|", kunpat_keys));

        final ArrayList<String> heppat_keys = new ArrayList<>(romkan_data.get_to_kunrei().keySet());
        Collections.sort(heppat_keys, length_comparator);
        heppat = Pattern.compile(joinString("|", heppat_keys));
    }
    
    public static String normalize_double_n(final String a_string) {
        String res = Pattern.compile("nn").matcher(a_string).replaceAll("n'");
        res = Pattern.compile("n'(?=[^aiueoyn]|$)").matcher(res).replaceAll("n");
        
        return res;
    }
    
    public String to_katakana(final String a_string) {
        String res = a_string.toLowerCase();
        res = normalize_double_n(res);
        StringBuffer ret = new StringBuffer();
        HashMap<String, String> romkan = romkan_data.get_romkan();
        Matcher match = rompat.matcher(res);
        
        while (match.find()) {
            match.appendReplacement(ret, romkan.get(match.group()));
        }
        
        match.appendTail(ret);
        
        return ret.toString();
    }

    public String to_hiragana(final String a_string) {
        String res = a_string.toLowerCase();
        res = normalize_double_n(res);
        StringBuffer ret = new StringBuffer();
        HashMap<String, String> romkan_h = romkan_data.get_romkan_h();
        Matcher match = rompat_h.matcher(res);
        
        while (match.find()) {
            match.appendReplacement(ret, romkan_h.get(match.group()));
        }
        
        match.appendTail(ret);
        
        return ret.toString();
    }

    public String to_hepburn(final String a_string) {
        StringBuffer ret1 = new StringBuffer();
        StringBuffer ret2 = new StringBuffer();
        HashMap<String, String> kanrom = romkan_data.get_kanrom();
        HashMap<String, String> kanrom_h = romkan_data.get_kanrom_h();
        Matcher match = kanpat.matcher(a_string);
        
        while (match.find()) {
            match.appendReplacement(ret1, kanrom.get(match.group()));
        }
        
        match.appendTail(ret1);

        Matcher match_h = kanpat_h.matcher(ret1.toString());

        while (match_h.find()) {
            match_h.appendReplacement(ret2, kanrom_h.get(match_h.group()));
        }
        
        match_h.appendTail(ret2);

        String ret = Pattern.compile("n'(?=[^aiueoyn]|$)").matcher(ret2.toString()).replaceAll("n");

        if (ret.equals(a_string)) {
            ret = ret.toLowerCase();
            ret = normalize_double_n(ret);

            StringBuffer ret3 = new StringBuffer();
            HashMap<String, String> to_hepburn = romkan_data.get_to_hepburn();
            Matcher match3 = kunpat.matcher(ret);

            while (match3.find()) {
                match3.appendReplacement(ret3, to_hepburn.get(match3.group()));
            }
            
            match3.appendTail(ret3);

            ret = ret3.toString();
        }
        
        return ret;
    }

    public String to_kunrei(final String a_string) {
        StringBuffer ret1 = new StringBuffer();
        StringBuffer ret2 = new StringBuffer();
        HashMap<String, String> kanrom = romkan_data.get_kanrom();
        HashMap<String, String> kanrom_h = romkan_data.get_kanrom_h();
        Matcher match = kanpat.matcher(a_string);
        
        while (match.find()) {
            match.appendReplacement(ret1, kanrom.get(match.group()));
        }
        
        match.appendTail(ret1);
        
        Matcher match_h = kanpat_h.matcher(ret1.toString());
        
        while (match_h.find()) {
            match_h.appendReplacement(ret2, kanrom_h.get(match_h.group()));
        }
        
        match_h.appendTail(ret2);
        
        String ret = Pattern.compile("n'(?=[^aiueoyn]|$)").matcher(ret2.toString()).replaceAll("n").toLowerCase();
        
        ret = normalize_double_n(ret);
        
        StringBuffer ret3 = new StringBuffer();
        HashMap<String, String> to_kunrei = romkan_data.get_to_kunrei();
        Matcher match3 = heppat.matcher(ret);
        
        while (match3.find()) {
            match3.appendReplacement(ret3, to_kunrei.get(match3.group()));
        }
        
        match3.appendTail(ret3);
        
        ret = ret3.toString();
        
        return ret3.toString();
    }
}