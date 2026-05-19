package com.blog.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blog.backend.common.ArticleCacheKeys;
import com.blog.backend.common.ArticleStatus;
import com.blog.backend.common.BusinessException;
import com.blog.backend.common.InteractionType;
import com.blog.backend.common.Result;
import com.blog.backend.common.SecurityUtils;
import com.blog.backend.dto.InteractionDTO;
import com.blog.backend.entity.Article;
import com.blog.backend.entity.User;
import com.blog.backend.entity.UserArticleInteraction;
import com.blog.backend.mapper.ArticleMapper;
import com.blog.backend.mapper.UserArticleInteractionMapper;
import com.blog.backend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

@Tag(name = "文章互动")
@RestController
@RequestMapping("/api/articles/{articleId}/interactions")
@RequiredArgsConstructor
public class InteractionController {
    private final UserArticleInteractionMapper interactionMapper;
    private final ArticleMapper articleMapper;
    private final UserService userService;
    private final StringRedisTemplate redisTemplate;

    @Operation(summary = "点赞或收藏文章")
    @PostMapping
    @Transactional
    public Result<Void> add(@PathVariable Long articleId, @Valid @RequestBody InteractionDTO dto) {
        User user = currentUser();
        Article article = mustGetPublishedArticle(articleId);
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
            adjustCounter(article, dto.getInteractionType(), 1);
        }
        return Result.success();
    }

    @Operation(summary = "取消点赞或收藏")
    @DeleteMapping
    @Transactional
    public Result<Void> remove(@PathVariable Long articleId, @Valid @RequestBody InteractionDTO dto) {
        User user = currentUser();
        Article article = mustGetPublishedArticle(articleId);
        int deleted = interactionMapper.delete(new LambdaQueryWrapper<UserArticleInteraction>()
                .eq(UserArticleInteraction::getUserId, user.getId())
                .eq(UserArticleInteraction::getArticleId, articleId)
                .eq(UserArticleInteraction::getInteractionType, dto.getInteractionType()));
        if (deleted > 0) {
            adjustCounter(article, dto.getInteractionType(), -deleted);
        }
        return Result.success();
    }

    private User currentUser() {
        User user = userService.getByUsername(SecurityUtils.currentUsername());
        if (user == null) {
            throw new BusinessException(401, "请先登录");
        }
        return user;
    }

    private Article mustGetPublishedArticle(Long articleId) {
        Article article = articleMapper.selectById(articleId);
        if (article == null || !Integer.valueOf(ArticleStatus.PUBLISHED).equals(article.getStatus())) {
            throw new BusinessException(404, "文章不存在或未发布");
        }
        return article;
    }

    private void adjustCounter(Article article, Integer interactionType, int delta) {
        if (Integer.valueOf(InteractionType.LIKE).equals(interactionType)) {
            article.setLikeCount(Math.max(0, defaultInt(article.getLikeCount()) + delta));
        } else if (Integer.valueOf(InteractionType.FAVORITE).equals(interactionType)) {
            article.setFavoriteCount(Math.max(0, defaultInt(article.getFavoriteCount()) + delta));
        }
        article.setHotScore(calculateHotScore(article));
        article.setUpdateTime(LocalDateTime.now());
        articleMapper.updateById(article);
        redisTemplate.delete(ArticleCacheKeys.HOT_ARTICLE);
    }

    private BigDecimal calculateHotScore(Article article) {
        int view = defaultInt(article.getViewCount());
        int comment = defaultInt(article.getCommentCount());
        int like = defaultInt(article.getLikeCount());
        int favorite = defaultInt(article.getFavoriteCount());
        int recommendedBoost = Objects.equals(article.getIsRecommended(), 1) ? 100 : 0;
        return BigDecimal.valueOf(view + comment * 5L + like * 3L + favorite * 4L + recommendedBoost);
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }
}
