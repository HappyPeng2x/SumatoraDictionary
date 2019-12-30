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

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;
import androidx.paging.PagedList;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.happypeng.sumatora.android.sumatoradictionary.BuildConfig;
import org.happypeng.sumatora.android.sumatoradictionary.adapter.DictionaryPagedListAdapter;
import org.happypeng.sumatora.android.sumatoradictionary.viewholder.DictionarySearchElementViewHolder;
import org.happypeng.sumatora.android.sumatoradictionary.R;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchElement;
import org.happypeng.sumatora.android.sumatoradictionary.db.InstalledDictionary;
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.DisplayStatus;
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.QueryTool;
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.Settings;
import org.happypeng.sumatora.android.sumatoradictionary.model.BaseFragmentModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class BaseFragment<M extends BaseFragmentModel> extends Fragment {
    static final String AUTHORITY = "org.happypeng.sumatora.android.sumatoradictionary.fileprovider";

    private ProgressBar m_progressBar;
    private TextView m_statusText;
    private TextView m_searchStatusText;

    TextView m_languageText;

    Class<M> m_viewModelClass;
    M m_viewModel;

    PopupMenu m_languagePopupMenu;

    private Logger m_log;

    private String m_lang;
    private String m_backupLang;

    SearchView m_searchView;

    private int m_key;
    private String m_searchSet;
    private boolean m_allowSearchAll;
    private String m_title;
    private String m_tableObserve;
    private boolean m_hasHomeButton;
    private boolean m_disableBookmarkButton;

    String m_term;

    public BaseFragment() {
        if (BuildConfig.DEBUG_UI) {
            m_log = LoggerFactory.getLogger(this.getClass());
        }

        m_key = 0;
        m_searchSet = null;
        m_allowSearchAll = false;
        m_title = null;
        m_tableObserve = "";

        m_term = "";
    }

    public void setParameters(Class<M> a_viewModelClass,
                              int a_key, String aSearchSet, boolean aAllowSearchAll,
                              @NonNull String aTitle, @NonNull String aTableObserve,
                              boolean aHasHomeButton, boolean aDisableBookmarkButton) {
        m_viewModelClass = a_viewModelClass;
        m_key = a_key;
        m_searchSet = aSearchSet;
        m_allowSearchAll = aAllowSearchAll;
        m_title = aTitle;
        m_tableObserve = aTableObserve;
        m_hasHomeButton = aHasHomeButton;
        m_disableBookmarkButton = aDisableBookmarkButton;
    }

    private void setInPreparation() {
        m_statusText.setVisibility(View.VISIBLE);
        m_progressBar.setVisibility(View.VISIBLE);

        m_progressBar.setIndeterminate(true);
        m_progressBar.animate();

        m_statusText.setText("Loading database...");
        m_searchStatusText.setVisibility(View.GONE);


        if (m_searchView != null) {
            m_searchView.setActivated(false);
        }
    }

    private void setReady() {
        m_progressBar.setIndeterminate(false);
        m_progressBar.setMax(0);

        m_statusText.setText("");

        m_statusText.setVisibility(View.GONE);
        m_progressBar.setVisibility(View.GONE);
        m_searchStatusText.setVisibility(View.GONE);

        if (m_searchView != null) {
            m_searchView.setActivated(true);
        }
    }

    private void setSearching()
    {
        m_statusText.setVisibility(View.VISIBLE);
        m_progressBar.setVisibility(View.VISIBLE);
        m_searchStatusText.setVisibility(View.GONE);

        m_progressBar.setIndeterminate(true);
        m_progressBar.animate();

        if (m_searchView != null) {
            m_searchView.setActivated(false);
        }

        m_statusText.setText("Searching...");
    }

    private void setNoResultsFound()
    {
        m_progressBar.setIndeterminate(false);
        m_progressBar.setMax(0);

        if (m_searchView != null) {
            m_searchView.setActivated(true);
        }

        m_searchStatusText.setVisibility(View.VISIBLE);
        m_searchStatusText.setText("No results found for term '" + m_term + "'.");

        m_statusText.setVisibility(View.GONE);
        m_progressBar.setVisibility(View.GONE);
    }

    private void setResultsFound()
    {
        m_progressBar.setIndeterminate(false);
        m_progressBar.setMax(0);

        if (m_searchView != null) {
            m_searchView.setActivated(true);
        }

        m_searchStatusText.setVisibility(View.VISIBLE);
        m_searchStatusText.setText("Results for term '" + m_term + "':");

        m_statusText.setVisibility(View.GONE);
        m_progressBar.setVisibility(View.GONE);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        m_languagePopupMenu = null;

        final AppCompatActivity activity = (AppCompatActivity) getActivity();
        final View view = inflater.inflate(R.layout.fragment_dictionary_query, container, false);

        final Toolbar tb = (Toolbar) view.findViewById(R.id.dictionary_bookmark_fragment_toolbar);

        if (m_title != null) {
            tb.setTitle(m_title);
        }

        activity.setSupportActionBar(tb);

        setHasOptionsMenu(true);

        final ActionBar actionBar = activity.getSupportActionBar();
        actionBar.setHomeAsUpIndicator(R.drawable.ic_menu_white_24dp);
        actionBar.setDisplayHomeAsUpEnabled(m_hasHomeButton);

        m_progressBar = (ProgressBar) view.findViewById(R.id.dictionary_bookmark_fragment_progressbar);
        m_statusText = (TextView) view.findViewById(R.id.dictionary_bookmark_fragment_statustext);
        m_searchStatusText = (TextView) view.findViewById(R.id.dictionary_bookmark_fragment_search_status);

        setInPreparation();

        RecyclerView m_recyclerView = (RecyclerView) view.findViewById(R.id.dictionary_bookmark_fragment_recyclerview);

        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        layoutManager.setOrientation(RecyclerView.VERTICAL);
        m_recyclerView.setLayoutManager(layoutManager);

        m_viewModel = ViewModelProviders.of(getActivity()).get(String.valueOf(m_key),
                m_viewModelClass);
        m_viewModel.initialize(m_key, m_searchSet, m_allowSearchAll, m_tableObserve);

        final DictionarySearchElementViewHolder.Status viewHolderStatus = new DictionarySearchElementViewHolder.Status();
        final DictionaryPagedListAdapter listAdapter = new DictionaryPagedListAdapter(viewHolderStatus, m_disableBookmarkButton);

        m_recyclerView.setAdapter(listAdapter);

        DividerItemDecoration itemDecor = new DividerItemDecoration(getContext(),
                layoutManager.getOrientation());
        m_recyclerView.addItemDecoration(itemDecor);

        listAdapter.setBookmarkClickListener(new DictionarySearchElementViewHolder.ClickListener() {
            @Override
            public void onClick(View aView, DictionarySearchElement aEntry) {
                if (aEntry.getBookmark() == 0) {
                    m_viewModel.updateBookmark(aEntry.getSeq(), 1);
                } else {
                    m_viewModel.updateBookmark(aEntry.getSeq(), 0);
                }
            }
        });

        m_viewModel.getStatus().observe(getViewLifecycleOwner(),
                new Observer<DisplayStatus>() {
                    @Override
                    public void onChanged(DisplayStatus status) {
                        if (status == null || !status.isInitialized()) {
                            setInPreparation();

                            return;
                        }

                        viewHolderStatus.entities = m_viewModel.getDictionaryApplication().getEntities();

                        if (m_lang == null || !m_lang.equals(status.lang)) {
                            if (m_languageText != null) {
                                m_languageText.setText(status.lang);
                            }

                            m_lang = status.lang;

                            listAdapter.notifyDataSetChanged();
                        }

                        if ((m_backupLang == null && status.backupLang != null) ||
                                (m_backupLang != null && !m_backupLang.equals(status.backupLang))) {
                            m_backupLang = status.backupLang;

                            listAdapter.notifyDataSetChanged();
                        }

                        viewHolderStatus.lang = status.lang;

                        Integer bookmarkToolStatus = m_viewModel.getBookmarkToolStatus();

                        if (bookmarkToolStatus == null || bookmarkToolStatus == QueryTool.STATUS_PRE_INITIALIZED) {
                            setInPreparation();
                        } else if (bookmarkToolStatus == QueryTool.STATUS_INITIALIZED) {
                            setReady();
                        } else if (bookmarkToolStatus == QueryTool.STATUS_SEARCHING) {
                            setSearching();
                        } else if (bookmarkToolStatus == QueryTool.STATUS_RESULTS_FOUND ||
                                bookmarkToolStatus == QueryTool.STATUS_RESULTS_FOUND_ENDED) {
                            setResultsFound();
                        } else if (bookmarkToolStatus == QueryTool.STATUS_NO_RESULTS_FOUND_ENDED) {
                            setNoResultsFound();
                        }

                        if (status.installedDictionaries != null && m_languageText != null) {
                            // if (m_languageText != null && m_languagePopupMenu == null) {
                                m_languagePopupMenu = initLanguagePopupMenu(m_languageText,
                                        status.installedDictionaries);
                            //}
                        }
                    }
                });

        m_viewModel.getElementList().observe(getViewLifecycleOwner(),
                new Observer<PagedList<DictionarySearchElement>>() {
                    @Override
                    public void onChanged(PagedList<DictionarySearchElement> dictionarySearchElements) {
                        listAdapter.submitList(dictionarySearchElements);
                    }
                });

        return view;
    }

    private void displayInitializationToast() {
        Toast.makeText(getContext(), "Initialization still in progress...", Toast.LENGTH_LONG).show();
    }

    void colorMenu(Menu aMenu) {
        Context ctx = getContext();

        if (ctx == null)
            return;

        TypedValue typedValue = new TypedValue();

        TypedArray a = ctx.obtainStyledAttributes(typedValue.data, new int[]{R.attr.colorButtonNormal});
        int color = a.getColor(0, 0);

        a.recycle();

        for (int i = 0; i < aMenu.size(); i++) {
            MenuItem item = aMenu.getItem(i);
            Drawable icon = item.getIcon();
            if (icon != null) {
                icon.setColorFilter(new PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN));
            }
        }
    }

    private PopupMenu initLanguagePopupMenu(final View aAnchor, final List<InstalledDictionary> aLanguage) {
        PopupMenu popupMenu = null;

        if (aLanguage != null) {
            popupMenu = new PopupMenu(getContext(), aAnchor);
            Menu menu = popupMenu.getMenu();

            for (final InstalledDictionary l : aLanguage) {
                if ("jmdict_translation".equals(l.type)) {
                    menu.add(l.description).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                        @Override
                        public boolean onMenuItemClick(MenuItem item) {
                            m_viewModel.getDictionaryApplication().getSettings().setValue(Settings.LANG, l.lang);

                            return false;
                        }
                    });
                }
            }
        }

        return popupMenu;
    }

    public void setIntentSearchTerm(@NonNull String aIntentSearchTerm) {
        m_term = aIntentSearchTerm;
        m_viewModel.setTerm(m_term);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        // Avoid using old pointers when view has been destroyed
        m_progressBar = null;
        m_statusText = null;
        m_searchStatusText = null;
        m_languageText = null;
        m_languagePopupMenu = null;
        m_searchView = null;
    }
}
