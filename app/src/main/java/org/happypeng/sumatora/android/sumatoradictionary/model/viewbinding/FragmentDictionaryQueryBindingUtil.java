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

package org.happypeng.sumatora.android.sumatoradictionary.model.viewbinding;

import android.view.View;

import org.happypeng.sumatora.android.sumatoradictionary.databinding.FragmentDictionaryQueryBinding;

public class FragmentDictionaryQueryBindingUtil {
    public static void setReady(final FragmentDictionaryQueryBinding viewBinding) {
        viewBinding.dictionaryBookmarkFragmentProgressbar.setIndeterminate(false);
        viewBinding.dictionaryBookmarkFragmentProgressbar.setMax(0);

        viewBinding.dictionaryBookmarkFragmentStatustext.setText("");

        viewBinding.dictionaryBookmarkFragmentStatustext.setVisibility(View.GONE);
        viewBinding.dictionaryBookmarkFragmentProgressbar.setVisibility(View.GONE);
        viewBinding.dictionaryBookmarkFragmentSearchStatus.setVisibility(View.GONE);
    }

    public static void setNoResultsFound(final FragmentDictionaryQueryBinding viewBinding,
                                         final String term) {
        viewBinding.dictionaryBookmarkFragmentProgressbar.setIndeterminate(false);
        viewBinding.dictionaryBookmarkFragmentProgressbar.setMax(0);

        viewBinding.dictionaryBookmarkFragmentSearchStatus.setVisibility(View.VISIBLE);
        viewBinding.dictionaryBookmarkFragmentSearchStatus.setText("No results found for term '" + term + "'.");

        viewBinding.dictionaryBookmarkFragmentStatustext.setVisibility(View.GONE);
        viewBinding.dictionaryBookmarkFragmentProgressbar.setVisibility(View.GONE);
    }

    public static void setResultsFound(final FragmentDictionaryQueryBinding viewBinding,
                                       final String term) {
        viewBinding.dictionaryBookmarkFragmentProgressbar.setIndeterminate(false);
        viewBinding.dictionaryBookmarkFragmentProgressbar.setMax(0);

        viewBinding.dictionaryBookmarkFragmentSearchStatus.setVisibility(View.VISIBLE);
        viewBinding.dictionaryBookmarkFragmentSearchStatus.setText("Results for term '" + term + "':");

        viewBinding.dictionaryBookmarkFragmentStatustext.setVisibility(View.GONE);
        viewBinding.dictionaryBookmarkFragmentProgressbar.setVisibility(View.GONE);
    }

    public static void setSearching(final FragmentDictionaryQueryBinding viewBinding) {
        viewBinding.dictionaryBookmarkFragmentStatustext.setVisibility(View.VISIBLE);
        viewBinding.dictionaryBookmarkFragmentProgressbar.setVisibility(View.VISIBLE);
        viewBinding.dictionaryBookmarkFragmentStatustext.setVisibility(View.VISIBLE);
        viewBinding.dictionaryBookmarkFragmentSearchStatus.setVisibility(View.GONE);

        viewBinding.dictionaryBookmarkFragmentProgressbar.setIndeterminate(true);
        viewBinding.dictionaryBookmarkFragmentProgressbar.animate();

        viewBinding.dictionaryBookmarkFragmentStatustext.setText("Searching...");
    }

    public static void setInPreparation(final FragmentDictionaryQueryBinding viewBinding) {
        viewBinding.dictionaryBookmarkFragmentStatustext.setVisibility(View.VISIBLE);
        viewBinding.dictionaryBookmarkFragmentProgressbar.setVisibility(View.VISIBLE);
        viewBinding.dictionaryBookmarkFragmentStatustext.setVisibility(View.VISIBLE);
        viewBinding.dictionaryBookmarkFragmentSearchStatus.setVisibility(View.GONE);

        viewBinding.dictionaryBookmarkFragmentProgressbar.setIndeterminate(true);
        viewBinding.dictionaryBookmarkFragmentProgressbar.animate();

        viewBinding.dictionaryBookmarkFragmentStatustext.setText("Reading database...");
    }
}
