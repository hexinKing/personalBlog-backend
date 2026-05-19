package com.blog.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.blog.backend.common.ArticleStatus;
import com.blog.backend.common.CommentStatus;
import com.blog.backend.entity.Article;
import com.blog.backend.entity.Category;
import com.blog.backend.entity.Comment;
import com.blog.backend.entity.Tag;
import com.blog.backend.mapper.ArticleMapper;
import com.blog.backend.mapper.CategoryMapper;
import com.blog.backend.mapper.CommentMapper;
import com.blog.backend.mapper.TagMapper;
import com.blog.backend.service.ArticleService;
import com.blog.backend.service.DashboardService;
import com.blog.backend.vo.DashboardVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DashboardServiceImpl implements DashboardService {
    private final ArticleMapper articleMapper;
    private final CommentMapper commentMapper;
    private final CategoryMapper categoryMapper;
    private final TagMapper tagMapper;
    private final ArticleService articleService;

    @Override
    public DashboardVO getOverview() {
        DashboardVO vo = new DashboardVO();
        vo.setTotalArticles(articleMapper.selectCount(new LambdaQueryWrapper<Article>()));
        vo.setPublishedArticles(articleMapper.selectCount(new LambdaQueryWrapper<Article>().eq(Article::getStatus, ArticleStatus.PUBLISHED)));
        vo.setDraftArticles(articleMapper.selectCount(new LambdaQueryWrapper<Article>().eq(Article::getStatus, ArticleStatus.DRAFT)));
        vo.setTotalComments(commentMapper.selectCount(new LambdaQueryWrapper<Comment>()));
        vo.setPendingComments(commentMapper.selectCount(new LambdaQueryWrapper<Comment>().eq(Comment::getStatus, CommentStatus.PENDING)));
        vo.setCategoryCount(categoryMapper.selectCount(new LambdaQueryWrapper<Category>()));
        vo.setTagCount(tagMapper.selectCount(new LambdaQueryWrapper<Tag>()));
        vo.setHotArticles(articleService.listHotArticles());
        vo.setTotalViews(sumTotalViews());
        return vo;
    }

    private Long sumTotalViews() {
        return articleMapper.selectObjs(new QueryWrapper<Article>().select("COALESCE(SUM(view_count), 0)"))
                .stream()
                .findFirst()
                .map(value -> ((Number) value).longValue())
                .orElse(0L);
    }
}
