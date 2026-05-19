package com.blog.backend.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.backend.common.ClientIpUtils;
import com.blog.backend.common.Result;
import com.blog.backend.common.SecurityUtils;
import com.blog.backend.dto.ArticleQueryDTO;
import com.blog.backend.dto.ArticleSaveDTO;
import com.blog.backend.entity.Article;
import com.blog.backend.service.ArticleService;
import com.blog.backend.vo.ArticleVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "文章管理")
@RestController
@RequestMapping("/api/articles")
@RequiredArgsConstructor
public class ArticleController {

    private final ArticleService articleService;

    @Operation(summary = "文章列表")
    @GetMapping
    public Result<Page<ArticleVO>> list(ArticleQueryDTO queryDTO) {
        return Result.success(articleService.listArticles(queryDTO));
    }

    @Operation(summary = "热门文章")
    @GetMapping("/hot")
    public Result<List<Article>> listHot() {
        return Result.success(articleService.listHotArticles());
    }

    @Operation(summary = "文章详情")
    @GetMapping("/{id}")
    public Result<ArticleVO> getDetail(@PathVariable Long id, HttpServletRequest request) {
        return Result.success(articleService.getArticleDetail(id, ClientIpUtils.getClientIp(request)));
    }

    @Operation(summary = "发布文章")
    @PostMapping
    public Result<Long> save(@Valid @RequestBody ArticleSaveDTO article) {
        return Result.success(articleService.saveArticle(article, SecurityUtils.currentUsername()));
    }

    @Operation(summary = "删除文章")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        articleService.deleteArticle(id);
        return Result.success();
    }
}
