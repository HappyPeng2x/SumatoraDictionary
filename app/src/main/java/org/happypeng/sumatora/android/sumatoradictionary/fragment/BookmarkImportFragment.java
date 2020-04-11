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
import androidx.arch.core.util.Function;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.Transformations;

import org.happypeng.sumatora.android.sumatoradictionary.DictionaryApplication;
import org.happypeng.sumatora.android.sumatoradictionary.R;
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentDatabase;
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentLanguageSettings;
import org.happypeng.sumatora.android.sumatoradictionary.model.BaseFragmentModel;
import org.happypeng.sumatora.android.sumatoradictionary.model.BaseFragmentModelFactory;
import org.happypeng.sumatora.android.sumatoradictionary.model.BookmarkImportModel;

public class BookmarkImportFragment extends BaseFragment<BookmarkImportModel> {
    @Override
    protected Class<BookmarkImportModel> getViewModelClass() {
        return BookmarkImportModel.class;
    }

    @Override
    protected BaseFragmentModelFactory.Creator getViewModelCreator() {
        return new BaseFragmentModelFactory.Creator() {
            @Override
            public BaseFragmentModel create() {
                final LiveData<Long> tableObserve =
                        Transformations.switchMap(((DictionaryApplication) getActivity().getApplication()).getPersistentDatabase(),
                        new Function<PersistentDatabase, LiveData<Long>>() {
                            @Override
                            public LiveData<Long> apply(PersistentDatabase input) {
                                if (input != null) {
                                    return input.dictionaryBookmarkDao().getFirstLive();
                                }

                                return null;
                            }
                        });

                return new BookmarkImportModel(getActivity().getApplication(),
                        getKey(), "SELECT seq FROM DictionaryBookmarkImport WHERE ref=" + getKey(),
                        true, tableObserve);
            }
        };
    }

    @Override
    protected int getKey() {
        return 3;
    }

    @Override
    protected String getTitle() {
        return "Import bookmarks";
    }

    @Override
    protected boolean getHasHomeButton() {
        return false;
    }

    @Override
    protected boolean getDisableBookmarkButton() {
        return true;
    }

    public BookmarkImportFragment() {
        super();
    }

    private Uri mUri;
    private TextView m_languageText;

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
        m_languageText = languageMenuItem.getActionView().findViewById(R.id.menuitem_language_text);

        if (m_viewModel.getInstalledDictionaries().getValue() != null) {
            initLanguagePopupMenu(m_languageText, m_viewModel.getInstalledDictionaries().getValue());
        }

        m_viewModel.getLanguageSettingsLive().observe(getViewLifecycleOwner(),
                new Observer<PersistentLanguageSettings>() {
                    @Override
                    public void onChanged(PersistentLanguageSettings persistentLanguageSettings) {
                        if (persistentLanguageSettings != null) {
                            m_languageText.setText(persistentLanguageSettings.lang);
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

    @Override
    View getLanguagePopupMenuAnchorView() {
        return m_languageText;
    }

    public void processUri(Uri aUri) {
        if (m_viewModel != null) {
            m_viewModel.processUri(aUri);
        } else {
            mUri = aUri;
        }
    }
}
