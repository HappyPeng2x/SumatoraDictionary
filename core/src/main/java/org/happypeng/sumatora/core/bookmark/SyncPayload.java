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

package org.happypeng.sumatora.core.bookmark;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

// The envelope written to/read from the single synced file on the Nextcloud server. `version`
// lets a future incompatible payload shape be detected before it's blindly merged.
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class SyncPayload {
    @JsonProperty("version")
    public int version;

    @JsonProperty("bookmarks")
    public List<Bookmark> bookmarks;

    @JsonProperty("tombstones")
    public List<BookmarkTombstone> tombstones;

    public SyncPayload() {
        version = 1;
        bookmarks = new ArrayList<>();
        tombstones = new ArrayList<>();
    }

    public SyncPayload(int version, List<Bookmark> bookmarks, List<BookmarkTombstone> tombstones) {
        this.version = version;
        this.bookmarks = bookmarks;
        this.tombstones = tombstones;
    }

    public static SyncPayload empty() {
        return new SyncPayload(1, new ArrayList<>(), new ArrayList<>());
    }
}
