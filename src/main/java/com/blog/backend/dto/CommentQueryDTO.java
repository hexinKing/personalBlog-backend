package com.blog.backend.dto;

import lombok.Data;

@Data
public class CommentQueryDTO {
    private Integer pageNum = 1;
    private Integer pageSize = 10;
    private Long articleId;
    private Integer status;
    private String keyword;
    private String nickname;
    private String email;
}
