package com.blog.backend.controller;

import com.blog.backend.common.Result;
import com.blog.backend.dto.CategorySaveDTO;
import com.blog.backend.entity.Category;
import com.blog.backend.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "分类管理")
@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

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
        categoryService.removeById(id);
        return Result.success();
    }
}
