package com.blog.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blog.backend.common.BusinessException;
import com.blog.backend.common.Result;
import com.blog.backend.dto.CategorySaveDTO;
import com.blog.backend.entity.Article;
import com.blog.backend.entity.Category;
import com.blog.backend.mapper.ArticleMapper;
import com.blog.backend.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "分类管理")
@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;
    private final ArticleMapper articleMapper;

    @Operation(summary = "所有分类")
    @GetMapping
    public Result<List<Category>> list() {
        return Result.success(categoryService.list());
    }

    @Operation(summary = "添加分类")
    @PostMapping
    public Result<Void> save(@Valid @RequestBody CategorySaveDTO dto) {
        Category category = new Category();
        BeanUtils.copyProperties(dto, category);
        categoryService.saveOrUpdate(category);
        return Result.success();
    }

    @Operation(summary = "删除分类")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        Long articleCount = articleMapper.selectCount(new LambdaQueryWrapper<Article>().eq(Article::getCategoryId, id));
        if (articleCount > 0) {
            throw new BusinessException("该分类下仍有关联文章，请先迁移文章");
        }
        categoryService.removeById(id);
        return Result.success();
    }
}
