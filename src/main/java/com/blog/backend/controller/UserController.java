package com.blog.backend.controller;

import com.blog.backend.common.BusinessException;
import com.blog.backend.common.Result;
import com.blog.backend.common.SecurityUtils;
import com.blog.backend.entity.User;
import com.blog.backend.service.UserService;
import com.blog.backend.vo.UserInfoVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "用户管理")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "获取用户信息")
    @GetMapping("/info")
    public Result<UserInfoVO> getInfo(@RequestParam(required = false) String username) {
        String currentUsername = SecurityUtils.currentUsername();
        if (currentUsername == null) {
            throw new BusinessException(401, "请先登录");
        }
        if (username == null || username.isBlank()) {
            username = currentUsername;
        }
        if (!SecurityUtils.hasRole("ADMIN") && !currentUsername.equals(username)) {
            throw new BusinessException(403, "只能查看自己的用户信息");
        }

        User user = userService.getByUsername(username);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }

        UserInfoVO vo = new UserInfoVO();
        BeanUtils.copyProperties(user, vo);
        return Result.success(vo);
    }
}
