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
import android.text.Editable;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.ListPopupWindow;
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
import org.happypeng.sumatora.android.sumatoradictionary.fragment.EntryDetailBottomSheet;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import dagger.hilt.android.AndroidEntryPoint;
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers;
import io.reactivex.rxjava3.core.Single;
import io.reactivex.rxjava3.disposables.CompositeDisposable;
import io.reactivex.rxjava3.disposables.Disposable;
import io.reactivex.rxjava3.schedulers.Schedulers;
import io.reactivex.rxjava3.subjects.PublishSubject;
import io.reactivex.rxjava3.subjects.Subject;

@AndroidEntryPoint
public abstract class BaseFragment extends Fragment {
    protected FragmentDictionaryQueryBinding viewBinding;
    protected QueryMenu queryMenu;

    protected CompositeDisposable viewAutoDisposable = new CompositeDisposable();
    protected CompositeDisposable fragmentAutoDisposable = new CompositeDisposable();

    private Subject<String> intentSearchTerm = PublishSubject.create();
    private String currentSearchTerm = "";

    private ListPopupWindow tagCompletionPopup;
    private Disposable tagLookupDisposable;
    private boolean isInsertingTag = false;

    private static final Pattern TAG_PATTERN = Pattern.compile("#[^\\s#,]+");

    private static class TagForegroundSpan extends ForegroundColorSpan {
        TagForegroundSpan(int color) { super(color); }
    }

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

        viewAutoDisposable.add(queryFragmentModel.states().map(QueryState::getTerm)
                .distinctUntilChanged()
                .subscribe(t -> currentSearchTerm = t));

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
                        queryFragmentModel.getDisableMemoEdit(),
                        queryFragmentModel.getDisableTagEdit(),
                        queryFragmentModel.getCommitBookmarksFun(),
                        queryFragmentModel.getCommitTagsFun(),
                        queryFragmentModel.getAvailableTagsFun(),
                        queryFragmentModel.getListSummaryFun(),
                        new DictionarySearchElementViewHolder.Colors(
                                ContextCompat.getColor(getContext(), R.color.text_background_primary),
                                ContextCompat.getColor(getContext(), R.color.text_background_primary_backup),
                                ContextCompat.getColor(getContext(), R.color.render_pos),
                                new DictionarySearchElementViewHolder.Colors.TagColors(
                                        ContextCompat.getColor(getContext(), R.color.tag_pos),
                                        ContextCompat.getColor(getContext(), R.color.tag_register),
                                        ContextCompat.getColor(getContext(), R.color.tag_kana),
                                        ContextCompat.getColor(getContext(), R.color.tag_kanji),
                                        ContextCompat.getColor(getContext(), R.color.tag_usage),
                                        ContextCompat.getColor(getContext(), R.color.tag_domain),
                                        ContextCompat.getColor(getContext(), R.color.tag_dialect)
                                ),
                                ContextCompat.getColor(getContext(), R.color.text_foreground_secondary)),
                        entry -> {
                            EntryDetailBottomSheet sheet = EntryDetailBottomSheet.Companion.newInstance(entry, null);
                            sheet.show(getChildFragmentManager(), "entry_detail");
                        });

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

        setupTagCompletion(queryFragmentModel);

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

    private void setupTagCompletion(BaseQueryFragmentModel model) {
        if (queryMenu == null || queryMenu.searchAutoComplete == null) return;

        tagCompletionPopup = new ListPopupWindow(requireContext());
        tagCompletionPopup.setAnchorView(queryMenu.searchAutoComplete);
        tagCompletionPopup.setWidth(ListPopupWindow.WRAP_CONTENT);
        tagCompletionPopup.setModal(false);

        tagCompletionPopup.setOnItemClickListener((parent, view, position, id) -> {
            String selectedTag = (String) parent.getItemAtPosition(position);
            isInsertingTag = true;
            insertTagIntoSearchBox(selectedTag);
            tagCompletionPopup.dismiss();
            queryMenu.searchAutoComplete.post(() -> isInsertingTag = false);
        });

        queryMenu.searchAutoComplete.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                applyTagSpans(s);
                if (!isInsertingTag) {
                    int cursor = queryMenu.searchAutoComplete.getSelectionStart();
                    showTagCompletion(s.toString(), cursor, model);
                }
            }
        });

        // Apply spans to any initial text already in the search box.
        applyTagSpans(queryMenu.searchAutoComplete.getText());
    }

    private void applyTagSpans(Editable s) {
        if (s == null || getContext() == null) return;
        TagForegroundSpan[] existing = s.getSpans(0, s.length(), TagForegroundSpan.class);
        for (TagForegroundSpan span : existing) {
            s.removeSpan(span);
        }
        int tagColor = ContextCompat.getColor(requireContext(), R.color.colorAccent);
        Matcher matcher = TAG_PATTERN.matcher(s);
        while (matcher.find()) {
            s.setSpan(new TagForegroundSpan(tagColor),
                    matcher.start(), matcher.end(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private void showTagCompletion(String text, int cursor, BaseQueryFragmentModel model) {
        if (tagCompletionPopup == null) return;

        int wordStart = cursor;
        while (wordStart > 0 && !Character.isWhitespace(text.charAt(wordStart - 1))) {
            wordStart--;
        }

        if (wordStart < text.length() && text.charAt(wordStart) == '#') {
            final String prefix = cursor > wordStart ? text.substring(wordStart + 1, cursor).toLowerCase() : "";

            if (tagLookupDisposable != null && !tagLookupDisposable.isDisposed()) {
                tagLookupDisposable.dispose();
            }
            tagLookupDisposable = Single.fromCallable(() -> {
                List<String> allTags = (List<String>) model.getAvailableTagsFun().invoke();
                // If prefix is an exact tag match, the user backspaced into a completed tag —
                // show all tags so they can easily pick a replacement.
                boolean exactMatch = false;
                for (String tag : allTags) {
                    if (tag.equalsIgnoreCase(prefix)) { exactMatch = true; break; }
                }
                String effectivePrefix = exactMatch ? "" : prefix;
                List<String> matching = new ArrayList<>();
                for (String tag : allTags) {
                    if (tag.toLowerCase().startsWith(effectivePrefix)) {
                        matching.add(tag);
                    }
                }
                return matching;
            })
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(matchingTags -> {
                if (tagCompletionPopup == null) return;
                if (!matchingTags.isEmpty()) {
                    ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(),
                            android.R.layout.simple_list_item_1, matchingTags);
                    tagCompletionPopup.setAdapter(adapter);
                    tagCompletionPopup.show();
                } else {
                    tagCompletionPopup.dismiss();
                }
            });
        } else {
            tagCompletionPopup.dismiss();
        }
    }

    private void insertTagIntoSearchBox(String tagName) {
        if (queryMenu == null || queryMenu.searchAutoComplete == null) return;

        String text = queryMenu.searchAutoComplete.getText().toString();
        int cursor = queryMenu.searchAutoComplete.getSelectionStart();

        int wordStart = cursor;
        while (wordStart > 0 && !Character.isWhitespace(text.charAt(wordStart - 1))) {
            wordStart--;
        }

        int wordEnd = cursor;
        while (wordEnd < text.length() && !Character.isWhitespace(text.charAt(wordEnd))) {
            wordEnd++;
        }
        // Skip any trailing spaces after the word
        while (wordEnd < text.length() && text.charAt(wordEnd) == ' ') wordEnd++;

        String replacement = "#" + tagName;
        String newText = text.substring(0, wordStart) + replacement + " " + text.substring(wordEnd);
        int newCursor = wordStart + replacement.length() + 1;

        queryMenu.searchAutoComplete.setText(newText);
        queryMenu.searchAutoComplete.setSelection(Math.min(newCursor, newText.length()));
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();

        if (tagLookupDisposable != null) {
            tagLookupDisposable.dispose();
            tagLookupDisposable = null;
        }
        if (tagCompletionPopup != null) {
            if (tagCompletionPopup.isShowing()) tagCompletionPopup.dismiss();
            tagCompletionPopup = null;
        }
        isInsertingTag = false;

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
