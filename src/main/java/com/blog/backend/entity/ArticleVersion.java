package com.blog.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("article_version")
public class ArticleVersion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long articleId;
    private Integer versionNo;
    private String title;
    private String slug;
    private String content;
    private String summary;
    private Long categoryId;
    private String coverUrl;
    private String seoTitle;
    private String seoDescription;
    private Integer status;
    private Long editorId;
    private String changeNote;
    private LocalDateTime createTime;
}
