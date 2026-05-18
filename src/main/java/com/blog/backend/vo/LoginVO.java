package com.blog.backend.vo;

import lombok.Data;

@Data
public class LoginVO {
    private String token;
    private String refreshToken;
    private String username;
    private String role;
    private Long expiresIn;
}
