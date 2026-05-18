package com.blog.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("article")
public class Article {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String title;
    private String slug;
    private String seoTitle;
    private String seoDescription;
    private Integer readingTime;
    private String content;
    private String summary;
    private String coverUrl;
    private Long categoryId;
    private Long authorId;
    private Integer status; // 0-草稿, 1-已发布, 2-已下线, 3-已归档, 4-定时发布
    private Integer isTop;
    private Integer isRecommended;
    private Integer allowComment;
    private Integer viewCount;
    private Integer commentCount;
    private Integer likeCount;
    private Integer favoriteCount;
    private java.math.BigDecimal hotScore;
    private LocalDateTime publishTime;
    private LocalDateTime scheduleTime;
    private LocalDateTime offlineTime;
    private LocalDateTime archiveTime;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
