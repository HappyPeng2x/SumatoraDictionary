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
        along with this program.  If not, see <http://www.gnu.org/licenses/>. */

package org.happypeng.sumatora.android.sumatoradictionary.fragment;

import android.os.Bundle;

import androidx.annotation.NonNull;
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
import org.happypeng.sumatora.android.sumatoradictionary.DictionarySearchElementViewHolder;
import org.happypeng.sumatora.android.sumatoradictionary.R;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryDatabase;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchElement;
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.QueryTool;
import org.happypeng.sumatora.android.sumatoradictionary.model.DictionarySearchFragmentModel;

import java.util.HashMap;
import java.util.Iterator;

public class DictionarySearchFragment extends Fragment {
    private ImageButton m_search_button;
    private ImageButton m_magic_cross;

    private EditText m_edit_text;

    private ProgressBar m_progress_bar;
    private TextView m_status_text;

    private String m_intentSearchTerm;

    private DictionarySearchFragmentModel m_viewModel;

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

    private void setSearching()
    {
        m_status_text.setVisibility(View.VISIBLE);
        m_progress_bar.setVisibility(View.VISIBLE);

        m_progress_bar.setIndeterminate(true);
        m_progress_bar.animate();

        m_search_button.setEnabled(false);

        m_status_text.setText("Searching...");
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

        RecyclerView m_recyclerView = (RecyclerView) view.findViewById(R.id.recyclerView);

        setInPreparation();

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
                    m_magic_cross.setVisibility(View.VISIBLE);
                } else {
                    m_magic_cross.setVisibility(View.GONE);
                }
            }
        });

        m_viewModel = ViewModelProviders.of(getActivity()).get(DictionarySearchFragmentModel.class);

        final DictionaryPagedListAdapter pagedListAdapter = new DictionaryPagedListAdapter();

        m_viewModel.getSearchEntries().observe(getViewLifecycleOwner(),
                new Observer<PagedList<DictionarySearchElement>>() {
                    @Override
                    public void onChanged(PagedList<DictionarySearchElement> dictionarySearchElements) {
                        pagedListAdapter.submitList(dictionarySearchElements);
                    }
                });

        m_viewModel.getBookmarksHash().observe(getViewLifecycleOwner(),
                new Observer<HashMap<Long, Long>>() {
                    @Override
                    public void onChanged(HashMap<Long, Long> aBookmarks) {
                         pagedListAdapter.setBookmarks(aBookmarks);
                    }
                });

        pagedListAdapter.setBookmarkClickListener(new DictionarySearchElementViewHolder.ClickListener() {
            @Override
            public void onClick(View aView, DictionarySearchElement aEntry) {
                if (aEntry.getBookmark() == 0) {
                    m_viewModel.updateBookmark(aEntry.getSeq(), 1);
                } else {
                    m_viewModel.updateBookmark(aEntry.getSeq(), 0);
                }
            }
        });

        m_recyclerView.setAdapter(pagedListAdapter);

        m_search_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                m_intentSearchTerm = m_edit_text.getText().toString();

                processIntentSearchTerm();

                //m_viewModel.getDictionaryQuery().getValue().setTerm(m_edit_text.getText().toString(), "eng");
            }
        });

        m_magic_cross.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setReady();

                m_edit_text.setText("");
            }
        });

        Observer<Integer> observer =
                new Observer<Integer>() {
                    @Override
                    public void onChanged(Integer integer) {
                        processIntentSearchTerm();

                        if (integer == QueryTool.QueriesList.STATUS_PRE_INITIALIZED) {
                            setInPreparation();
                        } else if (integer == QueryTool.QueriesList.STATUS_INITIALIZED) {
                            setReady();
                        } else if (integer == QueryTool.QueriesList.STATUS_SEARCHING) {
                            setSearching();
                        } else if (integer == QueryTool.QueriesList.STATUS_RESULTS_FOUND ||
                            integer == QueryTool.QueriesList.STATUS_RESULTS_FOUND_ENDED) {
                            setReady();
                        } else if (integer == QueryTool.QueriesList.STATUS_NO_RESULTS_FOUND_ENDED) {
                            setNoResultsFound();
                        }
                    }
                };

        m_viewModel.getQueryStatus().observe(getViewLifecycleOwner(), observer);

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        // Avoid using old pointers when view has been destroyed
        m_search_button = null;
        m_magic_cross = null;
        m_edit_text = null;
        m_progress_bar = null;
        m_status_text = null;
    }

    private void processIntentSearchTerm() {
        if (m_intentSearchTerm == null || m_intentSearchTerm.equals("") ||
            m_viewModel.getDictionaryQuery().getValue() == null ||
            m_edit_text == null) {
            return;
        }

        // Very important in order not to create a loop:
        // non-null m_intentSearchTerm indicates an intent to be processed,
        // which will be processed when the query status changes.
        String searchTerm = m_intentSearchTerm;
        m_intentSearchTerm = null;

        m_edit_text.setText("");
        m_edit_text.append(searchTerm);

        m_viewModel.getDictionaryQuery().getValue().setTerm(searchTerm, "eng");
    }

    public void setIntentSearchTerm(@NonNull String aIntentSearchTerm) {
        m_intentSearchTerm = aIntentSearchTerm;

        processIntentSearchTerm();
    }
}
