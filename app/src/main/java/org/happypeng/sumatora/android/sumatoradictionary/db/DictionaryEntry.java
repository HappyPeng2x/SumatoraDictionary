package org.happypeng.sumatora.android.sumatoradictionary.db;

import androidx.recyclerview.widget.DiffUtil;
import androidx.room.Entity;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

@Entity(primaryKeys = {"seq", "lang"})
public class DictionaryEntry {
    public long seq;
    public String readings;
    public String writings;
    @NonNull public String lang;
    public String gloss;
    public String bookmark;

    public DictionaryEntry() {
    }

    public static DiffUtil.ItemCallback<DictionaryEntry> DIFF_CALLBACK = new  DiffUtil.ItemCallback<DictionaryEntry>() {
        @Override
        public boolean areItemsTheSame(@NonNull DictionaryEntry oldItem, @NonNull DictionaryEntry newItem) {
            return oldItem.seq == newItem.seq && oldItem.lang == newItem.lang;
        }

        @Override
        public boolean areContentsTheSame(@NonNull DictionaryEntry oldItem, @NonNull DictionaryEntry newItem) {
            return oldItem.equals(newItem);
        }
    };

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }

        DictionaryEntry dictionaryEntry = (DictionaryEntry) obj;

        return dictionaryEntry.readings == dictionaryEntry.readings &&
                dictionaryEntry.writings == dictionaryEntry.writings &&
                dictionaryEntry.lang == dictionaryEntry.lang &&
                dictionaryEntry.gloss == dictionaryEntry.gloss &&
                dictionaryEntry.bookmark == dictionaryEntry.bookmark;
    }
}
