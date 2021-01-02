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
package org.happypeng.sumatora.android.sumatoradictionary.activity

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ArrayAdapter
import android.widget.PopupMenu
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.paging.PagedList
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.functions.Consumer
import org.happypeng.sumatora.android.sumatoradictionary.R
import org.happypeng.sumatora.android.sumatoradictionary.adapter.DictionaryPagedListAdapter
import org.happypeng.sumatora.android.sumatoradictionary.component.BookmarkImportComponent
import org.happypeng.sumatora.android.sumatoradictionary.databinding.FragmentDictionaryQueryBinding
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchElement
import org.happypeng.sumatora.android.sumatoradictionary.db.InstalledDictionary
import org.happypeng.sumatora.android.sumatoradictionary.db.PersistentLanguageSettings
import org.happypeng.sumatora.android.sumatoradictionary.model.BookmarkImportModel
import org.happypeng.sumatora.android.sumatoradictionary.model.state.ImportState
import org.happypeng.sumatora.android.sumatoradictionary.model.viewbinding.FragmentDictionaryQueryBindingUtil
import org.happypeng.sumatora.android.sumatoradictionary.model.viewbinding.QueryMenu
import org.happypeng.sumatora.android.sumatoradictionary.viewholder.DictionarySearchElementViewHolder
import javax.inject.Inject
import kotlin.reflect.KParameter

@AndroidEntryPoint
class DictionaryBookmarksImportActivity : AppCompatActivity() {
    @JvmField
    @Inject
    var bookmarkImportComponent: BookmarkImportComponent? = null
    private var viewBinding: FragmentDictionaryQueryBinding? = null
    private var autoDisposable: CompositeDisposable?
    private var pagedListAdapter: DictionaryPagedListAdapter? = null
    private val model: BookmarkImportModel
        get() = ViewModelProvider(this).get(BookmarkImportModel::class.java)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (autoDisposable == null) {
            autoDisposable = CompositeDisposable()
        }
        viewBinding = FragmentDictionaryQueryBinding.inflate(layoutInflater)
        setContentView(viewBinding!!.root)

        // Decoration
        val itemDecor = DividerItemDecoration(this,
                (viewBinding!!.dictionaryBookmarkFragmentRecyclerview.layoutManager as LinearLayoutManager?)!!.orientation)
        viewBinding!!.dictionaryBookmarkFragmentRecyclerview.addItemDecoration(itemDecor)
        val bookmarkImportModel = model

        // Toolbar configuration
        viewBinding!!.dictionaryBookmarkFragmentToolbar.title = title
        setSupportActionBar(viewBinding!!.dictionaryBookmarkFragmentToolbar)
        val actionBar = supportActionBar
        actionBar!!.setHomeAsUpIndicator(R.drawable.ic_menu_white_24dp)
        actionBar.setDisplayHomeAsUpEnabled(true)

        // Receive status
        autoDisposable!!.add(bookmarkImportModel.states().subscribe { (executed, _, closed) ->
            if (closed) {
                finish()
            }
            if (executed) {
                FragmentDictionaryQueryBindingUtil.setReady(viewBinding)
            } else {
                FragmentDictionaryQueryBindingUtil.setInPreparation(viewBinding)
            }
        })

        val completionAdapter = ArrayAdapter(this,
                android.R.layout.simple_dropdown_item_1line,
                listOf("#france", "#belgium", "#england"))

        pagedListAdapter = DictionaryPagedListAdapter(bookmarkImportModel.disableBookmarkButton,
                bookmarkImportModel.disableMemoEdit, { _, _, _ -> Unit }, completionAdapter,
                DictionarySearchElementViewHolder.Colors(ContextCompat.getColor(this, R.color.text_background_primary),
                        ContextCompat.getColor(this, R.color.text_background_primary_backup),
                        ContextCompat.getColor(this,
                                R.color.render_highlight),
                        ContextCompat.getColor(this,
                                R.color.render_pos)),
                {
                    startActivity(Intent(this, DictionaryTagsActivity::class.java))
                })

        autoDisposable!!.add(bookmarkImportModel.pagedListObservable.subscribe { l: PagedList<DictionarySearchElement?> -> pagedListAdapter!!.submitList(l) })
        viewBinding!!.dictionaryBookmarkFragmentRecyclerview.adapter = pagedListAdapter

        // Process received data as an intent
        val receivedIntent = intent
        val receivedAction = intent.action
        if (receivedAction == null) {
            finish()
            return
        }
        val data = receivedIntent.data
        if (data == null) {
            finish()
            return
        }
        bookmarkImportModel.bookmarkImportFileOpen(data)
    }

    override fun onDestroy() {
        if (pagedListAdapter != null) {
            pagedListAdapter!!.close()
            pagedListAdapter = null
        }
        autoDisposable!!.dispose()
        autoDisposable = null
        super.onDestroy()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val bookmarkImportModel = model
        val ret = super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.bookmark_import_toolbar_menu, menu)
        menu.findItem(R.id.import_bookmarks).setOnMenuItemClickListener {
            bookmarkImportModel.bookmarkImportCommit()
            false
        }
        menu.findItem(R.id.cancel_import).setOnMenuItemClickListener {
            bookmarkImportModel.bookmarkImportCancel()
            false
        }
        val languageMenuText = menu.findItem(R.id.bookmark_import_fragment_menu_language_text).actionView.findViewById<TextView>(R.id.menuitem_language_text)
        val languagePopupMenu = PopupMenu(this, languageMenuText)
        languageMenuText.setOnClickListener { v: View? -> languagePopupMenu.show() }
        val languagePopupMenuContent = languagePopupMenu.menu
        autoDisposable!!.add(
                bookmarkImportModel.installedDictionaries
                .distinctUntilChanged()
                .subscribe{ list ->
                    for (i in 0 until languagePopupMenuContent.size()) {
                        languagePopupMenuContent.removeItem(i)
                    }
                    if (list != null) {
                        for (l in list) {
                            if (l != null) {
                                if ("jmdict_translation" == l.type) {
                                    languagePopupMenuContent.add(l.description).setOnMenuItemClickListener { item: MenuItem? ->
                                        bookmarkImportModel.setLanguage(l.lang)
                                        false
                                    }
                                }
                            }
                        }
                    }
                })
        autoDisposable!!.add(
                bookmarkImportModel.states()
                .filter { (_, persistentLanguageSettings) -> persistentLanguageSettings != null }
                .map(ImportState::persistentLanguageSettings)
                .distinctUntilChanged()
                .subscribe { l -> languageMenuText.text = l?.lang ?: "" })
        QueryMenu.colorMenu(menu, this)
        return ret
    }

    init {
        autoDisposable = CompositeDisposable()
    }
}