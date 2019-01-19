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

package org.happypeng.sumatora.android.sumatoradictionary.fragment;


import android.os.Bundle;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;
import androidx.paging.PagedList;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.happypeng.sumatora.android.sumatoradictionary.DictionaryPagedListAdapter;
import org.happypeng.sumatora.android.sumatoradictionary.R;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryEntry;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchResult;
import org.happypeng.sumatora.android.sumatoradictionary.model.DictionaryBookmarkFragmentModel;
import org.happypeng.sumatora.android.sumatoradictionary.model.DictionarySearchFragmentModel;

public class DictionaryBookmarkFragment extends Fragment {
    private RecyclerView m_recyclerView;

    private ProgressBar m_progressBar;
    private TextView m_statusText;

    String m_bookmark;

    public DictionaryBookmarkFragment() {
    }

    private void setInPreparation()
    {
        m_statusText.setVisibility(View.VISIBLE);
        m_progressBar.setVisibility(View.VISIBLE);

        m_progressBar.setIndeterminate(true);
        m_progressBar.animate();

        m_statusText.setText("Loading database...");
    }

    private void setReady()
    {
        m_progressBar.setIndeterminate(false);
        m_progressBar.setMax(0);

        m_statusText.setText("");

        m_statusText.setVisibility(View.GONE);
        m_progressBar.setVisibility(View.GONE);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final AppCompatActivity activity = (AppCompatActivity) getActivity();
        final View view = inflater.inflate(R.layout.fragment_dictionary_bookmark, container, false);

        final Toolbar tb = (Toolbar) view.findViewById(R.id.dictionary_bookmark_fragment_toolbar);
        activity.setSupportActionBar(tb);

        final ActionBar actionBar = activity.getSupportActionBar();
        actionBar.setHomeAsUpIndicator(R.drawable.ic_menu_white_24dp);
        actionBar.setDisplayHomeAsUpEnabled(true);

        m_progressBar = (ProgressBar) view.findViewById(R.id.dictionary_bookmark_fragment_progressbar);
        m_statusText = (TextView) view.findViewById(R.id.dictionary_bookmark_fragment_statustext);

        m_recyclerView = (RecyclerView) view.findViewById(R.id.dictionary_bookmark_fragment_recyclerview);

        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        layoutManager.setOrientation(RecyclerView.VERTICAL);
        m_recyclerView.setLayoutManager(layoutManager);

        final DictionaryBookmarkFragmentModel viewModel = ViewModelProviders.of(getActivity()).get(DictionaryBookmarkFragmentModel.class);

        viewModel.getDatabaseReady().observe(this,
                new Observer<Boolean>() {
                    @Override
                    public void onChanged(Boolean aDbReady) {
                        if (aDbReady) {
                            setReady();

                            if (m_bookmark != null) {
                                viewModel.listBookmarks(m_bookmark, "eng");
                            }
                        } else {
                            setInPreparation();
                        }
                    }
                });

        if (viewModel.getDatabaseReady().getValue()) {
            setReady();
        } else {
            setInPreparation();
        }

        final DictionaryPagedListAdapter pagedListAdapter = new DictionaryPagedListAdapter();

        m_recyclerView.setAdapter(pagedListAdapter);

        viewModel.getSearchEntries().observe(this, new Observer<PagedList<DictionarySearchResult>>() {
            @Override
            public void onChanged(PagedList<DictionarySearchResult> aList) {
                // pagedListAdapter.submitList(aList);
            }
        });

        Bundle bundle = getArguments();

        if (bundle != null) {
            String bookmark = bundle.getString("bookmark");

            if (bookmark != null) {
                m_bookmark = bookmark;

                if (viewModel.getDatabaseReady().getValue()) {
                    viewModel.listBookmarks(m_bookmark, "eng");
                }

                bundle.remove("bookmark");
            }
        }

        return view;
    }

}
