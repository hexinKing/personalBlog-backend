package com.blog.backend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.blog.backend.entity.AuthToken;

public interface AuthTokenService extends IService<AuthToken> {
    void saveRefreshToken(Long userId, String refreshToken, Long refreshExpiration, String ip, String userAgent);
    boolean isRefreshTokenActive(String refreshToken);
    void revokeRefreshToken(String refreshToken);
}
