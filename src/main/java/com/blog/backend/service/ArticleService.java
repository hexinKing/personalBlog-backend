package com.blog.backend.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.blog.backend.dto.ArticleSaveDTO;
import com.blog.backend.entity.Article;
import com.blog.backend.vo.ArticleVO;
import com.blog.backend.dto.ArticleQueryDTO;

import java.util.List;

public interface ArticleService extends IService<Article> {
    Page<ArticleVO> listArticles(ArticleQueryDTO queryDTO);
    ArticleVO getArticleDetail(Long id, String ip);
    List<Article> listHotArticles();
    Long saveArticle(ArticleSaveDTO dto, String operatorUsername);
    void deleteArticle(Long id);
}
