package org.happypeng.sumatora.android.sumatoradictionary;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import net.java.sen.dictionary.Token;

import java.util.List;

public class DictionaryAdapter extends RecyclerView.Adapter<DictionaryViewHolder> {
    List<Token> mList;

    public DictionaryAdapter(List<Token> pList) {
        this.mList = pList;
    }

    @Override
    public DictionaryViewHolder onCreateViewHolder(ViewGroup pViewGroup, int pItemType) {
        View view = LayoutInflater.from(pViewGroup.getContext()).inflate(R.layout.cell_cards,pViewGroup,false);
        return new DictionaryViewHolder(view);
    }

    @Override
    public void onBindViewHolder(DictionaryViewHolder pViewHolder, int position) {
        Token token = mList.get(position);
        pViewHolder.bind(token);
    }

    @Override
    public int getItemCount() {
        return mList.size();
    }
}
