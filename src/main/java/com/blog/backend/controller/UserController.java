package com.blog.backend.controller;

import com.blog.backend.common.Result;
import com.blog.backend.entity.User;
import com.blog.backend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "用户管理")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "获取用户信息")
    @GetMapping("/info")
    public Result<User> getInfo(@RequestParam String username) {
        User user = userService.getByUsername(username);
        if (user != null) {
            user.setPassword(null); // 安全起见不返回密码
        }
        return Result.success(user);
    }
}
