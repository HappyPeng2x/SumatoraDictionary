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
import androidx.appcompat.widget.ShareActionProvider;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.FileProvider;
import androidx.core.view.MenuItemCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.happypeng.sumatora.android.sumatoradictionary.DictionaryListAdapter;
import org.happypeng.sumatora.android.sumatoradictionary.DictionarySearchElementViewHolder;
import org.happypeng.sumatora.android.sumatoradictionary.R;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchElement;
import org.happypeng.sumatora.android.sumatoradictionary.model.DictionaryBookmarkFragmentModel;
import org.happypeng.sumatora.android.sumatoradictionary.xml.DictionaryBookmarkXML;

import java.io.File;
import java.util.List;

public class DictionaryBookmarkFragment extends Fragment {
    private static final String AUTHORITY = "org.happypeng.sumatora.android.sumatoradictionary.fileprovider";

    private boolean m_ready;

    private ProgressBar m_progressBar;
    private TextView m_statusText;

    private List<DictionarySearchElement> m_bookmarks;

    private ShareActionProvider m_shareActionProvider;


    public DictionaryBookmarkFragment() {
    }

    private void setInPreparation()
    {
        if (m_ready) {
            m_statusText.setVisibility(View.VISIBLE);
            m_progressBar.setVisibility(View.VISIBLE);

            m_progressBar.setIndeterminate(true);
            m_progressBar.animate();

            m_statusText.setText("Loading database...");

            m_ready = false;
        }
    }

    private void setReady()
    {
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
        final AppCompatActivity activity = (AppCompatActivity) getActivity();
        final View view = inflater.inflate(R.layout.fragment_dictionary_bookmark, container, false);

        final Toolbar tb = (Toolbar) view.findViewById(R.id.dictionary_bookmark_fragment_toolbar);
        activity.setSupportActionBar(tb);

        setHasOptionsMenu(true);

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

        final DictionaryBookmarkFragmentModel viewModel = ViewModelProviders.of(getActivity()).get(DictionaryBookmarkFragmentModel.class);

        final DictionaryListAdapter listAdapter = new DictionaryListAdapter();

        viewModel.getBookmarks().observe(this,
                new Observer<List<DictionarySearchElement>>()
                {
                    @Override
                    public void onChanged(List<DictionarySearchElement> dictionarySearchElements) {
                        if (dictionarySearchElements != null) {
                            setReady();
                        } else {
                            setInPreparation();
                        }

                        listAdapter.submitList(dictionarySearchElements);

                        m_bookmarks = dictionarySearchElements;
                    }
                });

        m_recyclerView.setAdapter(listAdapter);

        listAdapter.setBookmarkClickListener(new DictionarySearchElementViewHolder.ClickListener() {
            @Override
            public void onClick(View aView, DictionarySearchElement aEntry) {
                    viewModel.deleteBookmark(aEntry.getSeq());
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
                        DictionaryBookmarkXML.writeXML(outputFile, m_bookmarks);

                        fileWritten = true;
                    } catch(Exception e) {
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

        TypedArray a = ctx.obtainStyledAttributes(typedValue.data, new int[] { R.attr.colorButtonNormal });
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
}
