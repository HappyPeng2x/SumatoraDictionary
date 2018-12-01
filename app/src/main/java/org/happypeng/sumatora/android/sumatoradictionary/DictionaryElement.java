package org.happypeng.sumatora.android.sumatoradictionary;

import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.Cursor;

import java.util.LinkedList;
import java.util.List;

import android.graphics.Color;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;

import android.text.SpannableStringBuilder;
import android.widget.TextView;

public class DictionaryElement {
    private SQLiteDatabase mDb;

    private int mSeq;

    private List<Writing> m_writings;
    private List<Reading> m_readings;

    private String m_lang;

    private List<Gloss> m_gloss;

    private List<WritingPriority> m_writingsPrio;
    private List<ReadingPriority> m_readingsPrio;

    private SpannableStringBuilder m_sb;

    public int getSeq()
    {
        return mSeq;
    }

    public DictionaryElement(SQLiteDatabase pDb, int pSeq, String pGlossLang)
    {
        mDb = pDb;
        mSeq = pSeq;
        m_lang = pGlossLang;

        readReadings();
        readWritings();
        readWritingsPrio();
        readReadingsPrio();
        readGloss();
        buildSpannableStringBuilder();
    }

    public static class Writing
    {
        private SQLiteDatabase mDb;
        public int mSeq;
        private List<WritingPriority> m_prio;

        public int keb_id;
        public String keb;
        public String ke_inf;

        public Writing(SQLiteDatabase pDb, int pSeq, int pKeb_id, String pKeb, String pKe_inf)
        {
            mDb = pDb;
            mSeq = pSeq;

            keb_id = pKeb_id;
            keb = pKeb;
            ke_inf = pKe_inf;
        }

        private void readPrio()
        {
            LinkedList<WritingPriority> result = new LinkedList<WritingPriority>();
            String[] cols = {"keb_id", "ke_pri"};
            Cursor cur = mDb.query("writings_prio", cols, "seq = " + Integer.toString(mSeq) + " AND keb_id = " + Integer.toString(keb_id), null, null, null, "keb_id");

            while (cur.moveToNext()) {
                result.add(new WritingPriority(cur.getInt(0), cur.getString(1)));
            }

            cur.close();

            m_prio = result;
        }

        public List<WritingPriority> getPrio()
        {
            if (m_prio == null) {
                readPrio();
            }

            return m_prio;
        }
    }

    private void readWritings()
    {
        LinkedList<Writing> result = new LinkedList<Writing>();
        String[] cols = {"keb_id", "keb", "ke_inf"};
        Cursor cur = mDb.query("writings", cols, "seq = " + Integer.toString(mSeq), null,null,null,"keb_id");

        while (cur.moveToNext()) {
            result.add(new Writing(mDb, mSeq, cur.getInt(0), cur.getString(1), cur.getString(2)));
        }

        cur.close();

        m_writings = result;
    }

    public List<Writing> getWritings()
    {
        if (m_writings == null) {
            readWritings();
        }

        return m_writings;
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
        private SQLiteDatabase mDb;
        public int mSeq;
        private List<ReadingPriority> m_prio;

        public int reb_id;
        public String reb;
        public String re_inf;

        public Reading(SQLiteDatabase pDb, int pSeq, int pReb_id, String pReb, String pRe_inf)
        {
            mDb = pDb;

            mSeq = pSeq;

            reb_id = pReb_id;
            reb = pReb;
            re_inf = pRe_inf;
        }

        private void readPrio()
        {
            LinkedList<ReadingPriority> result = new LinkedList<ReadingPriority>();
            String[] cols = {"reb_id", "re_pri"};
            Cursor cur = mDb.query("readings_prio", cols, "seq = " + Integer.toString(mSeq) + " AND reb_id = " + Integer.toString(reb_id), null, null, null, "reb_id");

            while (cur.moveToNext()) {
                result.add(new ReadingPriority(cur.getInt(0), cur.getString(1)));
            }

            cur.close();

            m_prio = result;
        }

        public List<ReadingPriority> getPrio()
        {
            if (m_prio == null) {
                readPrio();
            }

            return m_prio;
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

    private void readReadings()
    {
        LinkedList<Reading> result = new LinkedList<Reading>();
        String[] cols = {"reb_id", "reb", "re_inf"};
        Cursor cur = mDb.query("readings", cols, "seq = " + Integer.toString(mSeq), null,null,null, "reb_id");

        while (cur.moveToNext()) {
            result.add(new Reading(mDb, mSeq, cur.getInt(0), cur.getString(1), cur.getString(2)));
        }

        cur.close();

        m_readings = result;
    }

    public List<Reading> getReadings()
    {
        return m_readings;
    }

    private void readGloss()
    {
        LinkedList<Gloss> result = new LinkedList<Gloss>();
        String[] cols = {"sense_id", "lang", "gloss"};
        Cursor cur = mDb.query("gloss", cols, "lang = \"" + m_lang + "\" AND seq = " + Integer.toString(mSeq), null, null, null, "sense_id");

        while (cur.moveToNext()) {
            result.add(new Gloss(cur.getInt(0), cur.getString(1), cur.getString(2)));
        }

        cur.close();

        m_gloss = result;
    }

    public List<Gloss> getGloss()
    {
        return m_gloss;
    }

    private void readWritingsPrio()
    {
        for (Writing w : getWritings())
        {
            w.readPrio();
        }
    }

    private void readReadingsPrio()
    {
        for (Reading r : getReadings())
        {
            r.readPrio();
        }
    }

    public SpannableStringBuilder getSpannableStringBuilder()
    {
        return m_sb;
    }

    private void buildSpannableStringBuilder() {
        SpannableStringBuilder sb = new SpannableStringBuilder();

        List<DictionaryElement.Writing> writings = getWritings();
        List<DictionaryElement.Reading> readings = getReadings();

        int writingsLength = 0;
        int readingsLength = 0;

        for (DictionaryElement.Writing w : writings) {
            if (!w.getPrio().isEmpty()) {
                sb.append(w.keb, new BackgroundColorSpan(Color.parseColor("#ccffcc")), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                sb.append(w.keb);
            }

            sb.append(" ");

            writingsLength = writingsLength + w.keb.length() + 1;
        }

        for (DictionaryElement.Reading r : readings) {
            if (!r.getPrio().isEmpty()) {
                sb.append(r.reb, new BackgroundColorSpan(Color.parseColor("#ccffcc")), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                sb.append(r.reb);
            }

            sb.append(" ");

            readingsLength = readingsLength + r.reb.length() + 1;
        }

        sb.append("\n");
        readingsLength = readingsLength + 1;

        List<DictionaryElement.Gloss> gloss = getGloss();

        int glossLength = 0;
        int cur_sense = -1;

        for (DictionaryElement.Gloss g : gloss) {
            if (g.sense_id != cur_sense) {
                cur_sense = g.sense_id;

                if (cur_sense > 0) {
                    sb.append("\n");

                    glossLength = glossLength + 1;
                }

                sb.append(Integer.toString(cur_sense + 1));
                sb.append(". ");

                glossLength = glossLength + 2 + Integer.toString(cur_sense + 1).length();
            } else {
                sb.append(", ");

                glossLength = glossLength + 2;
            }

            sb.append(g.gloss);

            glossLength = glossLength + g.gloss.length();
        }

        sb.setSpan(new ForegroundColorSpan(Color.GRAY), writingsLength, writingsLength + readingsLength, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        sb.setSpan(new RelativeSizeSpan(1.5f), 0, writingsLength + readingsLength, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        m_sb = sb;
    }
}
