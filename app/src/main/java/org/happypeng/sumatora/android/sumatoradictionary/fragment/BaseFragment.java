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
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;

import org.happypeng.sumatora.android.sumatoradictionary.R;
import org.happypeng.sumatora.android.sumatoradictionary.databinding.FragmentDictionaryQueryBinding;
import org.happypeng.sumatora.android.sumatoradictionary.model.BaseQueryFragmentModel;
import org.happypeng.sumatora.android.sumatoradictionary.model.status.QueryViewStatus;
import org.happypeng.sumatora.android.sumatoradictionary.model.viewbinding.FragmentDictionaryQueryBindingUtil;
import org.happypeng.sumatora.android.sumatoradictionary.model.viewbinding.QueryMenu;

import dagger.hilt.android.AndroidEntryPoint;
import io.reactivex.rxjava3.disposables.CompositeDisposable;

@AndroidEntryPoint
public abstract class BaseFragment extends Fragment {
    protected FragmentDictionaryQueryBinding viewBinding;
    protected QueryMenu queryMenu;

    protected CompositeDisposable autoDisposable;

    protected Bundle savedInstanceState;

    protected BaseFragment() {
        autoDisposable = new CompositeDisposable();
    }

    protected BaseQueryFragmentModel getModel() { return null; }

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

        final BaseQueryFragmentModel queryFragmentModel = getModel();

        // Toolbar configuration
        ((AppCompatActivity) getActivity()).setSupportActionBar(viewBinding.dictionaryBookmarkFragmentToolbar);

        setHasOptionsMenu(true);

        final ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        actionBar.setHomeAsUpIndicator(R.drawable.ic_menu_white_24dp);
        actionBar.setDisplayHomeAsUpEnabled(true);

        autoDisposable.add(queryFragmentModel.getStatusObservable().map(QueryViewStatus::getTitle)
                .distinctUntilChanged()
                .subscribe(viewBinding.dictionaryBookmarkFragmentToolbar::setTitle));

        autoDisposable.add(queryFragmentModel.getStatusObservable().subscribe(status -> {
            if (status.getPreparing()) {
                FragmentDictionaryQueryBindingUtil.setInPreparation(viewBinding);
            } else {
                if (!"".equals(status.getTerm())) {
                    if (status.getSearching()) {
                        FragmentDictionaryQueryBindingUtil.setSearching(viewBinding);
                    } else if (status.getFound()) {
                        FragmentDictionaryQueryBindingUtil.setResultsFound(viewBinding, status.getTerm());
                    } else {
                        FragmentDictionaryQueryBindingUtil.setNoResultsFound(viewBinding, status.getTerm());
                    }
                } else {
                    FragmentDictionaryQueryBindingUtil.setReady(viewBinding);
                }
            }
        }));

        autoDisposable.add(queryFragmentModel.getPagedListAdapter().subscribe(adapter -> {
            viewBinding.dictionaryBookmarkFragmentRecyclerview.setAdapter(adapter);

            adapter.setBookmarkClickListener((aEntry, bookmark, memo) -> queryFragmentModel.editBookmark(aEntry, bookmark, memo));
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

        queryMenu.onCreateOptionsMenu(getActivity().getComponentName(),
                menu, inflater, getContext());

        final BaseQueryFragmentModel queryFragmentModel = getModel();

        autoDisposable.add(queryFragmentModel.getInstalledDictionaries()
                //.distinctUntilChanged()
                .subscribe(l -> queryMenu.addLanguageMenu(getContext(), l,
                        new QueryMenu.LanguageChangeCallback() {
                            @Override
                            public void change(String language) {
                                queryFragmentModel.setLanguage(language);
                            }
                        })));

        autoDisposable.add(queryFragmentModel.getStatusObservable()
                .filter(s -> s.getPersistentLanguageSettings() != null)
                .map(QueryViewStatus::getPersistentLanguageSettings)
                .distinctUntilChanged()
                .subscribe(l -> queryMenu.languageMenuText.setText(l.lang)));

        autoDisposable.add(queryFragmentModel.getStatusObservable()
                .map(QueryViewStatus::getSearchIconifiedByDefault)
                .distinctUntilChanged()
                .subscribe(b -> queryMenu.searchView.setIconifiedByDefault(b)));

        autoDisposable.add(queryFragmentModel.getStatusObservable()
                .map(QueryViewStatus::getShareButtonVisible)
                .distinctUntilChanged()
                .subscribe(queryMenu.shareBookmarks::setVisible));

        queryMenu.searchCloseButton.setOnClickListener(v -> {
            if ("".equals(queryMenu.searchView.getQuery().toString())) {
                queryMenu.searchView.setIconified(true);
            } else {
                queryFragmentModel.setTerm("");
            }
        });

        queryMenu.shareBookmarks.setOnMenuItemClickListener(v -> {
            queryFragmentModel.shareBookmarks();

            return false;
        });

        queryMenu.searchAutoComplete.setOnEditorActionListener((v, actionId, event) -> {
            if ("".equals(queryMenu.searchAutoComplete.getText().toString())) {
                queryFragmentModel.setTerm("");
            }

            return false;
        });

        autoDisposable.add(queryFragmentModel.getStatusObservable()
                .map(QueryViewStatus::getTerm)
                .distinctUntilChanged()
                .subscribe(s -> {
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
        final BaseQueryFragmentModel queryFragmentModel = getModel();

        queryFragmentModel.setTerm(aIntentSearchTerm);
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
