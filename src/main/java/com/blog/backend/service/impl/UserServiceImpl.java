package com.blog.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.blog.backend.common.CryptoUtils;
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

    private final PasswordEncoder passwordEncoder;
    private final PasswordResetTokenMapper passwordResetTokenMapper;

    private static final int LOGIN_FAIL_LIMIT = 5;
    private static final int LOGIN_LOCK_MINUTES = 15;

    @Override
    public User getByUsername(String username) {
        return baseMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getUsername, username));
    }

    @Override
    public void register(User user) {
        // 检查用户名是否已存在
        if (getByUsername(user.getUsername()) != null) {
            throw new RuntimeException("用户名已存在");
        }
        // 检查邮箱是否已存在
        if (baseMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, user.getEmail())) != null) {
            throw new RuntimeException("邮箱已被注册");
        }
        
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        user.setRole(user.getRole() == null ? "USER" : user.getRole());
        user.setStatus(user.getStatus() == null ? 1 : user.getStatus());
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
            throw new RuntimeException("邮箱不存在");
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
            throw new RuntimeException("邮箱不存在");
        }

        // 这里只返回 token 是为了便于本地联调；接入邮件服务后应改为发送邮件，不在响应中暴露。
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

        // 重置密码令牌只允许一次性使用，避免邮件泄露后被反复利用。
        if (resetToken == null) {
            throw new RuntimeException("重置令牌无效");
        }
        if (resetToken.getUsedTime() != null) {
            throw new RuntimeException("重置令牌已使用");
        }
        if (resetToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new RuntimeException("重置令牌已过期");
        }
        if (resetToken.getFailCount() != null && resetToken.getFailCount() >= 5) {
            throw new RuntimeException("重置令牌错误次数过多");
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
        if (user.getStatus() != null && user.getStatus() == 0) {
            throw new RuntimeException("账号已被禁用");
        }
        if (user.getLockedUntil() != null && user.getLockedUntil().isAfter(LocalDateTime.now())) {
            throw new RuntimeException("登录失败次数过多，请稍后再试");
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
        user.setStatus(1);
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
        // 连续失败锁定账号，降低暴力破解风险。
        int failCount = user.getLoginFailCount() == null ? 1 : user.getLoginFailCount() + 1;
        user.setLoginFailCount(failCount);
        if (failCount >= LOGIN_FAIL_LIMIT) {
            user.setLockedUntil(LocalDateTime.now().plusMinutes(LOGIN_LOCK_MINUTES));
            user.setStatus(2);
        }
        user.setUpdateTime(LocalDateTime.now());
        updateById(user);
    }
}
