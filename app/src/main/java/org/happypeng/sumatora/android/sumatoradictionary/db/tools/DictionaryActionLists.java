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
        along with this program.  If not, see <http://www.gnu.org/licenses/>. */

package org.happypeng.sumatora.android.sumatoradictionary.db.tools;


import java.util.List;

public class DictionaryActionLists {
    private final List<DictionaryAction> mDeleteActions;
    private final List<DictionaryAction> mUpdateActions;
    private final List<DictionaryAction> mDownloadActions;
    private final List<DictionaryAction> mVersionActions;

    public DictionaryActionLists(final List<DictionaryAction> aDeleteActions,
                                 final List<DictionaryAction> aUpdateActions,
                                 final List<DictionaryAction> aDownloadActions,
                                 final List<DictionaryAction> aVersionActions) {
        mDeleteActions = aDeleteActions;
        mUpdateActions = aUpdateActions;
        mDownloadActions = aDownloadActions;
        mVersionActions = aVersionActions;
    }

    public List<DictionaryAction> getDeleteActions() {
        return mDeleteActions;
    }

    public List<DictionaryAction> getUpdateActions() {
        return mUpdateActions;
    }

    public List<DictionaryAction> getDownloadActions() {
        return mDownloadActions;
    }

    public List<DictionaryAction> getVersionActions() {
        return mVersionActions;
    }
}
