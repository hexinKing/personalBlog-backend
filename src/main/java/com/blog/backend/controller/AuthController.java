package com.blog.backend.controller;

import com.blog.backend.common.BusinessException;
import com.blog.backend.common.ClientIpUtils;
import com.blog.backend.common.Result;
import com.blog.backend.dto.LoginRequest;
import com.blog.backend.dto.PasswordResetConfirmRequest;
import com.blog.backend.dto.PasswordResetRequest;
import com.blog.backend.dto.RegisterRequest;
import com.blog.backend.dto.TokenRefreshRequest;
import com.blog.backend.entity.User;
import com.blog.backend.security.JwtUtils;
import com.blog.backend.service.AuthTokenService;
import com.blog.backend.service.UserService;
import com.blog.backend.vo.LoginVO;
import com.blog.backend.vo.PasswordResetTokenVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@Tag(name = "认证管理")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String LOGIN_IP_KEY_PREFIX = "auth:login:ip:";
    private static final int LOGIN_IP_LIMIT = 20;
    private static final Duration LOGIN_IP_WINDOW = Duration.ofMinutes(1);

    private final AuthenticationManager authenticationManager;
    private final JwtUtils jwtUtils;
    private final UserService userService;
    private final AuthTokenService authTokenService;
    private final StringRedisTemplate redisTemplate;

    @Operation(summary = "登录")
    @PostMapping("/login")
    public Result<LoginVO> login(@Valid @RequestBody LoginRequest loginRequest, HttpServletRequest request) {
        String ip = ClientIpUtils.getClientIp(request);
        assertLoginRateLimit(ip);
        userService.assertCanLogin(loginRequest.getUsername());
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(loginRequest.getUsername(), loginRequest.getPassword()));

            String token = jwtUtils.createToken(authentication.getName());
            String refreshToken = jwtUtils.createRefreshToken(authentication.getName());
            User user = userService.getByUsername(authentication.getName());
            if (user == null) {
                throw new BusinessException(404, "用户不存在");
            }
            authTokenService.saveRefreshToken(user.getId(), refreshToken, jwtUtils.getRefreshExpiration(),
                    ip, request.getHeader("User-Agent"));
            userService.recordLoginSuccess(authentication.getName(), ip);

            LoginVO result = new LoginVO();
            result.setToken(token);
            result.setRefreshToken(refreshToken);
            result.setUsername(authentication.getName());
            result.setRole(user.getRole());
            result.setExpiresIn(jwtUtils.getExpiration());
            return Result.success(result);
        } catch (BadCredentialsException e) {
            userService.recordLoginFailure(loginRequest.getUsername());
            throw e;
        }
    }

    @Operation(summary = "注册")
    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterRequest registerRequest) {
        userService.register(registerRequest);
        return Result.success();
    }

    @Operation(summary = "申请重置密码")
    @PostMapping("/password-reset/request")
    public Result<PasswordResetTokenVO> requestPasswordReset(@Valid @RequestBody PasswordResetRequest request,
                                                            HttpServletRequest servletRequest) {
        return Result.success(userService.requestPasswordReset(request.getEmail(), ClientIpUtils.getClientIp(servletRequest)));
    }

    @Operation(summary = "确认重置密码")
    @PostMapping("/password-reset/confirm")
    public Result<Void> confirmPasswordReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        userService.confirmPasswordReset(request);
        return Result.success();
    }

    @Operation(summary = "刷新访问令牌")
    @PostMapping("/refresh")
    public Result<LoginVO> refresh(@Valid @RequestBody TokenRefreshRequest request) {
        if (!jwtUtils.validateToken(request.getRefreshToken())
                || !jwtUtils.isRefreshToken(request.getRefreshToken())
                || !authTokenService.isRefreshTokenActive(request.getRefreshToken())) {
            throw new BusinessException(401, "refreshToken无效或已过期");
        }
        String username = jwtUtils.getUsernameFromToken(request.getRefreshToken());
        User user = userService.getByUsername(username);
        String token = jwtUtils.createToken(username);

        LoginVO result = new LoginVO();
        result.setToken(token);
        result.setRefreshToken(request.getRefreshToken());
        result.setUsername(username);
        result.setRole(user == null ? null : user.getRole());
        result.setExpiresIn(jwtUtils.getExpiration());
        return Result.success(result);
    }

    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    public Result<Void> logout(@Valid @RequestBody TokenRefreshRequest request) {
        authTokenService.revokeRefreshToken(request.getRefreshToken());
        return Result.success();
    }

    private void assertLoginRateLimit(String ip) {
        String key = LOGIN_IP_KEY_PREFIX + (ip == null ? "unknown" : ip);
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, LOGIN_IP_WINDOW);
        }
        if (count != null && count > LOGIN_IP_LIMIT) {
            throw new BusinessException(429, "登录过于频繁，请稍后再试");
        }
    }
}
