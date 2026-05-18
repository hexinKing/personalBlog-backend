package com.blog.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class ArticleSaveDTO {
    private Long id;

    @NotBlank(message = "文章标题不能为空")
    @Size(max = 255, message = "文章标题不能超过255个字符")
    private String title;

    @Size(max = 150, message = "slug不能超过150个字符")
    private String slug;

    private String seoTitle;
    private String seoDescription;
    private Integer readingTime;

    @NotBlank(message = "文章内容不能为空")
    private String content;

    private String summary;
    private String coverUrl;
    private Long categoryId;
    private List<Long> tagIds;

    @NotNull(message = "文章状态不能为空")
    private Integer status;

    private Integer isTop;
    private Integer isRecommended;
    private Integer allowComment;
    private LocalDateTime publishTime;
    private LocalDateTime scheduleTime;
    private String changeNote;
}
