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

@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class Bookmark {
    @JsonProperty("seq")
    public long seq;

    @JsonProperty("bookmark")
    public long bookmark;

    @JsonProperty("memo")
    public String memo;

    @JsonProperty("tags")
    public String tags;

    public Bookmark() {}

    public Bookmark(long seq, long bookmark, String memo, String tags) {
        this.seq = seq;
        this.bookmark = bookmark;
        this.memo = memo;
        this.tags = tags;
    }
}
