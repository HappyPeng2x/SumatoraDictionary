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

package org.happypeng.sumatora.android.sumatoradictionary;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.TypedArray;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryLanguage;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchElement;
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentDatabase;
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.BookmarkImportTool;
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.Settings;
import org.happypeng.sumatora.android.sumatoradictionary.fragment.DictionaryBookmarkFragment;
import org.happypeng.sumatora.android.sumatoradictionary.fragment.DictionarySearchFragment;
import org.happypeng.sumatora.android.sumatoradictionary.model.DictionaryBookmarkFragmentModel;
import org.happypeng.sumatora.android.sumatoradictionary.model.DictionaryBookmarkImportActivityModel;
import org.happypeng.sumatora.android.sumatoradictionary.xml.DictionaryBookmarkXML;

import java.io.FileDescriptor;
import java.io.InputStream;
import java.util.List;

public class DictionaryBookmarksImportActivity extends AppCompatActivity {
    private ProgressBar m_progressBar;
    private TextView m_statusText;
    private List<DictionarySearchElement> m_bookmarks;
    private TextView m_languageText;

    private PopupMenu m_languagePopupMenu;

    private DictionaryBookmarkImportActivityModel m_viewModel;

    private boolean m_ready;

    private String m_currentLang;
    private String m_currentBackupLang;

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
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dictionary_bookmarks_import);

        // set up toolbar
        final Toolbar tb = (Toolbar) findViewById(R.id.dictionary_bookmarks_import_toolbar);
        setSupportActionBar(tb);

        m_languageText = (TextView) findViewById(R.id.import_fragment_language_text);

        // set up progress bar
        m_progressBar = (ProgressBar) findViewById(R.id.dictionary_bookmarks_import_progressbar);
        m_statusText = (TextView) findViewById(R.id.dictionary_bookmarks_import_statustext);

        // set up recycler view
        RecyclerView m_recyclerView = (RecyclerView) findViewById(R.id.dictionary_bookmarks_import_recyclerview);

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setOrientation(RecyclerView.VERTICAL);
        m_recyclerView.setLayoutManager(layoutManager);

        m_viewModel = ViewModelProviders.of(this).get(DictionaryBookmarkImportActivityModel.class);

        final DictionarySearchElementViewHolder.Status viewHolderStatus = new DictionarySearchElementViewHolder.Status();
        final DictionaryListAdapter listAdapter = new DictionaryListAdapter(true, viewHolderStatus);

        listAdapter.submitList(null);

        m_recyclerView.setAdapter(listAdapter);

        DividerItemDecoration itemDecor = new DividerItemDecoration(this,
                layoutManager.getOrientation());
        m_recyclerView.addItemDecoration(itemDecor);

        m_ready = true;

        setInPreparation();

        // perform intent
        Intent receivedIntent = getIntent();
        String receivedAction = getIntent().getAction();
        String receivedType = receivedIntent.getType();

        if (receivedAction == null) {
            finish();
            return;
        }

        final Uri data = receivedIntent.getData();

        if (data == null) {
            finish();
            return;
        }

        m_viewModel.setUri(data);

        final DictionaryApplication app = (DictionaryApplication) m_viewModel.getApplication();

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

        m_viewModel.getStatus().observe(this,
                new Observer<DictionaryBookmarkImportActivityModel.Status>() {
                    @Override
                    public void onChanged(DictionaryBookmarkImportActivityModel.Status status) {
                        if (status.toolStatus == BookmarkImportTool.STATUS_ERROR) {
                            setReady();

                            AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(DictionaryBookmarksImportActivity.this);

                            alertDialogBuilder.setMessage("Impossible to import file. Please check the contents.");
                            alertDialogBuilder.setTitle("Error");
                            alertDialogBuilder.setCancelable(true);

                            alertDialogBuilder.setPositiveButton("OK",
                                    new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            finish();
                                        }
                                    });

                            alertDialogBuilder.create().show();
                        } else if (status.isInitialized()) {
                            setReady();

                            if (m_bookmarks != status.bookmarkElements) {
                                listAdapter.submitList(status.bookmarkElements);
                                m_bookmarks = status.bookmarkElements;
                            }

                            if (m_currentLang == null || !m_currentLang.equals(status.lang)) {
                                m_currentLang = status.lang;

                                m_languageText.setText(status.lang);

                                viewHolderStatus.lang = status.lang;

                                listAdapter.notifyDataSetChanged();
                            }

                            if (m_currentBackupLang == null || !m_currentBackupLang.equals(status.backupLang)) {
                                m_currentBackupLang = status.backupLang;

                                listAdapter.notifyDataSetChanged();
                            }
                        } else {
                            setInPreparation();
                        }
                    }
                });
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        boolean res = super.onCreateOptionsMenu(menu);
        final DictionaryBookmarkImportActivityModel viewModel = ViewModelProviders.of(DictionaryBookmarksImportActivity.this).get(DictionaryBookmarkImportActivityModel.class);

        getMenuInflater().inflate(R.menu.bookmark_import_toolbar_menu, menu);

        menu.findItem(R.id.import_bookmarks).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                if (m_bookmarks != null) {
                    viewModel.commitBookmarks();

                    finish();
                } else {
                    System.err.println("Clicked on validate before loading bookmarks to import");
                }

                return false;
            }
        });

        menu.findItem(R.id.cancel_import).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                viewModel.cancelImport();

                finish();

                return false;
            }
        });

        colorMenu(menu);

        return res;
    }

    private void colorMenu(Menu aMenu) {
        TypedValue typedValue = new TypedValue();

        TypedArray a = obtainStyledAttributes(typedValue.data, new int[] { R.attr.colorButtonNormal });
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
            popupMenu = new PopupMenu(this, aAnchor);
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
}
