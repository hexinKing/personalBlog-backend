package com.blog.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.blog.backend.dto.ArticleQueryDTO;
import com.blog.backend.dto.ArticleSaveDTO;
import com.blog.backend.entity.Article;
import com.blog.backend.entity.ArticleTag;
import com.blog.backend.entity.ArticleVersion;
import com.blog.backend.entity.Category;
import com.blog.backend.entity.Tag;
import com.blog.backend.entity.User;
import com.blog.backend.mapper.ArticleMapper;
import com.blog.backend.mapper.ArticleTagMapper;
import com.blog.backend.mapper.ArticleVersionMapper;
import com.blog.backend.mapper.CategoryMapper;
import com.blog.backend.mapper.TagMapper;
import com.blog.backend.service.ArticleService;
import com.blog.backend.service.AuditService;
import com.blog.backend.service.UserService;
import com.blog.backend.vo.ArticleVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ArticleServiceImpl extends ServiceImpl<ArticleMapper, Article> implements ArticleService {

    private final CategoryMapper categoryMapper;
    private final TagMapper tagMapper;
    private final ArticleTagMapper articleTagMapper;
    private final ArticleVersionMapper articleVersionMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final UserService userService;
    private final AuditService auditService;

    private static final String HOT_ARTICLE_CACHE_KEY = "article:hot";

    @Override
    public List<Article> listHotArticles() {
        String json = redisTemplate.opsForValue().get(HOT_ARTICLE_CACHE_KEY);
        if (json != null) {
            try {
                return objectMapper.readValue(json, new TypeReference<List<Article>>() {});
            } catch (JsonProcessingException e) {
                log.error("解析热门文章缓存失败", e);
            }
        }

        List<Article> hotArticles = baseMapper.selectList(new LambdaQueryWrapper<Article>()
                .eq(Article::getStatus, 1)
                .orderByDesc(Article::getIsRecommended)
                .orderByDesc(Article::getHotScore)
                .orderByDesc(Article::getViewCount)
                .last("LIMIT 10"));
        
        try {
            redisTemplate.opsForValue().set(HOT_ARTICLE_CACHE_KEY, objectMapper.writeValueAsString(hotArticles), 1, TimeUnit.HOURS);
        } catch (JsonProcessingException e) {
            log.error("设置热门文章缓存失败", e);
        }
        
        return hotArticles;
    }

    @Override
    public Page<ArticleVO> listArticles(ArticleQueryDTO queryDTO) {
        Page<Article> page = new Page<>(queryDTO.getPageNum(), queryDTO.getPageSize());
        LambdaQueryWrapper<Article> wrapper = new LambdaQueryWrapper<>();
        
        if (queryDTO.getCategoryId() != null) {
            wrapper.eq(Article::getCategoryId, queryDTO.getCategoryId());
        }
        if (queryDTO.getStatus() != null) {
            wrapper.eq(Article::getStatus, queryDTO.getStatus());
        } else {
            wrapper.eq(Article::getStatus, 1); // 默认查询已发布的
        }
        if (queryDTO.getKeyword() != null) {
            wrapper.and(w -> w.like(Article::getTitle, queryDTO.getKeyword())
                    .or().like(Article::getSummary, queryDTO.getKeyword())
                    .or().like(Article::getContent, queryDTO.getKeyword()));
        }
        if (queryDTO.getTagId() != null) {
            List<Long> articleIds = articleTagMapper.selectList(new LambdaQueryWrapper<ArticleTag>()
                    .eq(ArticleTag::getTagId, queryDTO.getTagId()))
                    .stream().map(ArticleTag::getArticleId).collect(Collectors.toList());
            if (articleIds.isEmpty()) return new Page<>();
            wrapper.in(Article::getId, articleIds);
        }

        wrapper.orderByDesc(Article::getCreateTime);
        baseMapper.selectPage(page, wrapper);

        Page<ArticleVO> voPage = new Page<>();
        BeanUtils.copyProperties(page, voPage);
        
        List<ArticleVO> voList = page.getRecords().stream().map(article -> {
            ArticleVO vo = new ArticleVO();
            BeanUtils.copyProperties(article, vo);
            Category category = categoryMapper.selectById(article.getCategoryId());
            if (category != null) vo.setCategoryName(category.getName());

            // 列表页也补齐标签名，前端无需再为每篇文章单独请求标签接口。
            List<Long> tagIds = articleTagMapper.selectList(new LambdaQueryWrapper<ArticleTag>()
                    .eq(ArticleTag::getArticleId, article.getId()))
                    .stream().map(ArticleTag::getTagId).collect(Collectors.toList());
            if (!tagIds.isEmpty()) {
                List<String> tagNames = tagMapper.selectBatchIds(tagIds)
                        .stream().map(Tag::getName).collect(Collectors.toList());
                vo.setTags(tagNames);
            }
            return vo;
        }).collect(Collectors.toList());
        
        voPage.setRecords(voList);
        return voPage;
    }

    @Override
    @Transactional
    public ArticleVO getArticleDetail(Long id, String ip) {
        Article article = baseMapper.selectById(id);
        if (article == null) throw new RuntimeException("文章不存在");

        // 阅读量防刷：同一文章 + 同一IP 24小时只计一次，避免刷新页面刷高阅读量。
        String key = "article:view:" + id + ":" + ip;
        if (Boolean.FALSE.equals(redisTemplate.hasKey(key))) {
            article.setViewCount(defaultInt(article.getViewCount()) + 1);
            article.setHotScore(calculateHotScore(article));
            baseMapper.updateById(article);
            redisTemplate.opsForValue().set(key, "1", 24, TimeUnit.HOURS);
            redisTemplate.delete(HOT_ARTICLE_CACHE_KEY);
        }

        return toArticleVO(article);
    }

    @Override
    @Transactional
    public Long saveArticle(ArticleSaveDTO dto, String operatorUsername) {
        Article article = dto.getId() == null ? new Article() : getById(dto.getId());
        if (article == null) {
            throw new RuntimeException("文章不存在");
        }

        boolean creating = article.getId() == null;
        BeanUtils.copyProperties(dto, article);
        article.setViewCount(defaultInt(article.getViewCount()));
        article.setCommentCount(defaultInt(article.getCommentCount()));
        article.setLikeCount(defaultInt(article.getLikeCount()));
        article.setFavoriteCount(defaultInt(article.getFavoriteCount()));
        article.setIsTop(defaultInt(article.getIsTop()));
        article.setIsRecommended(defaultInt(article.getIsRecommended()));
        article.setAllowComment(article.getAllowComment() == null ? 1 : article.getAllowComment());
        article.setHotScore(calculateHotScore(article));

        // 兼容“发布文章”与“定时发布”两类入口：直接发布时自动补发布时间。
        if (Objects.equals(article.getStatus(), 1) && article.getPublishTime() == null) {
            article.setPublishTime(LocalDateTime.now());
        }
        if (Objects.equals(article.getStatus(), 4) && article.getScheduleTime() == null) {
            throw new RuntimeException("定时发布必须设置发布时间");
        }

        if (creating) {
            article.setAuthorId(resolveUserId(operatorUsername));
            article.setDeleted(0);
            article.setCreateTime(LocalDateTime.now());
        }
        article.setUpdateTime(LocalDateTime.now());
        saveOrUpdate(article);

        // 文章主体和标签关系必须放在同一事务内，避免文章保存成功但标签半同步。
        syncArticleTags(article.getId(), dto.getTagIds());
        saveVersion(article, dto.getChangeNote(), resolveUserId(operatorUsername));
        refreshCategoryAndTagCounts(article.getCategoryId(), dto.getTagIds());
        redisTemplate.delete(HOT_ARTICLE_CACHE_KEY);
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
        removeById(id);
        articleTagMapper.delete(new LambdaQueryWrapper<ArticleTag>().eq(ArticleTag::getArticleId, id));
        redisTemplate.delete(HOT_ARTICLE_CACHE_KEY);
        auditService.record("ARTICLE_DELETE", "ARTICLE", id, article.getTitle(), true, null);
    }

    private ArticleVO toArticleVO(Article article) {
        ArticleVO vo = new ArticleVO();
        BeanUtils.copyProperties(article, vo);

        Category category = article.getCategoryId() == null ? null : categoryMapper.selectById(article.getCategoryId());
        if (category != null) {
            vo.setCategoryName(category.getName());
        }

        List<Long> tagIds = articleTagMapper.selectList(new LambdaQueryWrapper<ArticleTag>()
                        .eq(ArticleTag::getArticleId, article.getId()))
                .stream()
                .map(ArticleTag::getTagId)
                .collect(Collectors.toList());
        vo.setTagIds(tagIds);
        if (!tagIds.isEmpty()) {
            vo.setTags(tagMapper.selectBatchIds(tagIds).stream().map(Tag::getName).collect(Collectors.toList()));
        }
        return vo;
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
        Long versionCount = articleVersionMapper.selectCount(new LambdaQueryWrapper<ArticleVersion>()
                .eq(ArticleVersion::getArticleId, article.getId()));
        ArticleVersion version = new ArticleVersion();
        BeanUtils.copyProperties(article, version);
        version.setId(null);
        version.setArticleId(article.getId());
        version.setVersionNo(versionCount.intValue() + 1);
        version.setEditorId(editorId);
        version.setChangeNote(changeNote);
        version.setCreateTime(LocalDateTime.now());
        articleVersionMapper.insert(version);
    }

    private void refreshCategoryAndTagCounts(Long categoryId, List<Long> tagIds) {
        if (categoryId != null) {
            Category category = categoryMapper.selectById(categoryId);
            if (category != null) {
                category.setArticleCount(Math.toIntExact(baseMapper.selectCount(new LambdaQueryWrapper<Article>()
                        .eq(Article::getCategoryId, categoryId)
                        .eq(Article::getStatus, 1))));
                categoryMapper.updateById(category);
            }
        }
        if (tagIds != null) {
            tagIds.stream().distinct().forEach(tagId -> {
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
