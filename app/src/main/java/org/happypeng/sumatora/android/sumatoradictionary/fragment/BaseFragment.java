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

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;

import org.happypeng.sumatora.android.sumatoradictionary.R;
import org.happypeng.sumatora.android.sumatoradictionary.activity.MainActivity;
import org.happypeng.sumatora.android.sumatoradictionary.adapter.DictionaryPagedListAdapter;
import org.happypeng.sumatora.android.sumatoradictionary.databinding.FragmentDictionaryQueryBinding;
import org.happypeng.sumatora.android.sumatoradictionary.model.BaseQueryFragmentModel;
import org.happypeng.sumatora.android.sumatoradictionary.model.state.QueryState;
import org.happypeng.sumatora.android.sumatoradictionary.model.viewbinding.FragmentDictionaryQueryBindingUtil;
import org.happypeng.sumatora.android.sumatoradictionary.model.viewbinding.QueryMenu;
import org.happypeng.sumatora.android.sumatoradictionary.viewholder.DictionarySearchElementViewHolder;

import dagger.hilt.android.AndroidEntryPoint;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subjects.Subject;

@AndroidEntryPoint
public abstract class BaseFragment extends Fragment {
    protected FragmentDictionaryQueryBinding viewBinding;
    protected QueryMenu queryMenu;

    protected CompositeDisposable viewAutoDisposable = new CompositeDisposable();
    protected CompositeDisposable fragmentAutoDisposable = new CompositeDisposable();

    private Subject<String> intentSearchTerm = PublishSubject.create();

    protected Bundle savedInstanceState;

    protected BaseQueryFragmentModel getModel() { return null; }

    protected BaseFragment() { }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final BaseQueryFragmentModel model = getModel();

        if (model != null) {
            fragmentAutoDisposable.add(intentSearchTerm.subscribe(model::setTerm));
        }
    }

    private DictionaryPagedListAdapter pagedListAdapter = null;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);

        if (viewAutoDisposable == null) {
            viewAutoDisposable = new CompositeDisposable();
        }

        this.savedInstanceState = savedInstanceState;

        viewBinding = FragmentDictionaryQueryBinding.inflate(inflater);

        // Decoration
        DividerItemDecoration itemDecor = new DividerItemDecoration(getContext(),
                ((LinearLayoutManager) viewBinding.dictionaryBookmarkFragmentRecyclerview.getLayoutManager()).getOrientation());
        viewBinding.dictionaryBookmarkFragmentRecyclerview.addItemDecoration(itemDecor);

        final BaseQueryFragmentModel queryFragmentModel = getModel();

        viewAutoDisposable.add(queryFragmentModel.states().filter(QueryState::getSetIntent)
                .subscribe(s -> setActivityIntentSearchTerm(s.getTerm())));

        // Toolbar configuration
        ((AppCompatActivity) getActivity()).setSupportActionBar(viewBinding.dictionaryBookmarkFragmentToolbar);

        setHasOptionsMenu(true);

        final ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        actionBar.setHomeAsUpIndicator(R.drawable.ic_menu_white_24dp);
        actionBar.setDisplayHomeAsUpEnabled(true);

        viewBinding.dictionaryBookmarkFragmentToolbar.setTitle(queryFragmentModel.getTitle());

        viewAutoDisposable.add(queryFragmentModel.states().subscribe(status -> {
            if (!status.getReady()) {
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

        pagedListAdapter =
                new DictionaryPagedListAdapter(queryFragmentModel.getDisableBookmarkButton(),
                        queryFragmentModel.getDisableMemoEdit(), queryFragmentModel.getCommitBookmarksFun(),
                        new DictionarySearchElementViewHolder.Colors(ContextCompat.getColor(getContext(),
                                R.color.text_background_primary),
                                ContextCompat.getColor(getContext(),
                                        R.color.text_background_primary_backup),
                                ContextCompat.getColor(getContext(),
                                        R.color.render_highlight),
                                ContextCompat.getColor(getContext(),
                                        R.color.render_pos)));

        viewAutoDisposable.add(queryFragmentModel.getPagedListObservable().subscribe(l ->
                pagedListAdapter.submitList(l)));

        viewBinding.dictionaryBookmarkFragmentRecyclerview.setAdapter(pagedListAdapter);

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

        queryMenu.searchView.setIconifiedByDefault(queryFragmentModel.getSearchIconifiedByDefault());
        queryMenu.shareBookmarks.setVisible(queryFragmentModel.getShareButtonVisible());

        viewAutoDisposable.add(queryFragmentModel.getInstalledDictionaries()
                .subscribe(l -> queryMenu.addLanguageMenu(getContext(), l,
                        new QueryMenu.LanguageChangeCallback() {
                            @Override
                            public void change(String language) {
                                queryFragmentModel.setLanguage(language);
                            }
                        })));

        viewAutoDisposable.add(queryFragmentModel.states()
                .filter(s -> s.getLanguage() != null)
                .map(QueryState::getLanguage)
                .distinctUntilChanged()
                .subscribe(l -> queryMenu.languageMenuText.setText(l)));

        viewAutoDisposable.add(queryFragmentModel.states().map(QueryState::getSearchBoxClosed)
                .distinctUntilChanged()
                .subscribe(b -> queryMenu.searchView.setIconified(b)));

        queryMenu.searchCloseButton.setOnClickListener(v ->
                queryFragmentModel.closeSearchBox(queryMenu.searchAutoComplete.getText().toString()));

        queryMenu.searchView.setOnSearchClickListener(v ->
                queryFragmentModel.openSearchBox());

        queryMenu.shareBookmarks.setOnMenuItemClickListener(v -> {
            queryFragmentModel.shareBookmarks();

            return false;
        });

        queryMenu.searchAutoComplete.setOnEditorActionListener((v, actionId, event) -> {
            if ("".equals(queryMenu.searchAutoComplete.getText().toString())) {
                setActivityIntentSearchTerm("");
            }

            return false;
        });

        viewAutoDisposable.add(queryFragmentModel.states()
                .map(QueryState::getClearSearchBox)
                .filter(x -> x)
                .subscribe(x -> queryMenu.searchView.setQuery("", false)));

        viewAutoDisposable.add(queryFragmentModel.states()
                .map(QueryState::getTerm)
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

    // This is only to be called by the activity
    public void setIntentSearchTerm(@NonNull String aIntentSearchTerm) {
        intentSearchTerm.onNext(aIntentSearchTerm);
    }

    // This can be called here
    void setActivityIntentSearchTerm(@NonNull String intentSearchTerm) {
        final MainActivity activity = (MainActivity) getActivity();

        if (activity == null) {
            return;
        }

        final Intent intent = getActivity().getIntent();
        intent.removeExtra("query");
        intent.putExtra("SEARCH_TERM", intentSearchTerm);

        activity.processIntent(intent);
    }

    public void focusSearchView() {
        if (queryMenu != null && queryMenu.searchView != null) {
            queryMenu.searchView.requestFocus();
        }
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        if (pagedListAdapter != null) {
            pagedListAdapter.close();
            pagedListAdapter = null;
        }

        viewAutoDisposable.dispose();
        viewAutoDisposable = null;

        viewBinding = null;
        queryMenu = null;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        fragmentAutoDisposable.dispose();
        fragmentAutoDisposable = null;
    }
}
