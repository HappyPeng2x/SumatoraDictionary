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

import android.content.res.TypedArray;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Bundle;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;
import androidx.paging.PagedList;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.happypeng.sumatora.android.sumatoradictionary.DictionaryBookmarksImportActivity;
import org.happypeng.sumatora.android.sumatoradictionary.DictionaryPagedListAdapter;
import org.happypeng.sumatora.android.sumatoradictionary.DictionarySearchElementViewHolder;
import org.happypeng.sumatora.android.sumatoradictionary.R;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryLanguage;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchElement;
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.QueryTool;
import org.happypeng.sumatora.android.sumatoradictionary.model.DictionaryBookmarkImportActivityModel;
import org.happypeng.sumatora.android.sumatoradictionary.model.DictionarySearchFragmentModel;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

public class DictionarySearchFragment extends Fragment {
    private ImageButton m_search_button;
    private ImageButton m_magic_cross;

    private EditText m_edit_text;

    private ProgressBar m_progress_bar;
    private TextView m_status_text;

    private boolean m_ready;

    private String m_intentSearchTerm;

    private DictionarySearchFragmentModel m_viewModel;

    private TextView m_languageText;

    private PopupMenu m_languagePopupMenu;

    public DictionarySearchFragment() {
        // Required empty public constructor
    }

    private void setInPreparation()
    {
        if (m_ready) {
            m_status_text.setVisibility(View.VISIBLE);
            m_progress_bar.setVisibility(View.VISIBLE);

            m_progress_bar.setIndeterminate(true);
            m_progress_bar.animate();

            m_search_button.setEnabled(false);

            m_status_text.setText("Loading database...");

            m_ready = false;
        }
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
        if (!m_ready) {
            m_progress_bar.setIndeterminate(false);
            m_progress_bar.setMax(0);

            m_search_button.setEnabled(true);

            m_status_text.setText("");

            m_status_text.setVisibility(View.GONE);
            m_progress_bar.setVisibility(View.GONE);

            m_ready = true;
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        m_languagePopupMenu = null;

        final AppCompatActivity activity = (AppCompatActivity) getActivity();
        final View view = inflater.inflate(R.layout.fragment_dictionary_search, container, false);

        final Toolbar tb = (Toolbar) view.findViewById(R.id.nav_toolbar);
        activity.setSupportActionBar(tb);

        final ActionBar actionBar = activity.getSupportActionBar();
        actionBar.setHomeAsUpIndicator(R.drawable.ic_menu_white_24dp);
        actionBar.setDisplayHomeAsUpEnabled(true);

        setHasOptionsMenu(true);

        m_languageText = (TextView) view.findViewById(R.id.search_fragment_language_text);

        m_search_button = (ImageButton) view.findViewById(R.id.button);

        m_magic_cross = (ImageButton) view.findViewById(R.id.magic_cross);
        m_edit_text = (EditText) view.findViewById(R.id.editText);

        m_progress_bar = (ProgressBar) view.findViewById(R.id.progressBar);
        m_status_text = (TextView) view.findViewById(R.id.statusText);

        RecyclerView m_recyclerView = (RecyclerView) view.findViewById(R.id.recyclerView);

        m_ready = true;

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

        final DictionaryPagedListAdapter pagedListAdapter = new DictionaryPagedListAdapter(m_viewModel.getDictionaryApplication().getSettings());

        m_viewModel.getDictionaryQuery().observe(this);

        m_viewModel.getDictionaryQuery().getSearchEntries().observe(this,
                new Observer<PagedList<DictionarySearchElement>>() {
                    @Override
                    public void onChanged(PagedList<DictionarySearchElement> dictionarySearchElements) {
                        if (dictionarySearchElements != null) {
                            pagedListAdapter.submitList(dictionarySearchElements);

                            setReady();
                        } else {
                            setInPreparation();
                        }
                    }
                });

        m_viewModel.getBookmarksHash().observe(this,
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
                m_viewModel.getDictionaryQuery().setQueryTerm(m_edit_text.getText().toString());
            }
        });

        m_magic_cross.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                m_edit_text.setText("");
            }
        });

        if (m_intentSearchTerm != null) {
            processIntentTerm(m_intentSearchTerm);
        }

        m_viewModel.getDictionaryApplication().getDictionaryLanguage().observe
                (this, new Observer<List<DictionaryLanguage>>() {
                    @Override
                    public void onChanged(List<DictionaryLanguage> dictionaryLanguages) {
                        m_languagePopupMenu = initLanguagePopupMenu(m_languageText, dictionaryLanguages);
                    }
                });

        m_viewModel.getDictionaryApplication().getSettings().getLang().observe(this, new Observer<String>() {
            @Override
            public void onChanged(String s) {
                m_languageText.setText(s);

                pagedListAdapter.notifyDataSetChanged();
            }
        });

        m_languageText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (m_languagePopupMenu != null) {
                    m_languagePopupMenu.show();
                }
            }
        });

        return view;
    }

    public void processIntentTerm(String aTerm)
    {
        if (m_viewModel != null) {
            m_edit_text.setText("");
            m_edit_text.append(aTerm);
            m_viewModel.getDictionaryQuery().setQueryTerm(aTerm);
            m_intentSearchTerm = null;
        } else {
            m_intentSearchTerm = aTerm;
        }
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
                        m_viewModel.getDictionaryApplication().getSettings().setLang(l.lang);

                        return false;
                    }
                });
            }
        }

        return popupMenu;
    }
}
