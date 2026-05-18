package com.blog.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("comment")
public class Comment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long articleId;
    private Long parentId;
    private Long rootId;
    private Long userId;
    private Long replyToUserId;
    private String nickname;
    private String email;
    private String content;
    private String ipHash;
    private String userAgent;
    private Integer status; // 0-审核中, 1-通过, 2-拒绝
    private Integer likeCount;
    private Long auditUserId;
    private LocalDateTime auditTime;
    private String rejectReason;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
