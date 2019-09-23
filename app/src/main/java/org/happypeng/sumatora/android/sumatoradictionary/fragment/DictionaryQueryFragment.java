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


import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.FileProvider;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;
import androidx.paging.PagedList;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.happypeng.sumatora.android.sumatoradictionary.BuildConfig;
import org.happypeng.sumatora.android.sumatoradictionary.DictionaryPagedListAdapter;
import org.happypeng.sumatora.android.sumatoradictionary.DictionarySearchElementViewHolder;
import org.happypeng.sumatora.android.sumatoradictionary.R;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryBookmark;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryLanguage;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchElement;
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentDatabase;
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.QueryTool;
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.DisplayStatus;
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.Settings;
import org.happypeng.sumatora.android.sumatoradictionary.model.DictionaryQueryFragmentModel;
import org.happypeng.sumatora.android.sumatoradictionary.xml.DictionaryBookmarkXML;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;

public class DictionaryQueryFragment extends Fragment {
    private static final String AUTHORITY = "org.happypeng.sumatora.android.sumatoradictionary.fileprovider";

    private ProgressBar m_progressBar;
    private TextView m_statusText;

    private TextView m_languageText;

    private DictionaryQueryFragmentModel m_viewModel;

    private PopupMenu m_languagePopupMenu;

    private Logger m_log;

    private String m_lang;
    private String m_backupLang;

    private SearchView m_searchView;

    private int m_key;
    private String m_searchSet;
    private boolean m_allowSearchAll;
    private String m_title;
    private boolean m_allowExport;
    private boolean m_openSearchBox;
    private String m_tableObserve;

    public DictionaryQueryFragment() {
        if (BuildConfig.DEBUG_UI) {
            m_log = LoggerFactory.getLogger(this.getClass());
        }

        m_key = 0;
        m_searchSet = null;
        m_allowSearchAll = false;
        m_title = null;
        m_allowExport = false;
        m_openSearchBox = false;
        m_tableObserve = "";
    }

    public void setParameters(int a_key, String aSearchSet, boolean aAllowSearchAll,
                              @NonNull String aTitle, boolean aAllowExport,
                              boolean aOpenSearchBox, @NonNull String aTableObserve) {
        m_key = a_key;
        m_searchSet = aSearchSet;
        m_allowSearchAll = aAllowSearchAll;
        m_title = aTitle;
        m_allowExport = aAllowExport;
        m_openSearchBox = aOpenSearchBox;
        m_tableObserve = aTableObserve;
    }

    private void setInPreparation() {
        m_statusText.setVisibility(View.VISIBLE);
        m_progressBar.setVisibility(View.VISIBLE);

        m_progressBar.setIndeterminate(true);
        m_progressBar.animate();

        m_statusText.setText("Loading database...");


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

        if (m_searchView != null) {
            m_searchView.setActivated(true);
        }
    }

    private void setSearching()
    {
        m_statusText.setVisibility(View.VISIBLE);
        m_progressBar.setVisibility(View.VISIBLE);

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

        m_statusText.setText("No results found.");

        m_statusText.setVisibility(View.VISIBLE);
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
        actionBar.setDisplayHomeAsUpEnabled(true);

        m_progressBar = (ProgressBar) view.findViewById(R.id.dictionary_bookmark_fragment_progressbar);
        m_statusText = (TextView) view.findViewById(R.id.dictionary_bookmark_fragment_statustext);

        setInPreparation();

        RecyclerView m_recyclerView = (RecyclerView) view.findViewById(R.id.dictionary_bookmark_fragment_recyclerview);

        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        layoutManager.setOrientation(RecyclerView.VERTICAL);
        m_recyclerView.setLayoutManager(layoutManager);

        m_viewModel = ViewModelProviders.of(getActivity()).get(String.valueOf(m_key),
                DictionaryQueryFragmentModel.class);
        m_viewModel.initialize(m_key, m_searchSet, m_allowSearchAll, m_tableObserve);

        final DictionarySearchElementViewHolder.Status viewHolderStatus = new DictionarySearchElementViewHolder.Status();
        final DictionaryPagedListAdapter listAdapter = new DictionaryPagedListAdapter(viewHolderStatus);

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

        m_viewModel.getDictionaryApplication().getDictionaryLanguage().observe
                (this, new Observer<List<DictionaryLanguage>>() {
                    @Override
                    public void onChanged(List<DictionaryLanguage> dictionaryLanguages) {
                        if (m_languageText != null && m_languagePopupMenu == null) {
                            m_languagePopupMenu = initLanguagePopupMenu(m_languageText,
                                    dictionaryLanguages);
                        }                    }
                });

        m_viewModel.getStatus().observe(getViewLifecycleOwner(),
                new Observer<DisplayStatus>() {
                    @Override
                    public void onChanged(DisplayStatus status) {
                        if (!status.isInitialized()) {
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
                            setReady();
                        } else if (bookmarkToolStatus == QueryTool.STATUS_NO_RESULTS_FOUND_ENDED) {
                            setNoResultsFound();
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

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);

        inflater.inflate(R.menu.bookmark_query_menu, menu);

        if (m_allowExport) {
            menu.findItem(R.id.share_bookmarks).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
                @Override
                public boolean onMenuItemClick(MenuItem item) {
                    shareBookmarks();

                    return false;
                }
            });
        } else {
            menu.findItem(R.id.share_bookmarks).setVisible(false);
        }

        SearchManager searchManager = (SearchManager) getActivity().getSystemService(Context.SEARCH_SERVICE);
        MenuItem searchViewMenuItem = menu.findItem(R.id.bookmark_fragment_menu_search);

        m_searchView = (SearchView) searchViewMenuItem.getActionView();
        m_searchView.setSearchableInfo(searchManager.getSearchableInfo(getActivity().getComponentName()));

        if (!m_openSearchBox) {
            m_searchView.setIconifiedByDefault(true);
        } else {
            m_searchView.setIconifiedByDefault(false);
        }

        final SearchView.SearchAutoComplete mSearchSrcTextView =
                m_searchView.findViewById(androidx.appcompat.R.id.search_src_text);

        mSearchSrcTextView.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (m_searchView.getQuery() != null) {
                    m_viewModel.setTerm(m_searchView.getQuery().toString());
                }

                return false;
            }
        });

        MenuItem languageMenuItem = menu.findItem(R.id.bookmark_fragment_menu_language_text);
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

        colorMenu(menu);
    }

    private void displayInitializationToast() {
        Toast.makeText(getContext(), "Initialization still in progress...", Toast.LENGTH_LONG).show();
    }

    private void shareBookmarks() {
        // return immediately if initialization is not finished
        if (m_viewModel == null) {
            displayInitializationToast();

            return;
        }

        final PersistentDatabase db = m_viewModel.getDictionaryApplication().getPersistentDatabase().getValue();

        if (db == null) {
            displayInitializationToast();

            return;
        }

        try {
            File parentDir = new File(getContext().getFilesDir(), "bookmarks");
            final File outputFile = new File(parentDir, "bookmarks.xml");

            parentDir.mkdirs();

            new AsyncTask<Void, Void, Void>() {
                private boolean fileWritten = false;

                @Override
                protected Void doInBackground(Void... voids) {
                    try {
                        List<DictionaryBookmark> bookmarks = db.dictionaryBookmarkDao().getAll();

                        DictionaryBookmarkXML.writeXML(outputFile, bookmarks);

                        fileWritten = true;
                    } catch (Exception e) {
                        System.err.print(e.toString());
                    }

                    return null;
                }

                @Override
                protected void onPostExecute(Void aVoid) {
                    super.onPostExecute(aVoid);

                    if (fileWritten) {
                        Uri contentUri = FileProvider.getUriForFile
                                (getActivity().getApplicationContext(),
                                        AUTHORITY, outputFile);

                        Intent sharingIntent = new Intent(Intent.ACTION_SEND);

                        sharingIntent.setType("text/*");
                        sharingIntent.putExtra(Intent.EXTRA_STREAM, contentUri);
                        startActivity(Intent.createChooser(sharingIntent, "Share bookmarks"));
                    }
                }
            }.execute();
        } catch (Exception e) {
            Toast.makeText(getContext(), "Bookmarks sharing failed...", Toast.LENGTH_LONG).show();

            System.err.println(e.toString());
        }
    }

    private void colorMenu(Menu aMenu) {
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

    public void setIntentSearchTerm(@NonNull String aIntentSearchTerm) {
        m_viewModel.setTerm(aIntentSearchTerm);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        // Avoid using old pointers when view has been destroyed
        m_progressBar = null;
        m_statusText = null;
        m_languageText = null;
        m_languagePopupMenu = null;
        m_searchView = null;
    }
}
