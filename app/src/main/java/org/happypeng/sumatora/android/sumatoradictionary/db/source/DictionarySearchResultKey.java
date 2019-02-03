package org.happypeng.sumatora.android.sumatoradictionary.db.source;

public class DictionarySearchResultKey {
    public int entryOrder;
    public long seq;

    public DictionarySearchResultKey(int aEntryOrder, long aSeq) {
        entryOrder = aEntryOrder;
        seq = aSeq;
    }
}
