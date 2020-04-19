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
import android.os.Parcelable;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;

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
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.ValueHolder;
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

    protected M m_viewModel;

    PopupMenu m_languagePopupMenu;

    Logger m_log;

    private DictionarySearchElementViewHolder.Status m_viewHolderStatus;

    SearchView m_searchView;

    String m_term;

    private Bundle m_state;

    protected abstract Class<M> getViewModelClass();
    protected abstract BaseFragmentModelFactory.Creator getViewModelCreator();
    protected abstract int getKey();
    protected abstract String getTitle();
    protected abstract boolean getHasHomeButton();
    protected abstract boolean getDisableBookmarkButton();

    public BaseFragment() {
        if (BuildConfig.DEBUG_BASE_FRAGMENT) {
            m_log = LoggerFactory.getLogger(this.getClass());
        }
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
        if (savedInstanceState == null && m_state != null) {
            savedInstanceState = m_state;
            m_state = null;
        }

        m_languagePopupMenu = null;

        final AppCompatActivity activity = (AppCompatActivity) getActivity();
        final View view = inflater.inflate(R.layout.fragment_dictionary_query, container, false);

        final Toolbar tb = (Toolbar) view.findViewById(R.id.dictionary_bookmark_fragment_toolbar);

        final String title = getTitle();

        if (title != null) {
            tb.setTitle(title);
        }

        activity.setSupportActionBar(tb);

        setHasOptionsMenu(true);

        final ActionBar actionBar = activity.getSupportActionBar();
        actionBar.setHomeAsUpIndicator(R.drawable.ic_menu_white_24dp);
        actionBar.setDisplayHomeAsUpEnabled(getHasHomeButton());

        m_progressBar = view.findViewById(R.id.dictionary_bookmark_fragment_progressbar);
        m_statusText = view.findViewById(R.id.dictionary_bookmark_fragment_statustext);
        m_searchStatusText = view.findViewById(R.id.dictionary_bookmark_fragment_search_status);
        m_recyclerView = view.findViewById(R.id.dictionary_bookmark_fragment_recyclerview);

        setInPreparation();

        m_layoutManager = (LinearLayoutManager) m_recyclerView.getLayoutManager();

        ViewModelProvider provider = new ViewModelProvider(getActivity(),
                new BaseFragmentModelFactory(getActivity().getApplication(),
                        getViewModelCreator()));
        m_viewModel = provider.get(Integer.toString(getKey()), getViewModelClass());

        if (m_term == null) {
            m_term = m_viewModel.getTerm();
        }

        m_viewHolderStatus = new DictionarySearchElementViewHolder.Status();
        m_listAdapter = new DictionaryPagedListAdapter(m_viewHolderStatus, getDisableBookmarkButton());

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
                        m_listAdapter.submitList(dictionarySearchElements,
                                new Runnable() {
                                    @Override
                                    public void run() {
                                        Parcelable layoutManagerStateParcelable = m_viewModel.getLayoutManagerState();

                                        if (layoutManagerStateParcelable != null) {
                                            m_layoutManager.onRestoreInstanceState(layoutManagerStateParcelable);
                                            m_viewModel.setLayoutManagerState(null);
                                        }
                                    }
                                });
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

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        if (m_log != null) {
            m_log.info("onSaveInstanceState");
        }

        super.onSaveInstanceState(outState);

        if (m_viewModel != null) {
            if (m_layoutManager != null) {
                m_viewModel.setLayoutManagerState(m_layoutManager.onSaveInstanceState());
            }

            if (m_searchView != null) {
                m_viewModel.setSearchViewOpenedState(!m_searchView.isIconified());
            }
        }
    }

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

        m_state = new Bundle();
        onSaveInstanceState(m_state);

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
