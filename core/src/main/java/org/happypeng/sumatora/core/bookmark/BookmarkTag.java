package org.happypeng.sumatora.core.bookmark;

public class BookmarkTag {
    public long seq;
    public String tag;

    public BookmarkTag() { tag = ""; }

    public BookmarkTag(long seq, String tag) {
        this.seq = seq;
        this.tag = tag;
    }
}
