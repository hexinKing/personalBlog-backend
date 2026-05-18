package com.blog.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.blog.backend.common.CryptoUtils;
import com.blog.backend.entity.AuthToken;
import com.blog.backend.mapper.AuthTokenMapper;
import com.blog.backend.service.AuthTokenService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class AuthTokenServiceImpl extends ServiceImpl<AuthTokenMapper, AuthToken> implements AuthTokenService {
    private static final int TOKEN_TYPE_REFRESH = 1;

    @Override
    public void saveRefreshToken(Long userId, String refreshToken, Long refreshExpiration, String ip, String userAgent) {
        LocalDateTime now = LocalDateTime.now();
        AuthToken authToken = new AuthToken();
        authToken.setUserId(userId);
        // 只保存 refresh token 哈希，数据库泄露时不会直接暴露可用凭证。
        authToken.setTokenHash(CryptoUtils.sha256(refreshToken));
        authToken.setTokenType(TOKEN_TYPE_REFRESH);
        authToken.setIssuedAt(now);
        authToken.setExpiresAt(now.plusSeconds(refreshExpiration));
        authToken.setRequestIp(ip);
        authToken.setUserAgent(userAgent);
        authToken.setCreateTime(now);
        authToken.setUpdateTime(now);
        save(authToken);
    }

    @Override
    public boolean isRefreshTokenActive(String refreshToken) {
        AuthToken authToken = getOne(new LambdaQueryWrapper<AuthToken>()
                .eq(AuthToken::getTokenHash, CryptoUtils.sha256(refreshToken))
                .eq(AuthToken::getTokenType, TOKEN_TYPE_REFRESH));
        return authToken != null
                && authToken.getRevokedAt() == null
                && authToken.getExpiresAt() != null
                && authToken.getExpiresAt().isAfter(LocalDateTime.now());
    }

    @Override
    public void revokeRefreshToken(String refreshToken) {
        AuthToken authToken = getOne(new LambdaQueryWrapper<AuthToken>()
                .eq(AuthToken::getTokenHash, CryptoUtils.sha256(refreshToken))
                .eq(AuthToken::getTokenType, TOKEN_TYPE_REFRESH));
        if (authToken != null && authToken.getRevokedAt() == null) {
            authToken.setRevokedAt(LocalDateTime.now());
            updateById(authToken);
        }
    }
}
