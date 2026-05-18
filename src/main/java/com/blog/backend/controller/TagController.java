package com.blog.backend.controller;

import com.blog.backend.common.Result;
import com.blog.backend.dto.TagSaveDTO;
import com.blog.backend.entity.Tag;
import com.blog.backend.service.TagService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@io.swagger.v3.oas.annotations.tags.Tag(name = "标签管理")
@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

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
        tagService.removeById(id);
        return Result.success();
    }
}
