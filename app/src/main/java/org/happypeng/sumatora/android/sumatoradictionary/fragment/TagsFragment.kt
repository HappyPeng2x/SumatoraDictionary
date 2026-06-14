/* Sumatora Dictionary
        Copyright (C) 2024 Nicolas Centa

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

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.DividerItemDecoration
import dagger.hilt.android.AndroidEntryPoint
import io.reactivex.rxjava3.disposables.CompositeDisposable
import org.happypeng.sumatora.android.sumatoradictionary.R
import org.happypeng.sumatora.android.sumatoradictionary.adapter.TagsAdapter
import org.happypeng.sumatora.android.sumatoradictionary.databinding.FragmentTagsBinding
import org.happypeng.sumatora.android.sumatoradictionary.model.MainActivityModel
import org.happypeng.sumatora.android.sumatoradictionary.model.TagsFragmentModel
import org.happypeng.sumatora.android.sumatoradictionary.model.intent.MainActivitySearchFromTagsIntent

@AndroidEntryPoint
class TagsFragment : Fragment() {
    private val viewModel: TagsFragmentModel by viewModels()
    private val activityViewModel: MainActivityModel by activityViewModels()

    private lateinit var adapter: TagsAdapter
    private val compositeDisposable = CompositeDisposable()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val binding = FragmentTagsBinding.inflate(inflater, container, false)

        (activity as AppCompatActivity).setSupportActionBar(binding.tagsFragmentToolbar)
        (activity as AppCompatActivity).supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_outline_menu_24px)
        }

        adapter = TagsAdapter { tag ->
            activityViewModel.sendIntent(MainActivitySearchFromTagsIntent(tag))
        }

        binding.tagsFragmentRecyclerview.apply {
            this.adapter = this@TagsFragment.adapter
            addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        }

        return binding.root
    }

    override fun onStart() {
        super.onStart()
        compositeDisposable.add(
            viewModel.tagsObservable.subscribe { tags ->
                adapter.submitList(tags)
            }
        )
    }

    override fun onStop() {
        compositeDisposable.clear()
        super.onStop()
    }
}
