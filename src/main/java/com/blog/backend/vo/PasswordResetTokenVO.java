package com.blog.backend.vo;

import lombok.Data;

@Data
public class PasswordResetTokenVO {
    private String message;
    private String resetToken;
}
