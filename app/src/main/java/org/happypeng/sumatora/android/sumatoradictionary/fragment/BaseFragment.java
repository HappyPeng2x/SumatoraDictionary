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
import androidx.lifecycle.ViewModelProvider;
import androidx.paging.PagedList;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.happypeng.sumatora.android.sumatoradictionary.BuildConfig;
import org.happypeng.sumatora.android.sumatoradictionary.R;
import org.happypeng.sumatora.android.sumatoradictionary.adapter.DictionaryPagedListAdapter;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchElement;
import org.happypeng.sumatora.android.sumatoradictionary.db.InstalledDictionary;
import org.happypeng.sumatora.android.sumatoradictionary.model.BaseFragmentModel;
import org.happypeng.sumatora.android.sumatoradictionary.model.BaseFragmentModelFactory;
import org.happypeng.sumatora.android.sumatoradictionary.viewholder.DictionarySearchElementViewHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public abstract class BaseFragment<M extends BaseFragmentModel> extends Fragment {
    static final String AUTHORITY = "org.happypeng.sumatora.android.sumatoradictionary.fileprovider";

    private ProgressBar m_progressBar;
    private TextView m_statusText;
    private TextView m_searchStatusText;
    private RecyclerView m_recyclerView;
    private LinearLayoutManager m_layoutManager;

    private DictionaryPagedListAdapter m_listAdapter;

    private Class<M> m_viewModelClass;
    private BaseFragmentModelFactory.Creator m_viewModelCreator;
    M m_viewModel;

    PopupMenu m_languagePopupMenu;

    Logger m_log;

    private DictionarySearchElementViewHolder.Status m_viewHolderStatus;

    SearchView m_searchView;

    private int m_key;
    private String m_title;
    private boolean m_hasHomeButton;
    private boolean m_disableBookmarkButton;

    String m_term;

    public BaseFragment() {
        if (BuildConfig.DEBUG_BASE_FRAGMENT) {
            m_log = LoggerFactory.getLogger(this.getClass());
        }

        m_key = 0;
        m_title = null;

        m_term = "";
    }

    void setParameters(Class<M> a_viewModelClass,
                       BaseFragmentModelFactory.Creator a_viewModelCreator,
                       int a_key, @NonNull String aTitle,
                       boolean aHasHomeButton, boolean aDisableBookmarkButton) {
        m_viewModelClass = a_viewModelClass;
        m_key = a_key;
        m_title = aTitle;
        m_hasHomeButton = aHasHomeButton;
        m_disableBookmarkButton = aDisableBookmarkButton;
        m_viewModelCreator = a_viewModelCreator;
    }

    private void setInPreparation() {
        // m_recyclerView.setAdapter(null);

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
        // m_recyclerView.setAdapter(m_listAdapter);

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

        if ("".equals(m_term)) {
            m_searchStatusText.setVisibility(View.GONE);
            m_searchStatusText.setText("");
        } else {
            m_searchStatusText.setVisibility(View.VISIBLE);
            m_searchStatusText.setText("No results found for term '" + m_term + "'.");
        }

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

        if ("".equals(m_term)) {
            m_searchStatusText.setVisibility(View.GONE);
            m_searchStatusText.setText("");
        } else {
            m_searchStatusText.setVisibility(View.VISIBLE);
            m_searchStatusText.setText("Results for term '" + m_term + "':");
        }

        m_statusText.setVisibility(View.GONE);
        m_progressBar.setVisibility(View.GONE);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        final String restoredTerm = savedInstanceState == null ? null : savedInstanceState.getString("term");
        final int restoredQueryPos = savedInstanceState == null ? 0 : savedInstanceState.getInt("query");
        final int restoredScrollPos = savedInstanceState == null ? 0 : savedInstanceState.getInt("scroll");

        if (m_log != null) {
            m_log.info("onCreateView()");

            m_log.info("Restore information: term '" +
                    (restoredTerm == null ? "" : restoredTerm) +
                    "' query position " + restoredQueryPos +
                    " scroll position " + restoredScrollPos);
        }

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
        m_recyclerView = (RecyclerView) view.findViewById(R.id.dictionary_bookmark_fragment_recyclerview);

        setInPreparation();

        m_layoutManager = new LinearLayoutManager(getContext());
        m_layoutManager.setOrientation(RecyclerView.VERTICAL);
        m_recyclerView.setLayoutManager(m_layoutManager);

        ViewModelProvider provider = new ViewModelProvider(getActivity(),
                new BaseFragmentModelFactory(getActivity().getApplication(),
                        m_viewModelCreator));
        m_viewModel = provider.get(Integer.toString(m_key), m_viewModelClass);

        m_viewHolderStatus = new DictionarySearchElementViewHolder.Status();
        m_listAdapter = new DictionaryPagedListAdapter(m_viewHolderStatus, m_disableBookmarkButton);

        m_recyclerView.setAdapter(m_listAdapter);

        DividerItemDecoration itemDecor = new DividerItemDecoration(getContext(),
                m_layoutManager.getOrientation());
        m_recyclerView.addItemDecoration(itemDecor);

        m_listAdapter.setBookmarkClickListener(new DictionarySearchElementViewHolder.ClickListener() {
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
                new Observer<Integer>() {
                    @Override
                    public void onChanged(Integer status) {
                        m_viewHolderStatus.entities = m_viewModel.getDictionaryApplication().getEntities();

                        if (status == null || status == BaseFragmentModel.STATUS_PRE_INITIALIZED) {
                            setInPreparation();
                        } else if (status == BaseFragmentModel.STATUS_INITIALIZED) {
                            setReady();
                        } else if (status == BaseFragmentModel.STATUS_SEARCHING) {
                            setSearching();
                        } else if (status == BaseFragmentModel.STATUS_RESULTS_FOUND ||
                                status == BaseFragmentModel.STATUS_RESULTS_FOUND_ENDED) {
                            setResultsFound();
                        } else if (status == BaseFragmentModel.STATUS_NO_RESULTS_FOUND_ENDED) {
                            setNoResultsFound();
                        }
                    }
                });

        m_viewModel.getDisplayElements().observe(getViewLifecycleOwner(),
                new Observer<PagedList<DictionarySearchElement>>() {
                    @Override
                    public void onChanged(PagedList<DictionarySearchElement> dictionarySearchElements) {
                        m_listAdapter.submitList(dictionarySearchElements);
                    }
                });

        m_viewModel.getInstalledDictionaries().observe(getViewLifecycleOwner(),
                new Observer<List<InstalledDictionary>>() {
                    @Override
                    public void onChanged(List<InstalledDictionary> installedDictionaries) {
                        initLanguagePopupMenu(getLanguagePopupMenuAnchorView(),
                                installedDictionaries);
                    }
                });

        return view;
    }

    /*
    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        if (m_log != null) {
            m_log.info("onSaveInstanceState()");
        }

        super.onSaveInstanceState(outState);

        if (m_layoutManager != null && m_viewModel != null) {
            final int scrollPos = m_layoutManager.findFirstVisibleItemPosition();
            final int queryPos =  m_viewModel.getQueryPosition();
            final String term = m_viewModel.getTerm();

            outState.putInt("scroll", scrollPos);
            outState.putInt("query", queryPos);
            outState.putString("term", term);

            if (m_log != null) {
                m_log.info("Save information: scroll position " + scrollPos + ", query position " + queryPos + ", term '" + term + "'");
            }
        }
    }
     */

    abstract View getLanguagePopupMenuAnchorView();

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

    void initLanguagePopupMenu(final View aAnchor, final List<InstalledDictionary> aLanguage) {
        if (aLanguage != null) {
            m_languagePopupMenu = new PopupMenu(getContext(), aAnchor);
            Menu menu = m_languagePopupMenu.getMenu();

            for (final InstalledDictionary l : aLanguage) {
                if ("jmdict_translation".equals(l.type)) {
                    menu.add(l.description).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                        @Override
                        public boolean onMenuItemClick(MenuItem item) {
                            if (!l.lang.equals("eng")) {
                                m_viewModel.getDictionaryApplication().setPersistentLanguageSettings(l.lang, "eng");
                            } else {
                                m_viewModel.getDictionaryApplication().setPersistentLanguageSettings(l.lang, null);
                            }

                            return false;
                        }
                    });
                }
            }
        }
    }

    public void setIntentSearchTerm(@NonNull String aIntentSearchTerm) {
        if (m_log != null) {
            m_log.info("setIntentSearchTerm()");
            m_log.info("Term from intent: " + aIntentSearchTerm);
        }

        m_term = aIntentSearchTerm;
        m_viewModel.setTerm(m_term);
    }

    @Override
    public void onDestroyView() {
        if (m_log != null) {
            m_log.info("onDestroyView()");
        }

        super.onDestroyView();

        // Avoid using old pointers when view has been destroyed
        m_progressBar = null;
        m_statusText = null;
        m_searchStatusText = null;
        m_languagePopupMenu = null;
        m_searchView = null;
        m_listAdapter = null;
        m_recyclerView = null;
        m_viewHolderStatus = null;
        m_layoutManager = null;
    }
}
