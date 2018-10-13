package org.happypeng.sumatora.android.sumatoradictionary;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.Cursor;

import java.util.List;

public class DictionaryAdapter extends RecyclerView.Adapter<DictionaryViewHolder> {
    private List<DictionaryElement> mList;
    private SQLiteDatabase mDb;

    public DictionaryAdapter(SQLiteDatabase pDb, List<DictionaryElement> pList)
    {
        mList = pList;
        mDb = pDb;
    }

    @Override
    public DictionaryViewHolder onCreateViewHolder(ViewGroup pViewGroup, int pItemType) {
        View view = LayoutInflater.from(pViewGroup.getContext()).inflate(R.layout.cell_cards,pViewGroup,false);
        return new DictionaryViewHolder(view, mDb);
    }

    @Override
    public void onBindViewHolder(DictionaryViewHolder pViewHolder, int position) {
        DictionaryElement ele = mList.get(position);
        pViewHolder.bind(ele);
    }

    @Override
    public int getItemCount() {
        return mList.size();
    }
}
