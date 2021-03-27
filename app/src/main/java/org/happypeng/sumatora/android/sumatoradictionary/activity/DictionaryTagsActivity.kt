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

import android.os.Bundle
import android.text.InputType
import android.view.Menu
import android.widget.EditText
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import dagger.hilt.android.AndroidEntryPoint
import io.reactivex.rxjava3.disposables.CompositeDisposable
import org.happypeng.sumatora.android.sumatoradictionary.R
import org.happypeng.sumatora.android.sumatoradictionary.adapter.DictionaryTagsAdapter
import org.happypeng.sumatora.android.sumatoradictionary.databinding.ActivityTagsBinding
import org.happypeng.sumatora.android.sumatoradictionary.db.DictionaryTagName
import org.happypeng.sumatora.android.sumatoradictionary.model.DictionaryTagsModel
import org.happypeng.sumatora.android.sumatoradictionary.model.viewbinding.QueryMenu
import org.happypeng.sumatora.android.sumatoradictionary.viewholder.DictionaryTagsViewHolderActions

@AndroidEntryPoint
class DictionaryTagsActivity : AppCompatActivity(), DictionaryTagsViewHolderActions {
    private var autoDisposable: CompositeDisposable = CompositeDisposable()

    private val viewModel: DictionaryTagsModel by viewModels()

    override fun selectForDeletion(selected: Boolean, tagName: DictionaryTagName) {
        viewModel.selectForDeletion(selected, tagName)
    }

    override fun toggleSelected(tagName: DictionaryTagName) {
        viewModel.toggleSelect(tagName)
    }

    private fun getNewTagTitle() {
        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setTitle("New tag name: ")


        val input = EditText(this)
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL
        builder.setView(input)

        builder.setPositiveButton("OK") { _, _ ->
            viewModel.createTagName(input.text.toString())
        }

        builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }

        builder.show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val activityTagsBinding = ActivityTagsBinding.inflate(layoutInflater)
        setContentView(activityTagsBinding.root)

        setSupportActionBar(activityTagsBinding.dictionaryTagsActivityToolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        val adapter = DictionaryTagsAdapter(this)
        activityTagsBinding.dictionaryTagsActivityRecyclerview.adapter = adapter

        val itemDecor = DividerItemDecoration(this,
                (activityTagsBinding.dictionaryTagsActivityRecyclerview.layoutManager as LinearLayoutManager).orientation)
        activityTagsBinding.dictionaryTagsActivityRecyclerview.addItemDecoration(itemDecor)

        autoDisposable.add(viewModel.states().filter { it.closed }.subscribe { finish() })
        autoDisposable.add(viewModel.states().filter { it.dictionaryTagNames != null }
                .map { it.dictionaryTagNames }.subscribe { adapter.submitList(it) })
        autoDisposable.add(viewModel.states().filter { it.add }.subscribe {
            getNewTagTitle() })
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val ret = super.onCreateOptionsMenu(menu)

        menuInflater.inflate(R.menu.dictionary_tags_activity_menu, menu)

        menu.findItem(R.id.dictionary_tags_activity_menu_add).setOnMenuItemClickListener {
            viewModel.addTag()
            false
        }

        menu.findItem(R.id.dictionary_tags_activity_menu_edit).setOnMenuItemClickListener {
            viewModel.enterEditMode()
            false
        }

        menu.findItem(R.id.dictionary_tags_activity_menu_edit_cancel).setOnMenuItemClickListener {
            viewModel.exitEditMode()
            false
        }

        menu.findItem(R.id.dictionary_tags_activity_menu_edit_delete).setOnMenuItemClickListener {
            viewModel.commitEditMode()
            false
        }

        autoDisposable.add(viewModel.states().map { it.edit }.distinctUntilChanged()
                .filter { it }
                .subscribe {
                    menu.findItem(R.id.dictionary_tags_activity_menu_edit).isVisible = false
                    menu.findItem(R.id.dictionary_tags_activity_menu_edit_cancel).isVisible = true
                    menu.findItem(R.id.dictionary_tags_activity_menu_add).isVisible = false
                    menu.findItem(R.id.dictionary_tags_activity_menu_edit_delete).isVisible = true
                })

        autoDisposable.add(viewModel.states().map { it.edit }.distinctUntilChanged()
                .filter { !it }
                .subscribe {
                    menu.findItem(R.id.dictionary_tags_activity_menu_edit).isVisible = true
                    menu.findItem(R.id.dictionary_tags_activity_menu_edit_cancel).isVisible = false
                    menu.findItem(R.id.dictionary_tags_activity_menu_add).isVisible = true
                    menu.findItem(R.id.dictionary_tags_activity_menu_edit_delete).isVisible = false
                })

        autoDisposable.add(viewModel.states().map { it.editCommitConfirm }.distinctUntilChanged()
                .filter { it }
                .subscribe {
                    println("CONFIRMED")
                    viewModel.commitConfirmEditMode()
                })

        QueryMenu.colorMenu(menu, this)

        return ret
    }

    override fun onDestroy() {
        autoDisposable.dispose()

        super.onDestroy()
    }
}