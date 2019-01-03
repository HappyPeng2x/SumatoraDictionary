package org.happypeng.sumatora.android.sumatoradictionary;

import androidx.paging.PagedListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryEntry;


public class DictionaryPagedListAdapter extends PagedListAdapter<DictionaryEntry, DictionaryPagedListAdapter.DictionaryEntryItemViewHolder> {
    protected DictionaryPagedListAdapter() {
        super(DictionaryEntry.DIFF_CALLBACK);
    }

    @Override
    public DictionaryEntryItemViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater layoutInflater = LayoutInflater.from(parent.getContext());
        View view = layoutInflater.inflate(R.layout.cell_cards, parent, false);
        return new DictionaryEntryItemViewHolder(view);
    }

    @Override
    public void onBindViewHolder(DictionaryEntryItemViewHolder holder, int position) {
        DictionaryEntry entry = getItem(position);

        if (entry != null) {
            holder.bindTo(entry);
        }
    }

    static class DictionaryEntryItemViewHolder extends RecyclerView.ViewHolder {
        private TextView m_textViewView;

        public DictionaryEntryItemViewHolder(View itemView) {
            super(itemView);

            m_textViewView = (TextView) itemView.findViewById(R.id.text);
        }

        public void bindTo(DictionaryEntry entry) {
            m_textViewView.setText(entry.writings + " " + entry.readings + "\n" + entry.gloss);
        }
    }
}
