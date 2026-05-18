package com.blog.backend.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class CommentVO {
    private Long id;
    private Long articleId;
    private Long parentId;
    private Long rootId;
    private Long userId;
    private String nickname;
    private String email;
    private String content;
    private Integer status;
    private Integer likeCount;
    private LocalDateTime createTime;
    private List<CommentVO> children = new ArrayList<>();
}
