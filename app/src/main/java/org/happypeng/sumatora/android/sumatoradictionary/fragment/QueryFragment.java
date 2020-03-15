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
import android.net.Uri;
import android.os.AsyncTask;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SearchView;
import androidx.core.content.FileProvider;
import androidx.lifecycle.Observer;

import org.happypeng.sumatora.android.sumatoradictionary.R;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryBookmark;
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistantLanguageSettings;
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentDatabase;
import org.happypeng.sumatora.android.sumatoradictionary.model.BaseFragmentModel;
import org.happypeng.sumatora.android.sumatoradictionary.xml.DictionaryBookmarkXML;

import java.io.File;
import java.util.List;

public class QueryFragment extends BaseFragment<BaseFragmentModel> {
    private boolean m_allowExport;
    private boolean m_openSearchBox;
    private TextView m_languageText;

    public QueryFragment() {
        super();

        m_allowExport = false;
        m_openSearchBox = false;
    }

    @Override
    View getLanguagePopupMenuAnchorView() {
        return m_languageText;
    }

    public void setParameters(int a_key, String aSearchSet, boolean aAllowSearchAll,
                              @NonNull String aTitle, boolean aAllowExport,
                              boolean aOpenSearchBox, @NonNull String aTableObserve) {
        setParameters(BaseFragmentModel.class, a_key, aSearchSet, aAllowSearchAll,
                aTitle, aTableObserve, true, false);

        m_allowExport = aAllowExport;
        m_openSearchBox = aOpenSearchBox;
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

        m_searchView.setQuery(m_term, false);

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
                    m_term = m_searchView.getQuery().toString();

                    if (!m_term.equals(m_viewModel.getTerm())) {
                        m_viewModel.setTerm(m_term);
                    }
                }

                return true;
            }
        });

        MenuItem languageMenuItem = menu.findItem(R.id.bookmark_fragment_menu_language_text);
        m_languageText = languageMenuItem.getActionView().findViewById(R.id.menuitem_language_text);

        if (m_viewModel.getInstalledDictionaries().getValue() != null) {
            initLanguagePopupMenu(m_languageText, m_viewModel.getInstalledDictionaries().getValue());
        }

        m_viewModel.getLanguageSettingsLive().observe(getViewLifecycleOwner(),
                new Observer<PersistantLanguageSettings>() {
                    @Override
                    public void onChanged(PersistantLanguageSettings persistantLanguageSettings) {
                        if (persistantLanguageSettings != null) {
                            m_languageText.setText(persistantLanguageSettings.lang);
                        }
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
}
