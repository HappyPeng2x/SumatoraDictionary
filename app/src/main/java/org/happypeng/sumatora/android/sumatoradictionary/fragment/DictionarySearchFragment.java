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

import android.app.SearchManager;
import android.content.Context;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;
import androidx.paging.PagedList;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import android.widget.ProgressBar;
import android.widget.TextView;

import org.happypeng.sumatora.android.sumatoradictionary.DictionaryPagedListAdapter;
import org.happypeng.sumatora.android.sumatoradictionary.DictionarySearchElementViewHolder;
import org.happypeng.sumatora.android.sumatoradictionary.R;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryLanguage;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchElement;
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.QueryTool;
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.Settings;
import org.happypeng.sumatora.android.sumatoradictionary.model.DictionarySearchFragmentModel;

import java.util.HashMap;
import java.util.List;

public class DictionarySearchFragment extends Fragment {
    private ProgressBar m_progress_bar;
    private TextView m_status_text;

    private DictionarySearchFragmentModel m_viewModel;

    private TextView m_languageText;

    private PopupMenu m_languagePopupMenu;

    SearchView m_searchView;

    private DictionarySearchElementViewHolder.Status m_viewHolderStatus;

    private String m_lang;
    private String m_backupLang;

    public DictionarySearchFragment() {
        // Required empty public constructor
    }

    private void setInPreparation()
    {
        m_status_text.setVisibility(View.VISIBLE);
        m_progress_bar.setVisibility(View.VISIBLE);

        m_progress_bar.setIndeterminate(true);
        m_progress_bar.animate();

        if (m_searchView != null) {
            m_searchView.setActivated(false);
        }

        m_status_text.setText("Loading database...");
    }

    private void setSearching()
    {
        m_status_text.setVisibility(View.VISIBLE);
        m_progress_bar.setVisibility(View.VISIBLE);

        m_progress_bar.setIndeterminate(true);
        m_progress_bar.animate();

        if (m_searchView != null) {
            m_searchView.setActivated(false);
        }

        m_status_text.setText("Searching...");
    }

    private void setNoResultsFound()
    {
        m_progress_bar.setIndeterminate(false);
        m_progress_bar.setMax(0);

        if (m_searchView != null) {
            m_searchView.setActivated(true);
        }

        m_status_text.setText("No results found.");

        m_status_text.setVisibility(View.VISIBLE);
        m_progress_bar.setVisibility(View.GONE);
    }

    private void setReady()
    {
        m_progress_bar.setIndeterminate(false);
        m_progress_bar.setMax(0);

        if (m_searchView != null) {
            m_searchView.setActivated(true);
        }

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

        setHasOptionsMenu(true);

        m_progress_bar = (ProgressBar) view.findViewById(R.id.progressBar);
        m_status_text = (TextView) view.findViewById(R.id.statusText);

        RecyclerView m_recyclerView = (RecyclerView) view.findViewById(R.id.recyclerView);

        setInPreparation();

        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        layoutManager.setOrientation(RecyclerView.VERTICAL);
        m_recyclerView.setLayoutManager(layoutManager);

        DividerItemDecoration itemDecor = new DividerItemDecoration(getContext(),
                layoutManager.getOrientation());
        m_recyclerView.addItemDecoration(itemDecor);

        m_viewModel = ViewModelProviders.of(getActivity()).get(DictionarySearchFragmentModel.class);

        m_viewHolderStatus = new DictionarySearchElementViewHolder.Status();
        final DictionaryPagedListAdapter pagedListAdapter = new DictionaryPagedListAdapter(m_viewHolderStatus);

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

        m_viewModel.getDictionaryApplication().getDictionaryLanguage().observe
                (this, new Observer<List<DictionaryLanguage>>() {
                    @Override
                    public void onChanged(List<DictionaryLanguage> dictionaryLanguages) {
                        if (m_languageText != null && m_languagePopupMenu == null) {
                            m_languagePopupMenu = initLanguagePopupMenu(m_languageText,
                                    dictionaryLanguages);
                        }
                    }
                });

        m_viewModel.getStatus().observe(getViewLifecycleOwner(),
                new Observer<DictionarySearchFragmentModel.Status>() {
                    @Override
                    public void onChanged(DictionarySearchFragmentModel.Status status) {
                        if (!status.isInitialized()) {
                            setInPreparation();

                            return;
                        }

                        m_viewHolderStatus.entities = m_viewModel.getDictionaryApplication().getEntities();

                        if (m_lang == null || !m_lang.equals(status.lang)) {
                            if (m_languageText != null) {
                                m_languageText.setText(status.lang);
                            }

                            m_lang = status.lang;

                            pagedListAdapter.notifyDataSetChanged();
                        }

                        if ((m_backupLang == null && status.backupLang != null) ||
                                (m_backupLang != null && !m_backupLang.equals(status.backupLang))) {
                            m_backupLang = status.backupLang;

                            pagedListAdapter.notifyDataSetChanged();
                        }

                        if (m_searchView != null) {
                            m_searchView.setQuery(status.term, false);
                        }

                        m_viewHolderStatus.lang = status.lang;
                        
                        if (status.queryStatus == QueryTool.QueriesList.STATUS_INITIALIZED) {
                            setReady();
                        } else if (status.queryStatus == QueryTool.QueriesList.STATUS_SEARCHING) {
                            setSearching();
                        } else if (status.queryStatus == QueryTool.QueriesList.STATUS_RESULTS_FOUND ||
                                status.queryStatus == QueryTool.QueriesList.STATUS_RESULTS_FOUND_ENDED) {
                            setReady();
                        } else if (status.queryStatus == QueryTool.QueriesList.STATUS_NO_RESULTS_FOUND_ENDED) {
                            setNoResultsFound();
                        }
                    }
                });

        return view;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        // Avoid using old pointers when view has been destroyed
        m_progress_bar = null;
        m_status_text = null;
        m_languageText = null;
        m_languagePopupMenu = null;
        m_searchView = null;
    }

    public void setIntentSearchTerm(@NonNull String aIntentSearchTerm) {
        m_viewModel.setTerm(aIntentSearchTerm);
    }

    private PopupMenu initLanguagePopupMenu(final View aAnchor, final List<DictionaryLanguage> aLanguage) {
        PopupMenu popupMenu = null;

        if (aLanguage != null) {
            popupMenu = new PopupMenu(getContext(), aAnchor);
            Menu menu = popupMenu.getMenu();

            for (final DictionaryLanguage l : aLanguage) {
                menu.add(l.description).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                    @Override
                    public boolean onMenuItemClick(MenuItem item) {
                        m_viewModel.getDictionaryApplication().getSettings().setValue(Settings.LANG, l.lang);

                        return false;
                    }
                });
            }
        }

        return popupMenu;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);

        inflater.inflate(R.menu.search_toolbar_menu, menu);

        SearchManager searchManager = (SearchManager) getActivity().getSystemService(Context.SEARCH_SERVICE);
        MenuItem searchViewMenuItem = menu.findItem(R.id.menu_search);

        m_searchView = (SearchView) searchViewMenuItem.getActionView();
        m_searchView.setSearchableInfo(searchManager.getSearchableInfo(getActivity().getComponentName()));
        m_searchView.setIconifiedByDefault(false);

        MenuItem languageMenuItem = menu.findItem(R.id.search_fragment_menu_language_text);
        m_languageText = languageMenuItem.getActionView().findViewById(R.id.menuitem_language_text);

        m_languageText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (m_languagePopupMenu == null &&
                        m_viewModel.getDictionaryApplication().getDictionaryLanguage().getValue() != null) {
                    m_languagePopupMenu = initLanguagePopupMenu(m_languageText,
                            m_viewModel.getDictionaryApplication().getDictionaryLanguage().getValue());
                }

                if (m_languagePopupMenu != null) {
                    m_languagePopupMenu.show();
                }
            }
        });
    }
}