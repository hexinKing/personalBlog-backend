package com.blog.backend.vo;

import com.blog.backend.entity.Article;
import lombok.Data;

import java.util.List;

@Data
public class DashboardVO {
    private Long totalArticles;
    private Long publishedArticles;
    private Long draftArticles;
    private Long totalViews;
    private Long totalComments;
    private Long pendingComments;
    private Long categoryCount;
    private Long tagCount;
    private List<Article> hotArticles;
}
