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

import androidx.arch.core.util.Function;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import org.happypeng.sumatora.android.sumatoradictionary.DictionaryApplication;
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentDatabase;

public class BookmarkFragment extends QueryFragment {
    @Override
    protected String getSearchSet() {
        return "SELECT seq FROM DictionaryBookmark";
    }

    @Override
    protected boolean getAllowSearchAll() {
        return true;
    }

    @Override
    protected LiveData<Long> getTableObserve(DictionaryApplication aApp) {
        return Transformations.switchMap(aApp.getPersistentDatabase(),
                new Function<PersistentDatabase, LiveData<Long>>() {
                    @Override
                    public LiveData<Long> apply(PersistentDatabase input) {
                        if (input != null) {
                            return input.dictionaryBookmarkDao().getFirstLive();
                        }

                        return null;
                    }
                });
    }

    @Override
    protected boolean getAllowExport() {
        return true;
    }

    @Override
    protected boolean getOpenSearchBox() {
        return false;
    }

    @Override
    protected int getKey() {
        return 2;
    }

    @Override
    protected String getTitle() {
        return "Bookmarks";
    }

    @Override
    protected boolean getHasHomeButton() {
        return true;
    }

    @Override
    protected boolean getDisableBookmarkButton() {
        return false;
    }
}
