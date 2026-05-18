package com.blog.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
        vo.setPublishedArticles(articleMapper.selectCount(new LambdaQueryWrapper<Article>().eq(Article::getStatus, 1)));
        vo.setDraftArticles(articleMapper.selectCount(new LambdaQueryWrapper<Article>().eq(Article::getStatus, 0)));
        vo.setTotalComments(commentMapper.selectCount(new LambdaQueryWrapper<Comment>()));
        vo.setPendingComments(commentMapper.selectCount(new LambdaQueryWrapper<Comment>().eq(Comment::getStatus, 0)));
        vo.setCategoryCount(categoryMapper.selectCount(new LambdaQueryWrapper<Category>()));
        vo.setTagCount(tagMapper.selectCount(new LambdaQueryWrapper<Tag>()));
        vo.setHotArticles(articleService.listHotArticles());

        Long totalViews = articleMapper.selectList(new LambdaQueryWrapper<Article>().select(Article::getViewCount))
                .stream()
                .mapToLong(article -> article.getViewCount() == null ? 0L : article.getViewCount())
                .sum();
        vo.setTotalViews(totalViews);
        return vo;
    }
}
