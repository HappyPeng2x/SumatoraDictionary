/* Sumatora Dictionary
        Copyright (C) 2020 Nicolas Centa

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

package org.happypeng.sumatora.android.sumatoradictionary.activity;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;

import org.happypeng.sumatora.android.sumatoradictionary.R;
import org.happypeng.sumatora.android.sumatoradictionary.component.BookmarkImportComponent;
import org.happypeng.sumatora.android.sumatoradictionary.databinding.FragmentDictionaryQueryBinding;
import org.happypeng.sumatora.android.sumatoradictionary.db.InstalledDictionary;
import org.happypeng.sumatora.android.sumatoradictionary.model.BookmarkImportModel;
import org.happypeng.sumatora.android.sumatoradictionary.model.status.BookmarkImportStatus;
import org.happypeng.sumatora.android.sumatoradictionary.model.status.QueryViewStatus;
import org.happypeng.sumatora.android.sumatoradictionary.model.viewbinding.QueryMenu;
import org.happypeng.sumatora.android.sumatoradictionary.model.viewbinding.FragmentDictionaryQueryBindingUtil;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

@AndroidEntryPoint
public class DictionaryBookmarksImportActivity extends AppCompatActivity {
    @Inject
    BookmarkImportComponent bookmarkImportComponent;

    private FragmentDictionaryQueryBinding viewBinding;

    private CompositeDisposable autoDisposable;


    private BookmarkImportModel getModel() {
        return new ViewModelProvider(this).get(BookmarkImportModel.class);
    }

    public DictionaryBookmarksImportActivity() {
        autoDisposable = new CompositeDisposable();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (autoDisposable == null) {
            autoDisposable = new CompositeDisposable();
        }

        viewBinding = FragmentDictionaryQueryBinding.inflate(getLayoutInflater());
        setContentView(viewBinding.getRoot());

        // Decoration
        DividerItemDecoration itemDecor = new DividerItemDecoration(this,
                ((LinearLayoutManager) viewBinding.dictionaryBookmarkFragmentRecyclerview.getLayoutManager()).getOrientation());
        viewBinding.dictionaryBookmarkFragmentRecyclerview.addItemDecoration(itemDecor);

        final BookmarkImportModel bookmarkImportModel = getModel();

        // Toolbar configuration
        viewBinding.dictionaryBookmarkFragmentToolbar.setTitle(getTitle());
        setSupportActionBar(viewBinding.dictionaryBookmarkFragmentToolbar);

        final ActionBar actionBar = getSupportActionBar();
        actionBar.setHomeAsUpIndicator(R.drawable.ic_menu_white_24dp);
        actionBar.setDisplayHomeAsUpEnabled(true);

        // Receive status
        autoDisposable.add(bookmarkImportModel.getStatusObservable().subscribe(status -> {
            if (status.getClose()) {
                finish();
            }

            if (status.getExecuted()) {
                FragmentDictionaryQueryBindingUtil.setReady(viewBinding);
            } else {
                FragmentDictionaryQueryBindingUtil.setInPreparation(viewBinding);
            }
        }));

        autoDisposable.add(bookmarkImportModel.getPagedListAdapter().subscribe(adapter ->
                viewBinding.dictionaryBookmarkFragmentRecyclerview.setAdapter(adapter)
        ));

        // Process received data as an intent
        Intent receivedIntent = getIntent();
        String receivedAction = getIntent().getAction();

        if (receivedAction == null) {
            finish();
            return;
        }

        final Uri data = receivedIntent.getData();

        if (data == null) {
            finish();
            return;
        }

        bookmarkImportModel.bookmarkImportFileOpen(data);
    }

    @Override
    protected void onDestroy() {
        autoDisposable.dispose();
        autoDisposable = null;

        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        final BookmarkImportModel bookmarkImportModel = getModel();

        final boolean ret = super.onCreateOptionsMenu(menu);

        getMenuInflater().inflate(R.menu.bookmark_import_toolbar_menu, menu);

        menu.findItem(R.id.import_bookmarks).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                bookmarkImportModel.bookmarkImportCommit();

                return false;
            }
        });

        menu.findItem(R.id.cancel_import).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                bookmarkImportModel.bookmarkImportCancel();

                return false;
            }
        });

        final TextView languageMenuText = menu.findItem(R.id.bookmark_import_fragment_menu_language_text).getActionView().findViewById(R.id.menuitem_language_text);
        final PopupMenu languagePopupMenu = new PopupMenu(this, languageMenuText);

        languageMenuText.setOnClickListener(v -> {
            languagePopupMenu.show();
        });

        final Menu languagePopupMenuContent = languagePopupMenu.getMenu();

        autoDisposable.add(bookmarkImportModel.getInstalledDictionaries()
                .distinctUntilChanged()
                .subscribe(list -> {
                    for (int i = 0; i < languagePopupMenuContent.size(); i++) {
                        languagePopupMenuContent.removeItem(i);
                    }

                    for (final InstalledDictionary l : list) {
                        if ("jmdict_translation".equals(l.type)) {
                            languagePopupMenuContent.add(l.description).setOnMenuItemClickListener(item -> {
                                bookmarkImportModel.setLanguage(l.lang);

                                return false;
                            });
                        }
                    }
                }));

        autoDisposable.add(bookmarkImportModel.getStatusObservable()
                .filter(s -> s.getPersistentLanguageSettings() != null)
                .map(BookmarkImportStatus::getPersistentLanguageSettings)
                .distinctUntilChanged()
                .subscribe(l -> languageMenuText.setText(l.lang)));

        QueryMenu.colorMenu(menu, this);

        return ret;
    }
}
