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
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.FileProvider;
import androidx.core.view.MenuItemCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.happypeng.sumatora.android.sumatoradictionary.BuildConfig;
import org.happypeng.sumatora.android.sumatoradictionary.DictionaryListAdapter;
import org.happypeng.sumatora.android.sumatoradictionary.DictionarySearchElementViewHolder;
import org.happypeng.sumatora.android.sumatoradictionary.R;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryBookmark;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryLanguage;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchElement;
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.DisplayStatus;
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.Settings;
import org.happypeng.sumatora.android.sumatoradictionary.model.DictionaryBookmarkFragmentModel;
import org.happypeng.sumatora.android.sumatoradictionary.xml.DictionaryBookmarkXML;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.LinkedList;
import java.util.List;

public class DictionaryBookmarkFragment extends Fragment {
    private static final String AUTHORITY = "org.happypeng.sumatora.android.sumatoradictionary.fileprovider";

    private boolean m_ready;

    private ProgressBar m_progressBar;
    private TextView m_statusText;

    private List<DictionarySearchElement> m_bookmarks;

    private TextView m_languageText;

    private DictionaryBookmarkFragmentModel m_viewModel;

    private PopupMenu m_languagePopupMenu;

    private Logger m_log;

    public DictionaryBookmarkFragment() {
        if (BuildConfig.DEBUG_UI) {
            m_log = LoggerFactory.getLogger(this.getClass());
        }
    }

    private void setInPreparation() {
        if (m_ready) {
            m_statusText.setVisibility(View.VISIBLE);
            m_progressBar.setVisibility(View.VISIBLE);

            m_progressBar.setIndeterminate(true);
            m_progressBar.animate();

            m_statusText.setText("Loading database...");

            m_ready = false;
        }
    }

    private void setReady() {
        if (!m_ready) {
            m_progressBar.setIndeterminate(false);
            m_progressBar.setMax(0);

            m_statusText.setText("");

            m_statusText.setVisibility(View.GONE);
            m_progressBar.setVisibility(View.GONE);

            m_ready = true;
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        m_languagePopupMenu = null;

        final AppCompatActivity activity = (AppCompatActivity) getActivity();
        final View view = inflater.inflate(R.layout.fragment_dictionary_bookmark, container, false);

        final Toolbar tb = (Toolbar) view.findViewById(R.id.dictionary_bookmark_fragment_toolbar);
        activity.setSupportActionBar(tb);

        setHasOptionsMenu(true);

        m_languageText = (TextView) view.findViewById(R.id.bookmark_fragment_language_text);

        final ActionBar actionBar = activity.getSupportActionBar();
        actionBar.setHomeAsUpIndicator(R.drawable.ic_menu_white_24dp);
        actionBar.setDisplayHomeAsUpEnabled(true);

        m_progressBar = (ProgressBar) view.findViewById(R.id.dictionary_bookmark_fragment_progressbar);
        m_statusText = (TextView) view.findViewById(R.id.dictionary_bookmark_fragment_statustext);

        m_ready = true;

        setInPreparation();

        RecyclerView m_recyclerView = (RecyclerView) view.findViewById(R.id.dictionary_bookmark_fragment_recyclerview);

        LinearLayoutManager layoutManager = new LinearLayoutManager(getContext());
        layoutManager.setOrientation(RecyclerView.VERTICAL);
        m_recyclerView.setLayoutManager(layoutManager);

        m_viewModel = ViewModelProviders.of(getActivity()).get(DictionaryBookmarkFragmentModel.class);

        final DictionarySearchElementViewHolder.Status viewHolderStatus = new DictionarySearchElementViewHolder.Status();
        final DictionaryListAdapter listAdapter = new DictionaryListAdapter(viewHolderStatus);

        m_recyclerView.setAdapter(listAdapter);

        DividerItemDecoration itemDecor = new DividerItemDecoration(getContext(),
                layoutManager.getOrientation());
        m_recyclerView.addItemDecoration(itemDecor);

        listAdapter.setBookmarkClickListener(new DictionarySearchElementViewHolder.ClickListener() {
            @Override
            public void onClick(View aView, DictionarySearchElement aEntry) {
                m_viewModel.deleteBookmark(aEntry.getSeq());
            }
        });

        m_viewModel.getDictionaryApplication().getDictionaryLanguage().observe
                (this, new Observer<List<DictionaryLanguage>>() {
                    @Override
                    public void onChanged(List<DictionaryLanguage> dictionaryLanguages) {
                        m_languagePopupMenu = initLanguagePopupMenu(m_languageText, dictionaryLanguages);
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

        m_viewModel.getStatus().observe(getViewLifecycleOwner(),
                new Observer<DisplayStatus>() {
                    @Override
                    public void onChanged(DisplayStatus status) {
                        if (status.isInitialized()) {
                            setReady();

                            viewHolderStatus.entities = m_viewModel.getDictionaryApplication().getEntities();

                            if (!m_languageText.getText().toString().equals(status.lang)) {
                                m_languageText.setText(status.lang);
                            }

                            if (viewHolderStatus.lang == null) {
                                viewHolderStatus.lang = status.lang;
                            } else if (!viewHolderStatus.lang.equals(status.lang)) {
                                viewHolderStatus.lang = status.lang;
                                listAdapter.notifyDataSetChanged();
                            }

                            if (m_bookmarks != status.displayElements) {
                                listAdapter.submitList(status.displayElements);
                                m_bookmarks = status.displayElements;
                            }
                        } else {
                            setInPreparation();
                        }
                    }
                });

        return view;
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);

        inflater.inflate(R.menu.bookmark_toolbar_menu, menu);

        menu.findItem(R.id.share_bookmarks).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                shareBookmarks();

                return false;
            }
        });

        colorMenu(menu);
    }

    private void shareBookmarks() {
        if (m_bookmarks == null) {
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
                        List<DictionaryBookmark> bookmarks = new LinkedList<>();

                        for (DictionarySearchElement e : m_bookmarks) {
                            DictionaryBookmark b = new DictionaryBookmark();
                            b.seq = e.getSeq();
                            bookmarks.add(b);
                        }

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

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        // Avoid using old pointers when view has been destroyed
        m_progressBar = null;
        m_statusText = null;
        m_languageText = null;
        m_languagePopupMenu = null;
        m_bookmarks = null;
    }
}
