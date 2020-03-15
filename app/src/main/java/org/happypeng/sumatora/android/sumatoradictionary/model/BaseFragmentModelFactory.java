/* Sumatora Dictionary
        Copyright (C) 2019 Nicolas Centa

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

package org.happypeng.sumatora.android.sumatoradictionary.model;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

public class BaseFragmentModelFactory extends ViewModelProvider.AndroidViewModelFactory {
    public interface Creator {
        BaseFragmentModel create();
    }

    private final Creator mCreator;

    public BaseFragmentModelFactory(@NonNull Application application,
                                    Creator aCreator) {
        super(application);

        mCreator = aCreator;
    }

    @NonNull
    @Override
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        T ret = modelClass.cast(mCreator.create());

        if (ret != null) {
            return ret;
        }

        throw new IllegalArgumentException("ViewModel cast failed");
    }
}
