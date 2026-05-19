package com.blog.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.blog.backend.common.ArticleCacheKeys;
import com.blog.backend.common.ArticleStatus;
import com.blog.backend.common.BusinessException;
import com.blog.backend.dto.ArticleQueryDTO;
import com.blog.backend.dto.ArticleSaveDTO;
import com.blog.backend.entity.Article;
import com.blog.backend.entity.ArticleDailyStat;
import com.blog.backend.entity.ArticleTag;
import com.blog.backend.entity.ArticleVersion;
import com.blog.backend.entity.Category;
import com.blog.backend.entity.Comment;
import com.blog.backend.entity.Notification;
import com.blog.backend.entity.Tag;
import com.blog.backend.entity.User;
import com.blog.backend.entity.UserArticleInteraction;
import com.blog.backend.mapper.ArticleDailyStatMapper;
import com.blog.backend.mapper.ArticleMapper;
import com.blog.backend.mapper.ArticleTagMapper;
import com.blog.backend.mapper.ArticleVersionMapper;
import com.blog.backend.mapper.CategoryMapper;
import com.blog.backend.mapper.CommentMapper;
import com.blog.backend.mapper.NotificationMapper;
import com.blog.backend.mapper.TagMapper;
import com.blog.backend.mapper.UserArticleInteractionMapper;
import com.blog.backend.service.ArticleService;
import com.blog.backend.service.AuditService;
import com.blog.backend.service.UserService;
import com.blog.backend.vo.ArticleVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ArticleServiceImpl extends ServiceImpl<ArticleMapper, Article> implements ArticleService {

    private final CategoryMapper categoryMapper;
    private final TagMapper tagMapper;
    private final ArticleTagMapper articleTagMapper;
    private final ArticleVersionMapper articleVersionMapper;
    private final CommentMapper commentMapper;
    private final UserArticleInteractionMapper interactionMapper;
    private final ArticleDailyStatMapper articleDailyStatMapper;
    private final NotificationMapper notificationMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final UserService userService;
    private final AuditService auditService;

    @Override
    public List<Article> listHotArticles() {
        String json = redisTemplate.opsForValue().get(ArticleCacheKeys.HOT_ARTICLE);
        if (json != null) {
            try {
                return objectMapper.readValue(json, new TypeReference<List<Article>>() {});
            } catch (JsonProcessingException e) {
                log.error("解析热门文章缓存失败", e);
            }
        }

        List<Article> hotArticles = baseMapper.selectList(new LambdaQueryWrapper<Article>()
                .select(Article::getId, Article::getTitle, Article::getSlug, Article::getSummary, Article::getCoverUrl,
                        Article::getCategoryId, Article::getAuthorId, Article::getStatus, Article::getIsTop,
                        Article::getIsRecommended, Article::getViewCount, Article::getCommentCount,
                        Article::getLikeCount, Article::getFavoriteCount, Article::getHotScore,
                        Article::getPublishTime, Article::getCreateTime, Article::getUpdateTime)
                .eq(Article::getStatus, ArticleStatus.PUBLISHED)
                .orderByDesc(Article::getIsRecommended)
                .orderByDesc(Article::getHotScore)
                .orderByDesc(Article::getViewCount)
                .last("LIMIT 10"));

        try {
            redisTemplate.opsForValue().set(ArticleCacheKeys.HOT_ARTICLE, objectMapper.writeValueAsString(hotArticles), 1, TimeUnit.HOURS);
        } catch (JsonProcessingException e) {
            log.error("设置热门文章缓存失败", e);
        }

        return hotArticles;
    }

    @Override
    public Page<ArticleVO> listArticles(ArticleQueryDTO queryDTO) {
        int pageNum = queryDTO.getPageNum() == null || queryDTO.getPageNum() < 1 ? 1 : queryDTO.getPageNum();
        int pageSize = queryDTO.getPageSize() == null ? 10 : Math.min(Math.max(queryDTO.getPageSize(), 1), 100);
        Page<Article> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<>();

        if (queryDTO.getCategoryId() != null) {
            wrapper.eq(Article::getCategoryId, queryDTO.getCategoryId());
        }
        if (queryDTO.getStatus() != null) {
            wrapper.eq(Article::getStatus, queryDTO.getStatus());
        } else {
            wrapper.eq(Article::getStatus, ArticleStatus.PUBLISHED);
        }
        if (queryDTO.getKeyword() != null && !queryDTO.getKeyword().isBlank()) {
            wrapper.and(w -> w.like(Article::getTitle, queryDTO.getKeyword())
                    .or().like(Article::getSummary, queryDTO.getKeyword())
                    .or().like(Article::getContent, queryDTO.getKeyword()));
        }
        if (queryDTO.getTagId() != null) {
            List<Long> articleIds = articleTagMapper.selectList(new LambdaQueryWrapper<ArticleTag>()
                            .eq(ArticleTag::getTagId, queryDTO.getTagId()))
                    .stream().map(ArticleTag::getArticleId).toList();
            if (articleIds.isEmpty()) {
                return emptyVoPage(pageNum, pageSize);
            }
            wrapper.in(Article::getId, articleIds);
        }

        wrapper.orderByDesc(Article::getCreateTime);
        baseMapper.selectPage(page, wrapper);

        Page<ArticleVO> voPage = new Page<>();
        BeanUtils.copyProperties(page, voPage, "records");
        voPage.setRecords(toArticleVOList(page.getRecords()));
        return voPage;
    }

    @Override
    @Transactional
    public ArticleVO getArticleDetail(Long id, String ip) {
        Article article = baseMapper.selectById(id);
        if (article == null) {
            throw new BusinessException(404, "文章不存在");
        }

        String key = "article:view:" + id + ":" + (ip == null ? "unknown" : ip);
        if (Boolean.FALSE.equals(redisTemplate.hasKey(key))) {
            article.setViewCount(defaultInt(article.getViewCount()) + 1);
            article.setHotScore(calculateHotScore(article));
            baseMapper.updateById(article);
            redisTemplate.opsForValue().set(key, "1", 24, TimeUnit.HOURS);
        }

        return toArticleVO(article);
    }

    @Override
    @Transactional
    public Long saveArticle(ArticleSaveDTO dto, String operatorUsername) {
        Article article = dto.getId() == null ? new Article() : getById(dto.getId());
        if (article == null) {
            throw new BusinessException(404, "文章不存在");
        }

        boolean creating = article.getId() == null;
        Long oldCategoryId = article.getCategoryId();
        applyArticleFields(dto, article);
        article.setViewCount(defaultInt(article.getViewCount()));
        article.setCommentCount(defaultInt(article.getCommentCount()));
        article.setLikeCount(defaultInt(article.getLikeCount()));
        article.setFavoriteCount(defaultInt(article.getFavoriteCount()));
        article.setIsTop(defaultInt(article.getIsTop()));
        article.setIsRecommended(defaultInt(article.getIsRecommended()));
        article.setAllowComment(article.getAllowComment() == null ? 1 : article.getAllowComment());
        article.setHotScore(calculateHotScore(article));

        if (Objects.equals(article.getStatus(), ArticleStatus.PUBLISHED) && article.getPublishTime() == null) {
            article.setPublishTime(LocalDateTime.now());
        }
        if (Objects.equals(article.getStatus(), ArticleStatus.SCHEDULED) && article.getScheduleTime() == null) {
            throw new BusinessException("定时发布必须设置发布时间");
        }

        if (creating) {
            article.setAuthorId(resolveUserId(operatorUsername));
            article.setDeleted(0);
            article.setCreateTime(LocalDateTime.now());
        }
        article.setUpdateTime(LocalDateTime.now());
        saveOrUpdate(article);

        List<Long> oldTagIds = creating ? Collections.emptyList() : listArticleTagIds(article.getId());
        syncArticleTags(article.getId(), dto.getTagIds());
        saveVersion(article, dto.getChangeNote(), resolveUserId(operatorUsername));

        refreshCategoryAndTagCounts(article.getCategoryId(), mergeTagIds(oldTagIds, dto.getTagIds()));
        if (oldCategoryId != null && !oldCategoryId.equals(article.getCategoryId())) {
            refreshCategoryAndTagCounts(oldCategoryId, null);
        }
        redisTemplate.delete(ArticleCacheKeys.HOT_ARTICLE);
        auditService.record(creating ? "ARTICLE_CREATE" : "ARTICLE_UPDATE", "ARTICLE", article.getId(), article.getTitle(), true, null);
        return article.getId();
    }

    @Override
    @Transactional
    public void deleteArticle(Long id) {
        Article article = getById(id);
        if (article == null) {
            return;
        }
        List<Long> tagIds = listArticleTagIds(id);
        Long categoryId = article.getCategoryId();

        articleVersionMapper.delete(new LambdaQueryWrapper<ArticleVersion>().eq(ArticleVersion::getArticleId, id));
        commentMapper.delete(new LambdaQueryWrapper<Comment>().eq(Comment::getArticleId, id));
        interactionMapper.delete(new LambdaQueryWrapper<UserArticleInteraction>().eq(UserArticleInteraction::getArticleId, id));
        articleDailyStatMapper.delete(new LambdaQueryWrapper<ArticleDailyStat>().eq(ArticleDailyStat::getArticleId, id));
        notificationMapper.delete(new LambdaQueryWrapper<Notification>()
                .eq(Notification::getRelatedType, "ARTICLE")
                .eq(Notification::getRelatedId, id));
        articleTagMapper.delete(new LambdaQueryWrapper<ArticleTag>().eq(ArticleTag::getArticleId, id));
        removeById(id);

        refreshCategoryAndTagCounts(categoryId, tagIds);
        redisTemplate.delete(ArticleCacheKeys.HOT_ARTICLE);
        auditService.record("ARTICLE_DELETE", "ARTICLE", id, article.getTitle(), true, null);
    }

    private Page<ArticleVO> emptyVoPage(long pageNum, long pageSize) {
        Page<ArticleVO> page = new Page<>(pageNum, pageSize);
        page.setRecords(Collections.emptyList());
        page.setTotal(0);
        return page;
    }

    private List<ArticleVO> toArticleVOList(List<Article> articles) {
        if (articles == null || articles.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> articleIds = articles.stream().map(Article::getId).toList();
        Map<Long, Category> categories = loadCategories(articles);
        Map<Long, List<Long>> tagIdsByArticleId = loadTagIds(articleIds);
        Map<Long, Tag> tags = loadTags(tagIdsByArticleId);

        return articles.stream()
                .map(article -> toArticleVO(article, categories, tagIdsByArticleId, tags))
                .toList();
    }

    private ArticleVO toArticleVO(Article article) {
        Map<Long, Category> categories = loadCategories(List.of(article));
        Map<Long, List<Long>> tagIdsByArticleId = loadTagIds(List.of(article.getId()));
        Map<Long, Tag> tags = loadTags(tagIdsByArticleId);
        return toArticleVO(article, categories, tagIdsByArticleId, tags);
    }

    private ArticleVO toArticleVO(Article article, Map<Long, Category> categories,
                                  Map<Long, List<Long>> tagIdsByArticleId, Map<Long, Tag> tags) {
        ArticleVO vo = new ArticleVO();
        BeanUtils.copyProperties(article, vo);

        Category category = categories.get(article.getCategoryId());
        if (category != null) {
            vo.setCategoryName(category.getName());
        }

        List<Long> tagIds = tagIdsByArticleId.getOrDefault(article.getId(), Collections.emptyList());
        vo.setTagIds(tagIds);
        vo.setTags(tagIds.stream()
                .map(tags::get)
                .filter(Objects::nonNull)
                .map(Tag::getName)
                .toList());
        return vo;
    }

    private Map<Long, Category> loadCategories(List<Article> articles) {
        List<Long> categoryIds = articles.stream()
                .map(Article::getCategoryId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (categoryIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return categoryMapper.selectBatchIds(categoryIds).stream()
                .collect(Collectors.toMap(Category::getId, Function.identity()));
    }

    private Map<Long, List<Long>> loadTagIds(List<Long> articleIds) {
        if (articleIds == null || articleIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return articleTagMapper.selectList(new LambdaQueryWrapper<ArticleTag>().in(ArticleTag::getArticleId, articleIds))
                .stream()
                .collect(Collectors.groupingBy(ArticleTag::getArticleId,
                        Collectors.mapping(ArticleTag::getTagId, Collectors.toList())));
    }

    private Map<Long, Tag> loadTags(Map<Long, List<Long>> tagIdsByArticleId) {
        List<Long> tagIds = tagIdsByArticleId.values().stream()
                .flatMap(List::stream)
                .distinct()
                .toList();
        if (tagIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return tagMapper.selectBatchIds(tagIds).stream()
                .collect(Collectors.toMap(Tag::getId, Function.identity()));
    }

    private List<Long> listArticleTagIds(Long articleId) {
        if (articleId == null) {
            return Collections.emptyList();
        }
        return articleTagMapper.selectList(new LambdaQueryWrapper<ArticleTag>().eq(ArticleTag::getArticleId, articleId))
                .stream()
                .map(ArticleTag::getTagId)
                .toList();
    }

    private void applyArticleFields(ArticleSaveDTO dto, Article article) {
        article.setTitle(dto.getTitle());
        article.setSlug(dto.getSlug());
        article.setSeoTitle(dto.getSeoTitle());
        article.setSeoDescription(dto.getSeoDescription());
        article.setReadingTime(dto.getReadingTime());
        article.setContent(dto.getContent());
        article.setSummary(dto.getSummary());
        article.setCoverUrl(dto.getCoverUrl());
        article.setCategoryId(dto.getCategoryId());
        article.setStatus(dto.getStatus());
        article.setIsTop(dto.getIsTop());
        article.setIsRecommended(dto.getIsRecommended());
        article.setAllowComment(dto.getAllowComment());
        article.setPublishTime(dto.getPublishTime());
        article.setScheduleTime(dto.getScheduleTime());
    }

    private void syncArticleTags(Long articleId, List<Long> tagIds) {
        articleTagMapper.delete(new LambdaQueryWrapper<ArticleTag>().eq(ArticleTag::getArticleId, articleId));
        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }
        tagIds.stream().distinct().forEach(tagId -> {
            ArticleTag articleTag = new ArticleTag();
            articleTag.setArticleId(articleId);
            articleTag.setTagId(tagId);
            articleTag.setCreateTime(LocalDateTime.now());
            articleTagMapper.insert(articleTag);
        });
    }

    private void saveVersion(Article article, String changeNote, Long editorId) {
        Integer maxVersionNo = articleVersionMapper.selectObjs(new QueryWrapper<ArticleVersion>()
                        .select("COALESCE(MAX(version_no), 0)")
                        .eq("article_id", article.getId()))
                .stream()
                .findFirst()
                .map(value -> ((Number) value).intValue())
                .orElse(0);
        ArticleVersion version = new ArticleVersion();
        BeanUtils.copyProperties(article, version);
        version.setId(null);
        version.setArticleId(article.getId());
        version.setVersionNo(maxVersionNo + 1);
        version.setEditorId(editorId);
        version.setChangeNote(changeNote);
        version.setCreateTime(LocalDateTime.now());
        articleVersionMapper.insert(version);
    }

    private List<Long> mergeTagIds(List<Long> oldTagIds, List<Long> newTagIds) {
        Map<Long, Boolean> merged = new HashMap<>();
        if (oldTagIds != null) {
            oldTagIds.forEach(tagId -> merged.put(tagId, true));
        }
        if (newTagIds != null) {
            newTagIds.forEach(tagId -> merged.put(tagId, true));
        }
        return merged.keySet().stream().toList();
    }

    private void refreshCategoryAndTagCounts(Long categoryId, List<Long> tagIds) {
        if (categoryId != null) {
            Category category = categoryMapper.selectById(categoryId);
            if (category != null) {
                category.setArticleCount(Math.toIntExact(baseMapper.selectCount(new LambdaQueryWrapper<Article>()
                        .eq(Article::getCategoryId, categoryId)
                        .eq(Article::getStatus, ArticleStatus.PUBLISHED))));
                categoryMapper.updateById(category);
            }
        }
        if (tagIds != null) {
            tagIds.stream().filter(Objects::nonNull).distinct().forEach(tagId -> {
                Tag tag = tagMapper.selectById(tagId);
                if (tag != null) {
                    Long count = articleTagMapper.selectCount(new LambdaQueryWrapper<ArticleTag>().eq(ArticleTag::getTagId, tagId));
                    tag.setArticleCount(Math.toIntExact(count));
                    tagMapper.updateById(tag);
                }
            });
        }
    }

    private Long resolveUserId(String username) {
        if (username == null) {
            return null;
        }
        User user = userService.getByUsername(username);
        return user == null ? null : user.getId();
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
