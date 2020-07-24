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

package org.happypeng.sumatora.android.sumatoradictionary.fragment;


import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;

import org.happypeng.sumatora.android.sumatoradictionary.R;
import org.happypeng.sumatora.android.sumatoradictionary.component.BookmarkComponent;
import org.happypeng.sumatora.android.sumatoradictionary.component.BookmarkShareComponent;
import org.happypeng.sumatora.android.sumatoradictionary.component.LanguageMenuComponent;
import org.happypeng.sumatora.android.sumatoradictionary.component.LanguageSettingsComponent;
import org.happypeng.sumatora.android.sumatoradictionary.component.PersistentDatabaseComponent;
import org.happypeng.sumatora.android.sumatoradictionary.databinding.FragmentDictionaryQueryBinding;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchElement;
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.BookmarkQueryTool;
import org.happypeng.sumatora.android.sumatoradictionary.model.BookmarkFragmentModel;
import org.happypeng.sumatora.android.sumatoradictionary.model.viewbinding.BookmarkMenu;
import org.happypeng.sumatora.android.sumatoradictionary.model.viewbinding.FragmentDictionaryQueryBindingUtil;
import org.happypeng.sumatora.android.sumatoradictionary.viewholder.DictionarySearchElementViewHolder;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

@AndroidEntryPoint
public class BookmarkFragment extends Fragment {
    @Inject
    BookmarkComponent bookmarkComponent;

    @Inject
    PersistentDatabaseComponent persistentDatabaseComponent;

    @Inject
    LanguageMenuComponent languageMenuComponent;

    @Inject
    LanguageSettingsComponent languageSettingsComponent;

    @Inject
    BookmarkShareComponent bookmarkShareComponent;

    protected BookmarkFragmentModel bookmarkFragmentModel;

    protected FragmentDictionaryQueryBinding viewBinding;
    protected BookmarkMenu bookmarkMenu;

    private String currentTerm;

    protected CompositeDisposable autoDisposable;

    protected Bundle savedInstanceState;

    /* Redefine on inheritance */
    protected String getTitle() {
        return "Bookmarks";
    }
    protected int getKey() {
        return 2;
    }
    protected boolean getSearchIconifiedByDefault() { return true; }

    protected BookmarkFragmentModel getModel() {
        // Provider needs to be attached to activity to survive configuration changes
        ViewModelProvider viewModelProvider = new ViewModelProvider(getActivity(),
                new BookmarkFragmentModel.Factory(getActivity().getApplication(),
                        persistentDatabaseComponent, bookmarkComponent, languageSettingsComponent,
                        (l) -> new BookmarkQueryTool(persistentDatabaseComponent, getKey(), l),
                        getKey()));

        return viewModelProvider.get(Integer.toString(getKey()), BookmarkFragmentModel.class);
    }

    public BookmarkFragment() {
        autoDisposable = new CompositeDisposable();

        currentTerm = "";
        bookmarkFragmentModel = null;
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);

        if (autoDisposable == null) {
            autoDisposable = new CompositeDisposable();
        }

        this.savedInstanceState = savedInstanceState;

        viewBinding = FragmentDictionaryQueryBinding.inflate(inflater);

        // Decoration
        DividerItemDecoration itemDecor = new DividerItemDecoration(getContext(),
                ((LinearLayoutManager) viewBinding.dictionaryBookmarkFragmentRecyclerview.getLayoutManager()).getOrientation());
        viewBinding.dictionaryBookmarkFragmentRecyclerview.addItemDecoration(itemDecor);

        bookmarkFragmentModel = getModel();

        // Toolbar configuration
        viewBinding.dictionaryBookmarkFragmentToolbar.setTitle(getTitle());
        ((AppCompatActivity) getActivity()).setSupportActionBar(viewBinding.dictionaryBookmarkFragmentToolbar);

        setHasOptionsMenu(true);

        final ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        actionBar.setHomeAsUpIndicator(R.drawable.ic_menu_white_24dp);
        actionBar.setDisplayHomeAsUpEnabled(true);

        final Observable<BookmarkFragmentModel.QueryEvent> queryEventObservable =
                bookmarkFragmentModel.getQueryEvent().observeOn(AndroidSchedulers.mainThread());

        autoDisposable.add(queryEventObservable.subscribe(queryEvent -> {
            currentTerm = queryEvent.term;

            if (queryEvent.queryTool == null) {
                FragmentDictionaryQueryBindingUtil.setInPreparation(viewBinding);
            } else {
                if (!"".equals(queryEvent.term)) {
                    if (queryEvent.found) {
                        FragmentDictionaryQueryBindingUtil.setResultsFound(viewBinding, queryEvent.term);
                    } else {
                        FragmentDictionaryQueryBindingUtil.setNoResultsFound(viewBinding, queryEvent.term);
                    }
                } else {
                    FragmentDictionaryQueryBindingUtil.setReady(viewBinding);
                }
            }

            if (bookmarkMenu != null) {
                bookmarkMenu.filterMemos.setChecked(queryEvent.filterMemos);
                bookmarkMenu.filterBookmarks.setChecked(queryEvent.filterBookmarks);
            }
        }));

        autoDisposable.add(bookmarkFragmentModel.getPagedListAdapter().subscribe(adapter -> {
            viewBinding.dictionaryBookmarkFragmentRecyclerview.setAdapter(adapter);

            adapter.setBookmarkClickListener(new DictionarySearchElementViewHolder.EventListener() {
                @Override
                public void onBookmarkClick(View aView, DictionarySearchElement aEntry) {
                    bookmarkFragmentModel.toggleBookmark(aEntry);
                }

                @Override
                public void onMemoEdit(DictionarySearchElement aEntry, String aString) {
                    bookmarkFragmentModel.editMemo(aEntry, aString);
                }
            });
        }));

        focusSearchView();

        return viewBinding.getRoot();
    }


    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        if (bookmarkMenu != null) {
            bookmarkMenu.onSaveInstanceState(outState);
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);

        bookmarkMenu = new BookmarkMenu();

        autoDisposable.add(bookmarkMenu.onCreateOptionsMenu(getActivity().getComponentName(),
                menu, inflater, getContext(), languageMenuComponent));

        bookmarkMenu.searchView.setIconifiedByDefault(getSearchIconifiedByDefault());

        bookmarkMenu.searchCloseButton.setOnClickListener(v -> {
            if ("".equals(currentTerm)) {
                bookmarkMenu.searchView.setIconified(true);
            } else {
                bookmarkMenu.searchView.setQuery("", true);

                if (!"".equals(currentTerm)) {
                    bookmarkFragmentModel.setTerm("");
                }
            }
        });

        bookmarkMenu.searchAutoComplete.setOnEditorActionListener((v, actionId, event) -> {
            if ("".equals(bookmarkMenu.searchAutoComplete.getText().toString())) {
                if (!"".equals(currentTerm)) {
                    bookmarkFragmentModel.setTerm("");
                }
            }

            return false;
        });

        autoDisposable.add(bookmarkFragmentModel.getQueryEvent().map(e -> e.term).distinctUntilChanged()
                .observeOn(AndroidSchedulers.mainThread()).subscribe(s -> {
                    if (!s.equals(bookmarkMenu.searchView.getQuery().toString())) {
                        bookmarkMenu.searchView.setQuery(s, false);
                    }
                }));

        if (savedInstanceState != null) {
            bookmarkMenu.restoreInstanceState(savedInstanceState);
        }

        bookmarkMenu.filterBookmarks.setOnMenuItemClickListener(item -> {
            bookmarkFragmentModel.filterBookmarks(!item.isChecked());

            return false;
        });

        bookmarkMenu.filterMemos.setOnMenuItemClickListener(item -> {
            bookmarkFragmentModel.filterMemos(!item.isChecked());

            return false;
        });

        bookmarkMenu.shareBookmarks.setOnMenuItemClickListener(item -> {
            autoDisposable.add(bookmarkShareComponent.shareBookmarks());

            return false;
        });

        focusSearchView();
    }

    public void setIntentSearchTerm(@NonNull String aIntentSearchTerm) {
        bookmarkFragmentModel.setTerm(aIntentSearchTerm);

        if (bookmarkMenu != null && bookmarkMenu.searchView != null && !aIntentSearchTerm.equals(bookmarkMenu.searchView.getQuery().toString())) {
            bookmarkMenu.searchView.setQuery(aIntentSearchTerm, false);
        }
    }

    public void focusSearchView() {
        if (bookmarkMenu != null && bookmarkMenu.searchView != null) {
            bookmarkMenu.searchView.requestFocus();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        autoDisposable.dispose();

        autoDisposable = null;

        viewBinding = null;
        bookmarkMenu = null;
    }
}
