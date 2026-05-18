package com.blog.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blog.backend.common.Result;
import com.blog.backend.common.SecurityUtils;
import com.blog.backend.dto.InteractionDTO;
import com.blog.backend.entity.User;
import com.blog.backend.entity.UserArticleInteraction;
import com.blog.backend.mapper.UserArticleInteractionMapper;
import com.blog.backend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@Tag(name = "文章互动")
@RestController
@RequestMapping("/api/articles/{articleId}/interactions")
@RequiredArgsConstructor
public class InteractionController {
    private final UserArticleInteractionMapper interactionMapper;
    private final UserService userService;

    @Operation(summary = "点赞或收藏文章")
    @PostMapping
    public Result<Void> add(@PathVariable Long articleId, @Valid @RequestBody InteractionDTO dto) {
        User user = userService.getByUsername(SecurityUtils.currentUsername());
        if (user == null) {
            throw new RuntimeException("请先登录");
        }
        Long count = interactionMapper.selectCount(new LambdaQueryWrapper<UserArticleInteraction>()
                .eq(UserArticleInteraction::getUserId, user.getId())
                .eq(UserArticleInteraction::getArticleId, articleId)
                .eq(UserArticleInteraction::getInteractionType, dto.getInteractionType()));
        if (count == 0) {
            UserArticleInteraction interaction = new UserArticleInteraction();
            interaction.setUserId(user.getId());
            interaction.setArticleId(articleId);
            interaction.setInteractionType(dto.getInteractionType());
            interaction.setCreateTime(LocalDateTime.now());
            interactionMapper.insert(interaction);
        }
        return Result.success();
    }

    @Operation(summary = "取消点赞或收藏")
    @DeleteMapping
    public Result<Void> remove(@PathVariable Long articleId, @Valid @RequestBody InteractionDTO dto) {
        User user = userService.getByUsername(SecurityUtils.currentUsername());
        if (user == null) {
            throw new RuntimeException("请先登录");
        }
        interactionMapper.delete(new LambdaQueryWrapper<UserArticleInteraction>()
                .eq(UserArticleInteraction::getUserId, user.getId())
                .eq(UserArticleInteraction::getArticleId, articleId)
                .eq(UserArticleInteraction::getInteractionType, dto.getInteractionType()));
        return Result.success();
    }
}
