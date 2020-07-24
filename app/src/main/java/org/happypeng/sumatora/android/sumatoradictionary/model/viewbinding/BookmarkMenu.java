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

package org.happypeng.sumatora.android.sumatoradictionary.model.viewbinding;

import android.app.SearchManager;
import android.content.ComponentName;
import android.content.Context;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SearchView;

import org.happypeng.sumatora.android.sumatoradictionary.R;
import org.happypeng.sumatora.android.sumatoradictionary.component.LanguageMenuComponent;

import io.reactivex.rxjava3.disposables.Disposable;

public class BookmarkMenu extends QueryMenu {
    public MenuItem shareBookmarks;
    public MenuItem filterBookmarks;
    public MenuItem filterMemos;

    @Override
    public Disposable onCreateOptionsMenu(final @NonNull ComponentName componentName,
                                          final @NonNull Menu menu, final @NonNull MenuInflater inflater,
                                          final @NonNull Context context,
                                          final @NonNull LanguageMenuComponent languageMenuComponent) {
        inflater.inflate(R.menu.bookmark_query_menu, menu);

        shareBookmarks = menu.findItem(R.id.share_bookmarks);
        filterBookmarks = menu.findItem(R.id.bookmark_filter_bookmarks);
        filterMemos = menu.findItem(R.id.bookmark_filter_memos);

        SearchManager searchManager = (SearchManager) context.getSystemService(Context.SEARCH_SERVICE);
        MenuItem searchViewMenuItem = menu.findItem(R.id.bookmark_fragment_menu_search);

        searchView = (SearchView) searchViewMenuItem.getActionView();
        searchView.setSearchableInfo(searchManager.getSearchableInfo(componentName));

        searchCloseButton = searchView.findViewById(R.id.search_close_btn);

        MenuItem languageMenuItem = menu.findItem(R.id.bookmark_fragment_menu_language_text);

        Disposable disposable = languageMenuComponent.initLanguagePopupMenu(languageMenuItem.getActionView().findViewById(R.id.menuitem_language_text));

        searchView.requestFocus();

        searchAutoComplete =
                searchView.findViewById(androidx.appcompat.R.id.search_src_text);

        colorMenu(menu, context);

        return disposable;
    }
}
