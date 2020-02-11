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

import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.Observer;

import org.happypeng.sumatora.android.sumatoradictionary.R;
import org.happypeng.sumatora.android.sumatoradictionary.model.BaseFragmentModel;
import org.happypeng.sumatora.android.sumatoradictionary.model.BookmarkImportModel;

public class BookmarkImportFragment extends BaseFragment<BookmarkImportModel> {
    public BookmarkImportFragment() {
        super();
    }

    private Uri mUri;

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);

        inflater.inflate(R.menu.bookmark_import_toolbar_menu, menu);

        final FragmentActivity activity = getActivity();

        menu.findItem(R.id.import_bookmarks).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                if (m_viewModel.getUriImported()) {
                    m_viewModel.commitBookmarks();

                    if (activity != null) {
                        activity.finish();
                    }
                }

                return false;
            }
        });

        menu.findItem(R.id.cancel_import).setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                m_viewModel.cancelImport();

                if (activity != null) {
                    activity.finish();
                }

                return false;
            }
        });

        MenuItem languageMenuItem = menu.findItem(R.id.bookmark_import_fragment_menu_language_text);
        final TextView languageText = languageMenuItem.getActionView().findViewById(R.id.menuitem_language_text);

        m_viewModel.setLangSelectionMenuStatusView(languageText);

        m_viewModel.getLangSelectionStatus().observe(getViewLifecycleOwner(),
                new Observer<BaseFragmentModel.LangSelectionStatus>() {
                    @Override
                    public void onChanged(BaseFragmentModel.LangSelectionStatus langSelectionStatus) {
                        languageText.setText(langSelectionStatus.lang);

                        m_listAdapter.notifyDataSetChanged();

                        m_viewHolderStatus.lang = langSelectionStatus.lang;
                    }
                });


        languageText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (m_languagePopupMenu != null) {
                    m_languagePopupMenu.show();
                }
            }
        });

        colorMenu(menu);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View v = super.onCreateView(inflater, container, savedInstanceState);

        if (mUri != null) {
            m_viewModel.processUri(mUri);
            mUri = null;
        }

        return v;
    }

    public void setParameters(int aKey) {
        setParameters(BookmarkImportModel.class, aKey,
                "SELECT seq FROM DictionaryBookmarkImport WHERE ref=" + aKey, true,
                "Import bookmarks", "DictionaryBookmarkImport", false, true);
    }

    public void processUri(Uri aUri) {
        if (m_viewModel != null) {
            m_viewModel.processUri(aUri);
        } else {
            mUri = aUri;
        }
    }
}
