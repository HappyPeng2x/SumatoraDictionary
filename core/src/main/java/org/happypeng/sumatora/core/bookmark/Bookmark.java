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
