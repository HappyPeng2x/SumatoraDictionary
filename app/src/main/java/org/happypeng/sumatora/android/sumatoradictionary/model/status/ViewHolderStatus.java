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

package org.happypeng.sumatora.android.sumatoradictionary.model.status;

import org.happypeng.sumatora.android.sumatoradictionary.db.DictionarySearchElement;

public class ViewHolderStatus {
    private final DictionarySearchElement entry;
    private final boolean bookmarked;
    private final String memo;
    private final boolean memoOpened;
    private final boolean closed;

    private final String newMemo;
    private final boolean newMemoChanged;
    private final boolean newBookmark;
    private final boolean newBookmarkChanged;

    public ViewHolderStatus(final DictionarySearchElement entry,
                            final boolean bookmarked,
                            final String memo,
                            final boolean memoOpened,
                            final boolean closed,
                            final String newMemo,
                            final boolean newMemoChanged,
                            final boolean newBookmark,
                            final boolean newBookmarkChanged) {
        this.entry = entry;
        this.bookmarked = bookmarked;
        this.memo = memo;
        this.memoOpened = memoOpened;
        this.closed = closed;
        this.newMemo = newMemo;
        this.newMemoChanged = newMemoChanged;
        this.newBookmark = newBookmark;
        this.newBookmarkChanged = newBookmarkChanged;
    }

    public DictionarySearchElement getEntry() { return entry; }
    public boolean getBookmarked() { return bookmarked; }
    public String getMemo() { return memo; }
    public boolean getMemoOpened() { return memoOpened; }
    public boolean getClosed() { return closed; }
    public String getNewMemo() { return newMemo; }
    public boolean getNewMemoChanged() { return newMemoChanged; }
    public boolean getNewBookmark() { return newBookmark; }
    public boolean getNewBookmarkChanged() { return newBookmarkChanged; }
}
