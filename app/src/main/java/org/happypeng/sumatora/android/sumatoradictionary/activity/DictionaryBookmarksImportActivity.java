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
import android.view.MenuInflater;
import android.view.MenuItem;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;

import org.happypeng.sumatora.android.sumatoradictionary.R;
import org.happypeng.sumatora.android.sumatoradictionary.component.BookmarkImportComponent;
import org.happypeng.sumatora.android.sumatoradictionary.component.LanguageMenuComponent;
import org.happypeng.sumatora.android.sumatoradictionary.component.LanguageSettingsComponent;
import org.happypeng.sumatora.android.sumatoradictionary.component.PersistentDatabaseComponent;
import org.happypeng.sumatora.android.sumatoradictionary.databinding.FragmentDictionaryQueryBinding;
import org.happypeng.sumatora.android.sumatoradictionary.model.BookmarkImportModel;
import org.happypeng.sumatora.android.sumatoradictionary.model.viewbinding.QueryMenu;
import org.happypeng.sumatora.android.sumatoradictionary.model.viewbinding.FragmentDictionaryQueryBindingUtil;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

@AndroidEntryPoint
public class DictionaryBookmarksImportActivity extends AppCompatActivity {
    @Inject
    BookmarkImportComponent bookmarkImportComponent;

    @Inject
    PersistentDatabaseComponent persistentDatabaseComponent;

    @Inject
    LanguageMenuComponent languageMenuComponent;

    @Inject
    LanguageSettingsComponent languageSettingsComponent;

    protected BookmarkImportModel bookmarkImportModel;

    protected FragmentDictionaryQueryBinding viewBinding;
    protected QueryMenu queryMenu;

    protected CompositeDisposable autoDisposable;

    protected int getKey() { return 3; }
    protected BookmarkImportModel getModel() {
        ViewModelProvider viewModelProvider = new ViewModelProvider(this,
                new BookmarkImportModel.Factory(getApplication(),
                        persistentDatabaseComponent, bookmarkImportComponent, languageSettingsComponent, getKey()));

        return viewModelProvider.get(Integer.toString(getKey()), BookmarkImportModel.class);
    }

    public DictionaryBookmarksImportActivity() {
        autoDisposable = new CompositeDisposable();
        bookmarkImportModel = null;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        viewBinding = FragmentDictionaryQueryBinding.inflate(getLayoutInflater());
        setContentView(viewBinding.getRoot());

        // Decoration
        DividerItemDecoration itemDecor = new DividerItemDecoration(this,
                ((LinearLayoutManager) viewBinding.dictionaryBookmarkFragmentRecyclerview.getLayoutManager()).getOrientation());
        viewBinding.dictionaryBookmarkFragmentRecyclerview.addItemDecoration(itemDecor);

        bookmarkImportModel = getModel();

        // Toolbar configuration
        viewBinding.dictionaryBookmarkFragmentToolbar.setTitle(getTitle());
        setSupportActionBar(viewBinding.dictionaryBookmarkFragmentToolbar);

        final ActionBar actionBar = getSupportActionBar();
        actionBar.setHomeAsUpIndicator(R.drawable.ic_menu_white_24dp);
        actionBar.setDisplayHomeAsUpEnabled(true);

        final Observable<BookmarkImportModel.QueryEvent> queryEventObservable =
                bookmarkImportModel.getQueryEvent().observeOn(AndroidSchedulers.mainThread());

        autoDisposable.add(queryEventObservable.subscribe(queryEvent -> {
            // Update state
            if (queryEvent.executed) {
                FragmentDictionaryQueryBindingUtil.setReady(viewBinding);
            } else {
                FragmentDictionaryQueryBindingUtil.setInPreparation(viewBinding);
            }
        }));

        autoDisposable.add(bookmarkImportModel.getPagedListAdapter().subscribe(adapter ->
                viewBinding.dictionaryBookmarkFragmentRecyclerview.setAdapter(adapter)
        ));

        autoDisposable.add(bookmarkImportComponent.getImportBookmarksObservable().subscribe(p ->
        {
            if (p.first == BookmarkImportComponent.ACTION_CANCEL ||
                    p.first == BookmarkImportComponent.ACTION_IMPORT) {
                finish();
            }
        }));

        // Process received data
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

        bookmarkImportComponent.processURI(data, getKey());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        final boolean ret = super.onCreateOptionsMenu(menu);

        getMenuInflater().inflate(R.menu.bookmark_import_toolbar_menu, menu);

        menu.findItem(R.id.import_bookmarks).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                bookmarkImportComponent.commitBookmarks(getKey());

                return false;
            }
        });

        menu.findItem(R.id.cancel_import).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                bookmarkImportComponent.cancelImport(getKey());

                return false;
            }
        });

        autoDisposable.add(languageMenuComponent.initLanguagePopupMenu(menu.findItem(R.id.bookmark_import_fragment_menu_language_text)
                .getActionView().findViewById(R.id.menuitem_language_text)));

        QueryMenu.colorMenu(menu, this);

        return ret;
    }
}
