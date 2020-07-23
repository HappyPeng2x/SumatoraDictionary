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
import org.happypeng.sumatora.android.sumatoradictionary.component.LanguageMenuComponent;
import org.happypeng.sumatora.android.sumatoradictionary.component.LanguageSettingsComponent;
import org.happypeng.sumatora.android.sumatoradictionary.component.PersistentDatabaseComponent;
import org.happypeng.sumatora.android.sumatoradictionary.databinding.FragmentDictionaryQueryBinding;
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchElement;
import org.happypeng.sumatora.android.sumatoradictionary.db.tools.DictionarySearchQueryTool;
import org.happypeng.sumatora.android.sumatoradictionary.model.QueryFragmentModel;
import org.happypeng.sumatora.android.sumatoradictionary.model.viewbinding.QueryMenu;
import org.happypeng.sumatora.android.sumatoradictionary.model.viewbinding.FragmentDictionaryQueryBindingUtil;
import org.happypeng.sumatora.android.sumatoradictionary.viewholder.DictionarySearchElementViewHolder;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Observable;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

@AndroidEntryPoint
public class QueryFragment extends Fragment {
    @Inject
    BookmarkComponent bookmarkComponent;

    @Inject
    PersistentDatabaseComponent persistentDatabaseComponent;

    @Inject
    LanguageMenuComponent languageMenuComponent;

    @Inject
    LanguageSettingsComponent languageSettingsComponent;

    protected QueryFragmentModel queryFragmentModel;

    protected FragmentDictionaryQueryBinding viewBinding;
    protected QueryMenu queryMenu;

    private String currentTerm;

    protected CompositeDisposable autoDisposable;

    protected Bundle savedInstanceState;

    /* Redefine on inheritance */
    protected String getTitle() { return "Search"; }
    protected int getKey() { return 1; }
    protected boolean getSearchIconifiedByDefault() { return false; }
    protected QueryFragmentModel getModel() {
        // Provider needs to be attached to activity to survive configuration changes
        ViewModelProvider viewModelProvider = new ViewModelProvider(getActivity(),
                new QueryFragmentModel.Factory(getActivity().getApplication(),
                        persistentDatabaseComponent, bookmarkComponent, languageSettingsComponent,
                        (l) -> new DictionarySearchQueryTool(persistentDatabaseComponent, getKey(), null, l),
                        getKey()));

        return viewModelProvider.get(Integer.toString(getKey()), QueryFragmentModel.class);
    }

    public QueryFragment() {
        autoDisposable = new CompositeDisposable();

        currentTerm = "";
        queryFragmentModel = null;
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

        queryFragmentModel = getModel();

        // Toolbar configuration
        viewBinding.dictionaryBookmarkFragmentToolbar.setTitle(getTitle());
        ((AppCompatActivity) getActivity()).setSupportActionBar(viewBinding.dictionaryBookmarkFragmentToolbar);

        setHasOptionsMenu(true);

        final ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        actionBar.setHomeAsUpIndicator(R.drawable.ic_menu_white_24dp);
        actionBar.setDisplayHomeAsUpEnabled(true);

        final Observable<QueryFragmentModel.QueryEvent> queryEventObservable =
                queryFragmentModel.getQueryEvent().observeOn(AndroidSchedulers.mainThread());

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
        }));

        autoDisposable.add(queryFragmentModel.getPagedListAdapter().subscribe(adapter -> {
            viewBinding.dictionaryBookmarkFragmentRecyclerview.setAdapter(adapter);

            adapter.setBookmarkClickListener(new DictionarySearchElementViewHolder.EventListener() {
                @Override
                public void onBookmarkClick(View aView, DictionarySearchElement aEntry) {
                    queryFragmentModel.toggleBookmark(aEntry);
                }

                @Override
                public void onMemoEdit(DictionarySearchElement aEntry, String aString) {
                    queryFragmentModel.editMemo(aEntry, aString);
                }
            });
        }));

        focusSearchView();

        return viewBinding.getRoot();
    }


    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        if (queryMenu != null) {
            queryMenu.onSaveInstanceState(outState);
        }
    }

    @Override
    public void onCreateOptionsMenu(@NonNull Menu menu, @NonNull MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);

        queryMenu = new QueryMenu();

        autoDisposable.add(queryMenu.onCreateOptionsMenu(getActivity().getComponentName(),
                menu, inflater, getContext(), languageMenuComponent));

        queryMenu.shareBookmarks.setVisible(false);

        queryMenu.searchView.setIconifiedByDefault(getSearchIconifiedByDefault());

        queryMenu.searchCloseButton.setOnClickListener(v -> {
            if ("".equals(currentTerm)) {
                queryMenu.searchView.setIconified(true);
            } else {
                queryMenu.searchView.setQuery("", true);

                if (!"".equals(currentTerm)) {
                    queryFragmentModel.setTerm("");
                }
            }
        });

        queryMenu.searchAutoComplete.setOnEditorActionListener((v, actionId, event) -> {
            if ("".equals(queryMenu.searchAutoComplete.getText().toString())) {
                if (!"".equals(currentTerm)) {
                    queryFragmentModel.setTerm("");
                }
            }

            return false;
        });

        autoDisposable.add(queryFragmentModel.getQueryEvent().map(e -> e.term).distinctUntilChanged()
                .observeOn(AndroidSchedulers.mainThread()).subscribe(s -> {
                    if (!s.equals(queryMenu.searchView.getQuery().toString())) {
                        queryMenu.searchView.setQuery(s, false);
                    }
                }));

        if (savedInstanceState != null) {
            queryMenu.restoreInstanceState(savedInstanceState);
        }

        focusSearchView();
    }

    public void setIntentSearchTerm(@NonNull String aIntentSearchTerm) {
        queryFragmentModel.setTerm(aIntentSearchTerm);

        if (queryMenu != null && queryMenu.searchView != null && !aIntentSearchTerm.equals(queryMenu.searchView.getQuery().toString())) {
            queryMenu.searchView.setQuery(aIntentSearchTerm, false);
        }
    }

    public void focusSearchView() {
        if (queryMenu != null && queryMenu.searchView != null) {
            queryMenu.searchView.requestFocus();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        autoDisposable.dispose();

        autoDisposable = null;

        viewBinding = null;
        queryMenu = null;
    }
}
