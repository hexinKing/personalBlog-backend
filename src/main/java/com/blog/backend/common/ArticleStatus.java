package com.blog.backend.common;

public final class ArticleStatus {

    private ArticleStatus() {
    }

    public static final int DRAFT = 0;
    public static final int PUBLISHED = 1;
    public static final int OFFLINE = 2;
    public static final int ARCHIVED = 3;
    public static final int SCHEDULED = 4;
}
