package com.blog.backend.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.blog.backend.dto.PasswordResetConfirmRequest;
import com.blog.backend.dto.RegisterRequest;
import com.blog.backend.entity.User;
import com.blog.backend.vo.PasswordResetTokenVO;

public interface UserService extends IService<User> {
    User getByUsername(String username);
    void register(User user);
    void register(RegisterRequest request);
    void resetPassword(String email, String newPassword);
    PasswordResetTokenVO requestPasswordReset(String email, String ip);
    void confirmPasswordReset(PasswordResetConfirmRequest request);
    void assertCanLogin(String username);
    void recordLoginSuccess(String username, String ip);
    void recordLoginFailure(String username);
}
