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
package org.happypeng.sumatora.android.sumatoradictionary.fragment

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.*
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.paging.PagedList
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.functions.Consumer
import io.reactivex.rxjava3.subjects.PublishSubject
import io.reactivex.rxjava3.subjects.Subject
import org.happypeng.sumatora.android.sumatoradictionary.R
import org.happypeng.sumatora.android.sumatoradictionary.activity.DictionaryTagsActivity
import org.happypeng.sumatora.android.sumatoradictionary.activity.MainActivity
import org.happypeng.sumatora.android.sumatoradictionary.adapter.DictionaryPagedListAdapter
import org.happypeng.sumatora.android.sumatoradictionary.databinding.FragmentDictionaryQueryBinding
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchElement
import org.happypeng.sumatora.android.sumatoradictionary.db.InstalledDictionary
import org.happypeng.sumatora.android.sumatoradictionary.model.BaseQueryFragmentModel
import org.happypeng.sumatora.android.sumatoradictionary.model.state.QueryState
import org.happypeng.sumatora.android.sumatoradictionary.model.viewbinding.FragmentDictionaryQueryBindingUtil
import org.happypeng.sumatora.android.sumatoradictionary.model.viewbinding.QueryMenu
import org.happypeng.sumatora.android.sumatoradictionary.model.viewbinding.QueryMenu.LanguageChangeCallback
import org.happypeng.sumatora.android.sumatoradictionary.viewholder.DictionarySearchElementViewHolder

@AndroidEntryPoint
abstract class BaseFragment protected constructor() : Fragment() {
    private var viewBinding: FragmentDictionaryQueryBinding? = null
    private var queryMenu: QueryMenu? = null
    private var viewAutoDisposable: CompositeDisposable? = CompositeDisposable()
    private var fragmentAutoDisposable: CompositeDisposable? = CompositeDisposable()
    private val intentSearchTerm: Subject<String> = PublishSubject.create()
    protected var savedInstanceState: Bundle? = null

    open fun getModel(): BaseQueryFragmentModel? {
        return null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val model = getModel()
        if (model != null) {
            fragmentAutoDisposable!!.add(intentSearchTerm.subscribe { t: String? -> model.setTerm(t!!) })
        }
    }

    private var pagedListAdapter: DictionaryPagedListAdapter? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        if (viewAutoDisposable == null) {
            viewAutoDisposable = CompositeDisposable()
        }
        this.savedInstanceState = savedInstanceState
        viewBinding = FragmentDictionaryQueryBinding.inflate(inflater)

        // Decoration
        val itemDecor = DividerItemDecoration(context,
                (viewBinding!!.dictionaryBookmarkFragmentRecyclerview.layoutManager as LinearLayoutManager?)!!.orientation)
        viewBinding!!.dictionaryBookmarkFragmentRecyclerview.addItemDecoration(itemDecor)
        val queryFragmentModel = getModel()
        viewAutoDisposable!!.add(queryFragmentModel!!.states().filter(QueryState::setIntent)
                .subscribe { (term) -> setActivityIntentSearchTerm(term) })

        // Toolbar configuration
        (activity as AppCompatActivity?)!!.setSupportActionBar(viewBinding!!.dictionaryBookmarkFragmentToolbar)
        setHasOptionsMenu(true)
        val actionBar = (activity as AppCompatActivity?)!!.supportActionBar
        actionBar!!.setHomeAsUpIndicator(R.drawable.ic_menu_white_24dp)
        actionBar.setDisplayHomeAsUpEnabled(true)
        viewBinding!!.dictionaryBookmarkFragmentToolbar.title = queryFragmentModel.title
        viewAutoDisposable!!.add(queryFragmentModel.states().subscribe { (term, found, _, _, _, searching, ready) ->
            if (!ready) {
                FragmentDictionaryQueryBindingUtil.setInPreparation(viewBinding)
            } else {
                if ("" != term) {
                    if (searching) {
                        FragmentDictionaryQueryBindingUtil.setSearching(viewBinding)
                    } else if (found) {
                        FragmentDictionaryQueryBindingUtil.setResultsFound(viewBinding, term)
                    } else {
                        FragmentDictionaryQueryBindingUtil.setNoResultsFound(viewBinding, term)
                    }
                } else {
                    FragmentDictionaryQueryBindingUtil.setReady(viewBinding)
                }
            }
        })

        val emptyList: List<String> = listOf()
        val completionAdapter = ArrayAdapter(requireContext(),
                android.R.layout.simple_dropdown_item_1line, emptyList)

/*        queryFragmentModel.tagNames.subscribe {
            completionAdapter.clear()
            completionAdapter.addAll(it.toMutableList())
        }*/

        pagedListAdapter = DictionaryPagedListAdapter(queryFragmentModel.disableBookmarkButton,
                queryFragmentModel.disableMemoEdit, queryFragmentModel.commitBookmarksFun, completionAdapter,
                DictionarySearchElementViewHolder.Colors(ContextCompat.getColor(activity as Context, R.color.text_background_primary),
                        ContextCompat.getColor(activity as Context, R.color.text_background_primary_backup),
                        ContextCompat.getColor(activity as Context,
                                R.color.render_highlight),
                        ContextCompat.getColor(activity as Context,
                                R.color.render_pos)), {
                                    startActivity(Intent(this.activity, DictionaryTagsActivity::class.java))
        })

        viewAutoDisposable!!.add(queryFragmentModel.pagedListObservable.subscribe { l: PagedList<DictionarySearchElement?> -> pagedListAdapter!!.submitList(l) })
        viewBinding!!.dictionaryBookmarkFragmentRecyclerview.adapter = pagedListAdapter
        focusSearchView()
        return viewBinding!!.root
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (queryMenu != null) {
            queryMenu!!.onSaveInstanceState(outState)
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        queryMenu = QueryMenu()
        queryMenu!!.onCreateOptionsMenu(requireActivity().componentName,
                menu, inflater, requireContext())
        val queryFragmentModel = getModel()
        queryMenu!!.searchView.setIconifiedByDefault(queryFragmentModel!!.searchIconifiedByDefault)
        queryMenu!!.shareBookmarks.isVisible = queryFragmentModel.shareButtonVisible
        viewAutoDisposable!!.add(queryFragmentModel.installedDictionaries
                .subscribe(Consumer { l: List<InstalledDictionary?>? ->
                    queryMenu!!.addLanguageMenu(context, l,
                            object : LanguageChangeCallback() {
                                override fun change(language: String) {
                                    queryFragmentModel.setLanguage(language)
                                }
                            })
                }))
        viewAutoDisposable!!.add(queryFragmentModel.states()
                .filter { (_, _, language) -> language != null }
                .map(QueryState::language)
                .distinctUntilChanged()
                .subscribe { l: String? -> queryMenu!!.languageMenuText.text = l })
        viewAutoDisposable!!.add(queryFragmentModel.states().map(QueryState::searchBoxClosed)
                .distinctUntilChanged()
                .subscribe { b: Boolean? -> queryMenu!!.searchView.isIconified = b!! })
        queryMenu!!.searchCloseButton.setOnClickListener { v: View? -> queryFragmentModel.closeSearchBox(queryMenu!!.searchAutoComplete.text.toString()) }
        queryMenu!!.searchView.setOnSearchClickListener { v: View? -> queryFragmentModel.openSearchBox() }
        queryMenu!!.shareBookmarks.setOnMenuItemClickListener { v: MenuItem? ->
            queryFragmentModel.shareBookmarks()
            false
        }
        queryMenu!!.searchAutoComplete.setOnEditorActionListener { v: TextView?, actionId: Int, event: KeyEvent? ->
            if ("" == queryMenu!!.searchAutoComplete.text.toString()) {
                setActivityIntentSearchTerm("")
            }
            false
        }
        viewAutoDisposable!!.add(queryFragmentModel.states()
                .map(QueryState::term)
                .distinctUntilChanged()
                .subscribe { s: String ->
                    if (s != queryMenu!!.searchView.query.toString()) {
                        queryMenu!!.searchView.setQuery(s, false)
                    }
                })

        viewAutoDisposable!!.add(queryFragmentModel.states()
                .map(QueryState::clearSearchBox)
                .filter { x: Boolean? -> x!! }
                .subscribe { x: Boolean? -> queryMenu!!.searchView.setQuery("", false) })

        if (savedInstanceState != null) {
            queryMenu!!.restoreInstanceState(savedInstanceState!!)
        }
        focusSearchView()
    }

    // This is only to be called by the activity
    fun setIntentSearchTerm(aIntentSearchTerm: String) {
        intentSearchTerm.onNext(aIntentSearchTerm)
    }

    // This can be called here
    private fun setActivityIntentSearchTerm(intentSearchTerm: String) {
        val activity = activity as MainActivity? ?: return
        val intent =  activity.intent
        intent.removeExtra("query")
        intent.putExtra("SEARCH_TERM", intentSearchTerm)
        activity.processIntent(intent)
    }

    private fun focusSearchView() {
        if (queryMenu != null && queryMenu!!.searchView != null) {
            queryMenu!!.searchView.requestFocus()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        if (pagedListAdapter != null) {
            pagedListAdapter!!.close()
            pagedListAdapter = null
        }
        viewAutoDisposable!!.dispose()
        viewAutoDisposable = null
        viewBinding = null
        queryMenu = null
    }

    override fun onDestroy() {
        super.onDestroy()
        fragmentAutoDisposable!!.dispose()
        fragmentAutoDisposable = null
    }
}