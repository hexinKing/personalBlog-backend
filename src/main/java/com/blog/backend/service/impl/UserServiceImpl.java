package com.blog.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.blog.backend.common.BusinessException;
import com.blog.backend.common.CryptoUtils;
import com.blog.backend.common.UserStatus;
import com.blog.backend.dto.PasswordResetConfirmRequest;
import com.blog.backend.dto.RegisterRequest;
import com.blog.backend.entity.PasswordResetToken;
import com.blog.backend.entity.User;
import com.blog.backend.mapper.PasswordResetTokenMapper;
import com.blog.backend.mapper.UserMapper;
import com.blog.backend.service.UserService;
import com.blog.backend.vo.PasswordResetTokenVO;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    private static final int LOGIN_FAIL_LIMIT = 5;
    private static final int LOGIN_LOCK_MINUTES = 15;

    private final PasswordEncoder passwordEncoder;
    private final PasswordResetTokenMapper passwordResetTokenMapper;

    @Override
    public User getByUsername(String username) {
        return baseMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
    }

    @Override
    public void register(User user) {
        if (getByUsername(user.getUsername()) != null) {
            throw new BusinessException("用户名已存在");
        }
        if (baseMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, user.getEmail())) != null) {
            throw new BusinessException("邮箱已被注册");
        }

        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setRole("USER");
        user.setStatus(UserStatus.ENABLED);
        user.setLoginFailCount(0);
        user.setPasswordVersion(1);
        user.setDeleted(0);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        save(user);
    }

    @Override
    public void register(RegisterRequest request) {
        User user = new User();
        user.setUsername(request.getUsername());
        user.setNickname(request.getNickname());
        user.setPassword(request.getPassword());
        user.setEmail(request.getEmail());
        register(user);
    }

    @Override
    public void resetPassword(String email, String newPassword) {
        User user = baseMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        if (user == null) {
            throw new BusinessException(404, "邮箱不存在");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        user.setPasswordVersion((user.getPasswordVersion() == null ? 1 : user.getPasswordVersion()) + 1);
        user.setUpdateTime(LocalDateTime.now());
        updateById(user);
    }

    @Override
    public PasswordResetTokenVO requestPasswordReset(String email, String ip) {
        User user = baseMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        if (user == null) {
            throw new BusinessException(404, "邮箱不存在");
        }

        String token = CryptoUtils.randomToken();
        PasswordResetToken resetToken = new PasswordResetToken();
        resetToken.setUserId(user.getId());
        resetToken.setEmail(email);
        resetToken.setTokenHash(CryptoUtils.sha256(token));
        resetToken.setExpiresAt(LocalDateTime.now().plusMinutes(30));
        resetToken.setFailCount(0);
        resetToken.setRequestIp(ip);
        resetToken.setCreateTime(LocalDateTime.now());
        passwordResetTokenMapper.insert(resetToken);

        PasswordResetTokenVO vo = new PasswordResetTokenVO();
        vo.setMessage("重置令牌已生成，有效期30分钟。接入邮件服务后应通过邮件发送。");
        vo.setResetToken(token);
        return vo;
    }

    @Override
    public void confirmPasswordReset(PasswordResetConfirmRequest request) {
        PasswordResetToken resetToken = passwordResetTokenMapper.selectOne(new LambdaQueryWrapper<PasswordResetToken>()
                .eq(PasswordResetToken::getEmail, request.getEmail())
                .eq(PasswordResetToken::getTokenHash, CryptoUtils.sha256(request.getToken())));

        if (resetToken == null) {
            throw new BusinessException("重置令牌无效");
        }
        if (resetToken.getUsedTime() != null) {
            throw new BusinessException("重置令牌已使用");
        }
        if (resetToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException("重置令牌已过期");
        }
        if (resetToken.getFailCount() != null && resetToken.getFailCount() >= 5) {
            throw new BusinessException("重置令牌错误次数过多");
        }

        resetPassword(request.getEmail(), request.getNewPassword());
        resetToken.setUsedTime(LocalDateTime.now());
        passwordResetTokenMapper.updateById(resetToken);
    }

    @Override
    public void assertCanLogin(String username) {
        User user = getByUsername(username);
        if (user == null) {
            return;
        }
        if (user.getStatus() != null && user.getStatus() == UserStatus.DISABLED) {
            throw new BusinessException(403, "账号已被禁用");
        }
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            throw new BusinessException(423, "登录失败次数过多，请稍后再试");
        }
    }

    @Override
    public void recordLoginSuccess(String username, String ip) {
        User user = getByUsername(username);
        if (user == null) {
            return;
        }
        user.setLoginFailCount(0);
        user.setLockedUntil(null);
        user.setStatus(UserStatus.ENABLED);
        user.setLastLoginIp(ip);
        user.setLastLoginTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        updateById(user);
    }

    @Override
    public void recordLoginFailure(String username) {
        User user = getByUsername(username);
        if (user == null) {
            return;
        }
        int failCount = user.getLoginFailCount() == null ? 1 : user.getLoginFailCount() + 1;
        user.setLoginFailCount(failCount);
        if (failCount >= LOGIN_FAIL_LIMIT) {
            user.setLockedUntil(LocalDateTime.now().plusMinutes(LOGIN_LOCK_MINUTES));
            user.setStatus(UserStatus.LOCKED);
        }
        user.setUpdateTime(LocalDateTime.now());
        updateById(user);
    }
}
