package org.happypeng.sumatora.android.sumatoradictionary.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity
public class DictionaryControl {
    public DictionaryControl() {
    }

    @PrimaryKey @NonNull public String control;
    public long value;
}
