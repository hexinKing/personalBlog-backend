package com.blog.backend.vo;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ArticleVO {
    private Long id;
    private String title;
    private String slug;
    private String seoTitle;
    private String seoDescription;
    private String content;
    private String summary;
    private String coverUrl;
    private Long categoryId;
    private String categoryName;
    private Long authorId;
    private String authorName;
    private Integer status;
    private Integer isTop;
    private Integer isRecommended;
    private Integer allowComment;
    private List<String> tags;
    private List<Long> tagIds;
    private Integer viewCount;
    private Integer commentCount;
    private Integer likeCount;
    private Integer favoriteCount;
    private Integer readingTime;
    private LocalDateTime publishTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
