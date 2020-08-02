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
import android.content.res.TypedArray;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SearchView;

import org.happypeng.sumatora.android.sumatoradictionary.R;
import org.happypeng.sumatora.android.sumatoradictionary.db.InstalledDictionary;

import java.util.List;

public class QueryMenu {
    public SearchView searchView;
    public ImageView searchCloseButton;
    public SearchView.SearchAutoComplete searchAutoComplete;
    public MenuItem shareBookmarks;
    public TextView languageMenuText;

    private static String SEARCH_VIEW_OPENED_STATE = "search_view_opened_state";
    private static String SEARCH_VIEW_ICONIFIED_BY_DEFAULT = "search_view_iconified_by_default";

    public void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putBoolean(SEARCH_VIEW_ICONIFIED_BY_DEFAULT, searchView.isIconfiedByDefault());
        outState.putBoolean(SEARCH_VIEW_OPENED_STATE, searchView.isIconified());
    }

    public void restoreInstanceState(@NonNull Bundle inState) {
        searchView.setIconifiedByDefault(inState.getBoolean(SEARCH_VIEW_ICONIFIED_BY_DEFAULT));
        searchView.setIconified(inState.getBoolean(SEARCH_VIEW_OPENED_STATE));
    }

    public void onCreateOptionsMenu(final @NonNull ComponentName componentName,
                                    final @NonNull Menu menu, final @NonNull MenuInflater inflater,
                                    final @NonNull Context context) {
        inflater.inflate(R.menu.search_query_menu, menu);

        SearchManager searchManager = (SearchManager) context.getSystemService(Context.SEARCH_SERVICE);
        MenuItem searchViewMenuItem = menu.findItem(R.id.search_query_menu_search);

        searchView = (SearchView) searchViewMenuItem.getActionView();
        searchView.setSearchableInfo(searchManager.getSearchableInfo(componentName));

        searchCloseButton = searchView.findViewById(R.id.search_close_btn);

        shareBookmarks = menu.findItem(R.id.search_query_menu_share_bookmarks);

        MenuItem languageMenuItem = menu.findItem(R.id.search_query_menu_language_text);

        languageMenuText = languageMenuItem.getActionView().findViewById(R.id.menuitem_language_text);
        //Disposable disposable = languageMenuComponent.initLanguagePopupMenu(languageMenuItem.getActionView().findViewById(R.id.menuitem_language_text));

        searchView.requestFocus();

        searchAutoComplete =
                searchView.findViewById(androidx.appcompat.R.id.search_src_text);

        colorMenu(menu, context);

        //return disposable;
    }

    public static void colorMenu(@NonNull Menu aMenu, @NonNull Context context) {
        TypedValue typedValue = new TypedValue();

        TypedArray a = context.obtainStyledAttributes(typedValue.data, new int[] { R.attr.colorButtonNormal });
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

    public abstract static class LanguageChangeCallback {
        public abstract void change(String language);
    }

    public void addLanguageMenu(final Context context,
                                final List<InstalledDictionary> installedDictionaries,
                                final LanguageChangeCallback consumer) {
        PopupMenu languagePopupMenu = new PopupMenu(context, languageMenuText);

        languageMenuText.setOnClickListener(v -> {
            languagePopupMenu.show();
        });

        Menu menu = languagePopupMenu.getMenu();

        for (final InstalledDictionary l : installedDictionaries) {
            if ("jmdict_translation".equals(l.type)) {
                menu.add(l.description).setOnMenuItemClickListener(item -> {
                    consumer.change(l.lang);

                    return false;
                });
            }
        }
    }
}