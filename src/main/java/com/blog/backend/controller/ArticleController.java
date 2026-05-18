package com.blog.backend.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.blog.backend.common.SecurityUtils;
import com.blog.backend.common.Result;
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
import org.springframework.web.bind.annotation.*;

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
        String ip = request.getRemoteAddr();
        return Result.success(articleService.getArticleDetail(id, ip));
    }

    @Operation(summary = "发布文章")
    @PostMapping
    public Result<Long> save(@Valid @RequestBody ArticleSaveDTO article) {
        // 新建和更新统一入口，标签同步、版本历史和缓存失效由服务层事务处理。
        return Result.success(articleService.saveArticle(article, SecurityUtils.currentUsername()));
    }

    @Operation(summary = "删除文章")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        articleService.deleteArticle(id);
        return Result.success();
    }
}
