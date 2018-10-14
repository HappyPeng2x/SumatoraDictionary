package org.happypeng.sumatora.android.sumatoradictionary;

import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.Cursor;

import java.util.LinkedList;
import java.util.List;

public class DictionaryElement {
    private SQLiteDatabase mDb;

    private int mSeq;

    public int getSeq()
    {
        return mSeq;
    }

    public DictionaryElement(SQLiteDatabase pDb, int pSeq)
    {
        mDb = pDb;
        mSeq = pSeq;
    }

    public static class Writing
    {
        public int keb_id;
        public String keb;
        public String ke_inf;

        public Writing(int pKeb_id, String pKeb, String pKe_inf)
        {
            keb_id = pKeb_id;
            keb = pKeb;
            ke_inf = pKe_inf;
        }
    }

    public List<Writing> getWritings()
    {
        LinkedList<Writing> result = new LinkedList<Writing>();
        String[] cols = {"keb_id", "keb", "ke_inf"};
        Cursor cur = mDb.query("writings", cols, "seq = " + Integer.toString(mSeq), null,null,null,"keb_id");

        while (cur.moveToNext()) {
            result.add(new Writing(cur.getInt(0), cur.getString(1), cur.getString(2)));
        }

        cur.close();

        return result;
    }

    public static class Gloss
    {
        public int sense_id;
        public String lang;
        public String gloss;

        public Gloss(int pSense_id, String pLang, String pGloss)
        {
            sense_id = pSense_id;
            lang = pLang;
            gloss = pGloss;
        }
    }

    public static class Reading
    {
        public int reb_id;
        public String reb;
        public String re_inf;

        public Reading(int pReb_id, String pReb, String pRe_inf)
        {
            reb_id = pReb_id;
            reb = pReb;
            re_inf = pRe_inf;
        }
    }

    public static class WritingPriority
    {
        public int keb_id;
        public String ke_pri;

        public WritingPriority(int pKeb_id, String pKe_pri)
        {
            keb_id = pKeb_id;
            ke_pri = pKe_pri;
        }
    }

    public static class ReadingPriority
    {
        public int reb_id;
        public String re_pri;

        public ReadingPriority(int pReb_id, String pRe_pri)
        {
            reb_id = pReb_id;
            re_pri = pRe_pri;
        }
    }

    public List<Reading> getReadings()
    {
        LinkedList<Reading> result = new LinkedList<Reading>();
        String[] cols = {"reb_id", "reb", "re_inf"};
        Cursor cur = mDb.query("readings", cols, "seq = " + Integer.toString(mSeq), null,null,null, "reb_id");

        while (cur.moveToNext()) {
            result.add(new Reading(cur.getInt(0), cur.getString(1), cur.getString(2)));
        }

        cur.close();

        return result;
    }

    public List<Gloss> getGloss(String pLang)
    {
        LinkedList<Gloss> result = new LinkedList<Gloss>();
        String[] cols = {"sense_id", "lang", "gloss"};
        Cursor cur = mDb.query("gloss", cols, "lang = \"" + pLang + "\" AND seq = " + Integer.toString(mSeq), null, null, null, "sense_id");

        while (cur.moveToNext()) {
            result.add(new Gloss(cur.getInt(0), cur.getString(1), cur.getString(2)));
        }

        cur.close();

        return result;
    }

    public List<WritingPriority> getWritingsPrio(int pKeb_id)
    {
        LinkedList<WritingPriority> result = new LinkedList<WritingPriority>();
        String[] cols = {"keb_id", "ke_pri"};
        Cursor cur = mDb.query("writings_prio", cols, "seq = " + Integer.toString(mSeq) + " AND keb_id = " + Integer.toString(pKeb_id), null, null, null, "keb_id");

        // System.out.println("SEARCH FOR PRIORITY: " + mSeq + " " + pKeb_id);

        while (cur.moveToNext()) {
            result.add(new WritingPriority(cur.getInt(0), cur.getString(1)));
        }

        cur.close();

        return result;
    }

    public List<ReadingPriority> getReadingsPrio(int pReb_id)
    {
        LinkedList<ReadingPriority> result = new LinkedList<ReadingPriority>();
        String[] cols = {"reb_id", "re_pri"};
        Cursor cur = mDb.query("readings_prio", cols, "seq = " + Integer.toString(mSeq) + " AND reb_id = " + Integer.toString(pReb_id), null, null, null, "reb_id");

        while (cur.moveToNext()) {
            result.add(new ReadingPriority(cur.getInt(0), cur.getString(1)));
        }

        cur.close();

        return result;
    }
}
