package com.blog.backend.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.blog.backend.common.BusinessException;
import com.blog.backend.common.Result;
import com.blog.backend.dto.TagSaveDTO;
import com.blog.backend.entity.ArticleTag;
import com.blog.backend.entity.Tag;
import com.blog.backend.mapper.ArticleTagMapper;
import com.blog.backend.service.TagService;
import io.swagger.v3.oas.annotations.Operation;
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

@io.swagger.v3.oas.annotations.tags.Tag(name = "标签管理")
@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;
    private final ArticleTagMapper articleTagMapper;

    @Operation(summary = "所有标签")
    @GetMapping
    public Result<List<Tag>> list() {
        return Result.success(tagService.list());
    }

    @Operation(summary = "添加标签")
    @PostMapping
    public Result<Void> save(@Valid @RequestBody TagSaveDTO dto) {
        Tag tag = new Tag();
        BeanUtils.copyProperties(dto, tag);
        tagService.saveOrUpdate(tag);
        return Result.success();
    }

    @Operation(summary = "删除标签")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        Long articleCount = articleTagMapper.selectCount(new LambdaQueryWrapper<ArticleTag>().eq(ArticleTag::getTagId, id));
        if (articleCount > 0) {
            throw new BusinessException("该标签下仍有关联文章，请先迁移文章");
        }
        tagService.removeById(id);
        return Result.success();
    }
}
