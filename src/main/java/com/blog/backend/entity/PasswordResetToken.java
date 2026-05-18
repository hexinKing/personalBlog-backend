package com.blog.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("password_reset_token")
public class PasswordResetToken {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String email;
    private String tokenHash;
    private LocalDateTime expiresAt;
    private LocalDateTime usedTime;
    private Integer failCount;
    private String requestIp;
    private LocalDateTime createTime;
}
