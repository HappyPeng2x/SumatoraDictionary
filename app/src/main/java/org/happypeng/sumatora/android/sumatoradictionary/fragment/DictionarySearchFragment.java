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

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;
import androidx.paging.PagedList;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.happypeng.sumatora.android.sumatoradictionary.DictionaryPagedListAdapter;
import org.happypeng.sumatora.android.sumatoradictionary.R;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchElement;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchElementBase;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchResult;
import org.happypeng.sumatora.android.sumatoradictionary.model.DictionarySearchFragmentModel;

import java.util.HashMap;

public class DictionarySearchFragment extends Fragment {
    private RecyclerView m_recyclerView;

    private ImageButton m_search_button;
    private ImageButton m_magic_cross;

    private EditText m_edit_text;

    private ProgressBar m_progress_bar;
    private TextView m_status_text;

    private Observer<PagedList<DictionarySearchElement>> m_searchResultObserver;


    public DictionarySearchFragment() {
        // Required empty public constructor
    }

    private void setInPreparation()
    {
        m_status_text.setVisibility(View.VISIBLE);
        m_progress_bar.setVisibility(View.VISIBLE);

        m_progress_bar.setIndeterminate(true);
        m_progress_bar.animate();

        m_search_button.setEnabled(false);

        m_status_text.setText("Loading database...");
    }

    private void setSearchingDictionary()
    {
        m_status_text.setVisibility(View.VISIBLE);
        m_progress_bar.setVisibility(View.VISIBLE);

        m_progress_bar.setIndeterminate(true);
        m_progress_bar.animate();

        m_search_button.setEnabled(false);

        m_status_text.setText("Searching in the dictionary...");
    }

    private void setNoResultsFound()
    {
        m_progress_bar.setIndeterminate(false);
        m_progress_bar.setMax(0);

        m_search_button.setEnabled(true);

        m_status_text.setText("No results found.");

        m_status_text.setVisibility(View.VISIBLE);
        m_progress_bar.setVisibility(View.GONE);
    }

    private void setReady()
    {
        m_progress_bar.setIndeterminate(false);
        m_progress_bar.setMax(0);

        m_search_button.setEnabled(true);

        m_status_text.setText("");

        m_status_text.setVisibility(View.GONE);
        m_progress_bar.setVisibility(View.GONE);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final AppCompatActivity activity = (AppCompatActivity) getActivity();
        final View view = inflater.inflate(R.layout.fragment_dictionary_search, container, false);

        final Toolbar tb = (Toolbar) view.findViewById(R.id.nav_toolbar);
        activity.setSupportActionBar(tb);

        final ActionBar actionBar = activity.getSupportActionBar();
        actionBar.setHomeAsUpIndicator(R.drawable.ic_menu_white_24dp);
        actionBar.setDisplayHomeAsUpEnabled(true);

        m_search_button = (ImageButton) view.findViewById(R.id.button);

        m_magic_cross = (ImageButton) view.findViewById(R.id.magic_cross);
        m_edit_text = (EditText) view.findViewById(R.id.editText);

        m_progress_bar = (ProgressBar) view.findViewById(R.id.progressBar);
        m_status_text = (TextView) view.findViewById(R.id.statusText);

        m_recyclerView = (RecyclerView) view.findViewById(R.id.recyclerView);

        m_recyclerView.setItemAnimator(null);

        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        layoutManager.setOrientation(RecyclerView.VERTICAL);
        m_recyclerView.setLayoutManager(layoutManager);

        m_edit_text.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                if (s.length() > 0) {
                    m_magic_cross.setVisibility(android.view.View.VISIBLE);
                } else {
                    m_magic_cross.setVisibility(android.view.View.GONE);
                }
            }
        });

        final DictionarySearchFragmentModel viewModel = ViewModelProviders.of(getActivity()).get(DictionarySearchFragmentModel.class);

        final DictionaryPagedListAdapter pagedListAdapter = new DictionaryPagedListAdapter();

        pagedListAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onChanged() {
                super.onChanged();
            }

            @Override
            public void onItemRangeChanged(int positionStart, int itemCount) {
                super.onItemRangeChanged(positionStart, itemCount);
            }

            @Override
            public void onItemRangeChanged(int positionStart, int itemCount, @Nullable Object payload) {
                super.onItemRangeChanged(positionStart, itemCount, payload);
            }

            @Override
            public void onItemRangeInserted(int positionStart, int itemCount) {
                super.onItemRangeInserted(positionStart, itemCount);

                //System.out.println("ITEM RANGE INSERTED " + positionStart + itemCount);
            }

            @Override
            public void onItemRangeRemoved(int positionStart, int itemCount) {
                super.onItemRangeRemoved(positionStart, itemCount);

                // System.out.println("ITEM RANGE REMOVED " + positionStart + itemCount);
            }

            @Override
            public void onItemRangeMoved(int fromPosition, int toPosition, int itemCount) {
                super.onItemRangeMoved(fromPosition, toPosition, itemCount);
            }
        });

        m_searchResultObserver = new Observer<PagedList<DictionarySearchElement>>() {
            @Override
            public void onChanged(PagedList<DictionarySearchElement> dictionarySearchResults) {
                pagedListAdapter.submitList(dictionarySearchResults);
            }
        };

        viewModel.getSearchEntriesReady().observe(this,
                new Observer<Boolean>() {
                    @Override
                    public void onChanged(Boolean aDbReady) {
                        if (aDbReady) {
                            viewModel.getSearchEntries().observe(DictionarySearchFragment.this,
                                    m_searchResultObserver);

                            setReady();
                        } else {
                            setInPreparation();
                        }
                    }
                });

        if (viewModel.getSearchEntriesReady().getValue()) {
            setReady();
        } else {
            setInPreparation();
        }

/*        viewModel.getBookmarks().observe(this,
                new Observer<HashMap<Long, Long>>() {
                    @Override
                    public void onChanged(HashMap<Long, Long> aBookmarks) {
                         pagedListAdapter.setBookmarks(aBookmarks);
                    }
                });*/

        pagedListAdapter.setBookmarkClickListener(new DictionaryPagedListAdapter.ClickListener() {
            @Override
            public void onClick(View aView, DictionarySearchElementBase aEntry) {
                if (aEntry.getBookmark() == 0) {
                    viewModel.updateBookmark(aEntry.getSeq(), 1);
                } else {
                    viewModel.updateBookmark(aEntry.getSeq(), 0);
                }
            }
        });

        m_recyclerView.setAdapter(pagedListAdapter);

        // New search button logic
        m_search_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // So fast we don't need it anymore!
                //setSearchingDictionary();

                viewModel.search(m_edit_text.getText().toString(), "eng");
            }
        });

        m_magic_cross.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                m_edit_text.setText("");
            }
        });

        return view;
    }

}
