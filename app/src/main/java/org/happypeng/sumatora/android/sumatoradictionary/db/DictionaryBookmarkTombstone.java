/* Sumatora Dictionary
        Copyright (C) 2026 Nicolas Centa

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

package org.happypeng.sumatora.android.sumatoradictionary.db;

import androidx.room.Entity;

import org.happypeng.sumatora.core.bookmark.BookmarkTombstone;

// Records that a bookmark (identified by seq) was deleted locally, so Nextcloud sync's merge can
// tell "deleted on this device" apart from "never existed on this device" and stop a deletion
// from being resurrected by a copy elsewhere that still has the live row. See
// BookmarkComponent.updateBookmark() (the sole place a DictionaryBookmark row is ever removed)
// and BookmarkMergeService.mergeWithTombstones in :core for how this gets resolved during sync.
@Entity(primaryKeys = {"seq"})
public class DictionaryBookmarkTombstone {
    public long seq;
    public long deletedAt;

    public DictionaryBookmarkTombstone() {}

    public DictionaryBookmarkTombstone(long aSeq, long aDeletedAt) {
        seq = aSeq;
        deletedAt = aDeletedAt;
    }

    public BookmarkTombstone toBookmarkTombstone() {
        return new BookmarkTombstone(seq, deletedAt);
    }

    public static DictionaryBookmarkTombstone fromBookmarkTombstone(BookmarkTombstone t) {
        return new DictionaryBookmarkTombstone(t.seq, t.deletedAt);
    }
}
